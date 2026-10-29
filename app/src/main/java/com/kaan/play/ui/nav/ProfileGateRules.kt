package com.kaan.play.ui.nav

/**
 * Açılışta profil seçim kapısı gösterilsin mi? Girdiler DataStore'dan gelen ilk GERÇEK değerlerdir
 * (başlangıç değeri değil); çağıran bunları bekledikten sonra tek seferde karar verir.
 *
 * - Profil yoksa kapı açık (kurulum/seçim ekranı gerekir).
 * - "Son profili hatırla" kapalıysa kapı açık.
 * - Hatırlanacak aktif profil yoksa kapı açık.
 * - Aksi halde kapalı: doğrudan ana sayfa.
 */
fun shouldShowProfileGate(profileCount: Int, hasActive: Boolean, rememberLast: Boolean): Boolean =
    profileCount == 0 || !rememberLast || !hasActive
