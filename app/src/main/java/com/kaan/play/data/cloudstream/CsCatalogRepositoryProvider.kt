package com.kaan.play.data.cloudstream

import com.kaan.play.AppContainer

/**
 * CloudStream katalog sağlayıcısı.
 *
 * Tek örnek kullanılır; katalog ekranları her yeniden çizildiğinde yeni bir
 * sağlayıcı oluşturulmasının önüne geçer. Nuvio ve mevcut kaynak bulma akışı
 * bu sağlayıcıdan bağımsızdır.
 */
val AppContainer.csCatalogRepository: CsCatalogRepository
    get() = CsCatalogRepository(csRepos)
