package net.trueog.diamondbankog

import java.util.UUID

internal class EventManager {
    private var listeners = listOf<PlayerBalanceChangedListener>()

    fun sendUpdate(uuid: UUID, playerShards: PostgreSQL.PlayerShards) {
        listeners.forEach { it.onUpdate(PlayerBalanceChangedEvent(uuid, playerShards)) }
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
