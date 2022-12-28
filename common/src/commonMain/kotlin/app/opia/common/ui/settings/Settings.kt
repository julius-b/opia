package app.opia.common.ui.settings

import app.opia.common.db.Actor
import com.arkivanov.decompose.value.Value

interface OpiaSettings {

    val models: Value<Model>

    suspend fun logoutClicked()

    data class Model(
        val self: Actor?
    )
}
