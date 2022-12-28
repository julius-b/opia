package app.opia.common.api.repository

import app.opia.common.api.endpoint.MessagingApi
import app.opia.db.OpiaDatabase

// TODO int32 bitmap might be better
enum class LinkPerm {
    nil, read, write, canEditMsg, canInvite, canKick, canEdit, isAdmin
}

// TODO move some ChatSync duplicates here, like encrypt & upload msg
class MessagingRepo(
    private val db: OpiaDatabase, val api: MessagingApi
) {

}
