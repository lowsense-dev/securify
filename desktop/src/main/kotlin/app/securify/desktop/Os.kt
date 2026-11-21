package app.securify.desktop

object Os {
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}
