package com.kaan.play.data.cloudstream

import com.kaan.play.AppContainer

/** CloudStream katalog sağlayıcısı; mevcut Nuvio/oynatma sağlayıcılarından bağımsızdır. */
val AppContainer.csCatalogRepository: CsCatalogRepository
    get() = CsCatalogRepository(csRepos)
