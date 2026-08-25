package com.leanandroid.compose

/**
 * The Lean runtime. [init] still runs Lean's module initialisers, so treat it as
 * slow and keep it off the main thread.
 */
object Lean {
    init {
        // One library: the Lean runtime, the parts of the standard library this app
        // reaches, lean-compose and the JNI bridge are linked together statically and
        // reduced with --gc-sections.
        System.loadLibrary("leanbridge")
    }

    external fun init(): Boolean
    external fun version(): String
    external fun factorial(n: Int): String
    external fun sumTo(n: Int): Long

    /** The view tree, rendered by Lean on the device from the state passed in. */
    external fun screenJson(count: Int): String
}
