package me.tye.cogworks.commands;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tye.cogworks.operationHandlers.DeleteQueue;
import me.tye.cogworks.operationHandlers.PluginBrowse;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.StoredPlugins;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.util.Util.encodeUrl;

public class PluginCommand implements CommandExecutor {

@Override
public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
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

  new DeleteQueue(sender, args[1]);
}

private void installPlugin(CommandSender sender, String[] args) {
  if (!sender.hasPermission("cogworks.plugin.ins.gen"))
    return;
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

    if (Plugins.installPluginURL(sender, "pluginInstall", args[1], fileName, true)) {
      new Log(sender, "pluginInstall.finish").setFileName(fileName).log();
    }

  } catch (MalformedURLException e) {
    new Log(sender, "pluginInstall.badUrl").setUrl(args[1]).log();
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

  ModrinthSearch search = Plugins.modrinthSearch(sender, "pluginInstall", query.substring(1));

  HashMap<JsonObject,JsonArray> validPlugins = search.getValidPlugins();
  ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();

  if (validPlugins.isEmpty() || validPluginKeys.isEmpty())
    return;

  new Log(sender, "pluginInstall.pluginSelect").log();
  for (int i = 0; 10 > i; i++) {
    if (validPluginKeys.size() <= i)
      break;
    JsonObject project = validPluginKeys.get(i);
    TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
    projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
    projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
    projectName.setUnderlined(true);
    sender.spigot().sendMessage(projectName);
  }

  ChatParams params = new ChatParams(sender, "pluginSelect").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys);
  if (sender instanceof Player)
    response.put(sender.getName(), params);
  else
    response.put("~", params);
}

private void browsePlugins(CommandSender sender, int offset) {
  PluginBrowse pluginBrowse = new PluginBrowse(sender, offset);
  pluginBrowse.execute();
}
}
