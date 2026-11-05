# Supabase Crash Report Kurulumu

1. Supabase kontrol panelini açın ve projenizi seçin.
2. Sol menüden **Edge Functions** sekmesine gidin.
3. **New Function** butonuna basıp fonksiyon adını `crash-report` yapın.
4. `supabase/functions/crash-report/index.ts` dosyasındaki kodu editöre yapıştırın.
5. Sol menüden **Project Settings > Edge Functions > Secrets** bölümüne gidin.
6. `TELEGRAM_BOT_TOKEN` adıyla BotFather'dan aldığınız yeni bot anahtarını ekleyin.
7. `TELEGRAM_CHAT_ID` adıyla bildirimlerin gideceği sohbet/kanal kimliğini ekleyin.
8. Fonksiyonu **Deploy** edin.
9. Edge Function için **Verify JWT** kapatılmalı (istemcinin doğrudan ve anonim istek iletebilmesi için gereklidir).
