package net.trueog.diamondbankog.commands

import kotlin.math.floor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.InventorySnapshot
import net.trueog.diamondbankog.InventorySnapshotUtils
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class Deposit : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (economyDisabled) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member.")
            )
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        val worldName = sender.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You cannot use /deposit when in a minigame.")
            )
            return true
        }

        if (!sender.hasPermission("diamondbank-og.deposit")) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
            )
            return true
        }

        if (args.isNullOrEmpty()) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to deposit."
                )
            )
            return true
        }
        if (args.size != 1) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to deposit. Either a number or \"all\"."
                )
            )
            return true
        }

        var shards: Long = -1L
        if (args[0] != "all") {
            val amount: Float
            try {
                amount = args[0].toFloat()
                if (amount <= 0) {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You cannot deposit a negative or zero amount.")
                    )
                    return true
                }
            } catch (_: Exception) {
                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                return true
            }
            val split = amount.toString().split(".")
            if (split[1].length > 1) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                    )
                )
                return true
            }
            shards = (split[0].toLong() * 9) + split[1].toLong()
        }

        scope.launch {
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val inventorySnapshot = runOnMainThread {
                        sender.inventory.lock()
                        InventorySnapshot.from(sender.inventory)
                    }

                    val removedInShards: Long =
                        if (shards == -1L) {
                            InventorySnapshotUtils.removeAll(inventorySnapshot).toLong()
                        } else {
                            InventorySnapshotUtils.removeShards(inventorySnapshot, shards).toLong()
                        }

                    if (removedInShards == -1L) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You do not have enough inventory space for the change."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }
                    if (shards != -1L && removedInShards != shards) {
                        val shardsInDiamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You do not have <yellow>$shardsInDiamonds <aqua>Diamond${if (shardsInDiamonds != "1.0") "s" else ""} <red>to deposit."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    balanceManager.addToPlayerShards(sender.uniqueId, removedInShards, ShardType.BANK).getOrElse {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Something went wrong while trying to add to your balance."
                            )
                        )
                        return@tryWithLockSuspend
                    }

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    val diamondsDeposited = String.format("%.1f", floor((removedInShards / 9.0) * 10) / 10.0)
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully deposited <yellow>$diamondsDeposited <aqua>Diamond${if (diamondsDeposited != "1.0") "s" else ""} <green>into your bank account."
                        )
                    )
                }
            ) {
                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /deposit.")
                    )
                    return@launch
                }

                else -> {}
            }
        }
        return true
    }
}
