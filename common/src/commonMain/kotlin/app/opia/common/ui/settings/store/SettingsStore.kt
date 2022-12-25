package app.opia.common.ui.settings.store

import app.opia.common.db.Actor
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import com.arkivanov.mvikotlin.core.store.Store

interface SettingsStore : Store<Intent, State, Nothing> {
    sealed class Intent {
    }

    data class State(
        val self: Actor? = null
    )
}
