package app.opia.common.api.model

import java.time.ZonedDateTime

const val InstallationId = "Installation-Id"
const val Authorization = "Authorization"
const val RefreshToken = "Refresh-Token"
const val ChallengeResponse = "Challenge-Response"

const val VaultKey = "Vault-Key"
const val SigningKey = "Signing-Key"
const val StaticKexKey = "Static-Kex-Key"

data class GenericDeleteResponse(
    val rows: Long?, val deleted_at: ZonedDateTime?
)
