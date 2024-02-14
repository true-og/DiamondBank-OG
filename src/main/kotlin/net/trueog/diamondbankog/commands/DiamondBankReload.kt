package net.trueog.diamondbankog.commands

import io.sentry.Sentry
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.PostgreSQL
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DiamondBankReload : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (Config.load()) {
            sender.sendMessage(DiamondBankOG.mm.deserialize("<red>Failed to reload the config. Check the console for more information."))
            Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
            return true
        }

        if (Config.sentryEnabled) {
            try {
                Sentry.init { options ->
                    options.dsn = Config.sentryDsn
                }
                DiamondBankOG.sentryEnabled = true
            } catch (e: Exception) {
                DiamondBankOG.sentryEnabled = false
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Could not initialise Sentry. The Sentry(-compatible) DSN in your config might be invalid."))
            }
        }

        DiamondBankOG.postgreSQL = PostgreSQL()
        try {
            DiamondBankOG.postgreSQL.initDB()
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
            sender.sendMessage(DiamondBankOG.mm.deserialize("<red>Failed to reload the config. Check the console for more information."))
            Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
            return true
        }

        sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully reloaded the config."))
        return true
    }
}