plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.oss.licenses.plugin)
}

android {
    namespace = "com.example.mqttpanelcraft"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
        // Unreferenced uploaded artwork has no provenance. Retain it in git, exclude it from APK/AAB.
        ignoreAssetsPattern = "uploaded_image_1769085955416.png"
    }

    defaultConfig {
        // Play receipts bind to this id. Change it once, before the first Play Console upload.
        applicationId = "com.spec127.mqttpanelcraft"
        minSdk = 24
        targetSdk = 36
        versionCode = 221
        versionName = "0.15.10"
        val playKey = providers.gradleProperty("PLAY_BILLING_PUBLIC_KEY").orElse("").get()
        require(playKey.matches(Regex("[A-Za-z0-9+/=]*"))) { "Invalid Play public key encoding" }
        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$playKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    val uploadStore = providers.environmentVariable("MPC_UPLOAD_STORE_FILE").orNull
    if (uploadStore != null) {
        signingConfigs.create("playUpload") {
            storeFile = file(uploadStore)
            storePassword = providers.environmentVariable("MPC_UPLOAD_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("MPC_UPLOAD_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("MPC_UPLOAD_KEY_PASSWORD").orNull
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("playUpload")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

val verifyPlayReleaseInputs by tasks.registering {
    doLast {
        check(providers.gradleProperty("PLAY_BILLING_PUBLIC_KEY").orNull?.isNotBlank() == true) {
            "Release blocked: configure the Play app licensing public key in local Gradle properties."
        }
        val policy = providers.gradleProperty("PRIVACY_POLICY_URL").orNull.orEmpty()
        check(policy.startsWith("https://")) { "Release blocked: a verified public HTTPS privacy policy URL is required." }
        listOf("MPC_UPLOAD_STORE_FILE", "MPC_UPLOAD_STORE_PASSWORD", "MPC_UPLOAD_KEY_ALIAS", "MPC_UPLOAD_KEY_PASSWORD").forEach {
            check(providers.environmentVariable(it).orNull?.isNotBlank() == true) { "Release blocked: configure upload signing environment." }
        }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(verifyPlayReleaseInputs) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(libs.play.services.ads)
    implementation(libs.billing)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation(libs.play.services.oss.licenses)
}
