package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.Helper.withdraw
import net.trueog.diamondbankog.PostgreSQL.DiamondType
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

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

            val playerDiamonds =
                DiamondBankOG.postgreSQL.getPlayerDiamonds(sender.uniqueId, DiamondType.INVENTORY)
            if (playerDiamonds.inventoryDiamonds == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }
            if (playerDiamonds.inventoryDiamonds == 0) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit."))
                return@launch
            }

            var amount = playerDiamonds.inventoryDiamonds
            if (args[0] != "all") {
                try {
                    amount = args[0].toInt()
                    if (amount < 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot deposit a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }

                if (amount > playerDiamonds.inventoryDiamonds) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <red>in your inventory."))
                    return@launch
                }
            }

            var error = sender.inventory.withdraw(amount, playerDiamonds)
            if (error) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to deposit."))
                return@launch
            }

            error = DiamondBankOG.postgreSQL.addToPlayerDiamonds(
                sender.uniqueId,
                amount,
                DiamondType.BANK
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_DIAMONDS,
                    amount,
                    DiamondType.BANK,
                    playerDiamonds,
                    "deposit"
                )
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Something went wrong while trying to deposit."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully deposited <yellow>$amount <aqua>${if (amount == 1) "Diamond" else "Diamonds"} <green>into your bank account."))
        }
        return true
    }
}