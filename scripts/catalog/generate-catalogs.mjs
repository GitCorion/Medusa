#!/usr/bin/env node
/**
 * Genera los catálogos de descubrimiento (plataformas, tendencias y popular regional) a
 * partir de TMDB y los deja en `docs/catalogs/<REGION>/`. Se publican versionándolos en el
 * repositorio y se sirven por raw.githubusercontent.com; no hay Hosting de terceros.
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

async function providerCatalog(providerId, region, trendingRanks = null) {
  const [movies, tv] = await Promise.all([
    fetchDiscoverPages('movie', { providerId, region }),
    fetchDiscoverPages('tv', { providerId, region }),
  ]);
  const all = dedupe([...movies.map((x) => compact(x, false)), ...tv.map((x) => compact(x, true))]);
  if (!trendingRanks || trendingRanks.size === 0) {
    return all.sort((a, b) => b[2] - a[2]);
  }
  return all.sort((a, b) => {
    const keyA = `${a[0]}:${a[1]}`;
    const keyB = `${b[0]}:${b[1]}`;
    const rankA = trendingRanks.get(keyA);
    const rankB = trendingRanks.get(keyB);
    if (rankA !== undefined && rankB !== undefined) return rankA - rankB;
    if (rankA !== undefined) return -1;
    if (rankB !== undefined) return 1;
    return b[2] - a[2];
  });
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

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data));
}

async function main() {
  for (const region of regions) {
    const dir = path.join(OUT_ROOT, region);
    const platformFiles = {};

    process.stdout.write(`[${region}] trending... `);
    let trendingItems = [];
    try {
      trendingItems = await trendingCatalog();
      writeJson(path.join(dir, 'trending_day.json'), { v: FORMAT_VERSION, items: trendingItems });
      console.log(trendingItems.length);
    } catch (e) {
      console.log(`ERROR ${e.message}`);
    }

    const trendingRanks = new Map();
    trendingItems.forEach((x, i) => trendingRanks.set(`${x[0]}:${x[1]}`, i + 1));

    for (const platform of config.platforms) {
      process.stdout.write(`[${region}] ${platform.id}... `);
      try {
        const items = await providerCatalog(platform.providerId, region, trendingRanks);
        const rel = `platforms/${platform.id}.json`;
        writeJson(path.join(dir, rel), { v: FORMAT_VERSION, items });
        platformFiles[platform.id] = rel;
        console.log(items.length);
      } catch (e) {
        console.log(`ERROR ${e.message}`);
      }
    }

    process.stdout.write(`[${region}] popular... `);
    try {
      const items = await popularCatalog(region);
      writeJson(path.join(dir, 'popular.json'), { v: FORMAT_VERSION, items });
      console.log(items.length);
    } catch (e) {
      console.log(`ERROR ${e.message}`);
    }

    writeJson(path.join(dir, 'index.json'), {
      v: FORMAT_VERSION,
      region,
      updatedAt: Date.now(),
      platforms: platformFiles,
      trending_day: 'trending_day.json',
      popular: 'popular.json',
    });
  }
  console.log(`Catálogos generados en ${OUT_ROOT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
