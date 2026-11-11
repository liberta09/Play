package com.kaan.play.ui.party

import android.content.Context
import com.kaan.play.R
import com.kaan.play.data.party.WatchPartyError
import com.kaan.play.data.party.WatchPartyException

fun Context.watchPartyErrorMessage(error: WatchPartyError): String = when (error) {
    WatchPartyError.NotAuthenticated -> getString(R.string.watch_party_error_not_authenticated)
    WatchPartyError.InvalidInput -> getString(R.string.watch_party_error_invalid_input)
    WatchPartyError.RoomNotFound -> getString(R.string.watch_party_error_room_not_found)
    WatchPartyError.RoomFull -> getString(R.string.watch_party_error_room_full)
    WatchPartyError.NotHostOrClosed -> getString(R.string.watch_party_error_not_host_or_closed)
    WatchPartyError.CodeGenerationFailed -> getString(R.string.watch_party_error_code_generation_failed)
    WatchPartyError.ServerUnavailable -> getString(R.string.watch_party_error_server_unavailable)
    WatchPartyError.ConnectionFailed -> getString(R.string.watch_party_error_connection_failed)
    WatchPartyError.Network -> getString(R.string.watch_party_error_network)
    is WatchPartyError.Unknown -> getString(R.string.watch_party_error_connection_failed)
}

fun Throwable.toWatchPartyErrorMessage(context: Context): String = when (this) {
    is WatchPartyException -> context.watchPartyErrorMessage(error)
    else -> message ?: context.getString(R.string.watch_party_error_connection_failed)
}
