package me.tye.filemanager.commands;

import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.yamlClasses.PluginData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TabComplete implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ArrayList<String> completions = new ArrayList<>();
        if (label.equals("plugin")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "install", "remove"), completions);
            }
            if (args.length == 2 && args[0].equals("install") && args[1].isEmpty()) {
                return List.of("<pluginName | URL>");
            }
            if (args.length == 2 && args[0].equals("remove")) {
                ArrayList<String> plugins = new ArrayList<>();

                for (PluginData data : FileManager.readPluginData())
                    plugins.add(data.getName());

                StringUtil.copyPartialMatches(args[1], plugins, completions);
            }
        }

        if (label.equals("file")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "chat", "gui"), completions);
            }
        }
        return completions;
    }
}
