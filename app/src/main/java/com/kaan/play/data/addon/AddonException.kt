package com.kaan.play.data.addon

/**
 * Addon ekleme/doğrulama hatası. Arayüz [kind]'a göre yerelleştirilmiş mesaj seçer;
 * [detail] alan adı ([Kind.MissingField], [Kind.InvalidField]) ya da HTTP kodu ([Kind.Http]) taşır.
 */
class AddonException(
    val kind: Kind,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception("Addon $kind${detail?.let { ": $it" }.orEmpty()}", cause) {

    enum class Kind {
        /** Bağlantı http(s) değil, adres yok ya da `?` parametresi içeriyor. */
        InvalidUrl,
        AlreadyAdded,
        /** Bu tür (depo vb.) Faz 1'de desteklenmiyor. */
        UnsupportedKind,
        Network,
        Http,
        TooLarge,
        NotJson,
        MissingField,
        InvalidField,
        /** Manifest geçerli ama `stream` kaynağı sunmuyor. */
        NoStreamResource,
        /** `stream` kaynağı var ama ne film ne dizi için. */
        UnsupportedTypes,

        /** Kullanıcının seçtiği kaynak türü ile manifest içeriği uyuşmuyor. */
        WrongSourceType,

        /** Eklenti deposu geçerli ama kullanılabilir hiç kazıyıcı girişi yok. */
        NoPlugins,

        /** Hiçbir kazıyıcı dosyası indirilemedi ya da geçerli değil; [detail] ilk başarısız kazıyıcının adı. */
        PluginFileFailed,
    }
}
