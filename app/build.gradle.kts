plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.mbp16.travelmoneynote"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mbp16.travelmoneynote"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    val ciKeystoreFile = file("keystore.jks")

    signingConfigs {
        create("release") {
            if (ciKeystoreFile.exists()) {
                storeFile = ciKeystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // [추가 3] CI 환경일 때만 서명 설정 적용!
            // 로컬(내 컴퓨터)에는 파일이 없으므로 이 부분이 무시됩니다.
            // -> 따라서 로컬에선 Android Studio GUI로 빌드할 수 있게 됩니다.
            if (ciKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            // === [원래 있던 코드 유지] ===
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Coil for image loading
    implementation(libs.coil.compose)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // OkHttp for network calls
    implementation(libs.okhttp)
    
    // Glance for widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
