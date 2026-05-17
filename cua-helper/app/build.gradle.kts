plugins {
    id("com.android.application")
}

android {
    namespace = "com.hermes.cua"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hermes.cua"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.RikkaApps:Shizuku-API:13.1.5")
    implementation("com.github.RikkaApps:Shizuku-Provider:13.1.5")
}
