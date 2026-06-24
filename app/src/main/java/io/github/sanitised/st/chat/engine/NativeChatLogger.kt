package io.github.sanitised.st.chat.engine

import android.util.Log

interface NativeChatLogger {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)

    object Android : NativeChatLogger {
        override fun info(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun warn(tag: String, message: String, throwable: Throwable?) {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }

    object None : NativeChatLogger {
        override fun info(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
