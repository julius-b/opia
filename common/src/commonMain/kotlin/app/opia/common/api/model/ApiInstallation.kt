package app.opia.common.api.model

import app.opia.common.db.Installation
import app.opia.common.utils.Platform
import java.util.UUID

/**
 * used for request (no `self`) and response (also because `self` can't be null)
 * created_at: ignored in request, unimportant for response
 */
data class ApiInstallation(
    val id: UUID,
    val name: String,
    val desc: String,
    val os: Platform,
    val client_vname: String,
    //val created_at: Date
) {
    constructor(installation: Installation) : this(
        installation.id, installation.name, installation.desc, installation.os, installation.client_vname
    )
}
