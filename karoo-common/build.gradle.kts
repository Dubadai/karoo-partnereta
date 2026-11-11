// karoo-common: the code and resources every DDB Karoo app shares (KarooType, the settings kit,
// the Example card, update notices). Vendored into each app as a folder (setup/sync-kit.sh), the same
// way karoo-ext is; never edit it inside an app. Source of truth: Karoo/karoo-common in the workspace.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dubadai.karoo.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":karoo-ext"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    // Fixed versions, not the app's catalog: not every app's catalog has them
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
