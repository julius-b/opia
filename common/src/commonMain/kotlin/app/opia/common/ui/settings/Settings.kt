package app.opia.common.ui.settings

import app.opia.common.db.Actor
import com.arkivanov.decompose.value.Value

interface OpiaSettings {

    val models: Value<Model>

    fun onDistributorChanged(distributor: String)

    fun onNameChanged(name: String)

    fun onDescChanged(desc: String)

    fun updateClicked()

    suspend fun logoutClicked()

    data class Model(
        val self: Actor?,
        val name: String,
        val desc: String,
        val distributors: List<String>,
        val distributor: String?,
        val endpoint: String?
    )

    sealed class Output {
        object Logout : Output()
    }
}
