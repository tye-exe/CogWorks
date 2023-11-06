package me.tye.cogworks.commands;

import me.tye.cogworks.util.StoredPlugins;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.yamlClasses.PluginData;
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
      if (sender.hasPermission("cogworks.plugin.ins.gen")) {
        StringUtil.copyPartialMatches(args[0], List.of("install"), completions);
      }
      if (sender.hasPermission("cogworks.plugin.ins.modrinth")) {
        StringUtil.copyPartialMatches(args[0], List.of("search", "browse"), completions);
      }
      if (sender.hasPermission("cogworks.plugin.rm")) {
        StringUtil.copyPartialMatches(args[0], List.of("remove"), completions);
      }
      if (sender.hasPermission("cogworks.plugin.reload")) {
        StringUtil.copyPartialMatches(args[0], List.of("reload"), completions);
      }

      StringUtil.copyPartialMatches(args[0], List.of("help"), completions);
    }

    if (args.length == 2) {

      if (args[1].isEmpty()) {
        if (args[0].equals("install") && sender.hasPermission("cogworks.plugin.ins.gen"))
          return List.of(Util.getLang("tabComplete.plugin.install"));

        if (args[0].equals("search") && sender.hasPermission("cogworks.plugin.ins.modrinth"))
          return List.of(Util.getLang("tabComplete.plugin.search"));
      }
    }

    //adds all the undeleted & not selected plugins to the return list
    if (args[0].equals("remove") && sender.hasPermission("cogworks.plugin.rm")) {
      ArrayList<String> plugins = new ArrayList<>();

      try {
        for (PluginData data : StoredPlugins.readPluginData()) {
          if (data.isDeletePending() || Arrays.stream(args).toList().contains(data.getName())) {
            continue;
          }

          plugins.add(data.getName());
        }
      } catch (IOException e) {
        new Log(sender, "tabComplete.dataReadError").setException(e).log();
      }

      StringUtil.copyPartialMatches(args[args.length-1], plugins, completions);
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
