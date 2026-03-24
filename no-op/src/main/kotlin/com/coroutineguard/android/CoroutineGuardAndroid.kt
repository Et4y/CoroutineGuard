// ── No-op implementation ──────────────────────────────────────────────────────────────────
// This is a no-op stub. It exists solely to provide the same API surface as
// CoroutineGuardAndroid in :android with zero runtime cost. install() accepts its
// arguments and immediately returns — not a single instruction executes beyond the
// function call overhead, which the JIT will inline away entirely.
//
// Use releaseImplementation("com.coroutineguard:no-op") to activate this stub.
// Use debugImplementation("com.coroutineguard:android") to activate the real implementation.
//
// WHY this file shares the package com.coroutineguard.android with :android:
// App code calls CoroutineGuardAndroid.install() by its fully-qualified name. For the
// release build to compile and run without any source changes, the stub must live in
// exactly the same package as the class it replaces. The two modules are NEVER on the
// same classpath simultaneously (debug uses :android, release uses :no-op), so there
// is no duplicate-class conflict.
//
// WHY there is no META-INF/services file anywhere in this module:
// The real :core module registers GuardInterceptor via:
//   META-INF/services/kotlinx.coroutines.CoroutineContextElement
// That file is what causes GuardInterceptor to be loaded by the JVM ServiceLoader and
// injected into every coroutine context. If this module — or any of its dependencies —
// contained that file, the interceptor would still be registered in release builds,
// loading classes, consuming heap, and adding overhead to every coroutine completion.
// The absence of that file is what makes this module a true zero-overhead replacement.
// ─────────────────────────────────────────────────────────────────────────────────────────
package com.coroutineguard.android

import android.app.Application
import com.coroutineguard.core.CoroutineGuardConfig

/**
 * No-op stub for [com.coroutineguard.android.CoroutineGuardAndroid].
 *
 * Identical API surface to the real implementation — [install] accepts the same parameters
 * so app code compiles unchanged against either artifact. The function body is empty:
 * no config is stored, no lifecycle callbacks are registered, no coroutine interceptors
 * are activated.
 */
object CoroutineGuardAndroid {

    /**
     * No-op. Accepts [app] and [config] to match the real API surface and then does nothing.
     *
     * In a release build, the JVM JIT compiler will inline and eliminate this call entirely
     * after the first few invocations — it is provably side-effect-free.
     */
    @Suppress("UNUSED_PARAMETER")
    fun install(
        app: Application,
        config: CoroutineGuardConfig = CoroutineGuardConfig.Builder().build(),
    ) {
        // Intentionally empty.
    }
}
