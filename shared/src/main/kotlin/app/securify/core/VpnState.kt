package app.securify.core

sealed interface VpnState {
    data object Off : VpnState
    data object Connecting : VpnState
    data class On(val server: String, val since: Long) : VpnState
    data class Failed(val message: String) : VpnState
}

data class Traffic(val up: Long = 0, val down: Long = 0, val upTotal: Long = 0, val downTotal: Long = 0)
