package io.github.andromdaa;

import java.util.Comparator;

import org.bukkit.entity.Player;

public class PlayerComparator implements Comparator<Player> {

	private final Plugin plugin;

	public PlayerComparator(Plugin plugin) {

		this.plugin = plugin;

	}

	@Override
	public int compare(Player o1, Player o2) {

		return Double.compare(plugin.econ.getBalance(o1), plugin.econ.getBalance(o2));

	}

}