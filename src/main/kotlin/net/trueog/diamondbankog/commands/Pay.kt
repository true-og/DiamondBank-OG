package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.PostgreSQL
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Pay : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.pay")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
                return@launch
            }
            if (args.size != 2) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
                return@launch
            }

            val receiver = Bukkit.getOfflinePlayer(args[0])
            if (!receiver.hasPlayedBefore()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>That player doesn't exist."))
                return@launch
            }

            var amount = -1L
            if (args[0] != "all") {
                try {
                    amount = args[1].toLong()
                    if (amount <= 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot pay a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                    return@launch
                }
            }

            val withdrawnAmount = Helper.withdrawFromPlayer(sender, amount) ?: return@launch

            val error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                receiver.uniqueId,
                amount,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                // TODO: Houston, we have an issue
            }

            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully paid <yellow>$withdrawnAmount <aqua>${if (withdrawnAmount == 1L) "Diamond" else "Diamonds"} <green>to <red>${receiver.name}<green>."))

            if (receiver.isOnline) {
                val receiverPlayer = receiver.player ?: return@launch
                receiverPlayer.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>${sender.name} has paid you <yellow>$withdrawnAmount <aqua>${if (withdrawnAmount == 1L) "Diamond" else "Diamonds"}<green>."))
            }
        }
        return true
    }
}