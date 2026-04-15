plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pravos.kamindriver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pravos.kamindriver"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL — override in a build variant if needed
        buildConfigField("String", "BASE_URL", "\"http://kamintransbus-transfer.de/\"")

        // Optional debug Mapbox public token (pk.*) read from gradle.properties / local.properties
        val mapboxDebugToken = providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orElse("").get()
        buildConfigField("String", "MAPBOX_TOKEN_DEBUG", "\"$mapboxDebugToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Location (FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Networking: Retrofit + OkHttp + Moshi
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Mapbox Navigation SDK v2 + UI components
    // Requires MAPBOX_DOWNLOADS_TOKEN (sk.*) in gradle.properties / local.properties
    implementation("com.mapbox.navigation:android:2.17.6")
    implementation("com.mapbox.navigation:ui-maps:2.17.6")
    implementation("com.mapbox.navigation:ui-maneuver:2.17.6")
    implementation("com.mapbox.navigation:ui-tripprogress:2.17.6")
    implementation("com.mapbox.navigation:ui-voice:2.17.6")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
