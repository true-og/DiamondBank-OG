package net.trueog.diamondbankog.commands

import kotlin.math.abs
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.InventorySnapshot
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.Shard
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

internal class Compress : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "compress")) {
            return true
        }

        scope.launch {
            var isShulkerBox = false
            var finalShards: Int
            var finalDiamonds: Int
            var finalDiamondBlocks: Int
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val inventorySnapshot = runOnMainThread {
                        sender.inventory.lock()
                        InventorySnapshot.from(sender.inventory)
                    }

                    val (inventory, blockStateMeta, blockState) =
                        if (!args.isNullOrEmpty()) {
                            if (args.size > 1) {
                                sender.sendMessage(
                                    mm.deserialize(
                                        "${config.prefix}<reset>: <red>Do not provide more arguments than \"yes\" if you want to compress the items in the shulker box you're holding."
                                    )
                                )
                                sender.inventory.unlock()
                                return@tryWithLockSuspend
                            }
                            if (args[0] != "yes") {
                                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                                sender.inventory.unlock()
                                return@tryWithLockSuspend
                            }

                            val itemInMainHand = inventorySnapshot.itemInMainHand
                            if (itemInMainHand.type != Material.SHULKER_BOX) {
                                sender.sendMessage(
                                    mm.deserialize("${config.prefix}<reset>: <red>You are not holding a shulker box.")
                                )
                                sender.inventory.unlock()
                                return@tryWithLockSuspend
                            }

                            val blockStateMeta = itemInMainHand.itemMeta as BlockStateMeta
                            val blockState = blockStateMeta.blockState as ShulkerBox
                            isShulkerBox = true
                            Triple(blockState.inventory, blockStateMeta, blockState)
                        } else {
                            Triple(inventorySnapshot, null, null)
                        }

                    if (inventorySnapshot.itemInMainHand.type == Material.SHULKER_BOX && !isShulkerBox) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <#FFA500>Are you sure you want to compress the Diamond currency items in the shulker box you're holding? If so, run \"/compress yes\""
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    val shardsInInventory = inventory.countShards().toInt()
                    val diamondsInInventory = inventory.countDiamonds().toInt()
                    val diamondBlocksInInventory = inventory.countDiamondBlocks().toInt()

                    finalShards = shardsInInventory
                    finalDiamonds = diamondsInInventory
                    finalDiamondBlocks = diamondBlocksInInventory

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
                        val emptySlots = inventory.storageContents.filter { it == null }.size * 64
                        val leftOverSpaceDiamonds =
                            inventory.storageContents
                                .filterNotNull()
                                .filter { it.type == Material.DIAMOND }
                                .sumOf { 64 - it.amount }

                        if (changeInDiamonds > emptySlots + leftOverSpaceDiamonds) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>You do not have enough space in your ${if (isShulkerBox) "shulker box" else "inventory"} to compress all the Diamond currency items (<green>+$changeInDiamonds <aqua>Diamonds<red>)."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    }

                    if (changeInDiamondBlocks > 0) {
                        val emptySlots = inventory.storageContents.filter { it == null }.size * 64
                        val leftOverSpaceDiamondBlocks =
                            inventory.storageContents
                                .filterNotNull()
                                .filter { it.type == Material.DIAMOND_BLOCK }
                                .sumOf { 64 - it.amount }

                        if (changeInDiamondBlocks > emptySlots + leftOverSpaceDiamondBlocks) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>You do not have enough space in your ${if (isShulkerBox) "shulker box" else "inventory"} to compress all the Diamond currency items (<green>+$changeInDiamondBlocks <aqua>Diamond Blocks<red>)."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    }

                    if (changeInShards < 0) {
                        val removeMap = inventory.removeItem(Shard.createItemStack(abs(changeInShards)))
                        if (removeMap.isNotEmpty()) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    }

                    if (changeInDiamonds > 0) {
                        val addMap = inventory.addItem(ItemStack(Material.DIAMOND, changeInDiamonds))
                        if (addMap.isNotEmpty()) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    } else if (changeInDiamonds < 0) {
                        val removeMap = inventory.removeItem(ItemStack(Material.DIAMOND, abs(changeInDiamonds)))
                        if (removeMap.isNotEmpty()) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    }

                    if (changeInDiamondBlocks > 0) {
                        val addMap = inventory.addItem(ItemStack(Material.DIAMOND_BLOCK, changeInDiamondBlocks))
                        if (addMap.isNotEmpty()) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    } else if (changeInDiamondBlocks < 0) {
                        val removeMap =
                            inventory.removeItem(ItemStack(Material.DIAMOND_BLOCK, abs(changeInDiamondBlocks)))
                        if (removeMap.isNotEmpty()) {
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                )
                            )
                            sender.inventory.unlock()
                            return@tryWithLockSuspend
                        }
                    }

                    if (blockStateMeta != null && blockState != null) {
                        blockStateMeta.blockState = blockState
                        inventorySnapshot.itemInMainHand.itemMeta = blockStateMeta
                    }

                    if (changeInShards == 0 && changeInDiamonds == 0 && changeInDiamondBlocks == 0) {
                        sender.inventory.unlock()
                        sender.sendMessage(
                            mm.deserialize("${config.prefix}<reset>: <#FFA500>Nothing found to compress.")
                        )
                        return@tryWithLockSuspend
                    }

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully compressed all the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}!"
                        )
                    )

                    val shardsLine =
                        if (changeInShards < 0)
                            "|<red>$changeInShards Diamond Shard${if (abs(changeInShards) != 1) "s" else ""}\n"
                        else ""
                    val diamondsLine =
                        (if (changeInDiamonds > 0) "|<green>+$changeInDiamonds"
                        else if (changeInDiamonds < 0) "|<red>$changeInDiamonds" else "") +
                            if (changeInDiamonds != 0)
                                " Diamond${
                                        if (abs(
                                                changeInDiamonds
                                            ) != 1
                                        ) "s" else ""
                                    }\n"
                            else ""
                    val diamondBlocksLine =
                        (if (changeInDiamondBlocks > 0) "|<green>+$changeInDiamondBlocks"
                        else if (changeInDiamondBlocks < 0) "|<red>$changeInDiamondBlocks" else "") +
                            if (changeInDiamondBlocks != 0)
                                " Diamond Block${
                                        if (abs(
                                                changeInDiamondBlocks
                                            ) != 1
                                        ) "s" else ""
                                    }"
                            else ""

                    sender.sendMessage(
                        mm.deserialize(
                            buildString {
                                    append("${config.prefix}<reset>: Compression Summary:\n")
                                    if (shardsLine.isNotEmpty()) append(shardsLine)
                                    if (diamondsLine.isNotEmpty()) append(diamondsLine)
                                    if (diamondBlocksLine.isNotEmpty()) append(diamondBlocksLine)
                                }
                                .trimMargin("|")
                        )
                    )
                }
            ) {
                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /compress.")
                    )
                    return@launch
                }

                else -> {}
            }
        }
        return true
    }
}
