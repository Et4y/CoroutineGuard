// ── No-op module ─────────────────────────────────────────────────────────────────────────
// This module provides the exact same API surface as :android with zero runtime cost.
// Use it as the release variant replacement:
//
//   debugImplementation("com.coroutineguard:android:x.y.z")
//   releaseImplementation("com.coroutineguard:no-op:x.y.z")
//
// WHY android.library and not kotlin.jvm:
// The public API of :android accepts `android.app.Application` as a parameter. To mirror
// that signature exactly, this module needs access to the Android framework classes — which
// only the android.library (or android.application) plugin provides. A kotlin.jvm module
// cannot import android.app.Application.
//
// WHY no dependencies on :core:
// :core contains META-INF/services/kotlinx.coroutines.CoroutineContextElement, which
// registers GuardInterceptor via the ServiceLoader mechanism. If :no-op depended on :core,
// that file would be merged into the final app's resources for the release build, causing
// GuardInterceptor to be loaded and installed into every coroutine context — the exact
// overhead this module exists to eliminate. By being dependency-free, :no-op guarantees
// that no CoroutineContextElement registration file reaches the release APK.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.coroutineguard.noop"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

// Intentionally empty — no dependencies whatsoever.
// The Android framework classes (Application, etc.) are provided by the compileSdk above,
// not by an explicit Gradle dependency. Everything else the no-op needs is self-contained
// in the stub classes defined in this module.
dependencies {
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
                artifactId = "no-op"
                version = VERSION_NAME
            }
        }
    }
}
