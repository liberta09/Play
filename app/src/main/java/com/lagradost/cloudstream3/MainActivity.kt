package com.lagradost.cloudstream3

import androidx.appcompat.app.AppCompatActivity

/**
 * Bazı CloudStream eklentilerinin beklediği ikili uyumluluk ana etkinlik türü.
 * CloudStream kitaplığı `MainActivityKt` yardımcılarını içerir ancak asıl uygulamanın
 * `MainActivity` sınıfını içermez. Play'in ana etkinliği bu türden türeyerek eklenti yaşam
 * döngüsü kontrollerinin DEX içinde bulunmayan sınıfa takılıp uygulamayı çökertmesini önler.
 */
open class MainActivity : AppCompatActivity()
