package app.opia.common.db

import android.content.Context
import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(OpiaDatabase.Schema, context, "OpiaDatabase.db")
    }
}
