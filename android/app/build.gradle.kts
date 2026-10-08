plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tel2wheel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tel2wheel"
        minSdk = 26
        targetSdk = 35
        // CI passes these from the release tag; local builds get 1 / "dev".
        versionCode = System.getenv("T2W_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("T2W_VERSION_NAME") ?: "dev"
    }

    // A fixed release key (from CI secrets) lets users update over an older install. Without it
    // the build falls back to the debug key, which is fine for sideloading but changes per machine.
    val releaseKeystore = System.getenv("T2W_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("T2W_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("T2W_KEY_ALIAS")
                keyPassword = System.getenv("T2W_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.12")
}
