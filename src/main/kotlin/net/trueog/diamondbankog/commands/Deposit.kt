package net.trueog.diamondbankog.commands

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.Helper
import net.trueog.diamondbankog.Helper.PostgresFunction.ADD_TO_PLAYER_BALANCE
import net.trueog.diamondbankog.Helper.PostgresFunction.SUBTRACT_FROM_PLAYER_BALANCE
import net.trueog.diamondbankog.Helper.withdraw
import net.trueog.diamondbankog.PostgreSQL
import net.trueog.diamondbankog.PostgreSQL.BalanceType.BANK_BALANCE
import net.trueog.diamondbankog.PostgreSQL.BalanceType.INVENTORY_BALANCE
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Deposit : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        GlobalScope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.deposit")) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have permission to use this command."))
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You did not provide the amount of <aqua>dDiamonds <red>that you want to deposit."))
                return@launch
            }
            if (args.size != 1) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Please (only) provide the amount of <aqua>dDiamonds <red>you want to deposit. Either a number or \"all\"."))
                return@launch
            }

            val playerBalance =
                DiamondBankOG.postgreSQL.getPlayerBalance(sender.uniqueId, INVENTORY_BALANCE)
            if (playerBalance.inventoryBalance == null) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to get your balance."))
                return@launch
            }
            if (playerBalance.inventoryBalance == 0L) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You don't have any <aqua>Diamonds <red>to deposit."))
                return@launch
            }

            var amount = playerBalance.inventoryBalance

            if (args[0] != "all") {
                try {
                    amount = args[0].toLong()
                    if (amount < 0) {
                        sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You cannot deposit a negative amount."))
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Invalid argument."))
                    return@launch
                }

                if (amount > playerBalance.inventoryBalance) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>You do not have <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <red>in your inventory."))
                    return@launch
                }
            }

            DiamondBankOG.blockInventoryFor.add(sender.uniqueId)

            var error = sender.inventory.withdraw(amount, playerBalance)
            if (error) return@launch

            error = DiamondBankOG.postgreSQL.subtractFromPlayerBalance(
                sender.uniqueId,
                amount,
                INVENTORY_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    SUBTRACT_FROM_PLAYER_BALANCE,
                    amount,
                    INVENTORY_BALANCE,
                    playerBalance,
                    "deposit"
                )
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to deposit."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            error = DiamondBankOG.postgreSQL.addToPlayerBalance(
                sender.uniqueId,
                amount,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    ADD_TO_PLAYER_BALANCE,
                    amount,
                    BANK_BALANCE,
                    playerBalance,
                    "deposit"
                )
                sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <red>Something went wrong while trying to deposit."))
                DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
                return@launch
            }

            DiamondBankOG.blockInventoryFor.remove(sender.uniqueId)
            sender.sendMessage(DiamondBankOG.mm.deserialize("<dark_gray>[<aqua>DiamondBank<white>-<dark_red>OG<dark_gray>]<reset>: <green>Successfully deposited <yellow>$amount <aqua>${if (amount == 1L) "Diamond" else "Diamonds"} <green>into your bank account."))
        }
        return true
    }
}