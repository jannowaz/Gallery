plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "org.fossify.gallery.baselineprofile"
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions.add("licensing")
    productFlavors {
        register("foss")
        register("gplay")
    }

    targetProjectPath = ":app"
}

// Runs the generator against the physically connected device instead of a Gradle Managed Device -
// no emulator/root setup needed, but the device must stay unlocked and screen-on for the duration.
baselineProfile {
    useConnectedDevices = true
    managedDevices.clear()
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
