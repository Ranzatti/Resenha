plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.resenha"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.resenha"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))

    implementation("com.google.firebase:firebase-messaging")

    // BOM do Supabase (Garante que as versões abaixo sejam totalmente compatíveis entre si)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.3.0"))

    // Os 4 pilares do app no Supabase:
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // O motor exigido pelo Supabase (Rede e JSON)
    implementation("io.ktor:ktor-client-android:3.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.3.0")
}

configurations.all {
    resolutionStrategy {
        // Força as versões do Compose para 1.6.1 (Compatível com Android 34 e Gradle 8.2)
        force("androidx.compose.ui:ui:1.6.1")
        force("androidx.compose.ui:ui-graphics:1.6.1")
        force("androidx.compose.ui:ui-tooling:1.6.1")
        force("androidx.compose.ui:ui-tooling-data:1.6.1")
        force("androidx.compose.ui:ui-text:1.6.1")
        force("androidx.compose.foundation:foundation:1.6.1")
        force("androidx.compose.foundation:foundation-layout:1.6.1")
        force("androidx.compose.animation:animation:1.6.1")
        force("androidx.compose.animation:animation-core:1.6.1")
        force("androidx.compose.runtime:runtime:1.6.1")
        force("androidx.compose.runtime:runtime-saveable:1.6.1")

        // Força o Lifecycle para 2.7.0 (Versão 2.10.0 exige Android 35)
        force("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
        force("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

        // Força o Core para 1.12.0 (Versão 1.15.0 exige Android 35)
        force("androidx.core:core:1.12.0")
        force("androidx.core:core-ktx:1.12.0")

        // Outras travas de segurança que já usamos
        force("androidx.browser:browser:1.7.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    }
}