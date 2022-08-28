package io.github.andromdaa.commands;

import io.github.andromdaa.PlayerComparator;
import io.github.andromdaa.Plugin;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;


public class BankCommand implements CommandExecutor {
    private final Plugin plugin;
    private final PriorityQueue<Player> balTop;

    public BankCommand(Plugin plugin) {
        this.plugin = plugin;
        PlayerComparator comparator = new PlayerComparator(plugin);
        balTop = new PriorityQueue<>(comparator);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)) return true;
        Player p = (Player) sender;


        // base command dispatcher
        if(args.length == 0) balance(p, args);
        else if(args[0].equalsIgnoreCase("balance")) balance(p, args);
        else if(args[0].equalsIgnoreCase("deposit")) deposit(p, args);
        else if(args[0].equalsIgnoreCase("pay")) pay(p, args);
        else if(args[0].equalsIgnoreCase("transfer")  ) transfer(p, args);
        else if(args[0].equalsIgnoreCase("withdraw")  ) withdraw(p, args);
        else if(args[0].equalsIgnoreCase("add")  ) addBal(p, args);
        else if(args[0].equalsIgnoreCase("remove")  ) removeBal(p, args);
        else if(args[0].equalsIgnoreCase("set")  ) setBal(p, args);
        else if(args[0].equalsIgnoreCase("baltop")) baltop(p);
        else if (args[0].equalsIgnoreCase("reload")) plugin.onReload(p);


        return true;
    }

    private void setBal(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.set")) return;
        if(args.length != 3) return;

        Player player = (Player) plugin.parseArgs(p, args[1], Player.class);
        Integer amount = (Integer) plugin.parseArgs(p, args[2], Double.class);

        if(amount == null || player == null) return;

        EconomyResponse response = plugin.setPlayerBalance(player, amount, plugin.constructString("config.setBalanceMsg", amount, player.getDisplayName()));
        p.sendMessage(response.errorMessage);
    }

    private void baltop(Player p) {
        if(plugin.checkPerms(p, "diamondbank.set")) return;

        for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
            if(!balTop.contains(offlinePlayer.getPlayer())) balTop.add(offlinePlayer.getPlayer());
        }

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if(!balTop.contains(onlinePlayer.getPlayer())) balTop.add(onlinePlayer);
        }

        int maxBalances = Math.min(plugin.getConfig().getInt("config.balTopMax"), balTop.size());

        p.sendMessage(this.plugin.constructString("config.balTopMsg", 0, p.getDisplayName()));

        for (int i = 0; i < maxBalances; i++) {
            Player bal = balTop.poll();
            p.sendMessage(String.format("%d: %s - %s", i + 1, bal.getDisplayName(), plugin.econ.getBalance(bal)));
        }

    }

    private void removeBal(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.remove")) return;
        if(args.length != 3) return;

        Player player = (Player) plugin.parseArgs(p, args[1], Player.class);
        Integer amount = (Integer) plugin.parseArgs(p, args[2], Double.class);

        if(amount == null || player == null) return;

        EconomyResponse response = plugin.setPlayerBalance(player, (plugin.econ.getBalance(player) - amount), plugin.constructString("config.removeBalanceMsg", amount, player.getDisplayName()));
        p.sendMessage(response.errorMessage);

    }

    private void addBal(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.add")) return;
        if(args.length != 3) return;

        Player player = (Player) plugin.parseArgs(p, args[1], Player.class);
        Integer amount = (Integer) plugin.parseArgs(p, args[2], Double.class);

        if(amount == null || player == null) return;

        plugin.econ.depositPlayer(player, amount);

        p.sendMessage(plugin.constructString("config.addBalanceMsg", amount, player.getDisplayName()));
    }

    private void transfer(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.transfer")) return;
        pay(p, args);
    }

    private void withdraw(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.withdraw")) return;
        if(args.length != 2) return;

        EconomyResponse response;

        if(args[1].equalsIgnoreCase("all")) response = plugin.econ.withdrawPlayer(p, plugin.econ.getBalance(p));
        else response = plugin.econ.withdrawPlayer(p, (double) plugin.parseArgs(p, args[1], Double.class));

        p.sendMessage(response.errorMessage);
    }

    private void pay(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.pay")) return;

        Player player = (Player) plugin.parseArgs(p, args[1], Player.class);
        Integer amount = (Integer) plugin.parseArgs(p, args[2], Double.class);

        if(amount == null || player == null) return;
        if(amount > plugin.econ.getBalance(p)) {
            p.sendMessage(plugin.constructString("config.insufficientBalanceMsg", amount, p.getDisplayName()));
            return;
        }

        EconomyResponse sender = plugin.setPlayerBalance(p, (plugin.econ.getBalance(p) - amount), plugin.constructString("config.paymentSentMsg", amount, player.getDisplayName()));
        EconomyResponse recipient = plugin.setPlayerBalance(player, (plugin.econ.getBalance(player) + amount), plugin.constructString("config.paymentReceivedMsg", amount, p.getDisplayName()));

        // Send sender confirmation message
        p.sendMessage(sender.errorMessage);
        // Send recipient alert message
        player.sendMessage(recipient.errorMessage);

    }

    private void deposit(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.deposit")) return;
        if (args.length > 2) return;

        ArrayList<ItemStack> items = new ArrayList<>();
        int diamonds = 0;

        if(args.length == 1) items.add(p.getInventory().getItemInMainHand());
        else items.addAll(Arrays.asList(p.getInventory().getContents()));

        for (ItemStack itemStack : items) {
            if(itemStack == null) continue;

            Material material = itemStack.getType();

            if (material == Material.DIAMOND_BLOCK) diamonds += (9 * itemStack.getAmount());
            else if (material == Material.DIAMOND) diamonds += itemStack.getAmount();

            itemStack.setAmount(0);
        }

        if(diamonds == 0) {
            p.sendMessage(plugin.constructString("config.insuffcientDiamondsMsg", 0, p.getDisplayName()));
            return;
        }

        EconomyResponse response = plugin.econ.depositPlayer(p, diamonds);
        p.sendMessage(response.errorMessage);
    }


    private void balance(Player p, String[] args) {
        if(plugin.checkPerms(p, "diamondbank.balance")) return;
        Double balance;

        if(args.length == 2) balance = (Double) plugin.parseArgs(p, args[1], Double.class);
        else balance = plugin.econ.getBalance(p);

        if(balance == null) return;

        p.sendMessage(plugin.constructString("config.balanceMsg", balance, p.getDisplayName()));
    }
}
