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
    debugImplementation("com.github.Et4y.CoroutineGuard:android:latest.release")
    releaseImplementation("com.github.Et4y.CoroutineGuard:no-op:latest.release")
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

**Step 3** — Use `guardedScope` instead of `viewModelScope` in your ViewModels:

```kotlin
import com.coroutineguard.android.guardedScope

class OrdersViewModel : ViewModel() {
    fun loadOrders() = guardedScope.launch {
        // monitored automatically ✅
        // label = "OrdersViewModel" auto-detected ✅
    }
}
```

That's it. Hang, silent-cancel, and scope-leak events now appear in Logcat tagged `CoroutineGuard`.

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

Label values are auto-detected from the ViewModel class name when using `guardedScope`. You can also pass an explicit label via the lower-level `guarded()` extension:

```kotlin
// guardedScope → label auto-detected as "OrdersViewModel"
guardedScope.launch { ... }

// guarded() → explicit label for non-ViewModel scopes
someScope.guarded("sync-orders").launch { ... }

// CoroutineName → label for individual coroutines
guardedScope.launch(CoroutineName("fetch-user")) { ... }
```

---

## Reporters

Add the reporter artifact for ready-made integrations with crash tools:

```kotlin
// app/build.gradle.kts
debugImplementation("com.github.Et4y.CoroutineGuard:reporter:latest.release")
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
    // Full implementation: GuardInterceptor is injected via guardedScope,
    // lifecycle observer detects scope leaks, DefaultDebugReporter logs to Logcat.
    debugImplementation("com.github.Et4y.CoroutineGuard:android:latest.release")

    // No-op stub: identical API surface, guardedScope returns viewModelScope unchanged,
    // install() is an empty function. The JIT eliminates all calls after inlining.
    releaseImplementation("com.github.Et4y.CoroutineGuard:no-op:latest.release")
}
```

The two modules share the same package (`com.coroutineguard.android`) and identical function signatures, so app code compiles unchanged against either. They are never on the same classpath simultaneously.

If you want monitoring in production, use `implementation` (not `debugImplementation`) for the `:android` artifact — `enableInProduction(true)` is the default, so no config change is needed.

---

## How It Works

```
ViewModel.guardedScope
  └─ viewModelScope.guarded("OrdersViewModel")
       └─ CoroutineScope(viewModelScope.coroutineContext + GuardInterceptor())
            │
            ├─ launch { } / async { }
            │    └─ copyForChild() → new GuardInterceptor instance
            │         └─ startTime = System.currentTimeMillis()
            │
            └─ coroutine resumes (first time)
                 └─ updateThreadContext() → job.invokeOnCompletion { }
                      ├─ duration > threshold?              → onHang
                      ├─ CancellationException(cause=null)? → onSilentCancel
                      └─ job.children.any { isActive }?     → onScopeLeak
```

**`guardedScope`** — Extension property on `ViewModel`. Calls `viewModelScope.guarded(simpleName)` which wraps the existing scope with a fresh `GuardInterceptor` in the coroutine context. No global registration, no `ServiceLoader`, no static state.

**`copyForChild()`** — `GuardInterceptor` implements `CopyableThreadContextElement`. Every `launch` or `async` call triggers `copyForChild()`, which creates a fresh `GuardInterceptor` instance for the child coroutine with its own `startTime`. Without this, all coroutines would share the parent's start time and the single `invokeOnCompletion` listener.

**`:no-op`** — `guardedScope` returns `viewModelScope` unchanged. `GuardInterceptor` does not exist in the release APK's classpath. `install()` is an empty function. Total overhead: one property access that the JIT eliminates.

---

## Module Structure

| Module | Plugin | Depends on | Purpose |
|---|---|---|---|
| `:core` | `kotlin.jvm` | — | `GuardInterceptor`, `CoroutineGuard`, `CoroutineGuardConfig`, exception types |
| `:android` | `android.library` | `:core` | `CoroutineGuardAndroid.install()`, lifecycle observer, Logcat reporter |
| `:reporter` | `android.library` | `:core` | `CoroutineGuardReporter` interface, `FirebaseReporter`, `SentryReporter`, `CompositeReporter` |
| `:no-op` | `android.library` | — | Zero-cost stubs; identical API surface to `:android`; `guardedScope` returns `viewModelScope` unchanged |

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
