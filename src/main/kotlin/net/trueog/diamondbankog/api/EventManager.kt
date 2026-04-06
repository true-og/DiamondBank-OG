package net.trueog.diamondbankog.api

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import net.trueog.diamondbankog.balance.shard.PlayerShards

internal class EventManager {
    private var listeners = CopyOnWriteArrayList<PlayerBalanceChangedListener>()

    fun sendUpdate(uuid: UUID, playerShards: PlayerShards) {
        listeners.forEach { it.onUpdate(PlayerBalanceChangedEvent(uuid, playerShards)) }
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
