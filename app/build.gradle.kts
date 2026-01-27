val hasGoogleServicesJson = rootProject.file("app/google-services.json").exists()

plugins {
    alias(libs.plugins.android.application)
}

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

fun getPropertyOrEnv(propertyName: String, envName: String = propertyName): String? {
    return if (project.hasProperty(propertyName)) {
        project.property(propertyName) as String
    } else {
        System.getenv(envName)
    }
}

android {
    signingConfigs {
        create("release") {
            val storeFile = getPropertyOrEnv("NETBIRD_UPLOAD_STORE_FILE")
            val storePassword = getPropertyOrEnv("NETBIRD_UPLOAD_STORE_PASSWORD")
            val keyAlias = getPropertyOrEnv("NETBIRD_UPLOAD_KEY_ALIAS")
            val keyPassword = getPropertyOrEnv("NETBIRD_UPLOAD_KEY_PASSWORD")

            if (storeFile != null) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
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
    implementation(libs.zxing)

    if (hasGoogleServicesJson) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
        implementation(libs.firebase.analytics)
    }
}
