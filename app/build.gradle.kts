plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        create("release") {
            if (project.hasProperty("NETBIRD_UPLOAD_STORE_FILE")) {
                storeFile = file(project.property("NETBIRD_UPLOAD_STORE_FILE") as String)
                storePassword = project.property("NETBIRD_UPLOAD_STORE_PASSWORD") as String
                keyAlias = project.property("NETBIRD_UPLOAD_KEY_ALIAS") as String
                keyPassword = project.property("NETBIRD_UPLOAD_KEY_PASSWORD") as String
            }
        }
    }
    namespace = "io.netbird.client"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "io.netbird.client"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = rootProject.extra["appVersionCode"] as Int
        versionName = rootProject.extra["appVersionName"] as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":tool"))
    implementation(project(":gomobile"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.browser)  // Added for CustomTabsIntent
    implementation(libs.lottie)

    // Firebase Crashlytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
}