package net.trueog.diamondbankog.api

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.balance.shard.PlayerShards

internal class EventManager {
    private var listeners = CopyOnWriteArrayList<PlayerBalanceChangedListener>()

    fun sendUpdate(uuid: UUID, playerShards: PlayerShards) {
        scope.launch { // Launch new coroutine to dispatch listeners to avoid TransactionLock deadlock
            listeners.forEach {
                try {
                    it.onUpdate(PlayerBalanceChangedEvent(uuid, playerShards))
                } catch (e: Exception) {
                    plugin.logger.log(Level.SEVERE, "PlayerBalanceChangedEvent listener threw", e)
                }
            }
        }
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
