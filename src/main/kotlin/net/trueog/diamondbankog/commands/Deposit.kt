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
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class Deposit : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        scope.launch {
            if (economyDisabled) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member."
                    )
                )
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You cannot use /deposit when in a minigame.")
                )
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.deposit")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to deposit."
                    )
                )
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to deposit. Either a number or \"all\"."
                    )
                )
                return@launch
            }

            val playerInventoryShards =
                balanceManager.getInventoryShards(sender.uniqueId).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get your balance."
                        )
                    )
                    return@launch
                }
            if (playerInventoryShards == 0L) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit.")
                )
                return@launch
            }

            var shards: Long = playerInventoryShards
            if (args[0] != "all") {
                val amount: Float
                try {
                    amount = args[0].toFloat()
                    if (amount <= 0) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You cannot deposit a negative or zero amount."
                            )
                        )
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
                val split = amount.toString().split(".")
                if (split[1].length > 1) {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                        )
                    )
                    return@launch
                }
                shards = (split[0].toLong() * 9) + split[1].toLong()

                if (shards > playerInventoryShards) {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>You do not have <yellow>${args[0]} <aqua>Diamond${if (args[0] != "1.0") "s" else ""} <red>in your inventory."
                        )
                    )
                    return@launch
                }
            }

            val originalShards = shards

            when (
                val result =
                    transactionLock.tryWithLockSuspend(sender.uniqueId) {
                        val notRemoved = sender.inventory.withdraw(shards.toInt())
                        if (notRemoved != 0) {
                            if (notRemoved <= -1) {
                                handleError(sender.uniqueId, shards, PlayerShards(-1, playerInventoryShards, -1))
                                sender.sendMessage(
                                    mm.deserialize(
                                        "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                                    )
                                )
                                return@tryWithLockSuspend true
                            }
                            val notRemovedDiamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                            shards -= notRemoved
                            val diamondsContinuing = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <#FFA500>Something went wrong while trying to remove <yellow>$notRemovedDiamonds <aqua>Diamond${if (notRemovedDiamonds != "1.0") "s" else ""}<#FFA500> from your inventory, proceeding with <yellow>$diamondsContinuing <aqua>Diamond${if (diamondsContinuing != "1.0") "s" else ""}<#FFA500>."
                                )
                            )
                        }

                        balanceManager.addToPlayerShards(sender.uniqueId, shards, ShardType.BANK).getOrElse {
                            handleError(sender.uniqueId, shards, PlayerShards(-1, playerInventoryShards, -1))
                            sender.sendMessage(
                                mm.deserialize(
                                    "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                                )
                            )
                            return@tryWithLockSuspend true
                        }
                        false
                    }
            ) {
                is TransactionLock.LockResult.Acquired -> {
                    if (result.result) {
                        return@launch
                    }
                }

                is TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        mm.deserialize("${config.prefix}<reset>: <red>You are currently blocked from using /deposit.")
                    )
                    return@launch
                }
            }

            val diamondsDeposited = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <green>Successfully deposited <yellow>$diamondsDeposited <aqua>Diamond${if (diamondsDeposited != "1.0") "s" else ""} <green>into your bank account."
                )
            )

            balanceManager
                .insertTransactionLog(
                    sender.uniqueId,
                    shards,
                    null,
                    "Deposit",
                    if (shards != originalShards) "Could not withdraw $originalShards shards, continued with $shards"
                    else null,
                )
                .getOrElse { handleError(sender.uniqueId, shards, null, null, true) }
        }
        return true
    }
}
