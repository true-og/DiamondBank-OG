package net.trueog.diamondbankog.util

import net.kyori.adventure.text.minimessage.MiniMessage
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.config.Config
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

object CommonCommandInterlude {
    /** @return should return */
    @OptIn(ExperimentalContracts::class)
    fun run(sender: CommandSender, command: String, config: Config, mm: MiniMessage): Boolean {
        contract { returns(false) implies (sender is Player) }
        if (DiamondBankOG.economyDisabled) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member.")
            )
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("You can only execute this command as a player.")
            return true
        }

        val worldName = sender.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You cannot use /$command when in a minigame.")
            )
            return true
        }

        if (!sender.hasPermission("diamondbank-og.$command")) {
            sender.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to use this command.")
            )
            return true
        }
        return false
    }
}