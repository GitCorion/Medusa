/**
 * Proxy de TMDB para Medusa.
 *
 * La clave de TMDB vive solo en el servidor (Secret Manager). La app llama a este endpoint y
 * aquí se inyecta la clave, se aplica una lista blanca de rutas y se cachea la respuesta para
 * que miles de usuarios compartan las mismas llamadas.
 *
 * Hosting lo expone en: https://medusa-fd388.web.app/api/tmdb/<ruta>
 */
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { logger } = require("firebase-functions");

const TMDB_KEY = defineSecret("TMDB_API_KEY");
const GITHUB_TOKEN = defineSecret("GITHUB_TOKEN");
const TMDB_BASE = "https://api.themoviedb.org/3/";
const CATALOG_BASE = "https://medusa-catalogs.web.app";
const GITHUB_REPO = "GitCorion/Medusa";

// Rutas que la app necesita. Cualquier otra se rechaza.
const ALLOWED = [
  /^trending\/(all|movie|tv)\/(day|week)$/,
  /^discover\/(movie|tv)$/,
  /^(movie|tv)\/top_rated$/,
  /^(movie|tv)\/\d+$/,
  /^(movie|tv)\/\d+\/(recommendations|videos)$/,
];

const cache = new Map();
const MAX_ENTRIES = 800;

/** ¿Hay ya una ejecución del workflow en curso? Evita disparos duplicados (coste de minutos). */
async function hasActiveCatalogRun(token) {
  try {
    const response = await fetch(
      `https://api.github.com/repos/${GITHUB_REPO}/actions/workflows/catalogs.yml/runs?per_page=1`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          "User-Agent": "medusa-catalog",
        },
      }
    );
    if (!response.ok) return false;
    const data = await response.json();
    const run = (data.workflow_runs || [])[0];
    if (!run) return false;
    return ["queued", "in_progress", "requested", "waiting", "pending"].includes(run.status);
  } catch (_) {
    return false;
  }
}

function ttlMs(path) {
  if (path.startsWith("trending/")) return 15 * 60 * 1000;
  if (path.startsWith("discover/")) return 6 * 60 * 60 * 1000;
  if (path.endsWith("/videos")) return 24 * 60 * 60 * 1000;
  return 12 * 60 * 60 * 1000;
}

function evictIfNeeded() {
  while (cache.size > MAX_ENTRIES) {
    const oldest = cache.keys().next().value;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
}

exports.tmdbProxy = onRequest(
  {
    region: "us-central1",
    secrets: [TMDB_KEY],
    memory: "256MiB",
    timeoutSeconds: 30,
  },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }
    if (req.method !== "GET") {
      res.status(405).json({ error: "method_not_allowed" });
      return;
    }

    const fullPath = (req.originalUrl || req.url || req.path || "").split("?")[0];
    const rawPath = fullPath
      .replace(/^\/api\/tmdb\/?/, "")
      .replace(/^\/+/, "")
      .replace(/\/+$/, "");

    if (!ALLOWED.some((r) => r.test(rawPath))) {
      res.status(404).json({ error: "endpoint_not_allowed" });
      return;
    }

    // La app puede mandar api_key vacía o la suya: aquí se descarta siempre.
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(req.query || {})) {
      if (key === "api_key") continue;
      if (Array.isArray(value)) value.forEach((v) => params.append(key, String(v)));
      else if (value !== undefined) params.append(key, String(value));
    }

    const cacheKey = rawPath + "?" + params.toString();
    const now = Date.now();
    const hit = cache.get(cacheKey);
    if (hit && hit.expires > now) {
      res.set(
        "Cache-Control",
        `public, max-age=${Math.max(1, Math.floor((hit.expires - now) / 1000))}`
      );
      res.status(200).type("application/json").send(hit.body);
      return;
    }

    const ttl = ttlMs(rawPath);
    params.set("api_key", TMDB_KEY.value());
    const url = `${TMDB_BASE}${rawPath}?${params.toString()}`;

    try {
      const upstream = await fetch(url, { headers: { Accept: "application/json" } });
      const body = await upstream.text();
      if (upstream.ok) {
        cache.set(cacheKey, { expires: now + ttl, body });
        evictIfNeeded();
      }
      res.set(
        "Cache-Control",
        upstream.ok ? `public, max-age=${Math.floor(ttl / 1000)}` : "no-store"
      );
      res.status(upstream.status).type("application/json").send(body);
    } catch (e) {
      console.error("TMDB proxy error", e);
      res.status(502).json({ error: "upstream_error" });
    }
  }
);

/**
 * Punto de entrada para regiones sin catálogo estático.
 *
 * - Si el catálogo ya existe en Hosting: responde "ready".
 * - Si no, dispara el workflow de GitHub que lo genera y publica.
 *   La app no espera: mientras tanto usa el proxy de TMDB y en el siguiente arranque
 *   ya descarga el catálogo estático.
 *
 * Requiere el secreto GITHUB_TOKEN con permiso `actions:write` sobre el repositorio.
 */
exports.requestRegionCatalog = onRequest(
  {
    region: "us-central1",
    secrets: [GITHUB_TOKEN],
    memory: "256MiB",
    timeoutSeconds: 30,
  },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }
    if (req.method !== "GET" && req.method !== "POST") {
      res.status(405).json({ error: "method_not_allowed" });
      return;
    }

    const region = String(req.query.region || "").toUpperCase();
    if (!/^[A-Z]{2}$/.test(region)) {
      res.status(400).json({ error: "invalid_region" });
      return;
    }

    try {
      const head = await fetch(`${CATALOG_BASE}/${region}/index.json`, { method: "HEAD" });
      if (head.ok) {
        res.status(200).json({ status: "ready", region });
        return;
      }
    } catch (_) {
      // Si el CDN no responde, seguimos e intentamos disparar la generación.
    }

    const token = GITHUB_TOKEN.value();
    if (!token || token === "none") {
      res.status(200).json({ status: "disabled", region });
      return;
    }

    // Si ya hay una generación en curso, no lanzamos otra (protege los minutos de Actions).
    if (await hasActiveCatalogRun(token)) {
      res.status(202).json({ status: "queued", region, reason: "already_running" });
      return;
    }

    try {
      const response = await fetch(
        `https://api.github.com/repos/${GITHUB_REPO}/actions/workflows/catalogs.yml/dispatches`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
            "User-Agent": "medusa-catalog",
          },
          body: JSON.stringify({ ref: "main" }),
        }
      );
      if (response.status === 204) {
        res.status(202).json({ status: "queued", region });
        return;
      }
      const text = await response.text();
      logger.warn("GitHub dispatch failed", response.status, text);
      res.status(502).json({ status: "error", region, code: response.status });
    } catch (e) {
      logger.error("requestRegionCatalog error", e);
      res.status(502).json({ status: "error", region });
    }
  }
);
