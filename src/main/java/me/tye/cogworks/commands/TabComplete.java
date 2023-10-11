package me.tye.cogworks.commands;

import me.tye.cogworks.util.Log;
import me.tye.cogworks.util.Plugins;
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

public class TabComplete implements TabCompleter {
@Override
public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
  ArrayList<String> completions = new ArrayList<>();
  if (label.equals("plugin")) {
    if (args.length == 1) {
      if (sender.hasPermission("cogworks.plugin.ins")) {
        StringUtil.copyPartialMatches(args[0], Arrays.asList("install", "browse"), completions);
      }
      if (sender.hasPermission("cogworks.plugin.rm")) {
        StringUtil.copyPartialMatches(args[0], List.of("remove"), completions);
      }
      if (sender.hasPermission("cogworks.plugin.reload")) {
        StringUtil.copyPartialMatches(args[0], List.of("reload"), completions);
      }

      if (!completions.isEmpty()) StringUtil.copyPartialMatches(args[0], List.of("help"), completions);
    }
    if (args.length == 2 && args[0].equals("install") && args[1].isEmpty()) {
      return List.of(Util.getLang("tabComplete.plugin.install"));
    }
    if (args.length == 2 && args[0].equals("remove")) {
      ArrayList<String> plugins = new ArrayList<>();

      try {
        for (PluginData data : Plugins.readPluginData())
          plugins.add(data.getName());
      } catch (IOException e) {
        new Log(sender, "tabComplete.dataReadError").log();
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
