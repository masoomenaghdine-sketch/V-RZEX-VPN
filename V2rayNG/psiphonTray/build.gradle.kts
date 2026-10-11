plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "net.grandcentrix.tray"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        resValue("string", "tray__authority", "com.vorzex.vpn.psiphon.tray")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../third_party/shirokhorshid/tray/src/main/java")
            res.srcDirs("../third_party/shirokhorshid/tray/src/main/res")
            manifest.srcFile("../third_party/shirokhorshid/tray/src/main/AndroidManifest.xml")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.8.2")
}
