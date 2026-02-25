package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.CommonOperations
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal class Balance : CommandExecutor {
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

            if (!sender.hasPermission("diamondbank-og.balance")) {
                sender.sendMessage(
                    mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
                )
                return@launch
            }

            if (args == null) return@launch

            if (args.isEmpty() && sender !is Player) {
                sender.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <red>Please provide that name or UUID of the player that you want to check the balance of."
                    )
                )
                return@launch
            }

            val balancePlayer =
                if (args.isEmpty()) {
                    sender as Player
                } else {
                    if (!sender.hasPermission("diamondbank-og.balance.others")) {
                        sender.sendMessage(
                            mm.deserialize(
                                "${config.prefix}<reset>: <red>You do not have permission to use this command in that way."
                            )
                        )
                        return@launch
                    }

                    val otherPlayer = CommonOperations.getPlayerUsingUuidOrName(args[0])
                    if (!otherPlayer.hasPlayedBefore()) {
                        sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>That player doesn't exist."))
                        return@launch
                    }
                    otherPlayer
                }
            val balance =
                balanceManager.getAllShards(balancePlayer.uniqueId).getOrElse {
                    sender.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>Something went wrong while trying to get ${
                                if (if (sender is Player) sender.uniqueId == balancePlayer.uniqueId else false) "your" else "their"
                            } balance."
                        )
                    )
                    return@launch
                }

            val totalBalance = balance.bank + balance.inventory + balance.enderChest
            sender.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <green>${
                        if (if (sender is Player) sender.uniqueId == balancePlayer.uniqueId else false) "Your Balance" else "Balance of ${
                            getPrefix(
                                balancePlayer.uniqueId
                            )
                        }${balancePlayer.name}"
                    }<reset><green>:\n" +
                        "Bank: ${CommonOperations.shardsToDiamondsFull(balance.bank)}\n" +
                        "<green>Inventory: ${CommonOperations.shardsToDiamondsFull(balance.inventory)}\n" +
                        "<green>Ender Chest: ${CommonOperations.shardsToDiamondsFull(balance.enderChest)}\n" +
                        "<bold><green>Total: ${CommonOperations.shardsToDiamondsFull(totalBalance)}"
                )
            )
            return@launch
        }
        return true
    }
}
