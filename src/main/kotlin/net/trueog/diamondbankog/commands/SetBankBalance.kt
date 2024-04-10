package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.util.*

class SetBankBalance : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.setbankbalance")) {
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

            val player = try {
                Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
            } catch (_: Exception) {
                Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
            }
            if (!player.hasPlayedBefore()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist."))
                return@launch
            }

            val balance: Long
            try {
                balance = args[1].toLong()
            } catch (_: Exception) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                return@launch
            }

            val error =
                DiamondBankOG.postgreSQL.setPlayerBalance(player.uniqueId, balance, BalanceType.BANK_BALANCE)
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to set that player's balance."))
                return@launch
            }
            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully set the balance of <red>${player.name} <green>to <yellow>$balance <aqua>${if (balance == 1L) "Diamond" else "Diamonds"}<green>."))
        }
        return true
    }
}