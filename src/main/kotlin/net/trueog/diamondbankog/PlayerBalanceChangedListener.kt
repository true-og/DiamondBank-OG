package net.trueog.diamondbankog

interface PlayerBalanceChangedListener {
    fun onUpdate(playerShards: PostgreSQL.PlayerShards)
}