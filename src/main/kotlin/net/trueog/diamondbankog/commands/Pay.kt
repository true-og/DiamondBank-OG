package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class Pay : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (DiamondBankOG.blockCommandsWithInventoryActionsFor.contains(sender.uniqueId)) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You are currently blocked from using /pay."))
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot use /pay <aqua>Diamonds <red>when in a minigame."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.pay")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
                return@launch
            }
            if (args.size != 2) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."))
                return@launch
            }

            val receiver = try {
                Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
            } catch (_: Exception) {
                Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
            }

            if (!receiver.hasPlayedBefore()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist."))
                return@launch
            }

            var amount = -1
            if (args[1] != "all") {
                try {
                    amount = args[1].toInt()
                    if (amount < 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot pay a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
            }

            val withdrawnAmount = Helper.withdrawFromPlayer(sender, amount) ?: return@launch

            val error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                receiver.uniqueId,
                amount,
                BalanceType.BANK_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_BALANCE, amount, BalanceType.BANK_BALANCE,
                    null, "pay"
                )
            }

            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully paid <yellow>$withdrawnAmount <aqua>${if (withdrawnAmount == 1) "Diamond" else "Diamonds"} <green>to <red>${receiver.name}<green>."))

            if (receiver.isOnline) {
                val receiverPlayer = receiver.player ?: return@launch
                receiverPlayer.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>${sender.name} has paid you <yellow>$withdrawnAmount <aqua>${if (withdrawnAmount == 1) "Diamond" else "Diamonds"}<green>."))
            }
        }
        return true
    }
}