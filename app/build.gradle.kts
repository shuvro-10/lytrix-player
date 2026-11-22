plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  id("com.google.gms.google-services")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.musicplayer.lytrix"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.musicplayer.lytrix"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a")
      isUniversalApk = true
    }
  }

  signingConfigs {
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val keystoreFile = file(keystorePath)
    if (keystoreFile.exists()) {
      create("release") {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
    debug {
      // Uses the default debug signing config
    }
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.11.0")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.play.services.location)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation("io.coil-kt:coil-compose:2.4.0")
  implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+1")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.palette.ktx)
  implementation("com.materialkolor:material-kolor:2.0.2")
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-gson:2.9.0")
  implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
  implementation("com.google.firebase:firebase-database")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("com.google.firebase:firebase-analytics")
  implementation("br.com.devsrsouza.compose.icons:tabler-icons:1.1.1")
// ðŸ”¥ Firebase Authentication (à¦—à§à¦—à¦² à¦²à¦—à¦‡à¦¨à§‡à¦° à¦œà¦¨à§à¦¯ à¦«à¦¾à§Ÿà¦¾à¦°à¦¬à§‡à¦¸ à¦¡à¦¿à¦ªà§‡à¦¨à§à¦¡à§‡à¦¨à§à¦¸à¦¿)
  implementation("com.google.firebase:firebase-auth-ktx:23.0.0")

  // ðŸ”¥ Android Credential Manager (à¦²à§‡à¦Ÿà§‡à¦¸à§à¦Ÿ à¦—à§à¦—à¦² à¦¸à¦¾à¦‡à¦¨-à¦‡à¦¨ à¦†à¦°à§à¦•à¦¿à¦Ÿà§‡à¦•à¦šà¦¾à¦°)
  implementation("androidx.credentials:credentials:1.2.2")
  implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  // ðŸ”¥ Start.io SDK (AdMob Removed)
  implementation("com.startapp:inapp-sdk:5.1.0")
  implementation("androidx.datastore:datastore-preferences:1.2.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  // Lottie Animation for Compose
  implementation("com.airbnb.android:lottie-compose:6.3.0")
  implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
  implementation("net.jthink:jaudiotagger:3.0.1")
  // Firebase BOM (à¦à¦Ÿà¦¿ à¦«à¦¾à¦¯à¦¼à¦¾à¦°à¦¬à§‡à¦¸à§‡à¦° à¦¸à¦¬ à¦²à¦¾à¦‡à¦¬à§à¦°à§‡à¦°à¦¿à¦° à¦²à§‡à¦Ÿà§‡à¦¸à§à¦Ÿ à¦­à¦¾à¦°à§à¦¸à¦¨ à¦…à¦Ÿà§‹à¦®à§‡à¦Ÿà¦¿à¦• à¦®à§à¦¯à¦¾à¦¨à§‡à¦œ à¦•à¦°à¦¬à§‡)


// Firestore à¦à¦¬à¦‚ Storage (à¦à¦–à¦¾à¦¨à§‡ à¦†à¦° -ktx à¦¦à§‡à¦“à§Ÿà¦¾à¦° à¦¦à¦°à¦•à¦¾à¦° à¦¨à§‡à¦‡)
  implementation("com.google.firebase:firebase-firestore")
  implementation("com.google.firebase:firebase-storage")
  // Ktor Client for InnerTube API requests
  implementation(platform("io.ktor:ktor-bom:3.5.2"))
  implementation("io.ktor:ktor-client-core")
  implementation("io.ktor:ktor-client-okhttp")
  implementation("io.ktor:ktor-client-content-negotiation")
  implementation("io.ktor:ktor-serialization-kotlinx-json")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.11.0")
  // Ktor Encoding (Compression à¦à¦° à¦à¦°à¦° à¦«à¦¿à¦•à§à¦¸ à¦•à¦°à¦¾à¦° à¦œà¦¨à§à¦¯)
  implementation("io.ktor:ktor-client-encoding")
  // OkHttp DNS over HTTPS (DnsOverHttps à¦à¦° à¦à¦°à¦° à¦«à¦¿à¦•à§à¦¸ à¦•à¦°à¦¾à¦° à¦œà¦¨à§à¦¯)
  implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
  implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
  implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  // ðŸ”¥ NewPipe Extractor (à¦²à§‡à¦Ÿà§‡à¦¸à§à¦Ÿ v0.26.5)
  implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
  implementation("org.mozilla:rhino:1.7.14")
  implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
  implementation("com.mocharealm.accompanist:lyrics-ui:2.0.0-rc.2")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation("com.google.api.grpc:proto-google-common-protos:2.22.0") {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
}
configurations {
  all {
    // à¦¡à§à¦ªà§à¦²à¦¿à¦•à§‡à¦Ÿ à¦à¦°à¦°à§‡à¦° à¦†à¦¸à¦² à¦•à¦¾à¦²à¦ªà§à¦°à¦¿à¦Ÿ à¦«à¦¾à§Ÿà¦¾à¦°à¦¬à§‡à¦¸! à¦à¦¬à¦¾à¦° à¦¸à¦ à¦¿à¦• à¦—à§à¦°à§à¦ª à¦¨à§‡à¦® à¦¦à¦¿à§Ÿà§‡ à¦à¦•à§à¦¸à¦•à§à¦²à§à¦¡ à¦•à¦°à¦¾ à¦¹à¦²à§‹:
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")

    // à¦¸à§‡à¦«à¦Ÿà¦¿à¦° à¦œà¦¨à§à¦¯ protobuf-java à¦¬à§à¦²à¦• à¦•à¦°à§‡ à¦¦à¦¿à¦šà§à¦›à¦¿:
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
}