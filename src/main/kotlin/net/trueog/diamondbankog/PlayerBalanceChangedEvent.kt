package net.trueog.diamondbankog

import java.util.UUID

data class PlayerBalanceChangedEvent(val uuid: UUID, val playerShards: PostgreSQL.PlayerShards)
