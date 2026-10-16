package com.kaan.play.data.cloudstream

import com.kaan.play.AppContainer

/** CloudStream katalog sağlayıcısı; uygulama kabında tek örnek olarak tutulur. */
val AppContainer.csCatalogRepository: CsCatalogRepository
    get() = csCatalogs
