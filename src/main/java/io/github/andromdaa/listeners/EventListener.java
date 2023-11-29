package io.github.andromdaa.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import io.github.andromdaa.Plugin;

public class EventListener implements Listener {
	final private Plugin plugin;
	private final double START_BAL;


	public EventListener(Plugin plugin) {
		this.plugin = plugin;
		this.START_BAL = plugin.getConfig().getInt("config.startingBalance");
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if(!p.getPersistentDataContainer().has(plugin.BANK_KEY, PersistentDataType.DOUBLE)) p.getPersistentDataContainer().set(plugin.BANK_KEY, PersistentDataType.DOUBLE, START_BAL);
	}

}