import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Gateway-Basis-URL kommt NICHT hartkodiert in den Quelltext, sondern aus einer
// Gradle-Property oder Umgebungsvariable (CI-Secret). Der eingecheckte Default ist
// ein neutraler Loopback-Platzhalter, damit das oeffentliche Repo keine internen
// Netzdetails enthaelt. Reale Adresse: `-Ptardis.gatewayUrl=...` oder
// TARDIS_GATEWAY_URL im CI-Secret setzen.
// `takeIf { isNotBlank }`, damit ein gesetztes-aber-leeres Env (CI expandiert ein
// nicht definiertes Secret zu "") auf den Platzhalter zurueckfaellt statt eine
// leere URL zu backen. Oeffentliche CI-Builds bleiben bewusst auf dem Loopback-
// Platzhalter; die reale Adresse wird nur beim internen Build (mobile-api) gesetzt.
val gatewayUrl: String = (project.findProperty("tardis.gatewayUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("TARDIS_GATEWAY_URL")?.takeIf { it.isNotBlank() }
    ?: "http://127.0.0.1:8140"

// ⚠⚠ DIESE ZAHL WIRD BEIM AUSLIEFERN HOCHGEZAEHLT, NICHT BEIM BAUEN.
//
// Sie stand bis 2026-09-27 auf 2, waehrend ausgeliefert war 4: ein Bau ohne
// gesetzte Property haette ein Downgrade erzeugt, das Android wortlos ablehnt
// und das wie ein kaputtes Update aussieht.
//
// Am 2026-10-09 ist derselbe Mechanismus in die andere Richtung
// umgeschlagen, und das ist der schwerere Fall: die APK wurde ohne Property
// gebaut, bekam also die Vorgabe 5 -- dieselbe Nummer wie die bereits
// ausgelieferte. Ein Downgrade faellt auf (der Installer weigert sich), ein
// STILLSTAND nicht: `/app-version` haette 6 angekuendigt, die Datei darunter
// waere 5 gewesen, und die App haette dasselbe Update bei jeder Pruefung
// erneut angeboten, in Schleife. Gemessen wurde es nur, weil die APK vor dem
// Ausliefern gegen das Manifest geprueft wurde (`ausliefern.sh`).
//
// Darum ist die Reihenfolge jetzt: hier hochzaehlen, committen, DANN bauen.
// Die Property bleibt als Notausgang, aber der Normalweg braucht sie nicht --
// und ein Normalweg ohne Handgriff ist einer, den man nicht vergessen kann.
val tardisVersionCode: Int = (project.findProperty("tardis.versionCode") as String?)?.toIntOrNull()
    ?: System.getenv("TARDIS_VERSION_CODE")?.toIntOrNull()
    ?: 16

android {
    namespace = "de.djouhri.tardis"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.djouhri.tardis"
        minSdk = 28
        targetSdk = 36
        versionCode = tardisVersionCode
        versionName = "2.0.$tardisVersionCode"

        buildConfigField("String", "GATEWAY_URL", "\"$gatewayUrl\"")
    }

    // Gemeinsames Sideload-Signing der Homelab-Apps, gleiches Muster wie
    // kalender-android/projectdeck-android. Die Zugangsdaten liegen NICHT im
    // Repo, sondern beim Buildhost unter ~/.android/. Fehlen sie, entsteht ein
    // unsigniertes Release statt eines Fehlschlags: der oeffentliche CI-Lauf
    // soll ohne Schluessel durchlaufen.
    val schluesselDatei = File(System.getProperty("user.home"), ".android/meisterminze-apps-release.properties")
    if (schluesselDatei.exists()) {
        val props = Properties()
        schluesselDatei.inputStream().use { props.load(it) }
        signingConfigs {
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (schluesselDatei.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        // Robolectric braucht die Ressourcen (Schriften, Themen) fuer die
        // Bildschirmfoto-Tests in `bildschirm/`.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Vorhandene Warnungen sollen die CI nicht rot faerben; der HTML-Report
        // wird als Artefakt abgelegt und bleibt so sichtbar.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // BouncyCastle (bcpkix/bcprov) liefert mehrfach identische META-INF-
            // Ressourcen; ohne Ausschluss bricht mergeDebugJavaResource mit
            // DuplicateRelativeFileException ab.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // DataStore
    implementation(libs.datastore.preferences)

    // CameraX + ML Kit (QR-Pairing-Scanner)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // PKCS#10-CSR-Bau fuer den geraetegebundenen Schluessel (AndroidKeyStore)
    implementation(libs.bouncycastle.pkix)

    // Lokale Zusatzbestaetigung (Biometrie/Geraete-PIN) fuer schreibende Aktionen
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    // Startbildschirm-Widget (Alarme + Dienste)
    implementation(libs.glance.appwidget)

    // Tests (JVM-Unit + Integration)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.ui.test.manifest)
}
