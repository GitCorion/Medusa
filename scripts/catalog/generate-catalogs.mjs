#!/usr/bin/env node
/**
 * Genera los catálogos de descubrimiento (plataformas, tendencias y popular regional) a
 * partir de TMDB y los deja en `docs/catalogs/<REGION>/` para servirlos con Firebase Hosting.
 *
 * La app descarga estos ficheros ya preparados y solo cruza los `tmdbId` contra la biblioteca
 * del usuario. La clave de TMDB vive aquí (GitHub Actions), nunca en el APK.
 *
 * Uso:
 *   TMDB_API_KEY=xxx CATALOG_REGIONS=ES,MX node scripts/catalog/generate-catalogs.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT_ROOT = path.join(ROOT, 'docs', 'catalogs');
const TMDB = 'https://api.themoviedb.org/3';
const FORMAT_VERSION = 1;
const CONCURRENCY = 8;
const LANG = 'es-ES';
const MAX_TRENDING_PAGES = 10;
const MAX_POPULAR_PAGES = 10;

const apiKey = process.env.TMDB_API_KEY;
if (!apiKey) {
  console.error('Falta TMDB_API_KEY');
  process.exit(1);
}

const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'platforms.json'), 'utf8'));
const regions = (process.env.CATALOG_REGIONS || config.regions.join(','))
  .split(',')
  .map((s) => s.trim().toUpperCase())
  .filter(Boolean);

function q(params) {
  return Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
}

async function fetchJson(url, attempt = 0) {
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (e) {
    if (attempt >= 4) throw e;
    await new Promise((r) => setTimeout(r, 500 * 2 ** attempt));
    return fetchJson(url, attempt + 1);
  }
}

function compact(item, isTv) {
  const title = item.title || item.name || '';
  const date = item.release_date || item.first_air_date || '';
  const year = date ? Number(date.slice(0, 4)) || null : null;
  const popularity = Math.round((item.popularity || 0) * 100) / 100;
  return [item.id, isTv ? 1 : 0, popularity, title, year];
}

async function fetchDiscoverPages(kind, { providerId, region, maxPages }) {
  const base = {
    api_key: apiKey,
    watch_region: region,
    with_watch_monetization_types: 'flatrate|free|ads',
    sort_by: 'popularity.desc',
    language: LANG,
  };
  if (providerId) base.with_watch_providers = providerId;

  const first = await fetchJson(`${TMDB}/discover/${kind}?${q({ ...base, page: 1 })}`);
  const total = Math.min(first.total_pages || 1, maxPages || 500);
  const results = [...(first.results || [])];
  for (let p = 2; p <= total; p += CONCURRENCY) {
    const pages = [];
    for (let i = p; i < Math.min(p + CONCURRENCY, total + 1); i++) pages.push(i);
    const batch = await Promise.all(
      pages.map((pg) => fetchJson(`${TMDB}/discover/${kind}?${q({ ...base, page: pg })}`))
    );
    for (const pg of batch) results.push(...(pg.results || []));
  }
  return results;
}

async function fetchTrendingPages(kind, maxPages) {
  const out = [];
  for (let p = 1; p <= maxPages; p++) {
    const d = await fetchJson(
      `${TMDB}/trending/${kind}/day?${q({ api_key: apiKey, page: p, language: LANG })}`
    );
    out.push(...(d.results || []));
    if (p >= (d.total_pages || 1)) break;
  }
  return out;
}

function dedupe(items) {
  const seen = new Set();
  return items.filter((x) => {
    const key = `${x[0]}:${x[1]}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

async function providerCatalog(providerId, region) {
  const [movies, tv] = await Promise.all([
    fetchDiscoverPages('movie', { providerId, region }),
    fetchDiscoverPages('tv', { providerId, region }),
  ]);
  return dedupe(
    [...movies.map((x) => compact(x, false)), ...tv.map((x) => compact(x, true))]
      .sort((a, b) => b[2] - a[2])
  );
}

async function trendingCatalog() {
  const [movies, tv] = await Promise.all([
    fetchTrendingPages('movie', MAX_TRENDING_PAGES),
    fetchTrendingPages('tv', MAX_TRENDING_PAGES),
  ]);
  const interleaved = [];
  const max = Math.max(movies.length, tv.length);
  for (let i = 0; i < max; i++) {
    if (i < movies.length) interleaved.push(compact(movies[i], false));
    if (i < tv.length) interleaved.push(compact(tv[i], true));
  }
  // El orden del array es el rank real del trending.
  return dedupe(interleaved);
}

async function popularCatalog(region) {
  const [movies, tv] = await Promise.all([
    fetchDiscoverPages('movie', { providerId: null, region, maxPages: MAX_POPULAR_PAGES }),
    fetchDiscoverPages('tv', { providerId: null, region, maxPages: MAX_POPULAR_PAGES }),
  ]);
  return dedupe(
    [...movies.map((x) => compact(x, false)), ...tv.map((x) => compact(x, true))]
      .sort((a, b) => b[2] - a[2])
  );
}

// ---------------------------------------------------------------------------
// Top 10 OFICIAL de Netflix (Tudum), semanal y por país.
// ---------------------------------------------------------------------------
const NETFLIX_TSV_URL = 'https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv';

function normalizeTitle(value) {
  return (value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]/g, '');
}

/** Descarga el TSV oficial una vez y devuelve el último semana por país. */
async function fetchNetflixTop10ByCountry() {
  const response = await fetch(NETFLIX_TSV_URL, {
    headers: { 'User-Agent': 'Mozilla/5.0', Accept: 'text/tab-separated-values,*/*' },
  });
  if (!response.ok) throw new Error(`Netflix TSV HTTP ${response.status}`);
  const text = await response.text();
  const lines = text.split('\n');
  const header = lines[0].split('\t');
  const col = (name) => header.indexOf(name);
  const iCountry = col('country_iso2');
  const iWeek = col('week');
  const iCategory = col('category');
  const iRank = col('weekly_rank');
  const iTitle = col('show_title');
  const iSeason = col('season_title');

  const latestWeek = new Map();
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i]) continue;
    const cols = lines[i].split('\t');
    if (cols.length <= iTitle) continue;
    const iso = cols[iCountry];
    const week = cols[iWeek];
    if (!iso || !week) continue;
    const prev = latestWeek.get(iso);
    if (!prev || week > prev) latestWeek.set(iso, week);
  }

  const byCountry = new Map();
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i]) continue;
    const cols = lines[i].split('\t');
    if (cols.length <= iTitle) continue;
    const iso = cols[iCountry];
    if (latestWeek.get(iso) !== cols[iWeek]) continue;
    const category = cols[iCategory];
    const rank = Number.parseInt(cols[iRank], 10);
    const title = cols[iTitle];
    if (!title || !Number.isFinite(rank)) continue;
    if (!byCountry.has(iso)) byCountry.set(iso, { week: cols[iWeek], films: [], tv: [] });
    const entry = { rank, title, season: cols[iSeason] };
    if (category === 'Films') byCountry.get(iso).films.push(entry);
    else if (category === 'TV') byCountry.get(iso).tv.push(entry);
  }
  byCountry.forEach((value) => {
    value.films.sort((a, b) => a.rank - b.rank);
    value.tv.sort((a, b) => a.rank - b.rank);
  });
  return byCountry;
}

