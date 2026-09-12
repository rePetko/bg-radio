plugins {
    id("com.android.application")
}

fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

// versionCode must increase monotonically; commit count does that for free.
val gitVersionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
// Reads "1.0" on a v1.0 tag, "1.0-3-gabc1234" three commits past it,
// and falls back to the bare hash until the first tag exists.
val gitVersionName = git("describe", "--tags", "--always", "--dirty")
    ?.removePrefix("v") ?: "0-dev"

android {
    namespace = "com.example.bgradio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.bgradio"
        minSdk = 24
        targetSdk = 34
        versionCode = gitVersionCode
        versionName = gitVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

base {
    archivesName = "bg-radio-$gitVersionName"
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dnsjava:dnsjava:3.6.5")
}
