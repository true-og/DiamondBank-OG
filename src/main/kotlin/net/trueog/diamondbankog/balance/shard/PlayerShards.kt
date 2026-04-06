package net.trueog.diamondbankog.balance.shard

data class PlayerShards(val bank: Long, val inventory: Long, val enderChest: Long) {
    val total = bank + inventory + enderChest
}