/** Resuelve un título del TSV a su TMDB ID (el TSV no trae año ni ID). */
async function resolveTmdbTitle(title, isTv) {
  const kind = isTv ? 'tv' : 'movie';
  const url = `${TMDB}/search/${kind}?${q({
    api_key: apiKey,
    query: title,
    language: LANG,
    include_adult: false,
    page: 1,
  })}`;
  try {
    const data = await fetchJson(url);
    const results = data.results || [];
    const target = normalizeTitle(title);
    let best = null;
    let bestScore = -1;
    for (const result of results) {
      const names = [result.title, result.name, result.original_title, result.original_name]
        .filter(Boolean)
        .map(normalizeTitle);
      let score = 0;
      if (names.includes(target)) score = 100;
      else if (names.some((name) => name.includes(target) || target.includes(name))) score = 50;
      score += Math.min(result.popularity || 0, 100) / 100;
      if (score > bestScore) {
        bestScore = score;
        best = result;
      }
    }
    if (!best) return null;
    return {
      tmdbId: best.id,
      title: best.title || best.name || title,
      type: isTv ? 'tv' : 'movie',
    };
  } catch {
    return null;
  }
}

async function netflixTop10For(region, netflixByCountry) {
  const data = netflixByCountry.get(region);
  if (!data) return null;
  const films = [];
  for (const entry of data.films) {
    const resolved = await resolveTmdbTitle(entry.title, false);
    if (resolved) films.push({ rank: entry.rank, ...resolved });
  }
  const tv = [];
  for (const entry of data.tv) {
    const resolved = await resolveTmdbTitle(entry.title, true);
    if (resolved) tv.push({ rank: entry.rank, ...resolved });
  }
  // Varias partes/seasons del mismo título comparten tmdbId: nos quedamos con la mejor.
  const dedupeByTmdb = (list) => {
    const seen = new Set();
    return list.filter((item) => {
      const key = String(item.tmdbId);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  };
  return { week: data.week, films: dedupeByTmdb(films), tv: dedupeByTmdb(tv) };
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data));
}

