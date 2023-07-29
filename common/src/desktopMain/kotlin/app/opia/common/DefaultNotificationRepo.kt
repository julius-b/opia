package app.opia.common

import java.util.*

object DefaultNotificationRepo : NotificationRepo {
    override suspend fun getCurrentLocalReg(ioid: UUID): ApiPushReg? {
        return null
    }
}
