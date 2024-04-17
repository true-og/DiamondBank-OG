package net.trueog.diamondbankog

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object Shard {
    val namespacedKey get() = NamespacedKey(DiamondBankOG.plugin, "diamondshard")

    fun createItemStack(amount: Int): ItemStack {
        val shard = ItemStack(Material.PRISMARINE_SHARD, amount)
        val shardMeta = shard.itemMeta
        shardMeta.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, "")
        shardMeta.addEnchant(Enchantment.DURABILITY, 1, false)
        shardMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        shard.itemMeta = shardMeta
        return shard
    }
}