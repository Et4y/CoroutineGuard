plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// ── Publishing ────────────────────────────────────────────────────────────────────────────
// JitPack reads these coordinates to build the Maven artifact.
// To consume: com.github.YOUR_USERNAME.CoroutineGuard:core:<tag>
val GROUP: String by project
val VERSION_NAME: String by project

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = GROUP
            artifactId = "core"
            version = VERSION_NAME
        }
    }
}
