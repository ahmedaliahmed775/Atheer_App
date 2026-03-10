// ملف بناء تطبيق Demo لـ Atheer SDK
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atheer.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.atheer.demo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        viewBinding = true
    }
}

dependencies {
    // Atheer SDK من المكتبة المحلية (v1.0.0)
    implementation(files("libs/atheer-sdk-release.aar"))

    // AndroidX الأساسية
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")

    // Material Design
    implementation("com.google.android.material:material:1.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Gson للتعامل مع JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // BiometricPrompt للأمان
    implementation("androidx.biometric:biometric:1.1.0")

    // Lottie للرسوم المتحركة
    implementation("com.airbnb.android:lottie:6.4.0")

    // تخزين آمن
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room — مطلوب لتشغيل Atheer SDK (يستخدم قاعدة بيانات Room داخلياً)
    implementation("androidx.room:room-runtime:2.6.1")

    // اختبارات
    testImplementation("junit:junit:4.13.2")
    // Retrofit للاتصال بالشبكة
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
