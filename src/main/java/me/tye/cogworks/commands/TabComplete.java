package me.tye.cogworks.commands;

import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.DeletePending;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PluginData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static me.tye.cogworks.util.Util.serverFolder;

public class TabComplete implements TabCompleter {

@Override
public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
        for (PluginData data : PluginData.read()) {
          if (data.isDeletePending() || Arrays.stream(args).toList().contains(data.getName())) {
            continue;
          }

          plugins.add(data.getName());
        }
      } catch (IOException e) {
        new Log(sender, "tabComplete.pluginReadError").setException(e).log();
      }

      StringUtil.copyPartialMatches(args[args.length-1], plugins, completions);
    }

  }

  if (label.equals("file")) {
    if (args.length == 1) {
      if (sender.hasPermission("cogworks.file.nav")) {
        StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "chat", "gui"), completions);
      }

      if (sender.hasPermission("cogworks.file.rec")) {
        StringUtil.copyPartialMatches(args[0], List.of("recover"), completions);
      }
    }

    if (args[0].equals("recover") && sender.hasPermission("cogworks.file.rec")) {

      if (args.length == 2) {
        try {
          StringUtil.copyPartialMatches(args[1], DeletePending.getUniqueOldPaths(), completions);
        } catch (IOException e) {
          new Log(sender, "tabComplete.recoverReadError").setException(e).log();
        }
      }

      if (args.length == 3) {
        return recoverFilePath(sender, args);
      }
    }

  }

  completions.sort(null);
  return completions;
}

/**
 Gets the suitable auto-completes available to the user for selecting a destination or file name to restore to.
 @param sender The command sender - used for logging.
 @param args   The args for the command.
 @return The sorted list of tab complete options for the user. */
private static ArrayList<String> recoverFilePath(CommandSender sender, String[] args) {
  if (!sender.hasPermission("cogworks.file.rec")) {
    return new ArrayList<>();
  }

  ArrayList<String> completions = new ArrayList<>();

  //adds the old file location to the suggestions.
  try {
    DeletePending delete = DeletePending.getDelete(args[1]);
    if (delete != null) {
      completions.add("./"+String.valueOf(delete.getRelativePath()).replace("\\", "/"));
    }

  } catch (IOException ex) {
    new Log(sender, "tabComplete.recoverReadError").setException(ex).log();
  }

  //return if the user entered a non-valid path return.
  Path fullPath;
  try {
    fullPath = serverFolder.toPath().resolve(Path.of(args[2]));
  } catch (InvalidPathException e) {
    return completions;
  }

  //if the path is not to a valid dir then get the last valid dir.
  Path parentPath = fullPath;
  if (!fullPath.toFile().isDirectory()) {
    parentPath = fullPath.getParent();
  }

  //return if there a was error getting the files in the dir
  if (parentPath.toFile().listFiles() == null) {
    return completions;
  }

  //adds the files in the same dir as the user to the suggestions.
  ArrayList<String> dirNames = new ArrayList<>();
  for (File file : Objects.requireNonNull(parentPath.toFile().listFiles())) {
    if (!file.isDirectory()) {
      continue;
    }
    dirNames.add(file.getName()+'/');
  }

  //if the user has typed a path then the last file name is gotten.
  String typed = "";
  if (args[2].endsWith("/") || !args[2].isEmpty()) {
    typed = fullPath.getFileName().toString();
  }

  StringUtil.copyPartialMatches(typed, dirNames, completions);

  return completions;
}

}
