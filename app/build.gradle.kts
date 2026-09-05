import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

providers.gradleProperty("ahuimarkBuildDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

android {
    namespace = "tw.ahuimark.battery"
    compileSdk = 36

    defaultConfig {
        applicationId = "tw.ahuimark.battery"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("glb", "pck")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("org.godotengine:godot:4.7.2.stable")

    val cameraVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-video:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val verifyGodotPack by tasks.registering {
    val pack = file("src/main/assets/prism_front.pck")
    val sources = rootProject.fileTree("godot") {
        include("scripts/**", "scenes/**", "shaders/**", "*.godot", "*.txt")
    }
    inputs.files(sources)
    inputs.file(pack)
    doLast {
        check(pack.isFile && sources.files.none { it.lastModified() > pack.lastModified() }) {
            "Godot pack missing or stale. Run tools/build-godot.ps1 -Godot <Godot 4.7.2 console executable>."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyGodotPack) }
