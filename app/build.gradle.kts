import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// TMDB anahtarı local.properties'ten (ya da CI için ortam değişkeninden) okunur; git'e girmez.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbApiKey: String = localProps.getProperty("TMDB_API_KEY")
    ?: System.getenv("TMDB_API_KEY")
    ?: ""

// SubDL altyazı araması için anahtar (isteğe bağlı): local.properties'te SUBDL_API_KEY. Yoksa SubDL sessizce atlanır.
// Anahtar git'e girmez (local.properties .gitignore'dadır); derlemede BuildConfig'e geçer.
val subdlApiKey: String = localProps.getProperty("SUBDL_API_KEY")
    ?: System.getenv("SUBDL_API_KEY")
    ?: ""

val telegramBotToken: String = localProps.getProperty("TELEGRAM_BOT_TOKEN")
    ?: System.getenv("TELEGRAM_BOT_TOKEN")
    ?: ""
val telegramChatId: String = localProps.getProperty("TELEGRAM_CHAT_ID")
    ?: System.getenv("TELEGRAM_CHAT_ID")
    ?: ""

val supabaseUrl: String = (localProps.getProperty("SUPABASE_URL")
    ?: System.getenv("SUPABASE_URL")
    ?: "").trim()

val supabasePublishableKey: String = (localProps.getProperty("SUPABASE_PUBLISHABLE_KEY")
    ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
    ?: "").trim()

android {
    namespace = "com.kaan.play"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kaan.play"
        minSdk = 24
        targetSdk = 37
        versionCode = 29
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "SUBDL_API_KEY", "\"$subdlApiKey\"")
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$telegramBotToken\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"$telegramChatId\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    // Yayın imzası: keystore yolu repoda, şifreler yalnızca local.properties'te (ya da ortam değişkeninde).
    signingConfigs {
        create("release") {
            val storePassword = localProps.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            val keyPasswordValue = localProps.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")
            if (storePassword != null && keyPasswordValue != null) {
                storeFile = rootProject.file("keystore/play-release.keystore")
                this.storePassword = storePassword
                keyAlias = "play"
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // Şifreler yoksa (ör. başka makine) imzasız derlenir; derleme kırılmaz.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Yalnızca desteklenen diller paketlensin (kütüphane çevirileri dahil).
    androidResources {
        localeFilters += listOf("en", "tr")
    }

    // Yerel kütüphaneler APK içinde sıkıştırılır (libtorrent işlemci başına ~16 MB; sıkıştırılmadan APK çok büyür).
    // İşlemci türüne göre ayrı APK (libtorrent her türde ~14-16 MB): her cihaz yalnızca kendi türünü indirir.
    // AAB (Play Store) bundan etkilenmez; mağaza bölmeyi kendisi yapar.
    // Tek evrensel APK (kullanıcı kararı 2026-10-19: telefon, tablet, TV aynı dosyayı kullanır).
    // splits.abi kapalı; arm64-v8a, armeabi-v7a, x86_64 yerel kütüphaneleri aynı APK'da bulunur.
    splits {
        abi {
            isEnable = false
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }


}

configurations.all {
    exclude(group = "org.jsoup", module = "jsoup")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Android Harmony DOM uyumlu yamalanmış Jsoup (W3CDom NPE düzeltmesi)
    implementation(files("libs/jsoup-1.23.2-patched.jar"))
    implementation(libs.quickjs.kt)
    // Faz 3 (CloudStream): gerçek eklentiler doğrudan Jackson bytecode'una derlenmiş durumda
    // (`com.fasterxml.jackson...`); kendi JSON kütüphanemiz (kotlinx.serialization) bunun yerini
    // tutamaz, bu yüzden eklenti uyumluluğu için gerçek Jackson çalışma zamanda bulunur.
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    // CloudStream resmi kütüphanesi (GPL-3.0) ve NiceHttp
    implementation(libs.cloudstream.library)
    implementation(libs.nicehttp)


    // Yerleşik torrent motoru: Java API + işlemci başına yerel kütüphane (.so).
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.libtorrent4j.android.arm)
    implementation(libs.libtorrent4j.android.x86)
    implementation(libs.libtorrent4j.android.x64)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Cihaz üstünde çeviri (Türkçe altyazı yoksa İngilizceden); model ilk kullanımda iner, metin sunucuya gitmez.
    implementation(libs.mlkit.translate)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Room şeması sürüm sürüm repoya yazılır; göçler bunlara karşı doğrulanır.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Yerel JVM birim testleri Android'e özgü yerel kütüphaneyi yükleyemez: QuickJS testleri masaüstü (-jvm) paketini kullanır.
// Cihaz testleri (androidTest) ve uygulama kendi Android paketini kullanır.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.dokar3:quickjs-kt-android"))
            .using(module(libs.quickjs.kt.jvm.get().toString()))
    }
}


// Release derlemesi sonunda Play-<sürüm>.apk ve Play-<sürüm>-SHA256.txt üretilir.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val targetApk = File(releaseDir, "Play-${android.defaultConfig.versionName}.apk")
        val generatedApk = releaseDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") && it.name != targetApk.name }
        if (generatedApk != null) {
            generatedApk.copyTo(targetApk, overwrite = true)
            generatedApk.delete()
        }
        if (targetApk.exists()) {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = targetApk.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            val hashFile = File(releaseDir, "Play-${android.defaultConfig.versionName}-SHA256.txt")
            hashFile.writeText("$hash *${targetApk.name}\n")
            println("Release APK: ${targetApk.name} (${targetApk.length()} bytes)")
            println("SHA-256: $hash -> ${hashFile.name}")
        }
    }
}
