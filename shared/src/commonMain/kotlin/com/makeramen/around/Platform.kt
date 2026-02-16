package com.makeramen.around

/** Platform-specific debug logging */
expect fun logDebug(tag: String, message: () -> String)
