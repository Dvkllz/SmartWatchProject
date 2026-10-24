plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val serverUrl = providers.gradleProperty("SOS_BASE_URL").orElse("http://10.0.2.2:3000/").get()
require(serverUrl.matches(Regex("https?://[A-Za-z0-9.:-]+/"))) { "SOS_BASE_URL debe ser http(s)://host:puerto/" }

android {
    namespace = "com.watchsos"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.watchsos"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BASE_URL", "\"$serverUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "sensors"
    productFlavors {
        create("demo") {
            dimension = "sensors"
            applicationIdSuffix = ".demo"
            buildConfigField("boolean", "DEMO", "true")
            resValue("string", "app_name", "SOS Demo")
        }
        create("live") {
            dimension = "sensors"
            buildConfigField("boolean", "DEMO", "false")
            resValue("string", "app_name", "SOS")
        }
    }
    buildTypes {
        debug { manifestPlaceholders["cleartextAllowed"] = "true" }
        release {
            manifestPlaceholders["cleartextAllowed"] = "false"
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.8.2")
    implementation("androidx.compose.foundation:foundation:1.8.2")
    implementation("androidx.compose.animation:animation:1.8.2")
    implementation("androidx.wear.compose:compose-material:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
