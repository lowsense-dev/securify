package app.securify.core

/** Case-insensitive subsequence match: "frf" matches "Frankfurt". */
fun fuzzyMatch(text: String, query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    val haystack = text.lowercase()
    var index = 0
    for (char in needle) {
        index = haystack.indexOf(char, index)
        if (index < 0) return false
        index++
    }
    return true
}
