package app.opia.common.api.repository

import app.opia.common.api.NetworkResponse
import app.opia.common.api.endpoint.InstallationApi
import app.opia.common.api.model.ApiInstallation
import app.opia.common.db.Installation
import app.opia.common.db.InstallationQueries
import app.opia.common.utils.getPlatformName
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InstallationRepo(
    private val db: InstallationQueries,
    private val api: InstallationApi
) {
    private val mutex = Mutex()

    // server returns no usable errors
    suspend fun upsertInstallation(): Installation? = mutex.withLock {
        println("[*] upsert_install > syncing...")
        var installation = db.getSelf().asFlow().mapToOneOrNull().first()
        println("[*] upsert_install > self: $installation")
        if (installation == null) {
            // NOTE: InetAddress causes exception on android, use expect/actual: withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName
            val hostname = "a name"
            val os = getPlatformName()//.name.lowercase()
            installation = Installation(
                true, UUID.randomUUID(), hostname, "uh wee", os, "opia_kt_mpp/1"
            )
            db.insert(installation)
            println("[+] upsert_install > saved: $installation")
        }
        println("[*] upsert_install > synchronizing...")
        val res = api.put(ApiInstallation(installation))

        return@withLock when (res) {
            is NetworkResponse.ApiSuccess -> {
                println("[*] upsert_install > upsert successful")
                installation
            }
            else -> {
                println("[-] upsert_install > bad response: $res")
                null
            }
        }
    }
}
