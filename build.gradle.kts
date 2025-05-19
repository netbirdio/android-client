// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

val versionPropsFile = file("version.properties")
val versionProps = java.util.Properties()

if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { stream ->
        versionProps.load(stream)
    }
}

// Check if versionName and versionCode are provided via command line
val cmdVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
val cmdVersionName = project.findProperty("versionName") as String?

// Define the version properties in the extra properties extension
ext {
    set("compileSdkVersion", 35)
    set("minSdkVersion", 26)
    set("targetSdkVersion", 35)
    set("appVersionCode", cmdVersionCode ?: (versionProps["versionCode"] as String).toInt())
    set("appVersionName", cmdVersionName ?: versionProps["versionName"] as String)
}