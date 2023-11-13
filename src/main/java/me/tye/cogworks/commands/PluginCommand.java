package me.tye.cogworks.commands;

import com.google.common.io.Files;
import me.tye.cogworks.operationHandlers.DeleteQueue;
import me.tye.cogworks.operationHandlers.PluginBrowse;
import me.tye.cogworks.operationHandlers.PluginSearch;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.StoredPlugins;
import me.tye.cogworks.util.customObjects.Log;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;

import static me.tye.cogworks.util.Util.encodeUrl;

public class PluginCommand implements CommandExecutor {

@Override
public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
  if (args.length < 1) {
    helpMessage(sender);
    return true;
  }
  new Thread(new Runnable() {

    private CommandSender sender;
    private String[] args;

    public Runnable init(CommandSender sender, String[] args) {
      this.sender = sender;
      this.args = args;
      return this;
    }

    @Override
    public void run() {

      switch (args[0]) {
      case "remove" ->
          deletePlugin(sender, args);

      case "install" ->
          installPlugin(sender, args);

      case "search" ->
          searchPlugins(sender, args);

      case "browse" ->
          browsePlugins(sender, 0);

      case "reload" -> {
        if (!sender.hasPermission("cogworks.plugin.reload"))
          return;

        new Log(sender, "reload.reloading").log();
        StoredPlugins.reloadPluginData(sender, "reload");
        new Log(sender, "reload.reloaded").log();
      }

      default ->
          helpMessage(sender);

      }
    }
  }.init(sender, args)).start();

  return true;
}

private void helpMessage(CommandSender sender) {
  new Log(sender, "help.plugin.help").log();

  if (sender.hasPermission("cogworks.plugin.ins.gen"))
    new Log(sender, "help.plugin.install").log();

  if (sender.hasPermission("cogworks.plugin.ins.modrinth")) {
    new Log(sender, "help.plugin.search").log();
    new Log(sender, "help.plugin.browse").log();
  }

  if (sender.hasPermission("cogworks.plugin.reload"))
    new Log(sender, "help.plugin.reload").log();

  if (sender.hasPermission("cogworks.plugin.rm"))
    new Log(sender, "help.plugin.remove").log();
}

private void deletePlugin(CommandSender sender, String[] args) {
  if (!sender.hasPermission("cogworks.plugin.rm"))
    return;

  if (args.length < 2) {
    new Log(sender, "deletePlugin.provideName").log();
    return;
  }

  DeleteQueue deleteQueue = new DeleteQueue(sender, args[1]);
  for (int i = 2; i < args.length; i++) {
    deleteQueue.addPluginToEval(args[i]);
  }
  deleteQueue.evaluatePlugins();
}

private void installPlugin(CommandSender sender, String[] args) {
  if (!sender.hasPermission("cogworks.plugin.ins.gen")) {
    return;
  }

  if (args.length < 2) {
    new Log(sender, "pluginInstall.noInput").log();
    return;
  }

  try {
    //checks if the arg given is a valid url or not.
    encodeUrl(args[1]);

    //gets the filename from the url
    String fileName;
    String[] splits = args[1].split("/");
    fileName = splits[splits.length-1];
    if (!Files.getFileExtension(fileName).equals("jar")) {
      fileName += ".jar";
    }

    Plugins.installPluginURL(sender, "pluginInstall", args[1], fileName, true);

  } catch (MalformedURLException e) {
    new Log(sender, "pluginInstall.badUrl").setUrl(args[1]).setException(e).log();
  }
}

private void searchPlugins(CommandSender sender, String[] args) {
  if (!sender.hasPermission("cogworks.plugin.ins.modrinth"))
    return;
  if (args.length < 2) {
    new Log(sender, "pluginInstall.noInput").log();
    return;
  }

  StringBuilder query = new StringBuilder();
  for (int i = 1; i < args.length; i++)
    query.append(" ").append(args[i]);

  new PluginSearch(sender, query.substring(1));
}

private void browsePlugins(CommandSender sender, int offset) {
  PluginBrowse pluginBrowse = new PluginBrowse(sender, offset);
  pluginBrowse.execute();
}
}
