package me.tye.cogworks.commands;

import me.tye.cogworks.util.StoredPlugins;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.dataClasses.DeletePending;
import me.tye.cogworks.util.customObjects.dataClasses.PluginData;
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
        for (PluginData data : StoredPlugins.readPluginData()) {
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

  if (label.equals("file") && sender.hasPermission("cogworks.file.nav")) {
    if (args.length == 1) {
      StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "chat", "gui", "recover"), completions);
    }

    if (args[0].equals("recover")) {
      if (args.length == 2) {
        try {
          StringUtil.copyPartialMatches(args[1], DeletePending.getUniqueOldPaths(), completions);
        } catch (IOException e) {
          new Log(sender, "tabComplete.recoverReadError").setException(e).log();
        }
      }

      if (args.length == 3) {

        //TODO: Show path to og file at top, show path to other available dirs below, if path wends ends dir make file restore with it's og name.


        if (args[2].isEmpty()) {
          try {
            DeletePending delete = DeletePending.getDelete(args[1]);

            String[] fileNames = serverFolder.list();
            if (fileNames == null) {
              return new ArrayList<>();
            }

            ArrayList<String> dirs = new ArrayList<>(Arrays.stream(fileNames).toList());
            dirs.sort(null);
            dirs.replaceAll(string -> string+File.separator);

            if (delete != null) {
              dirs.add(String.valueOf(delete.getRelativePath()));
            }

            return dirs;

          } catch (IOException e) {
            new Log(sender, "tabComplete.recoverReadError").setException(e).log();
          }
        }

        else {
          try {
            DeletePending delete = DeletePending.getDelete(args[1]);

            String[] fileNames = serverFolder.list();
            ArrayList<String> dirs;

            try {
              Path enteredPath = Path.of(args[2]);
              enteredPath = serverFolder.toPath().resolve(enteredPath);
              fileNames = enteredPath.getParent().toFile().list();
            } catch (InvalidPathException e) {

            }

            dirs = new ArrayList<>(Arrays.stream(fileNames).toList());
            dirs.sort(null);
            dirs.replaceAll(string -> string+File.separator);

            if (delete != null) {
              dirs.add(String.valueOf(delete.getRelativePath()));
            }

            return dirs;

          } catch (IOException e) {
            new Log(sender, "tabComplete.recoverReadError").setException(e).log();
          }
        }

      }
    }

  }

  completions.sort(null);
  return completions;
}

private static List<String> recoverFilePath(CommandSender sender, String[] args) {
  ArrayList<String> completions = new ArrayList<>();
  String destinationPath = args[2];

  //adds the old file location to the suggestions.
  try {
    DeletePending delete = DeletePending.getDelete(args[1]);
    completions.add(String.valueOf(delete.getRelativePath()));
  } catch (IOException ex) {
    new Log(sender, "tabComplete.recoverReadError").setException(ex).log();
  }

  //if the user entered a non-valid path return.
  Path fullPath;
  try {
    fullPath = serverFolder.toPath().resolve(Path.of(destinationPath));
  } catch (InvalidPathException e) {
    return completions;
  }

  //if there a was error getting the files in the dir
  if (fullPath.toFile().listFiles() == null) {
    return completions;
  }

  ArrayList<String> dirs = new ArrayList<>();

  //if the path is to a valid dir
  if (fullPath.toFile().exists() && fullPath.toFile().isDirectory()) {

    for (File file : fullPath.toFile().listFiles()) {
      if (!file.isDirectory()) {
        continue;
      }

      dirs.add(file.getName()+File.separator);
    }
  }
  //if the path isn't to a valid dir
  else {
    ud
  }
}

}
