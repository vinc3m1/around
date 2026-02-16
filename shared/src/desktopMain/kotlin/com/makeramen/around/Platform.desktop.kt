package com.makeramen.around

actual fun logDebug(tag: String, message: () -> String) {
    println("[$tag] ${message()}")
}
