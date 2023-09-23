package me.tye.filemanager.commands;

import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.yamlClasses.PluginData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.permissions.Permission;
import org.bukkit.util.StringUtil;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

import static me.tye.filemanager.FileManager.log;

public class TabComplete implements TabCompleter {
    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,@NonNull String label, @NonNull String[] args) {
        ArrayList<String> completions = new ArrayList<>();
        if (label.equals("plugin") ) {
            if (args.length == 1) {
                if (sender.hasPermission("fileman.plugin.install")) {
                    StringUtil.copyPartialMatches(args[0], Arrays.asList("install", "browse"), completions);
                }
                if (sender.hasPermission("fileman.plugin.remove")) {
                    StringUtil.copyPartialMatches(args[0], List.of("remove"), completions);
                }

                if (!completions.isEmpty()) StringUtil.copyPartialMatches(args[0], List.of("help"), completions);
            }
            if (args.length == 2 && args[0].equals("install") && args[1].isEmpty()) {
                return List.of("<pluginName | URL>");
            }
            if (args.length == 2 && args[0].equals("remove")) {
                ArrayList<String> plugins = new ArrayList<>();

                try {
                    for (PluginData data : FileManager.readPluginData())
                        plugins.add(data.getName());
                } catch (IOException e) {
                    log(e, sender, Level.WARNING, "There was an error reading the plugin names from the pluginData file.");
                }

                StringUtil.copyPartialMatches(args[1], plugins, completions);
            }
        }

        if (label.equals("file") && sender.hasPermission("fileman.file.nav")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "chat", "gui"), completions);
            }
        }
        completions.sort(null);
        return completions;
    }
}
