package net.trueog.diamondbankog.transaction.command

import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.*
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.shard.Shard
import net.trueog.diamondbankog.config.Config
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.withLockSuspend
import net.trueog.diamondbankog.transaction.InventorySnapshot
import net.trueog.diamondbankog.transaction.TransactionLock
import net.trueog.diamondbankog.util.CommonCommandInterlude
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.util.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.util.InventoryExtensions.countShards
import net.trueog.diamondbankog.util.MainThreadBlock.runOnMainThread
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

internal class Compress(
    val config: Config = DiamondBankOG.config,
    val balanceManager: BalanceManager = DiamondBankOG.balanceManager,
    val mm: MiniMessage = DiamondBankOG.mm,
    val scope: CoroutineScope = DiamondBankOG.scope,
    val transactionLock: TransactionLock = DiamondBankOG.transactionLock,
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "compress", config, mm)) {
            return true
        }

        scope.launch {
            var isShulkerBox = false
            var finalShards: Int
            var finalDiamonds: Int
            var finalDiamondBlocks: Int
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val summaryString =
                        sender.inventory
                            .withLockSuspend {
                                val inventorySnapshot = runOnMainThread {
                                    InventorySnapshot.from(sender.inventory, balanceManager)
                                }

                                val (inventory, blockStateMeta, blockState) =
                                    if (!args.isNullOrEmpty()) {
                                        if (args.size > 1) {
                                            sender.sendMessage(
                                                mm.deserialize(
                                                    "${config.prefix}<reset>: <red>Do not provide more arguments than \"yes\" if you want to compress the items in the shulker box you're holding."
                                                )
                                            )
                                            return@withLockSuspend Result.failure(Exception())
                                        }
                                        if (args[0] != "yes") {
                                            sender.sendMessage(
                                                mm.deserialize("${config.prefix}<reset>: <red>Invalid argument.")
                                            )
                                            return@withLockSuspend Result.failure(Exception())
                                        }

                                        val itemInMainHand = inventorySnapshot.itemInMainHand
                                        if (itemInMainHand.type != Material.SHULKER_BOX) {
                                            sender.sendMessage(
                                                mm.deserialize(
                                                    "${config.prefix}<reset>: <red>You are not holding a shulker box."
                                                )
                                            )
                                            return@withLockSuspend Result.failure(Exception())
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
                                    return@withLockSuspend Result.failure(Exception())
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
                                        return@withLockSuspend Result.failure(Exception())
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
                                        return@withLockSuspend Result.failure(Exception())
                                    }
                                }

                                val summaryStringBuilder = StringBuilder("Compression Summary:")

                                if (changeInShards < 0) {
                                    val removeMap = inventory.removeItem(Shard.createItemStack(abs(changeInShards)))
                                    if (removeMap.isNotEmpty()) {
                                        sender.sendMessage(
                                            mm.deserialize(
                                                "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                            )
                                        )
                                        return@withLockSuspend Result.failure(Exception())
                                    }
                                    summaryStringBuilder.append(
                                        "\n<red>$changeInShards Diamond Shard${if (changeInShards != -1) "s" else ""}"
                                    )
                                }

                                if (changeInDiamonds > 0) {
                                    val addMap = inventory.addItem(ItemStack(Material.DIAMOND, changeInDiamonds))
                                    if (addMap.isNotEmpty()) {
                                        sender.sendMessage(
                                            mm.deserialize(
                                                "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                            )
                                        )
                                        return@withLockSuspend Result.failure(Exception())
                                    }
                                    summaryStringBuilder.append(
                                        "\n<green>+$changeInDiamonds Diamond${if (changeInDiamonds != 1) "s" else ""}"
                                    )
                                } else if (changeInDiamonds < 0) {
                                    val removeMap =
                                        inventory.removeItem(ItemStack(Material.DIAMOND, abs(changeInDiamonds)))
                                    if (removeMap.isNotEmpty()) {
                                        sender.sendMessage(
                                            mm.deserialize(
                                                "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                            )
                                        )
                                        return@withLockSuspend Result.failure(Exception())
                                    }
                                    summaryStringBuilder.append(
                                        "\n<red>$changeInDiamonds Diamond${if (changeInDiamonds != -1) "s" else ""}"
                                    )
                                }

                                if (changeInDiamondBlocks > 0) {
                                    val addMap =
                                        inventory.addItem(ItemStack(Material.DIAMOND_BLOCK, changeInDiamondBlocks))
                                    if (addMap.isNotEmpty()) {
                                        sender.sendMessage(
                                            mm.deserialize(
                                                "${config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                            )
                                        )
                                        return@withLockSuspend Result.failure(Exception())
                                    }
                                    summaryStringBuilder.append(
                                        "\n<green>+$changeInDiamondBlocks Diamond Block${if (changeInDiamondBlocks != 1) "s" else ""}"
                                    )
                                }

                                if (blockStateMeta != null && blockState != null) {
                                    blockStateMeta.blockState = blockState
                                    inventorySnapshot.itemInMainHand.itemMeta = blockStateMeta
                                }

                                if (changeInShards == 0 && changeInDiamonds == 0 && changeInDiamondBlocks == 0) {
                                    sender.sendMessage(
                                        mm.deserialize("${config.prefix}<reset>: <#FFA500>Nothing found to compress.")
                                    )
                                    return@withLockSuspend Result.failure(Exception())
                                }

                                runOnMainThread { inventorySnapshot.restoreTo(sender.inventory) }
                                Result.success(summaryStringBuilder.toString())
                            }
                            .getOrElse {
                                return@tryWithLockSuspend
                            }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully compressed all the Diamond currency items in your ${if (isShulkerBox) "shulker box" else "inventory"}!"
                        )
                    )

                    sender.sendMessage(mm.deserialize("${config.prefix}<reset>: $summaryString"))
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
