package com.coroutineguard.core

class CoroutineGuardConfig private constructor(
    val hangThresholdMs: Long,
    val enableInProduction: Boolean,
    val enableHangDetection: Boolean,
    val enableSilentCancelDetection: Boolean,
    val enableScopeLeakDetection: Boolean,
    val onHang: ((label: String?, durationMs: Long) -> Unit)?,
    val onSilentCancel: ((label: String?, cause: Throwable?) -> Unit)?,
    val onScopeLeak: ((label: String?) -> Unit)?,
) {

    class Builder {
        private var hangThresholdMs: Long = 5_000L
        private var enableInProduction: Boolean = true
        private var enableHangDetection: Boolean = true
        private var enableSilentCancelDetection: Boolean = true
        private var enableScopeLeakDetection: Boolean = true
        private var onHang: ((label: String?, durationMs: Long) -> Unit)? = null
        private var onSilentCancel: ((label: String?, cause: Throwable?) -> Unit)? = null
        private var onScopeLeak: ((label: String?) -> Unit)? = null

        fun hangThresholdMs(value: Long) = apply { hangThresholdMs = value }
        fun enableInProduction(value: Boolean) = apply { enableInProduction = value }
        fun enableHangDetection(value: Boolean) = apply { enableHangDetection = value }
        fun enableSilentCancelDetection(value: Boolean) = apply { enableSilentCancelDetection = value }
        fun enableScopeLeakDetection(value: Boolean) = apply { enableScopeLeakDetection = value }
        fun onHang(callback: (label: String?, durationMs: Long) -> Unit) = apply { onHang = callback }
        fun onSilentCancel(callback: (label: String?, cause: Throwable?) -> Unit) = apply { onSilentCancel = callback }
        fun onScopeLeak(callback: (label: String?) -> Unit) = apply { onScopeLeak = callback }

        fun build(): CoroutineGuardConfig {
            require(hangThresholdMs > 0) {
                "hangThresholdMs must be > 0, was $hangThresholdMs"
            }
            return CoroutineGuardConfig(
                hangThresholdMs = hangThresholdMs,
                enableInProduction = enableInProduction,
                enableHangDetection = enableHangDetection,
                enableSilentCancelDetection = enableSilentCancelDetection,
                enableScopeLeakDetection = enableScopeLeakDetection,
                onHang = onHang,
                onSilentCancel = onSilentCancel,
                onScopeLeak = onScopeLeak,
            )
        }
    }
}
