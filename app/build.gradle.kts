import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// GitHub release assets: the APK is always partnereta.apk, so these links always point at the newest
// (non-pre-)release.
val RELEASES = "https://github.com/Dubadai/karoo-partnereta/releases/latest/download"

// Release signing key, kept out of git. See README "Releases".
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.daviddokakis.partnereta"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daviddokakis.partnereta"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.2.5"
        // Where the Karoo and UpdateCheck find the latest version. To test the update notice,
        // build with -PmanifestUrl=http://localhost:8791/manifest.json (BetterIntervals' README,
        // "Update notices").
        manifestPlaceholders["manifestUrl"] =
            (project.findProperty("manifestUrl") as String?) ?: "$RELEASES/manifest.json"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Your own release key when keystore.properties exists; otherwise the debug key, so
            // the project still builds anywhere (but such APKs can't update a published one).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":karoo-ext"))
    implementation(project(":karoo-common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// manifest.json for the GitHub release (upload it next to partnereta.apk). The Karoo's Settings › Apps
// page and UpdateCheck read it to offer the update.
//   ./gradlew -q :app:releaseManifest -PreleaseNotes="What's new…"
tasks.register("releaseManifest") {
    group = "release"
    description = "Writes build.nosync/manifest.json for the GitHub release"
    val out = layout.buildDirectory.file("manifest.json")
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val appId = android.defaultConfig.applicationId
    val notes = (project.findProperty("releaseNotes") as String?).orEmpty()
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val manifest = mapOf(
            "label" to "DDB PartnerETA",
            "packageName" to appId,
            "latestApkUrl" to "$RELEASES/partnereta.apk",
            "latestVersion" to name,
            "latestVersionCode" to code,
            "developer" to "github.com/Dubadai",
            "description" to "Follow a partner's live link on your map, with their ETA.",
            "releaseNotes" to notes,
        )
        val f = out.get().asFile
        f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifest)) + "\n")
        println("Wrote $f ($name, $code)")
    }
}
