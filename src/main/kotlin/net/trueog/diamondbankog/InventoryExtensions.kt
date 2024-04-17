package net.trueog.diamondbankog

import net.trueog.diamondbankog.PostgreSQL.*
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import kotlin.math.ceil

object InventoryExtensions {

    private fun Inventory.withdrawShards(shards: Int): Int {
        val removeMap = this.removeItem(Shard.createItemStack(shards))
        if (removeMap.isNotEmpty()) {
            var toBeRemoved = removeMap[0]!!.amount

            val itemStacks = this.contents.filterNotNull().filter { it.type == Material.SHULKER_BOX }
            for (itemStack in itemStacks) {
                val shulkerBlockState = (itemStack.itemMeta as BlockStateMeta)
                val shulkerBox = shulkerBlockState.blockState as ShulkerBox
                val shulkerRemoveMap = shulkerBox.inventory.removeItem(Shard.createItemStack(toBeRemoved))

                shulkerBlockState.blockState = shulkerBox
                itemStack.itemMeta = shulkerBlockState

                toBeRemoved -= if (shulkerRemoveMap.isEmpty()) toBeRemoved else toBeRemoved - shulkerRemoveMap[0]!!.amount
                if (toBeRemoved == 0) break
            }
            return toBeRemoved
        }
        return 0
    }

    private suspend fun Inventory.withdrawDiamonds(shards: Int): Int {
        val diamondsNeeded = ceil(shards / 9.0).toInt()
        val remainder = shards % 9
        val change = if (remainder == 0) 0 else 9 - remainder

        val removeMap = this.removeItem(ItemStack(Material.DIAMOND, diamondsNeeded))
        if (removeMap.isEmpty()) {
            if (change != 0) {
                val error = this.addBackShards(change)
                if (error) return -1
            }
            return 0
        }
        return removeMap[0]!!.amount * 9 - change
    }

    private suspend fun Inventory.withdrawDiamondBlocks(shards: Int): Int {
        val blocksNeeded = ceil(shards / 81.0).toInt()
        val remainder = shards % 81
        val change = if (remainder == 0) 0 else 81 - remainder

        val removeMap = this.removeItem(ItemStack(Material.DIAMOND_BLOCK, blocksNeeded))
        if (removeMap.isEmpty()) {
            if (change != 0) {
                val error = this.addBackDiamonds(change)
                if (error) return -1
            }
            return 0
        }
        return (removeMap[0]!!.amount * 9 - change) * 9
    }

    private suspend fun Inventory.addBackShards(shards: Int): Boolean {
        val player = this.holder as Player

        val addMap = this.addItem(Shard.createItemStack(shards))
        if (addMap.isEmpty()) return false

        val leftOver = addMap[0]!!.amount

        if (this.type != InventoryType.SHULKER_BOX) {
            val error = DiamondBankOG.postgreSQL.addToPlayerShards(player.uniqueId, leftOver, ShardType.BANK)
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: The change of $leftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset>has been deposited into your bank."))
            return error
        }

        val inventoryAddMap = player.inventory.addItem(Shard.createItemStack(leftOver))
        if (inventoryAddMap.isNotEmpty()) {
            val inventoryLeftOver = inventoryAddMap[0]!!.amount
            val error = DiamondBankOG.postgreSQL.addToPlayerShards(player.uniqueId, inventoryLeftOver, ShardType.BANK)
            if (error) return true
            player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: The change of ${leftOver - inventoryLeftOver} <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset> has been added to your inventory, and the remaining $inventoryLeftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset> has been deposited into your bank."))
            return false
        }
        player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: The change of $leftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset> has been added to your inventory."))
        return false
    }

    private suspend fun Inventory.addBackDiamonds(diamonds: Int): Boolean {
        val player = this.holder as Player

        val addMap = this.addItem(ItemStack(Material.DIAMOND, diamonds))
        if (addMap.isEmpty()) return false

        val leftOver = addMap[0]!!.amount

        if (this.type != InventoryType.SHULKER_BOX) {
            val error = this.addBackShards(leftOver * 9)
            return error
        }

        val inventoryAddMap = player.inventory.addItem(ItemStack(Material.DIAMOND, leftOver))
        if (inventoryAddMap.isNotEmpty()) {
            val error = this.addBackShards(inventoryAddMap[0]!!.amount * 9)
            return error
        }
        player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: The change of $leftOver <aqua>${if (leftOver == 1) "Diamond" else "Diamonds"} <reset> has been added to your inventory."))
        return false
    }

    suspend fun Inventory.withdraw(shards: Int): Boolean {
        if (this.holder !is Player) return true
        val player = this.holder as Player
        DiamondBankOG.blockInventoryFor.add(player.uniqueId)

        val notRemoved = this.withdrawShards(shards)
        if (notRemoved != 0) {
            val diamondsNotRemoved = this.withdrawDiamonds(notRemoved)
            if (diamondsNotRemoved != 0) {
                val blocksNotRemoved = this.withdrawDiamondBlocks(diamondsNotRemoved)
                if (blocksNotRemoved != 0) {
                    TODO("Warn using Sentry")
                }
            }
        }

        DiamondBankOG.blockInventoryFor.remove(player.uniqueId)
        return false
    }

    fun Inventory.countTotal(): Int {
        return this.countShards() + this.countDiamonds() * 9 + this.countDiamondBlocks() * 81
    }

    fun Inventory.countShards(): Int {
        val inventoryShards = this.all(Material.PRISMARINE_SHARD).values.filter{ it.itemMeta.persistentDataContainer.has(Shard.namespacedKey) }.sumOf { it.amount }
        val shulkerBoxShards = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.PRISMARINE_SHARD).values.filter{ it.itemMeta.persistentDataContainer.has(Shard.namespacedKey) }.sumOf { it.amount }
        }
        return inventoryShards + shulkerBoxShards
    }

    fun Inventory.countDiamonds(): Int {
        val inventoryDiamonds = this.all(Material.DIAMOND).values.sumOf { it.amount }
        val shulkerBoxDiamonds = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.DIAMOND).values.sumOf { it.amount }
        }
        return inventoryDiamonds + shulkerBoxDiamonds
    }

    fun Inventory.countDiamondBlocks(): Int {
        val inventoryDiamondBlocks = this.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount * 9 } * 9
        val shulkerBoxDiamondBlocks = this.all(Material.SHULKER_BOX).values.sumOf { itemStack ->
            ((itemStack.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount * 9 }
        }
        return inventoryDiamondBlocks + shulkerBoxDiamondBlocks
    }
}