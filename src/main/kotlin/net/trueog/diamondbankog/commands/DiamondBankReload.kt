package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import net.trueog.diamondbankog.DiamondBankOG.Companion.postgreSQL
import net.trueog.diamondbankog.PostgreSQL
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

internal class DiamondBankReload : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        config =
            Config.create()
                ?: run {
                    sender.sendMessage(
                        mm.deserialize("<red>Failed to reload the config. Check the console for more information.")
                    )
                    Bukkit.getPluginManager().disablePlugin(plugin)
                    return true
                }

        postgreSQL = PostgreSQL()
        try {
            postgreSQL.initDB()
        } catch (e: Exception) {
            plugin.logger.info(e.toString())
            sender.sendMessage(
                mm.deserialize("<red>Failed to reload the config. Check the console for more information.")
            )
            Bukkit.getPluginManager().disablePlugin(plugin)
            return true
        }

        sender.sendMessage(mm.deserialize("${config.prefix}<reset>: <green>Successfully reloaded the config."))
        return true
    }
}
