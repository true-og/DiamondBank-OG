package net.trueog.diamondbankog

internal class EventManager {
    private var listeners = listOf<PlayerBalanceChangedListener>()

    fun sendUpdate(playerShards: PostgreSQL.PlayerShards) {
        listeners.forEach { it.onUpdate(playerShards) }
    }

    fun register(listener: PlayerBalanceChangedListener) {
        listeners += listener
    }
}
