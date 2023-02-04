package app.opia.common.db

import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

@OptIn(ExperimentalUnsignedTypes::class)
fun createDatabase(driverFactory: DriverFactory): OpiaDatabase {
    val driver = driverFactory.createDriver()
    return OpiaDatabase.invoke(
        driver,
        Actor.Adapter(uuidAdapter, uuidAdapter, uuidAdapter, dateAdapter, dateAdapter),
        Actor_link.Adapter(
            uuidAdapter,
            uuidAdapter,
            dateAdapter,
            uuidAdapter,
            dateAdapter,
            uuidAdapter,
            dateAdapter
        ),
        Auth_session.Adapter(
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            dateAdapter,
            dateAdapter
        ),
        Installation.Adapter(uuidAdapter, EnumColumnAdapter()),
        Installation_ownership.Adapter(
            uuidAdapter, uuidAdapter, uuidAdapter, dateAdapter, dateAdapter
        ),
        Key_pair.Adapter(
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            EnumColumnAdapter(),
            EnumColumnAdapter(),
            EnumColumnAdapter(),
            ubyteArrayAdapter,
            ubyteArrayAdapter,
            uuidAdapter,
            ubyteArrayAdapter,
            dateAdapter,
            dateAdapter
        ),
        Msg.Adapter(
            uuidAdapter, uuidAdapter, uuidAdapter, dateAdapter, dateAdapter
        ),
        Msg_payload.Adapter(
            uuidAdapter
        ),
        Msg_rcpt.Adapter(
            uuidAdapter, uuidAdapter, uuidAdapter, dateAdapter, dateAdapter, dateAdapter
        ),
        Nc_update.Adapter(uuidAdapter, uuidAdapter, uuidAdapter),
        Notification_config.Adapter(uuidAdapter, uuidAdapter),
        Owned_field.Adapter(
            uuidAdapter, uuidAdapter, uuidAdapter, EnumColumnAdapter(), dateAdapter, dateAdapter
        ),
        Rcpt_sync_status.Adapter(
            uuidAdapter, uuidAdapter, dateAdapter
        ),
        Vault_key.Adapter(
            uuidAdapter,
            uuidAdapter,
            uuidAdapter,
            ubyteArrayAdapter,
            ubyteArrayAdapter,
            ubyteArrayAdapter,
            uuidAdapter,
            dateAdapter,
            dateAdapter
        )
    )
}

val uuidAdapter = object : ColumnAdapter<UUID, String> {
    override fun decode(databaseValue: String) = UUID.fromString(databaseValue)

    override fun encode(value: UUID) = value.toString()
}

@OptIn(ExperimentalUnsignedTypes::class)
val ubyteArrayAdapter = object : ColumnAdapter<UByteArray, ByteArray> {
    override fun decode(databaseValue: ByteArray) = databaseValue.asUByteArray()

    override fun encode(value: UByteArray) = value.asByteArray()
}

val dateAdapter = object : ColumnAdapter<ZonedDateTime, String> {
    override fun decode(databaseValue: String): ZonedDateTime {
        println("[?] dateAdapter > parsing databaseValue: $databaseValue")
        return ZonedDateTime.parse(databaseValue)
    }

    override fun encode(value: ZonedDateTime): String {
        println("[?] dateAdapter > parsing date: $value")
        val str = value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        println("[?] dateAdapter > formatted: $str")
        return str
    }
}
