package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.floor

class Deposit : CommandExecutor {
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
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You are currently blocked from using /deposit."))
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot use /deposit <aqua>Diamonds <red>when in a minigame."))
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.deposit")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You did not provide the amount of <aqua>Diamonds <red>that you want to deposit."))
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Please (only) provide the amount of <aqua>Diamonds <red>you want to deposit. Either a number or \"all\"."))
                return@launch
            }

            val playerInventoryShards =
                DiamondBankOG.postgreSQL.getPlayerShards(sender.uniqueId, ShardType.INVENTORY).shardsInInventory
            if (playerInventoryShards == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }
            if (playerInventoryShards == 0) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit."))
                return@launch
            }

            var shards = playerInventoryShards
            if (args[0] != "all") {
                val amount: Float
                try {
                    amount = args[0].toFloat()
                    if (amount <= 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot deposit a negative or zero amount."))
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

                if (shards > playerInventoryShards) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have <yellow>${args[0]} <aqua>${if (args[0] == "1.0") "Diamond" else "Diamonds"} <red>in your inventory."))
                    return@launch
                }
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)

            var error = sender.inventory.withdraw(shards)
            if (error) {
                DiamondBankOG.economyDisabled = true
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            error = DiamondBankOG.postgreSQL.addToPlayerShards(
                sender.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_SHARDS,
                    shards,
                    ShardType.BANK,
                    PostgreSQL.PlayerShards(null, playerInventoryShards, null),
                    "deposit"
                )
                DiamondBankOG.economyDisabled = true
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
            val diamondsDeposited = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully deposited <yellow>$diamondsDeposited <aqua>${if (diamondsDeposited == "1.0") "Diamond" else "Diamonds"} <green>into your bank account."))
        }
        return true
    }
}