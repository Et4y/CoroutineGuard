plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.coroutineguard.android"
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
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ── Publishing ────────────────────────────────────────────────────────────────────────────
// afterEvaluate is required for Android library modules: the `release` component is created
// by the Android Gradle Plugin during evaluation and is not available at configuration time.
val GROUP: String by project
val VERSION_NAME: String by project

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = GROUP
                artifactId = "android"
                version = VERSION_NAME
            }
        }
    }
}
