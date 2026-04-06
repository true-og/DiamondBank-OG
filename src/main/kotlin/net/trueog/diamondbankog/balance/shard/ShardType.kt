package net.trueog.diamondbankog.balance.shard

enum class ShardType(val string: String) {
    BANK("bank_shards"),
    INVENTORY("inventory_shards"),
    ENDER_CHEST("ender_chest_shards"),
    TOTAL("bank_shards, inventory_shards, ender_chest_shards"),
}
