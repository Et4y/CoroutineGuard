package com.coroutineguard.reporter

import com.coroutineguard.core.CoroutineHangException
import com.coroutineguard.core.ScopeLeakException
import com.coroutineguard.core.SilentCancellationException
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

/**
 * Forwards CoroutineGuard detection events to Firebase Crashlytics as non-fatal exceptions.
 *
 * Each event type is mapped to a dedicated [RuntimeException] subclass so Crashlytics groups
 * them into separate issues — hang events don't pollute the silent-cancel bucket and vice versa.
 *
 * ── Prerequisites ───────────────────────────────────────────────────────────────────────
 *
 * This class compiles against Firebase Crashlytics but does NOT bundle it (compileOnly).
 * The consuming app MUST declare its own Crashlytics dependency, e.g.:
 *
 *   // app/build.gradle.kts
 *   implementation(platform("com.google.firebase:firebase-bom:33.x.x"))
 *   implementation("com.google.firebase:firebase-crashlytics")
 *
 * If the app does not have Crashlytics on the classpath, instantiating [FirebaseReporter]
 * will throw [NoClassDefFoundError] at runtime. Guard the instantiation behind a
 * try/catch or a build-flavour check if Crashlytics is optional in your project.
 *
 * Firebase must also be initialized before any reporter method is called.
 * This is automatically handled by FirebaseApp.initializeApp() which the
 * Google Services Gradle plugin wires into your app's startup.
 */
class FirebaseReporter : CoroutineGuardReporter {

    override fun onHang(label: String?, durationMs: Long) {
        // recordException() logs a non-fatal event: the app does not crash, but the event
        // appears in the Crashlytics "Non-fatals" tab grouped by exception class + message.
        Firebase.crashlytics.recordException(
            CoroutineHangException(label, durationMs)
        )
    }

    override fun onSilentCancel(label: String?, cause: Throwable?) {
        // SilentCancellationException wraps `cause` as its Java cause, so Crashlytics
        // renders the full exception chain — the CancellationException and any inner cause.
        Firebase.crashlytics.recordException(
            SilentCancellationException(label, cause)
        )
    }

    override fun onScopeLeak(label: String?) {
        Firebase.crashlytics.recordException(
            ScopeLeakException(label)
        )
    }
}
