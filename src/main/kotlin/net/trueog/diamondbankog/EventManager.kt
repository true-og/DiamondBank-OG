package net.trueog.diamondbankog

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

internal class EventManager {
    private var listeners = CopyOnWriteArrayList<PlayerBalanceChangedListener>()

    fun sendUpdate(uuid: UUID, playerShards: PostgreSQL.PlayerShards) {
        listeners.forEach { it.onUpdate(PlayerBalanceChangedEvent(uuid, playerShards)) }
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
