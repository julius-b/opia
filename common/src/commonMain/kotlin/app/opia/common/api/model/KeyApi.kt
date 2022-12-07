@file:OptIn(ExperimentalUnsignedTypes::class)

package app.opia.common.api.model

import app.opia.common.api.repository.KeyAlgo
import app.opia.common.api.repository.KeyLifetime
import app.opia.common.api.repository.KeyType
import app.opia.common.db.Key_pair
import app.opia.common.db.Vault_key
import ch.oxc.nikea.extra.toBase64
import java.time.ZonedDateTime
import java.util.*

data class OutstandingEKexKeysResponse(
    // server uses Long but would never expect that many
    val outstanding: Int
)

// returned by /outstanding because that's the only network request
// that needs to be performed regularly
data class OutstandingEKexKeysHints(
    val identity_key: UUID?,
    val skex_key: UUID?
)

// TODO: No JsonAdapter for class kotlin.UByteArray - even though it is registered
// TODO: replace Moshi with kotlinx.serialization
// no cleartext key for api
data class ApiVaultKey(
    val id: UUID,
    val actor_id: UUID,
    val algo: String,
    val prevk_id: UUID?,
    val prevk_bckp: String, // Base64 encoded UByteArray
    val seck_enc: String, // Base64 encoded UByteArray
    val args: String,
    val secret_update_id: UUID,
    val created_at: ZonedDateTime,
    val deleted_at: ZonedDateTime?
) {
    fun toVaultKey() = Vault_key(
        this.id,
        this.actor_id,
        this.algo,
        this.prevk_id,
        Base64.getDecoder().decode(this.prevk_bckp).toUByteArray(),
        Base64.getDecoder().decode(this.seck_enc).toUByteArray(),
        this.args,
        UByteArray(0),
        this.secret_update_id,
        this.created_at,
        this.deleted_at
    )
}

// ApiVaultKey without server-side created timestamps
data class CreateVaultKeyParams(
    val id: UUID,
    val actor_id: UUID,
    val algo: String,
    val prevk_id: UUID?,
    val prevk_bckp: String, // Base64 encoded UByteArray
    val seck_clr: String, // TODO only during DEBUG
    val seck_enc: String, // Base64 encoded UByteArray
    val args: String,
    val secret_update_id: UUID,
) {
    companion object Factory {
        fun fromVaultKey(vaultKey: Vault_key) = CreateVaultKeyParams(
            vaultKey.id,
            vaultKey.actor_id,
            vaultKey.algo,
            vaultKey.prevk_id,
            vaultKey.prevk_bckp.toByteArray().toBase64(),
            vaultKey.seck_clr.toByteArray().toBase64(),
            vaultKey.seck_enc.toByteArray().toBase64(),
            vaultKey.args,
            vaultKey.secret_update_id
        )
    }
}

// Key_pair contains local-only fields `seck_clr` & `synced`
data class ApiKeyPair(
    val id: UUID,
    val actor_id: UUID,
    val installation_id: UUID,
    val ioid: UUID,
    val type: KeyType,
    val lifetime: KeyLifetime,
    val algo: KeyAlgo,
    val pubk: String, // Base64 encoded UByteArray
    val pubk_signed: String, // Base64 encoded UByteArray
    val signing_key_id: UUID?, // null for identityKey
    val used: Boolean,
    val created_at: ZonedDateTime,
    val deleted_at: ZonedDateTime?
) {
    fun toKeyPair() = Key_pair(
        this.id,
        this.actor_id,
        this.installation_id,
        this.ioid,
        this.type,
        this.lifetime,
        this.algo,
        Base64.getDecoder().decode(this.pubk).toUByteArray(),
        Base64.getDecoder().decode(this.pubk_signed).toUByteArray(),
        this.signing_key_id,
        UByteArray(0),
        this.used,
        synced = true,
        this.created_at,
        this.deleted_at
    )
}

// without: seck_clr, seck_enc, vault_key_id, used, synced, created_at, deleted_at
data class CreateKeyPairParams(
    val id: UUID,
    val actor_id: UUID,
    val installation_id: UUID,
    val ioid: UUID,
    val type: KeyType,
    val lifetime: KeyLifetime,
    val algo: KeyAlgo,
    val pubk: String, // Base64 encoded UByteArray
    val pubk_signed: String, // Base64 encoded UByteArray
    val signing_key_id: UUID?, // null for identityKey
) {
    companion object Factory {
        fun fromKeyPair(keyPair: Key_pair) = CreateKeyPairParams(
            keyPair.id,
            keyPair.actor_id,
            keyPair.installation_id,
            keyPair.ioid,
            keyPair.type,
            keyPair.lifetime,
            keyPair.algo,
            Base64.getEncoder().encodeToString(keyPair.pubk.toByteArray()),
            Base64.getEncoder().encodeToString(keyPair.pubk_signed.toByteArray()),
            keyPair.signing_key_id
        )
    }
}
