package net.trueog.diamondbankog

import java.util.*

data class PlayerBalanceChangedEvent(val uuid: UUID, val playerShards: PostgreSQL.PlayerShards)
