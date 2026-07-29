package net.trueog.diamondbankog.api

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.balance.shard.PlayerShards

internal class EventManager {
    private var listeners = CopyOnWriteArrayList<PlayerBalanceChangedListener>()
    private val queues = ConcurrentHashMap<UUID, Channel<PlayerBalanceChangedEvent>>()

    // One channel + one consumer coroutine per uuid. Consumer processes events
    // strictly in send order, off any lock, so listeners can safely call back
    // into the API without deadlocking TransactionLock.
    private fun queueFor(uuid: UUID) =
        queues.computeIfAbsent(uuid) {
            Channel<PlayerBalanceChangedEvent>(Channel.UNLIMITED).also { channel ->
                scope.launch {
                    for (event in channel) {
                        listeners.forEach {
                            try {
                                it.onUpdate(event)
                            } catch (e: Exception) {
                                plugin.logger.log(Level.SEVERE, "PlayerBalanceChangedEvent listener threw", e)
                            }
                        }
                    }
                }
            }
        }

    fun sendUpdate(uuid: UUID, playerShards: PlayerShards) {
        queueFor(uuid).trySend(PlayerBalanceChangedEvent(uuid, playerShards))
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
