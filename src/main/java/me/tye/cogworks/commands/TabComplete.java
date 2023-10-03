package me.tye.cogworks.commands;

import me.tye.cogworks.CogWorks;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.yamlClasses.PluginData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static me.tye.cogworks.CogWorks.log;

public class TabComplete implements TabCompleter {
    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,@NonNull String label, @NonNull String[] args) {
        ArrayList<String> completions = new ArrayList<>();
        if (label.equals("plugin") ) {
            if (args.length == 1) {
                if (sender.hasPermission("cogworks.plugin.ins")) {
                    StringUtil.copyPartialMatches(args[0], Arrays.asList("install", "browse"), completions);
                }
                if (sender.hasPermission("cogworks.plugin.rm")) {
                    StringUtil.copyPartialMatches(args[0], List.of("remove"), completions);
                }

                if (!completions.isEmpty()) StringUtil.copyPartialMatches(args[0], List.of("help"), completions);
            }
            if (args.length == 2 && args[0].equals("install") && args[1].isEmpty()) {
                return List.of(Util.getLang("tabComplete.plugins.install"));
            }
            if (args.length == 2 && args[0].equals("remove")) {
                ArrayList<String> plugins = new ArrayList<>();

                try {
                    for (PluginData data : CogWorks.readPluginData())
                        plugins.add(data.getName());
                } catch (IOException e) {
                    log(e, sender, Level.WARNING, Util.getLang("exceptions.dataReadError"));
                }

                StringUtil.copyPartialMatches(args[1], plugins, completions);
            }
        }

        if (label.equals("file") && sender.hasPermission("cogworks.file.nav")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "chat", "gui"), completions);
            }
        }
        completions.sort(null);
        return completions;
    }
}
