import type { VercelRequest, VercelResponse } from "@vercel/node";

type MediaType = "movie" | "tv";
type TmdbItem = {
  id: number;
  title?: string;
  name?: string;
  overview?: string;
  poster_path?: string | null;
  backdrop_path?: string | null;
  release_date?: string;
  first_air_date?: string;
  vote_average?: number;
  genre_ids?: number[];
};
type TmdbList = { results?: TmdbItem[] };

const TMDB = "https://api.themoviedb.org/3";
const IMAGE = "https://image.tmdb.org/t/p/w500";
const BACKDROP = "https://image.tmdb.org/t/p/w780";
const DAY = 24 * 60 * 60 * 1000;
const date = (d: Date) => d.toISOString().slice(0, 10);

function catalogConfig(id: string, now: Date): { path: string; type: MediaType; params: Record<string, string> } | null {
  const today = date(now);
  const recent = date(new Date(now.getTime() - 90 * DAY));
  const weekAgo = date(new Date(now.getTime() - 7 * DAY));
  switch (id) {
    case "play-new-movies":
      return { path: "discover/movie", type: "movie", params: { "primary_release_date.lte": today, "primary_release_date.gte": recent, sort_by: "primary_release_date.desc", include_adult: "false", region: "TR" } };
    case "play-new-series":
      return { path: "discover/tv", type: "tv", params: { "first_air_date.lte": today, "first_air_date.gte": recent, sort_by: "first_air_date.desc", include_adult: "false", watch_region: "TR" } };
    case "play-week-movies":
      return { path: "discover/movie", type: "movie", params: { "primary_release_date.lte": today, "primary_release_date.gte": weekAgo, sort_by: "popularity.desc", include_adult: "false", region: "TR" } };
    case "play-turkish-movies":
      return { path: "discover/movie", type: "movie", params: { with_origin_country: "TR", sort_by: "popularity.desc", include_adult: "false", "primary_release_date.lte": today } };
    case "play-turkish-series":
      return { path: "discover/tv", type: "tv", params: { with_origin_country: "TR", sort_by: "popularity.desc", include_adult: "false", "first_air_date.lte": today } };
    default:
      return null;
  }
}

async function tmdb<T>(path: string, params: Record<string, string>, apiKey: string): Promise<T> {
  const url = new URL(`${TMDB}/${path}`);
  url.searchParams.set("api_key", apiKey);
  url.searchParams.set("language", "tr-TR");
  url.searchParams.set("page", "1");
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  const response = await fetch(url, { headers: { accept: "application/json" } });
  if (!response.ok) throw new Error(`TMDB HTTP ${response.status}`);
  return response.json() as Promise<T>;
}

async function imdbId(type: MediaType, id: number, apiKey: string): Promise<string | null> {
  try {
    const data = await tmdb<{ imdb_id?: string | null }>(`${type}/${id}/external_ids`, {}, apiKey);
    return data.imdb_id?.startsWith("tt") ? data.imdb_id : null;
  } catch {
    return null;
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Cache-Control", "public, s-maxage=1800, stale-while-revalidate=43200");
  const type = String(req.query.type ?? "");
  const id = String(req.query.id ?? "");
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey) return res.status(503).json({ metas: [], error: "TMDB_API_KEY environment variable is not configured" });
  if (type !== "movie" && type !== "series") return res.status(400).json({ metas: [] });

  const config = catalogConfig(id, new Date());
  if (!config || (type === "movie") !== (config.type === "movie")) return res.status(404).json({ metas: [] });

  try {
    const data = await tmdb<TmdbList>(config.path, config.params, apiKey);
    const items = (data.results ?? []).filter((item) => item.poster_path && (item.title || item.name));
    const metas = await Promise.all(items.slice(0, 24).map(async (item) => {
      const imdb = await imdbId(config.type, item.id, apiKey);
      if (!imdb) return null;
      const name = item.title ?? item.name ?? "";
      const releaseInfo = item.release_date ?? item.first_air_date ?? "";
      return {
        id: imdb,
        type,
        name,
        poster: item.poster_path ? `${IMAGE}${item.poster_path}` : undefined,
        background: item.backdrop_path ? `${BACKDROP}${item.backdrop_path}` : undefined,
        description: item.overview || undefined,
        releaseInfo: releaseInfo.slice(0, 4) || undefined,
        imdbRating: item.vote_average ? item.vote_average.toFixed(1) : undefined,
        genres: item.genre_ids?.map(String) ?? []
      };
    }));
    return res.status(200).json({ metas: metas.filter(Boolean) });
  } catch {
    return res.status(502).json({ metas: [], error: "TMDB request failed" });
  }
}
