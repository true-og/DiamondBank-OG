package net.trueog.diamondbankog.api

import java.util.*
import net.trueog.diamondbankog.balance.shard.PlayerShards

data class PlayerBalanceChangedEvent(val uuid: UUID, val playerShards: PlayerShards)
