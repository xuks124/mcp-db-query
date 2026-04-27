plugins {
    id("com.android.application") version "8.2.0"
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
    // No external dependencies — uses only Android SDK APIs
}
