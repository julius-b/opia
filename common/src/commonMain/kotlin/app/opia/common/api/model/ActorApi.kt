package app.opia.common.api.model

import app.opia.common.db.Owned_field
import java.util.*

//@JsonClass(generateAdapter = true)
data class AccessToken(
    val exp: Long,
    val sub: UUID,
    val handle: String,
    val auth: Int,
    val iid: UUID,
    val ioid: UUID,
    val cap_chat: Boolean
)

enum class OwnedFieldType {
    email, phone_no
}

enum class OwnedFieldScope {
    signup, login
}

data class CreateOwnedFieldParams(
    val scope: OwnedFieldScope, val content: String
)

data class PatchOwnedFieldParams(
    val verification_code: String
)

data class PatchActorParams(
    val name: String? = null,
    val desc: String? = null,
    val profile_id: UUID? = null,
    val banner_id: UUID? = null
)

data class AuthHints(
    val owned_fields: Array<Owned_field>
)

data class CreateActorParams(
    val type: Char, val handle: String, val name: String, val secret: String
)

data class CreateAuthSessionParams(
    val unique: String, val secret: String, val cap_chat: Boolean, val ioid: UUID?
)
