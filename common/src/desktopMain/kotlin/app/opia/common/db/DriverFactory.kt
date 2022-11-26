package app.opia.common.db

import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import java.io.File

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        //val driver: SqlDriver =  JdbcSqliteDriver("jdbc:sqlite:$path") //JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // TODO $HOME/.opia
        val databasePath = File(System.getProperty("java.io.tmpdir"), "opia-compose.db")
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${databasePath.absolutePath}")
        OpiaDatabase.Schema.create(driver)
        return driver
    }
}
