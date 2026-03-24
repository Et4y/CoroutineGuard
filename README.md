# CoroutineGuard

[![CI](https://github.com/Et4y/CoroutineGuard/actions/workflows/ci.yml/badge.svg)](https://github.com/Et4y/CoroutineGuard/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-API_24%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-Apache_2.0-blue)

Runtime detection of coroutine hangs, silent cancellations, and scope leaks — with zero overhead in release builds.

---

## Why CoroutineGuard?

| Problem | Without CoroutineGuard | With CoroutineGuard |
|---|---|---|
| **Hanging coroutines** | Silently consume threads and memory for hours with no signal | `onHang` fires after a configurable threshold; forwards to Crashlytics/Sentry |
| **Silent cancellations** | Operations abandoned mid-flight; callers have no idea why | `onSilentCancel` surfaces every bare `CancellationException` with no root cause |
| **Scope leaks** | `GlobalScope` coroutines outlive their Activity indefinitely | `onScopeLeak` fires 500 ms after `Activity.onDestroy()` if children are still active |

---

## Quick Start

**Step 1** — Add the dependency (via [JitPack](https://jitpack.io)):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    debugImplementation("com.github.Et4y.CoroutineGuard:android:1.0.0")
    releaseImplementation("com.github.Et4y.CoroutineGuard:no-op:1.0.0")
}
```

**Step 2** — Install in your `Application`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineGuardAndroid.install(this)
    }
}
```

**Step 3** — That's it. Hang, silent-cancel, and scope-leak events now appear in Logcat tagged `CoroutineGuard`.

---

## Configuration

All options are set once via `CoroutineGuardConfig.Builder`:

```kotlin
CoroutineGuardAndroid.install(
    app = this,
    config = CoroutineGuardConfig.Builder()
        .hangThresholdMs(3_000)            // default: 5000 ms
        .enableInProduction(false)         // default: true — skip entirely in release
        .enableHangDetection(true)         // default: true
        .enableSilentCancelDetection(true) // default: true
        .enableScopeLeakDetection(true)    // default: true
        .onHang { label, durationMs ->
            // called when a coroutine runs longer than hangThresholdMs
        }
        .onSilentCancel { label, cause ->
            // called when a coroutine is cancelled with no root cause
        }
        .onScopeLeak { label ->
            // called when coroutines outlive their Activity scope
        }
        .build()
)
```

Label values come from [`CoroutineName`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-name/):

```kotlin
launch(CoroutineName("sync-orders")) {
    // if this hangs, onHang receives label = "sync-orders"
}
```

---

## Reporters

Add the reporter artifact for ready-made integrations with crash tools:

```kotlin
// app/build.gradle.kts
debugImplementation("com.github.Et4y.CoroutineGuard:reporter:1.0.0")
```

### Firebase Crashlytics

```kotlin
CoroutineGuardAndroid.install(
    app = this,
    config = CoroutineGuardConfig.Builder()
        .reporter(FirebaseReporter())
        .build()
)
```

Requires `com.google.firebase:firebase-crashlytics` in your app (the reporter uses `compileOnly` — it does not bundle Firebase).

### Sentry

```kotlin
CoroutineGuardAndroid.install(
    app = this,
    config = CoroutineGuardConfig.Builder()
        .reporter(SentryReporter())
        .build()
)
```

Requires `io.sentry:sentry-android` in your app.

### Multiple reporters

```kotlin
CoroutineGuardAndroid.install(
    app = this,
    config = CoroutineGuardConfig.Builder()
        .hangThresholdMs(3_000)
        .reporter(
            CompositeReporter(
                FirebaseReporter(),
                SentryReporter(),
            )
        )
        .build()
)
```

### Custom reporter

```kotlin
class MyAnalyticsReporter : CoroutineGuardReporter {
    override fun onHang(label: String?, durationMs: Long) =
        analytics.track("coroutine_hang", label, durationMs)

    override fun onSilentCancel(label: String?, cause: Throwable?) =
        analytics.track("silent_cancel", label)

    override fun onScopeLeak(label: String?) =
        analytics.track("scope_leak", label)
}
```

---

## Debug vs Release

CoroutineGuard is built for zero release overhead — not just skipped logic, but literally no classes loaded.

```kotlin
// app/build.gradle.kts
dependencies {
    // Full implementation: ServiceLoader registers GuardInterceptor into every coroutine,
    // lifecycle observer detects scope leaks, DefaultDebugReporter logs to Logcat.
    debugImplementation("com.github.Et4y.CoroutineGuard:android:1.0.0")

    // No-op stub: identical API surface, empty function bodies, no ServiceLoader file.
    // The JIT compiler inlines the install() call away after the first few invocations.
    releaseImplementation("com.github.Et4y.CoroutineGuard:no-op:1.0.0")
}
```

The two modules share the same package (`com.coroutineguard.android`) and identical function signatures, so app code compiles unchanged against either. They are never on the same classpath simultaneously.

If you want monitoring in production, use `debugImplementation` for both and set `enableInProduction(true)` (the default) in your config.

---

## How It Works

```
JVM startup
  └─ ServiceLoader reads META-INF/services/kotlinx.coroutines.CoroutineContextElement
       └─ GuardInterceptor is placed into the global default CoroutineContext
            │
            ├─ launch { } / async { }
            │    └─ copyForChild() → new GuardInterceptor instance
            │         └─ startTime = System.currentTimeMillis()
            │
            └─ coroutine resumes (first time)
                 └─ updateThreadContext() → job.invokeOnCompletion { }
                      ├─ duration > threshold?       → onHang
                      ├─ CancellationException(cause=null)? → onSilentCancel
                      └─ job.children.any { isActive }?     → onScopeLeak
```

**ServiceLoader** — `GuardInterceptor` is registered once at JVM startup via a `META-INF/services` file. `CoroutineGuard.install()` does not register anything; it only writes the config into a `@Volatile` field that the already-loaded interceptor reads on each coroutine completion.

**`copyForChild()`** — `GuardInterceptor` implements `CopyableThreadContextElement`. Every `launch` or `async` call triggers `copyForChild()`, which creates a fresh `GuardInterceptor` instance for the child coroutine with its own `startTime`. Without this, all coroutines would share the parent's start time and the single `invokeOnCompletion` listener.

**`:no-op`** — Contains no `META-INF/services` file. The `GuardInterceptor` class does not exist in the release APK's classpath. `ServiceLoader` finds nothing to load. `install()` is an empty function. Total overhead: one function call that the JIT eliminates.

---

## Module Structure

| Module | Plugin | Depends on | Purpose |
|---|---|---|---|
| `:core` | `kotlin.jvm` | — | `GuardInterceptor`, `CoroutineGuard`, `CoroutineGuardConfig`, exception types |
| `:android` | `android.library` | `:core` | `CoroutineGuardAndroid.install()`, lifecycle observer, Logcat reporter |
| `:reporter` | `android.library` | `:core` | `CoroutineGuardReporter` interface, `FirebaseReporter`, `SentryReporter`, `CompositeReporter` |
| `:no-op` | `android.library` | — | Zero-cost stubs; identical API surface to `:android`; no ServiceLoader file |

---

## Contributing

1. Fork the repository and create a branch from `main`
2. Make your changes — all modules must build and tests must pass: `./gradlew build test`
3. If adding a new detection type, implement it in `:core` and add a corresponding no-op in `:no-op`
4. If adding a new reporter, add it to `:reporter` using `compileOnly` for any third-party SDK dependency
5. Open a pull request — CI will run automatically

---

## License

```
Copyright 2024 Et4y

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
