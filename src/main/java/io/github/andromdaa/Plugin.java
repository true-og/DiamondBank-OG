package io.github.andromdaa;

import io.github.andromdaa.commands.BankCommand;
import io.github.andromdaa.listeners.EventListener;
import io.github.andromdaa.util.Util;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Plugin extends JavaPlugin {

    public static Plugin diamondBankPlugin;
    public PostgreSQL postgreSQL = null;

    public static Plugin getPlugin() {
        return diamondBankPlugin;
    }

    @Override
    public void onEnable() {
        diamondBankPlugin = this;

        postgreSQL = new PostgreSQL();
        try {
            postgreSQL.initDB();
        } catch (Exception e) {
            Plugin.getPlugin().getLogger().info(e.toString());
        }

//        postgreSQL.setPlayerBalance(UUID.fromString("7c7f7ff1-36b9-436d-b366-22ae95e268ef"), 10.0);
//        double test = postgreSQL.getPlayerBalance(UUID.fromString("7c7f7ff1-36b9-436d-b366-22ae95e268ef"));
//        getLogger().info(Double.toString(test));

        // register listeners
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        // register commands
        this.getCommand("bank").setExecutor(new BankCommand(this));

        // config options
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

    }

    public void onReload(Player p) {
        if (!checkPerms(p, "bank.reload")) {
            return;
        }

        reloadConfig();
        p.sendMessage(Util.legacySerializerAnyCase(getConfig().getString("config.reloadMessage")));

    }

    public String constructString(String path, double amount, String playerName) {

        String message = this.getConfig().getString(path);

        message = message.replaceAll("\\$player", playerName);
        message = message.replaceAll("\\$amount", String.valueOf(amount));

        TextComponent coloredMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        message = coloredMessage.content();

        return message;

    }

    public boolean checkPerms(Player player, String node) {
        if (!player.hasPermission(node)) {
            player.sendMessage(Util.legacySerializerAnyCase(constructString("config.permissionDenied", 0, "")));
            return false;
        }
        return true;
    }

    public <T> Object parseArgs(Player executor, String argument, Class<T> tClass) {
        if (tClass.equals(Integer.class)) {
            try {
                return Integer.parseInt(argument);
            } catch (NumberFormatException e) {
                executor
                        .sendMessage(Util.legacySerializerAnyCase(constructString("config.parsingError", 0, executor.getName())));
            }
        } else if (tClass.equals(Player.class)) {
            try {
                return getServer().getPlayer(argument);
            } catch (NullPointerException e) {
                executor.sendMessage(
                        Util.legacySerializerAnyCase(constructString("config.failedFindPlayer", 0, executor.getName())));
            }
        }
        return null;
    }
}
