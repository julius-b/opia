package app.opia.common

import java.util.*

// nullable ioid
// NOTE: consider making db field nullable as fcm could theoretically be stored there
data class ApiPushReg(
    // ProviderFCMNoUP or UP provider
    var provider: String,
    // fcm token or url
    val endpoint: String,
    // ioid, always null for fcm
    var ioid: UUID?
)

interface NotificationRepo {

    // null: user has no choice
    // TODO only show dropdown & endpoint when not null
    fun listDistributors(): List<String>? = null

    // null: nothing registered
    suspend fun getCurrentLocalReg(ioid: UUID): ApiPushReg?

    fun init(instance: String) {}

    fun setDistributor(instance: String, distributor: String? = null) {
        throw UnsupportedOperationException()
    }
}