async function main() {
  process.stdout.write('Descargando Top 10 oficial de Netflix... ');
  const netflixByCountry = await fetchNetflixTop10ByCountry().catch((e) => {
    console.log(`ERROR ${e.message}`);
    return new Map();
  });
  console.log(`${netflixByCountry.size} países`);

  for (const region of regions) {
    const dir = path.join(OUT_ROOT, region);
    const platformFiles = {};

    for (const platform of config.platforms) {
      process.stdout.write(`[${region}] ${platform.id}... `);
      try {
        const items = await providerCatalog(platform.providerId, region);
        const rel = `platforms/${platform.id}.json`;
        writeJson(path.join(dir, rel), { v: FORMAT_VERSION, items });
        platformFiles[platform.id] = rel;
        console.log(items.length);
      } catch (e) {
        console.log(`ERROR ${e.message}`);
      }
    }

    process.stdout.write(`[${region}] trending... `);
    try {
      const items = await trendingCatalog();
      writeJson(path.join(dir, 'trending_day.json'), { v: FORMAT_VERSION, items });
      console.log(items.length);
    } catch (e) {
      console.log(`ERROR ${e.message}`);
    }

    process.stdout.write(`[${region}] popular... `);
    try {
      const items = await popularCatalog(region);
      writeJson(path.join(dir, 'popular.json'), { v: FORMAT_VERSION, items });
      console.log(items.length);
    } catch (e) {
      console.log(`ERROR ${e.message}`);
    }

    process.stdout.write(`[${region}] netflix top10... `);
    let netflixTop10File = null;
    try {
      const netflix = await netflixTop10For(region, netflixByCountry);
      if (netflix) {
        writeJson(path.join(dir, 'netflix_top10.json'), {
          v: FORMAT_VERSION,
          region,
          week: netflix.week,
          films: netflix.films,
          tv: netflix.tv,
        });
        netflixTop10File = 'netflix_top10.json';
        console.log(`films ${netflix.films.length}, tv ${netflix.tv.length}`);
      } else {
        console.log('sin datos para la región');
      }
    } catch (e) {
      console.log(`ERROR ${e.message}`);
    }

    const index = {
      v: FORMAT_VERSION,
      region,
      updatedAt: Date.now(),
      platforms: platformFiles,
      trending_day: 'trending_day.json',
      popular: 'popular.json',
    };
    if (netflixTop10File) index.netflix_top10 = netflixTop10File;
    writeJson(path.join(dir, 'index.json'), index);
  }
  console.log(`Catálogos generados en ${OUT_ROOT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
