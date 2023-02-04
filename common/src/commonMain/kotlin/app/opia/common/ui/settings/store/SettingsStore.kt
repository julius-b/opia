package app.opia.common.ui.settings.store

import app.opia.common.db.Actor
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import com.arkivanov.mvikotlin.core.store.Store

interface SettingsStore : Store<Intent, State, Nothing> {
    sealed class Intent {
        data class SetDistributor(val distributor: String) : Intent()
        data class SetName(val name: String) : Intent()
        data class SetDesc(val desc: String) : Intent()
        object UpdateAccount : Intent()
    }

    data class State(
        val self: Actor? = null,
        val name: String = "",
        val desc: String = "",
        val distributors: List<String> = emptyList(),
        val distributor: String? = null,
        val endpoint: String? = null
    )
}
