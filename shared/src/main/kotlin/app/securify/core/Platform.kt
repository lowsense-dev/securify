package app.securify.core

/** Filled in by each app at startup: the shared core doesn't know which platform it runs on. */
object Platform {
    var appVersion = "0"
    var versionCode = 0
    var deviceId = ""
    var os = ""
    var osVersion = ""
    var model = ""
}
