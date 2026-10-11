plugins {
    alias(libs.plugins.android.library)
}

val upstreamApp = rootProject.file("third_party/shirokhorshid/app")
val upstreamBuildFile = upstreamApp.resolve("build.gradle")
val generatedEmbeddedValues = layout.buildDirectory.file(
    "generated/source/embeddedValues/com/psiphon3/psiphonlibrary/EmbeddedValues.java"
)

val generateEmbeddedValues by tasks.registering {
    inputs.file(upstreamBuildFile)
    outputs.file(generatedEmbeddedValues)

    doLast {
        val channelId = providers.environmentVariable("PSIPHON_PROPAGATION_CHANNEL_ID").orNull
            ?: providers.gradleProperty("PSIPHON_PROPAGATION_CHANNEL_ID").orNull.orEmpty()
        val sponsorId = providers.environmentVariable("PSIPHON_SPONSOR_ID").orNull
            ?: providers.gradleProperty("PSIPHON_SPONSOR_ID").orNull.orEmpty()
        if (channelId.isBlank() || sponsorId.isBlank()) {
            throw GradleException(
                "VXV embedded Psiphon requires PSIPHON_PROPAGATION_CHANNEL_ID and " +
                    "PSIPHON_SPONSOR_ID. Set them as CI secrets or Gradle properties; do not use guessed IDs."
            )
        }

        val upstreamScript = upstreamBuildFile.readText()
        val marker = "embeddedValuesFile.text = \"\"\"\\"
        val markerIndex = upstreamScript.indexOf(marker)
        if (markerIndex < 0) throw GradleException("Could not locate upstream EmbeddedValues template")
        val bodyStart = upstreamScript.indexOf('\n', markerIndex) + 1
        val bodyEnd = upstreamScript.indexOf("\n\"\"\"", bodyStart)
        if (bodyStart <= 0 || bodyEnd <= bodyStart) {
            throw GradleException("Could not parse upstream EmbeddedValues template")
        }
        var template = upstreamScript.substring(bodyStart, bodyEnd)

        fun javaEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
        fun replaceToken(token: String, value: String) {
            template = template.replace("${'$'}{$token}", value)
        }

        val serverList = providers.environmentVariable("PSIPHON_EMBEDDED_SERVER_LIST").orNull
            ?: providers.gradleProperty("PSIPHON_EMBEDDED_SERVER_LIST").orNull.orEmpty()
        val values = mapOf(
            "clientVersion" to (providers.environmentVariable("PSIPHON_CLIENT_VERSION").orNull ?: "1"),
            "serverListEntries" to if (serverList.isBlank()) "" else "\"${javaEscape(serverList)}\"",
            "feedbackKey" to (providers.environmentVariable("PSIPHON_FEEDBACK_ENCRYPTION_PUBLIC_KEY").orNull ?: ""),
            "feedbackUrls" to (providers.environmentVariable("PSIPHON_FEEDBACK_UPLOAD_URLS_JSON").orNull ?: "[]"),
            "propagationChannelId" to channelId,
            "remoteListUrls" to (providers.environmentVariable("PSIPHON_REMOTE_SERVER_LIST_URLS_JSON").orNull ?: "[]"),
            "remoteListSigKey" to (providers.environmentVariable("PSIPHON_REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY").orNull ?: ""),
            "obfuscatedUrls" to (providers.environmentVariable("PSIPHON_OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON").orNull ?: "[]"),
            "serverEntrySigKey" to (providers.environmentVariable("PSIPHON_SERVER_ENTRY_SIGNATURE_PUBLIC_KEY").orNull ?: ""),
            "serverEntryObfKey" to (providers.environmentVariable("PSIPHON_SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY").orNull ?: ""),
            "conduitCompartmentId" to (providers.environmentVariable("PSIPHON_CONDUIT_COMPARTMENT_ID").orNull ?: ""),
            "sponsorId" to sponsorId,
            "infoLinkUrl" to (providers.environmentVariable("PSIPHON_INFO_LINK_URL").orNull ?: "https://psiphon.ca"),
            "faqUrl" to (providers.environmentVariable("PSIPHON_FAQ_URL").orNull ?: "https://psiphon.ca/faq.html"),
            "getNewVersionUrl" to (providers.environmentVariable("PSIPHON_GET_NEW_VERSION_URL").orNull ?: "https://psiphon.ca"),
            "getNewVersionEmail" to (providers.environmentVariable("PSIPHON_GET_NEW_VERSION_EMAIL").orNull ?: "get@psiphon3.com"),
            "dataCollectionUrl" to (providers.environmentVariable("PSIPHON_DATA_COLLECTION_INFO_URL").orNull ?: "https://psiphon.ca/privacy.html")
        )
        values.forEach { (key, value) ->
            if (key in setOf("feedbackUrls", "remoteListUrls", "obfuscatedUrls")) {
                template = template.replace("${'$'}{escapeJava($key)", "${'$'}{escapeJava($key)")
            }
            replaceToken(key, value)
        }
        template = template
            .replace("${'$'}{escapeJava(feedbackUrls)}", javaEscape(values.getValue("feedbackUrls")))
            .replace("${'$'}{escapeJava(remoteListUrls)}", javaEscape(values.getValue("remoteListUrls")))
            .replace("${'$'}{escapeJava(obfuscatedUrls)}", javaEscape(values.getValue("obfuscatedUrls")))

        val output = generatedEmbeddedValues.get().asFile
        output.parentFile.mkdirs()
        output.writeText(template)
    }
}

android {
    namespace = "com.psiphon3"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../third_party/shirokhorshid/app/src/main/java", "src/main/java")
            res.srcDirs("../third_party/shirokhorshid/app/src/main/res")
            assets.srcDirs("../third_party/shirokhorshid/app/src/main/assets")
            jniLibs.srcDirs("../third_party/shirokhorshid/app/src/main/jniLibs")
            manifest.srcFile("../third_party/shirokhorshid/app/src/main/AndroidManifest.xml")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("../third_party/shirokhorshid/app/src/main/jni/Android.mk")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateEmbeddedValues)
}

android.sourceSets.getByName("main").java.srcDir(generatedEmbeddedValues.map { it.asFile.parentFile.parentFile.parentFile.parentFile })

dependencies {
    implementation(project(":psiphonTray"))
    implementation(fileTree(mapOf("dir" to upstreamApp.resolve("libs"), "include" to listOf("*.jar", "*.aar"))))
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.android.material:material:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.0.0")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.5")
    implementation("com.jakewharton.rxrelay2:rxrelay:2.1.0")
    api("androidx.lifecycle:lifecycle-viewmodel:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.2.0")
    implementation("androidx.work:work-runtime:2.7.1")
    implementation("androidx.work:work-rxjava2:2.7.1")
    compileOnly("com.google.auto.value:auto-value:1.5.2")
    annotationProcessor("com.google.auto.value:auto-value:1.5.2")
    implementation("androidx.room:room-runtime:2.4.1")
    annotationProcessor("androidx.room:room-compiler:2.4.1")
    implementation("androidx.paging:paging-runtime:2.1.2")
    implementation("androidx.paging:paging-rxjava2:2.1.2")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.github.drfonfon:android-geohash:0.22")
}
