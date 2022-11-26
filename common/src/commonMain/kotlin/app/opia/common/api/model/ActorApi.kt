package app.opia.common.api.model

import app.opia.common.db.Owned_field
import java.util.UUID

enum class OwnedFieldType{
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

data class AuthHints(
    val owned_fields: Array<Owned_field>
)

data class CreateActorParams(
    val type: Char, val handle: String, val name: String, val secret: String
)

data class CreateAuthSessionParams(
    val unique: String, val secret: String, val cap_chat: Boolean, val ioid: UUID?
)

data class CreateActorMembershipParams(
    val member_of_id: UUID, val is_admin: Boolean
)
