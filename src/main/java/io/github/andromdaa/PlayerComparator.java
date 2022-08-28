package io.github.andromdaa;

import org.bukkit.entity.Player;

import java.util.Comparator;

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
