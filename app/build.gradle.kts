plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.htc.android.teeter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.htc.android.teeter"
        minSdk = 23
        targetSdk = 36
        versionCode = 111351810
        versionName = "1.1.2220402125.459176.246117"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}