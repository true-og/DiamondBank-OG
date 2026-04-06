package net.trueog.diamondbankog.util

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.Bukkit

internal object MainThreadBlock {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <T> runOnMainThread(block: () -> T): T {
        return if (Bukkit.isPrimaryThread()) {
            block()
        } else {
            suspendCancellableCoroutine { cont ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        Runnable {
                            try {
                                cont.resume(block())
                            } catch (e: Throwable) {
                                cont.resumeWithException(e)
                            }
                        },
                    )
            }
        }
    }
}
