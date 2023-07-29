package app.opia.android.notify

import app.opia.common.ApiPushReg
import app.opia.common.NotificationRepo
import java.util.*

// const in sync with server
const val ProviderFCMNoUP = "FCM_WITHOUT_UP"

// android/ module generally unaware of user db
class FCMNotificationRepo : NotificationRepo {

    // system reg
    override suspend fun getCurrentLocalReg(ioid: UUID): ApiPushReg? {
        val fcmToken = FCMReceiver.getToken() ?: return null
        return ApiPushReg(ProviderFCMNoUP, fcmToken, null)
    }
}
