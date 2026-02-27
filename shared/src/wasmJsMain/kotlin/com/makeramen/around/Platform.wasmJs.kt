package com.makeramen.around

@JsFun("(msg) => console.log(msg)")
private external fun consoleLog(msg: String)

actual fun logDebug(tag: String, message: () -> String) {
    consoleLog("[$tag] ${message()}")
}
