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
// WHY no dependency on :core (the library module):
// :core is the module that contains GuardInterceptor, the ServiceLoader registration,
// and the real CoroutineGuardConfig. If :no-op depended on :core, those classes and any
// associated resources would be pulled into the release APK, defeating the purpose of
// this module. All types that :no-op needs (CoroutineGuardConfig, guarded()) are
// re-declared here as empty stubs in the same packages as the real implementations.
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

dependencies {
    // kotlinx-coroutines-core and lifecycle-viewmodel-ktx are external libraries — safe to
    // add here. Neither contains :core module classes or ServiceLoader registrations.
    // Required for CoroutineScope (guarded()) and viewModelScope (GuardedViewModelScopeDelegate).
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
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
