package com.lagradost.cloudstream3

import com.lagradost.nicehttp.Requests
import okhttp3.OkHttpClient

/**
 * CloudStream çalışma zamanı başlatma köprüsü.
 * Play'in sağladığı OkHttpClient'ı CloudStream'in global `app` nesnesine aktarır.
 */
fun initApp(client: OkHttpClient) {
    app.baseClient = client
    insecureApp.baseClient = client
}
