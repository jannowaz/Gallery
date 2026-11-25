import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import org.gradle.jvm.tasks.Jar
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.baselineprofile)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

base {
    val versionCode = project.property("VERSION_CODE").toString().toInt()
    archivesName = "gallery-$versionCode"
}

composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    // kotlin.collections.List/Set/Map are treated as unstable by default (they could be a mutable
    // implementation in disguise), which cascades into ~80 of this app's state/model classes and
    // blocks recomposition skipping for anything holding them. Verified (2026-07-11) that every
    // producer of these collections in this codebase replaces them wholesale rather than mutating
    // in place, so trusting them here is safe.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability-config.txt"))
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // x86/x86_64 native libs (JXL/AVIF/Brotli/WebP decoders) added up to ~15.6MB (38% of
            // the release APK) for ABIs no real phone/tablet ships - only relevant for x86
            // emulators, which use debug builds anyway. Debug keeps every ABI so emulator testing
            // is unaffected; only what actually gets distributed shrinks.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("licensing")
    productFlavors {
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
        getByName("androidTest").java.directories.add("src/androidTest/kotlin")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    // The upstream photo-filter artifact bundles a 4 KB native processor. Build
    // its unchanged JNI implementation locally with the installed NDK so edits
    // stay available on 16 KB-page devices as well.
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    packaging {
        // All bundled arm64-v8a native libraries are 16-KB ELF page-aligned, so Android can mmap
        // them directly from the APK without legacy extraction overhead.
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "META-INF/library_release.kotlin_module"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

// Keep the Java/Kotlin filter API from the upstream AAR, but strip its legacy
// native processor. The processor is rebuilt below with the current NDK, so
// there is exactly one implementation in every variant (including release).
val legacyPhotoFilters = configurations.create("legacyPhotoFilters") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val sanitizedPhotoFiltersAar = tasks.register<Jar>("sanitizePhotoFiltersAar") {
    from(legacyPhotoFilters.elements.map { files -> zipTree(files.single().asFile) })
    exclude("jni/**")
    archiveBaseName.set("androidphotofilters-no-native")
    archiveExtension.set("aar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/photofilters"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val sanitizedPhotoFiltersDependency = files(sanitizedPhotoFiltersAar.flatMap { it.archiveFile })
    .builtBy(sanitizedPhotoFiltersAar)

// Room writes the exact expected schema per version to app/schemas. Every migration up to v24 was
// written without this, i.e. against a hand-reconstructed CREATE TABLE - and a mismatch there only
// surfaces as a runtime crash on a user's device, never at build time. With the export on, the
// generated JSON is the reference for what a migration has to produce.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.print)
    implementation(libs.android.image.cropper)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation(libs.androidx.documentfile)
    legacyPhotoFilters(libs.androidphotofilters) {
        isTransitive = false
    }
    implementation(sanitizedPhotoFiltersDependency)
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.jxl.coder)
    implementation(libs.okio)
    compileOnly(libs.okhttp)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)
    detektPlugins(libs.compose.detekt)

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-video:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
    implementation("io.coil-kt.coil3:coil-gif:3.3.0")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime.ktx)
    // Both models are bundled and infer entirely on-device. Face detection catches portraits;
    // selfie segmentation adds full-body/back-view coverage for the cautious person-review scan.
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
