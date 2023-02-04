package app.opia.common

interface NotificationRepo {
    fun listDistributors(): List<String>

    fun init(instance: String)

    fun registerUnifiedPush(instance: String, distributor: String? = null)
}
