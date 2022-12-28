package app.opia.common.ui.home.store

import app.opia.common.db.Actor
import app.opia.common.ui.home.store.HomeStore.State
import com.arkivanov.mvikotlin.core.store.Store

interface HomeStore : Store<Nothing, State, Nothing> {
    data class State(
        val self: Actor? = null
    )
}