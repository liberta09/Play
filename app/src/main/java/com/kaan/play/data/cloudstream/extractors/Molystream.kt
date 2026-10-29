package com.kaan.play.data.cloudstream.extractors

import com.lagradost.cloudstream3.extractors.Vidmoly

/**
 * DiziBOX TV ve benzeri kaynakların kullandığı Molystream / Vidmoly CDN aynaları.
 */
open class Molystream : Vidmoly() {
    override var name: String = "Molystream"
    override var mainUrl: String = "https://molystream.org"
}

class DbxMolystream : Molystream() {
    override var mainUrl: String = "https://dbx.molystream.org"
}

class MolyTo : Molystream() {
    override var mainUrl: String = "https://moly.to"
}
