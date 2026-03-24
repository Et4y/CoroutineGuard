plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.coroutineguard.reporter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    // compileOnly: we compile against Firebase and Sentry APIs so we can call them,
    // but we do NOT bundle them in the library's AAR. The consuming app already declares
    // these as its own dependencies (typically via Firebase BOM or Sentry's Gradle plugin).
    //
    // Why NOT implementation/api?
    //   • Bundling would duplicate classes on the app's compile classpath → build errors.
    //   • The app's version of Firebase/Sentry might differ from ours → version conflicts.
    //   • Most apps already have these SDKs; we should not force a second copy.
    //
    // Side effect: if a developer adds FirebaseReporter() to their config but has NOT added
    // the Firebase Crashlytics SDK to their app, they will get a NoClassDefFoundError at
    // runtime (not compile time). FirebaseReporter's KDoc documents this precondition.
    compileOnly(libs.firebase.crashlytics)
    compileOnly(libs.sentry.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ── Publishing ────────────────────────────────────────────────────────────────────────────
val GROUP: String by project
val VERSION_NAME: String by project

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = GROUP
                artifactId = "reporter"
                version = VERSION_NAME
            }
        }
    }
}
