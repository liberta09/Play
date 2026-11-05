// Supabase Edge Function: crash-report (Deno)
// Güvenli çökme raporu iletim fonksiyonu

const MAX_BODY_BYTES = 8192; // En fazla 8 KB
const RATE_LIMIT_MAX = 5; // IP başına dakikada 5 istek
const RATE_LIMIT_WINDOW_MS = 60_000;

interface RateLimitRecord {
  count: number;
  resetAt: number;
}

const rateLimitMap = new Map<string, RateLimitRecord>();

Deno.serve(async (req: Request): Promise<Response> => {
  // 1) Yalnızca POST
  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  // 2) IP oranı sınırlama (bellek içi, en iyi çaba)
  const ip =
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    req.headers.get("cf-connecting-ip") ||
    "unknown";

  const now = Date.now();
  const limit = rateLimitMap.get(ip);
  if (limit && now < limit.resetAt) {
    if (limit.count >= RATE_LIMIT_MAX) {
      return new Response("Too Many Requests", { status: 429 });
    }
    limit.count++;
  } else {
    rateLimitMap.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
  }

  // Bellek temizliği (1000'den fazla kayıt birikirse eski kayıtları temizle)
  if (rateLimitMap.size > 1000) {
    for (const [key, val] of rateLimitMap.entries()) {
      if (now >= val.resetAt) rateLimitMap.delete(key);
    }
  }

  // 3) Gövde boyutu denetimi (en fazla 8 KB)
  const contentLength = req.headers.get("content-length");
  if (contentLength && parseInt(contentLength, 10) > MAX_BODY_BYTES) {
    return new Response("Payload Too Large", { status: 413 });
  }

  let bodyText: string;
  try {
    const rawBytes = await req.arrayBuffer();
    if (rawBytes.byteLength > MAX_BODY_BYTES) {
      return new Response("Payload Too Large", { status: 413 });
    }
    bodyText = new TextDecoder().decode(rawBytes);
  } catch {
    return new Response("Bad Request", { status: 400 });
  }

  // 4) JSON ayrıştırma ve alan doğrulama
  let data: Record<string, unknown>;
  try {
    data = JSON.parse(bodyText);
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }

  const { version, versionCode, device, text } = data;

  const isVersionValid = typeof version === "string" && version.length > 0 && version.length <= 64;
  const isVersionCodeValid = typeof versionCode === "number" && Number.isInteger(versionCode) && versionCode > 0;
  const isDeviceValid = typeof device === "string" && device.length > 0 && device.length <= 128;
  const isTextValid = typeof text === "string" && text.length > 0 && text.length <= 4000;

  if (!isVersionValid || !isVersionCodeValid || !isDeviceValid || !isTextValid) {
    return new Response("Unprocessable Entity", { status: 422 });
  }

  // 5) Çevre değişkenlerinden Telegram bilgilerini oku
  const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
  const chatId = Deno.env.get("TELEGRAM_CHAT_ID");

  if (!botToken || !chatId) {
    console.error("Crash report configuration missing on server.");
    return new Response("Server Configuration Error", { status: 500 });
  }

  // 6) Telegram'a düz metin (Markdown/HTML yok) olarak ilet
  const message = `[Play Çökme Raporu]\nSürüm: ${version} (${versionCode})\nCihaz: ${device}\n\n${text}`.slice(0, 4000);

  try {
    const telegramUrl = `https://api.telegram.org/bot${botToken}/sendMessage`;
    const res = await fetch(telegramUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: chatId,
        text: message,
      }),
    });

    if (!res.ok) {
      console.error(`Telegram API responded with status: ${res.status}`);
      return new Response("Gateway Error", { status: 502 });
    }

    // 7) 204 No Content dön
    return new Response(null, { status: 204 });
  } catch {
    console.error("Failed to forward crash report to Telegram.");
    return new Response("Internal Server Error", { status: 500 });
  }
});
