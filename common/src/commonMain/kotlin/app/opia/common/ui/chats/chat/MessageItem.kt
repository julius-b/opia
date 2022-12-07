package app.opia.common.ui.chats.chat

import java.time.ZonedDateTime

data class MessageItem(
    val from: String?,
    val text: String,
    val created_at: ZonedDateTime
)
