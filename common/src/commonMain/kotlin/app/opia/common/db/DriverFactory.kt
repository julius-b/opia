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

fun createDatabase(driverFactory: DriverFactory): OpiaDatabase {
    val driver = driverFactory.createDriver()
    return OpiaDatabase.invoke(
        driver,
        Actor.Adapter(uuidAdapter, uuidAdapter, uuidAdapter, dateAdapter, dateAdapter),
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
        Owned_field.Adapter(uuidAdapter, uuidAdapter, uuidAdapter, EnumColumnAdapter(), dateAdapter, dateAdapter),
    )
}

val uuidAdapter = object : ColumnAdapter<UUID, String> {
    override fun decode(databaseValue: String) = UUID.fromString(databaseValue)

    override fun encode(value: UUID) = value.toString()
}

val dateAdapter = object : ColumnAdapter<ZonedDateTime, String> {
    override fun decode(databaseValue: String): ZonedDateTime {
        println("[?] dateAdapter > parsing databaseValue: $databaseValue")
        return ZonedDateTime.parse(databaseValue)
    }

    override fun encode(value: ZonedDateTime): String {
        println("[?] dateAdapter > parsing date: $value")
        val str = value.format(DateTimeFormatter.ISO_DATE_TIME)
        println("[?] dateAdapter > formatted: $str")
        return str
    }
}
