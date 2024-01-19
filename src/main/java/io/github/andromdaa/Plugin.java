package io.github.andromdaa;

import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.andromdaa.commands.BankCommand;
import io.github.andromdaa.listeners.EventListener;
import io.github.andromdaa.util.Util;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public final class Plugin extends JavaPlugin {

	public static Plugin diamondBankPlugin;

	public final NamespacedKey BANK_KEY = new NamespacedKey(this, "andromda.bank");
	public Economy econ = null;

	@Override
	public void onEnable() {

		getServer().getServicesManager().register(Economy.class, new DiamondVault(this), this, ServicePriority.Normal);
		econ = getServer().getServicesManager().getRegistration(Economy.class).getProvider();

		diamondBankPlugin = this;

		// vault setup
		if (econ == null ) {

			this.getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", this.getName()));

			getServer().getPluginManager().disablePlugin(this);

			return;
		}

		// register listeners
		getServer().getPluginManager().registerEvents(new EventListener(this), this);

		// register commands
		this.getCommand("bank").setExecutor(new BankCommand(this));

		// config options
		getConfig().options().copyDefaults(true);
		saveDefaultConfig();

	}

	public static Plugin getPlugin() {

		return diamondBankPlugin;

	}

	public void onReload(Player p) {
		if(! checkPerms(p, "bank.reload")) {

			return;

		}

		reloadConfig();

		p.sendMessage(Util.legacySerializerAnyCase(getConfig().getString("config.reloadMessage")));

	}

	public <Z, T> Z getPersistentDataItem(Player p, NamespacedKey key, PersistentDataType<T, Z> dataType) {

		return p.getPersistentDataContainer().get(key, dataType);

	}

	public EconomyResponse setPlayerBalance(Player p, double amount, String message) {

		p.getPersistentDataContainer().set(this.BANK_KEY, PersistentDataType.DOUBLE, (amount));

		return new EconomyResponse(amount, this.econ.getBalance(p), EconomyResponse.ResponseType.SUCCESS, message);

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

		if(! player.hasPermission(node)) {

			player.sendMessage(Util.legacySerializerAnyCase(constructString("config.permissionDenied", 0, "")));

			return false;

		}

		return true;

	}

	public PersistentDataContainer getPlayerContainer(OfflinePlayer p) {

		Player player = (Player) p;
		return player.getPersistentDataContainer();

	}

	public <T> Object parseArgs(Player executor, String argument, Class<T> tClass) {

		if(tClass.equals(Integer.class)) {

			try {

				return Integer.parseInt(argument);

			}
			catch (NumberFormatException e) {

				executor.sendMessage(Util.legacySerializerAnyCase(constructString("config.parsingError", 0, executor.getName())));

			}

		}
		else if(tClass.equals(Player.class)) {

			try {

				return getServer().getPlayer(argument);

			}
			catch (NullPointerException e) {

				executor.sendMessage(Util.legacySerializerAnyCase(constructString("config.failedFindPlayer", 0, executor.getName())));

			}

		}

		return null;

	}

}