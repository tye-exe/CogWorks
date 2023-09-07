package me.tye.filemanager;

import me.tye.filemanager.commands.FileCommand;
import me.tye.filemanager.commands.PluginCommand;
import me.tye.filemanager.commands.TabComplete;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static me.tye.filemanager.commands.FileCommand.position;

public final class FileManager extends JavaPlugin {


    @Override
    public void onEnable() {

        //getLogger().log(Level.INFO, "Beep boop!");

        //Commands
        getCommand("plugin").setExecutor(new PluginCommand());
        getCommand("file").setExecutor(new FileCommand());

        getCommand("plugin").setTabCompleter(new TabComplete());
        getCommand("file").setTabCompleter(new TabComplete());

        //Listeners
        getServer().getPluginManager().registerEvents(new ChatManager(), this);
        getServer().getPluginManager().registerEvents(new FileGui(), this);

    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (position.containsKey(player.getName())) player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "[FileManager] Menu closed due to reload.");
        }
    }

    public static String makeValidForUrl(String text) {
        return text.replaceAll("[^A-z0-9s-]", "").replaceAll(" ", "%20");
    }
    public static ItemStack itemProperties(ItemStack item, String displayName, List<String> lore) {
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(displayName);
        if (lore != null) itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
        return item;
    }
}
