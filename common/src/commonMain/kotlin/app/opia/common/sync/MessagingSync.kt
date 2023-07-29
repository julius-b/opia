package app.opia.common.sync

import app.opia.common.di.ServiceLocator
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.LinkedList
import kotlin.coroutines.CoroutineContext

const val SYNC_TIMEOUT = 5000L

sealed class Message {
    internal object Sync : Message()
    internal data class SyncDone(val stats: Result<SyncStats, String>) : Message()

    data class WaitOne(val deferred: CompletableDeferred<SyncStats>) : Message()
}

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.msgActor(authContext: CoroutineContext) =
    actor<Message>(capacity = 2, context = authContext) {
        val sync = ChatSync.init()
        var activeJob: Job? = null

        // select in loop: ticket 5s (can be reset to sync now - ticker rendez-vous will wait and thereby re-calibrate)
        //      or select from channel SyncNow as soon as a loopRegister is registered
        // store here in concurrency-safe place, prevent LL read in sync while adding in add
        var notifyNext = LinkedList<CompletableDeferred<SyncStats>>()
        var notify = LinkedList<CompletableDeferred<SyncStats>>()

        // bootstrap
        launch { channel.send(Message.Sync) }

        launch(ServiceLocator.dispatchers.io) {
            while (isActive) {
                println("[*] MsgSync > syncing push-reg..")
                if (sync.syncPushCfg()) {
                    println("[+] MsgSync > push-reg synced")
                    break
                }
                println("[-] MsgSync > push-reg sync failed, retrying...")
                delay(SYNC_TIMEOUT)
            }
        }

        for (msg in channel) {
            when (msg) {
                Message.Sync -> {
                    if (activeJob != null && activeJob.isActive) {
                        println("[!] MsgSync > start - currently active!")
                        continue
                    }

                    // copy into active set
                    notify = notifyNext
                    notifyNext = LinkedList()
                    println("[+] MsgSync > start - syncing with #${notify.size} listeners...")
                    activeJob = launch(ServiceLocator.dispatchers.io) {
                        val stats = sync.sync()
                        channel.send(Message.SyncDone(stats))
                    }
                }

                is Message.SyncDone -> {
                    println("[+] MsgSync > done - stats: ${msg.stats}...")
                    if (msg.stats is Ok) {
                        println("[*] MsgSync > done - notifying #${notify.size} listeners...")
                        notify.forEach { it.complete(msg.stats.value) }
                    } else println("[-] MsgSync > done - sync failed")

                    if (notifyNext.isNotEmpty()) {
                        println("[+] MsgSync > done - listeners already waiting, re-syncing...")
                        launch { channel.send(Message.Sync) }
                        continue
                    }
                    launch {
                        println("[*] MsgSync > done - sleeping...")
                        delay(SYNC_TIMEOUT)
                        channel.send(Message.Sync)
                    }
                }

                // blocked by sync doesn't matter, just ensure buffer is large enough
                is Message.WaitOne -> {
                    notifyNext.add(msg.deferred)
                    // TODO will just be ignored; add a way to skip next delay and restart immediately
                    launch { channel.send(Message.Sync) }
                }
            }
        }
        println("[+] MsgSync > finishing, doing logout")
        // TODO insert won't work in cancelled state... maybe noncancellable
        sync.logout()
    }
