import type { VercelRequest, VercelResponse } from "@vercel/node";

const catalog = (type: "movie" | "series", id: string, name: string) => ({
  type,
  id,
  name,
  extra: [{ name: "skip" }, { name: "search" }],
});

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Cache-Control", "public, s-maxage=3600, stale-while-revalidate=86400");
  return res.status(200).json({
    id: "com.play.tmdb.catalog",
    version: "1.0.0",
    name: "PLAY Özel Katalog",
    description: "TMDB'den gerçek yayın tarihleri ve Türkiye filtreleriyle otomatik güncellenen film ve dizi katalogları.",
    resources: ["catalog", "meta"],
    types: ["movie", "series"],
    idPrefixes: ["tt"],
    catalogs: [
      catalog("movie", "play-new-movies", "PLAY • Yeni Eklenen Filmler"),
      catalog("series", "play-new-series", "PLAY • Yeni Eklenen Diziler"),
      catalog("movie", "play-week-movies", "PLAY • Haftanın Filmleri"),
      catalog("movie", "play-turkish-movies", "PLAY • Yerli Filmler"),
      catalog("series", "play-turkish-series", "PLAY • Yerli Diziler")
    ],
    behaviorHints: { configurable: true, configurationRequired: false }
  });
}
