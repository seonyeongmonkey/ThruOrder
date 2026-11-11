import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.thruorder.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.thruorder.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"

        // 카카오 로컬 API REST 키: 프로젝트 루트 local.properties 에 kakao.rest.key=... 로 넣는다 (GitHub에 올라가지 않음)
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        // 동승자 공동 장바구니: QR로 여는 웹 메뉴판(GitHub Pages)과 메시지 중계 서버(ntfy.sh, 데모용)
        buildConfigField("String", "SHARE_BASE_URL", "\"https://seonyeongmonkey.github.io/ThruOrder/\"")
        buildConfigField("String", "RELAY_BASE_URL", "\"https://ntfy.sh\"")
        buildConfigField("String", "KAKAO_REST_KEY", "\"${localProps.getProperty("kakao.rest.key", "")}\"")
    }

    // Android Automotive 표준 API (CarUxRestrictions, CarPropertyManager)
    useLibrary("android.car")

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Pleos Connect NaviHelper SDK
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // QR 생성
    implementation("com.google.zxing:core:3.5.3")
}
