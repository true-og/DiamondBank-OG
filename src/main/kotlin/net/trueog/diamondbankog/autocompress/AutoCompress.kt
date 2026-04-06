package net.trueog.diamondbankog.autocompress

import kotlin.math.abs
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.balance.shard.Shard
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.lock
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.unlock
import net.trueog.diamondbankog.transaction.InventorySnapshot
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.util.InventoryExtensions.countShards
import net.trueog.diamondbankog.util.MainThreadBlock
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal object AutoCompress {
    fun compress(player: Player) {
        DiamondBankOG.scope.launch {
            val worldName = player.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                return@launch
            }

            if (!player.hasPermission("diamondbank-og.compress")) {
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${DiamondBankOG.config.prefix}<reset>: <red>You do not have permission to compress."
                    )
                )
                return@launch
            }

            DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                val inventorySnapshot =
                    MainThreadBlock.runOnMainThread {
                        player.inventory.lock()
                        InventorySnapshot.from(player.inventory, DiamondBankOG.balanceManager)
                    }

                val shardsInInventory = inventorySnapshot.countShards().toInt()
                val diamondsInInventory = inventorySnapshot.countDiamonds().toInt()
                val diamondBlocksInInventory = inventorySnapshot.countDiamondBlocks().toInt()

                var finalShards = shardsInInventory
                var finalDiamonds = diamondsInInventory
                var finalDiamondBlocks = diamondBlocksInInventory

                finalDiamonds += finalShards / 9
                if (finalDiamonds - diamondsInInventory != 0) {
                    val remainder = finalShards % 9
                    finalShards = remainder
                }

                finalDiamondBlocks += finalDiamonds / 9
                if (finalDiamondBlocks - diamondBlocksInInventory != 0) {
                    val remainder = finalDiamonds % 9
                    finalDiamonds = remainder
                }

                val changeInShards = finalShards - shardsInInventory
                val changeInDiamonds = finalDiamonds - diamondsInInventory
                val changeInDiamondBlocks = finalDiamondBlocks - diamondBlocksInInventory

                if (changeInDiamonds > 0) {
                    val emptySlots = inventorySnapshot.storageContents.filter { it == null }.size * 64
                    val leftOverSpaceDiamonds =
                        inventorySnapshot.storageContents
                            .filterNotNull()
                            .filter { it.type == Material.DIAMOND }
                            .sumOf { 64 - it.amount }

                    if (changeInDiamonds > emptySlots + leftOverSpaceDiamonds) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>You do not have enough space in your inventory to compress all the Diamond currency items (<green>+$changeInDiamonds <aqua>Diamonds<red>)."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                }

                if (changeInDiamondBlocks > 0) {
                    val emptySlots = inventorySnapshot.storageContents.filter { it == null }.size * 64
                    val leftOverSpaceDiamondBlocks =
                        inventorySnapshot.storageContents
                            .filterNotNull()
                            .filter { it.type == Material.DIAMOND_BLOCK }
                            .sumOf { 64 - it.amount }

                    if (changeInDiamondBlocks > emptySlots + leftOverSpaceDiamondBlocks) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>You do not have enough space in your inventory to compress all the Diamond currency items (<green>+$changeInDiamondBlocks <aqua>Diamond Blocks<red>)."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                }

                if (changeInShards < 0) {
                    val removeMap = inventorySnapshot.removeItem(Shard.createItemStack(abs(changeInShards)))
                    if (removeMap.isNotEmpty()) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your inventory."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                }

                if (changeInDiamonds > 0) {
                    val addMap = inventorySnapshot.addItem(ItemStack(Material.DIAMOND, changeInDiamonds))
                    if (addMap.isNotEmpty()) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your inventory."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                } else if (changeInDiamonds < 0) {
                    val removeMap = inventorySnapshot.removeItem(ItemStack(Material.DIAMOND, abs(changeInDiamonds)))
                    if (removeMap.isNotEmpty()) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your inventory."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                }

                if (changeInDiamondBlocks > 0) {
                    val addMap = inventorySnapshot.addItem(ItemStack(Material.DIAMOND_BLOCK, changeInDiamondBlocks))
                    if (addMap.isNotEmpty()) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your inventory."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                } else if (changeInDiamondBlocks < 0) {
                    val removeMap =
                        inventorySnapshot.removeItem(ItemStack(Material.DIAMOND_BLOCK, abs(changeInDiamondBlocks)))
                    if (removeMap.isNotEmpty()) {
                        player.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${DiamondBankOG.config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your inventory."
                            )
                        )
                        player.inventory.unlock()
                        return@withLockSuspend
                    }
                }

                MainThreadBlock.runOnMainThread {
                    inventorySnapshot.restoreTo(player.inventory)
                    player.inventory.unlock()
                }
            }
        }
    }
}
