package com.myapp

import android.content.Context

fun foo(context: Context) {
    with (context) {
        with (2) {
            getString(R.string.zxc)
        }
    }
}

inline fun <T, R> with(receiver: T, block: T.() -> R): R = receiver.block()
