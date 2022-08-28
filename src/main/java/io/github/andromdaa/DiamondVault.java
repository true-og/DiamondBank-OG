package io.github.andromdaa;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;

public class DiamondVault implements Economy {
    private final Plugin plugin;

    public DiamondVault(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "Diamond Bank";
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getPlayerContainer(player).has(plugin.BANK_KEY, PersistentDataType.DOUBLE);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if(!hasAccount(player)) return 0;
        else return plugin.getPersistentDataItem((Player) player, plugin.BANK_KEY, PersistentDataType.DOUBLE);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if(!hasAccount(player)) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, plugin.constructString("config.failedFindPlayer", amount, player.getPlayer().getDisplayName()));
        else plugin.getPlayerContainer(player).set(plugin.BANK_KEY, PersistentDataType.DOUBLE, (getBalance(player) + amount) );

        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, plugin.constructString("config.depositMessage", amount, player.getPlayer().getDisplayName()));
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if(!hasAccount(player)) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, plugin.constructString("config.failedFindPlayer", amount, player.getPlayer().getDisplayName()));
        if(!has(player, amount)) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, plugin.constructString("config.insufficientBalanceMsg", amount, player.getPlayer().getDisplayName()));

        double refund;
        String errMsg = plugin.constructString("config.depositMessage", amount, player.getPlayer().getDisplayName());

        // set player balance
        plugin.getPlayerContainer(player).set(plugin.BANK_KEY, PersistentDataType.DOUBLE, (getBalance(player) - amount) );
        // add diamonds to inventory
        HashMap<Integer, ItemStack> overflow = ((Player) player).getInventory().addItem(new ItemStack(Material.DIAMOND, (int) amount));
        // store any overflow from not enough inventory slots
        refund = overflow.getOrDefault(0, new ItemStack(Material.AIR, 0)).getAmount();

        // send the player back the refund amount
        if (refund != 0) {
            errMsg = plugin.constructString("config.notEnoughInvErrMsg", refund, player.getPlayer().getDisplayName());
            plugin.econ.depositPlayer(player, refund);
        }

        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, errMsg);
    }

    // No use for anything after this

    @Override
    public boolean hasAccount(String playerName) {
        return false;
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public String currencyNamePlural() {
        return null;
    }

    @Override
    public String currencyNameSingular() {
        return null;
    }


    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return 0;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return false;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public double getBalance(String playerName) {
        return 0;
    }

    @Override
    public double getBalance(String playerName, String world) {
        return 0;
    }

    @Override
    public boolean has(String playerName, double amount) {
        return false;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return null;
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return null;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return false;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return false;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return null;
    }


    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return null;
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return null;
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return null;
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return null;
    }

    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return null;
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return null;
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public List<String> getBanks() {
        return null;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return false;
    }
}
