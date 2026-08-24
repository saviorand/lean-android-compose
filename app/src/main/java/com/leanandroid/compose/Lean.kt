package com.leanandroid.compose

/**
 * The Lean runtime. Loading libleanshared.so takes a couple of minutes on first
 * launch because it is ~160 MB, so callers should treat [init] as slow.
 */
object Lean {
    init {
        System.loadLibrary("leanshared")
        // lean-compose depends on the runtime above and must load before the bridge
        // that calls into it.
        System.loadLibrary("leancompose")
        System.loadLibrary("leanbridge")
    }

    external fun init(): Boolean
    external fun version(): String
    external fun factorial(n: Int): String
    external fun sumTo(n: Int): Long

    /** The view tree, rendered by Lean on the device from the state passed in. */
    external fun screenJson(count: Int): String
}
