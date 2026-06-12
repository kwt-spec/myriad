package com.cauldron.myriad

import android.app.Application

/**
 * Last-gasp persistence hook (MASTER_PLAN §8): if the process is about to die
 * from an uncaught exception, flush the newest game state first so a crash
 * resumes exactly where it happened. The store's atomic write protocol makes
 * this safe even if a normal save is in flight on another thread.
 */
object PanicSaver {
    @Volatile
    var hook: (() -> Unit)? = null
}

class MyriadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { PanicSaver.hook?.invoke() }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
