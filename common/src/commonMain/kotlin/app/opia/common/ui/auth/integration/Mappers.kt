package app.opia.common.ui.auth.integration

import app.opia.common.ui.auth.OpiaAuth.Event
import app.opia.common.ui.auth.OpiaAuth.Model
import app.opia.common.ui.auth.store.AuthStore.Label
import app.opia.common.ui.auth.store.AuthStore.State

internal val stateToModel: (State) -> Model = {
    Model(
        isLoading = it.isLoading,
        unique = it.unique,
        uniqueError = it.uniqueError,
        secret = it.secret,
        secretError = it.secretError
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.Authenticated -> Event.Authenticated
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}
