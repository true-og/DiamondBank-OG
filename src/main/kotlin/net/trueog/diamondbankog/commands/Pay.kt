package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.floor

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
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot use /pay when in a minigame."))
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

            if (sender.uniqueId == receiver.uniqueId) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot pay yourself."))
                return@launch
            }

            if (!receiver.hasPlayedBefore()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist."))
                return@launch
            }

            var shards = -1
            if (args[1] != "all") {
                val amount: Float
                try {
                    amount = args[1].toFloat()
                    if (amount <= 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot pay a negative or zero amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
                val split = amount.toString().split(".")
                if (split[1].length > 1) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."))
                    return@launch
                }
                shards = (split[0].toInt() * 9) + split[1].toInt()
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)

            val withdrawnAmount = Helper.withdrawFromPlayer(sender, shards) ?: return@launch

            if (withdrawnAmount != shards) {
                DiamondBankOG.economyDisabled = true
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            val error = DiamondBankOG.postgreSQL.addToPlayerShards(
                receiver.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_SHARDS, shards, ShardType.BANK,
                    null, "pay"
                )
                DiamondBankOG.economyDisabled = true
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)

            val diamondsPaid = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)

            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully paid <yellow>$diamondsPaid <aqua>${if (diamondsPaid == "1.0") "Diamond" else "Diamonds"} <green>to <red>${receiver.name}<green>."))

            if (receiver.isOnline) {
                val receiverPlayer = receiver.player ?: return@launch
                receiverPlayer.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>${sender.name} has paid you <yellow>$diamondsPaid <aqua>${if (diamondsPaid == "1.0") "Diamond" else "Diamonds"}<green>."))
            }
        }
        return true
    }
}