package net.trueog.diamondbankog

import org.bukkit.plugin.java.JavaPlugin

object TestCommands {
    @JvmStatic
    fun register(plugin: JavaPlugin) {
        plugin.getCommand("dbogtests")?.setExecutor(TestCommand())
        plugin.logger.warning("Registered test commands")
    }
}
