package app.opia.common

object DefaultNotificationRepo : NotificationRepo {
    override fun listDistributors() = emptyList<String>()

    override fun init(instance: String) {
        // do nothing
    }

    override fun registerUnifiedPush(instance: String, distributor: String?) {
        // do nothing
    }
}
