package me.tye.filemanager;

import me.tye.filemanager.commands.PluginCommand;
import me.tye.filemanager.commands.TabComplete;
import me.tye.filemanager.commands.fileCommand.FileCommand;
import me.tye.filemanager.commands.fileCommand.FileGui;
import me.tye.filemanager.events.ChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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
        getServer().getPluginManager().registerEvents(new ChatEvent(), this);
        getServer().getPluginManager().registerEvents(new FileGui(), this);

    }

    @Override
    public void onDisable() {

    }

    public static String makeValidForUrl(String text) {
        return text.replaceAll("[^a-z0-9\s-]", "").replaceAll(" ", "%20");
    }
    public static ItemStack itemProperties(ItemStack item, String displayName, List<String> lore) {
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(displayName);
        if (lore != null) itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
        return item;
    }
}
