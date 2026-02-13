package net.trueog.diamondbankog.commands

import kotlin.math.floor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.CommonOperations
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.lock
import net.trueog.diamondbankog.InventoryExtensions.unlock
import net.trueog.diamondbankog.InventorySnapshot
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.Shard
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack

internal class Withdraw : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (CommonCommandInterlude.run(sender, "withdraw")) {
            return true
        }

        if (args.isNullOrEmpty()) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to withdraw."
                )
            )
            return true
        }
        if (args.size != 1 && args.size != 2) {
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>Please provide the amount of <aqua>Diamonds <red>you want to withdraw. Either a number or \"all\"."
                )
            )
            return true
        }

        var shards = -1L
        if (args[0] != "all") {
            val amount: Float
            try {
                amount = args[0].toFloat()
                if (amount <= 0) {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You cannot withdraw a negative or zero amount.")
                    )
                    return true
                }
            } catch (_: Exception) {
                sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                return true
            }
            shards =
                CommonOperations.diamondsToShards(amount).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                        )
                    )
                    return true
                }
        }

        sender.inventory.lock()
        val inventorySnapshot = InventorySnapshot.from(sender.inventory)

        scope.launch {
            when (
                transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    val bankShards =
                        balanceManager.getBankShards(sender.uniqueId).getOrElse {
                            sender.inventory.unlock()
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>Something went wrong while trying to get your balance."
                                )
                            )
                            return@tryWithLockSuspend
                        }

                    val shardsToWithdraw =
                        if (shards == -1L) {
                            bankShards
                        } else {
                            shards
                        }

                    val diamonds = CommonOperations.shardsToDiamonds(shardsToWithdraw)
                    val bankDiamonds = CommonOperations.shardsToDiamonds(bankShards)

                    if (shards != -1L && shards > bankShards) {
                        sender.inventory.unlock()
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>Cannot withdraw <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <red>because your bank only contains <yellow>$bankDiamonds <aqua>Diamond${if (bankDiamonds != "1.0") "s" else ""}<red>."
                            )
                        )
                        return@tryWithLockSuspend
                    }

                    val diamondsToAdd = floor(shardsToWithdraw / 9.0).toInt()
                    val shardsChange = shardsToWithdraw.toInt() % 9

                    if (
                        inventorySnapshot.addItem(ItemStack(Material.DIAMOND, diamondsToAdd)).isNotEmpty() ||
                            inventorySnapshot.addItem(Shard.createItemStack(shardsChange)).isNotEmpty()
                    ) {
                        sender.inventory.unlock()
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You don't have enough inventory space to withdraw <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""}<red>."
                            )
                        )
                        return@tryWithLockSuspend
                    }

                    balanceManager.subtractFromBankShards(sender.uniqueId, shardsToWithdraw).getOrElse {
                        handleError(it)
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                            )
                        )
                        sender.inventory.unlock()
                        return@tryWithLockSuspend
                    }

                    runOnMainThread {
                        inventorySnapshot.restoreTo(sender.inventory)
                        sender.inventory.unlock()
                    }

                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <green>Successfully withdrew <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <green>from your bank account."
                        )
                    )

                    balanceManager.insertTransactionLog(sender.uniqueId, shards, null, "Withdraw", null).getOrElse {
                        handleError(it)
                    }
                }
            ) {
                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /withdraw.")
                    )
                }

                else -> {}
            }
        }
        return true
    }
}
