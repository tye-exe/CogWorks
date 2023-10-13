package me.tye.cogworks.commands;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.DeleteQueue;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.yamlClasses.PluginData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.CogWorks.encodeUrl;

public class PluginCommand implements CommandExecutor {

@Override
public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
  if (args.length >= 1) {
    switch (args[0]) {
    case "install" -> {
      if (!sender.hasPermission("cogworks.plugin.ins")) return true;
      if (args.length >= 2) {
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

            } catch (MalformedURLException ignore) {
              StringBuilder query = new StringBuilder();
              for (int i = 1; i < args.length; i++) query.append(" ").append(args[i]);
              ModrinthSearch search = Plugins.modrinthSearch(sender, "pluginInstall", query.substring(1));
              HashMap<JsonObject,JsonArray> validPlugins = search.getValidPlugins();
              ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();

              if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) return;

              new Log(sender, "pluginInstall.pluginSelect").log();
              for (int i = 0; 10 > i; i++) {
                if (validPluginKeys.size() <= i) break;
                JsonObject project = validPluginKeys.get(i);
                TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                projectName.setUnderlined(true);
                sender.spigot().sendMessage(projectName);
              }

              ChatParams params = new ChatParams(sender, "pluginSelect").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys);
              if (sender instanceof Player) response.put(sender.getName(), params);
              else response.put("~", params);

            }
          }
        }.init(sender, args)).start();
      } else {
        new Log(sender, "pluginInstall.noInput").log();
      }
      return true;
    }
    case "remove" -> {
      if (!sender.hasPermission("cogworks.plugin.rm")) return true;
      if (args.length >= 2) {
        Boolean deleteConfig = null;
        String pluginName = args[1];

        //checks if the plugin & config folders exist
        PluginData pluginData;
        try {
          pluginData = Plugins.readPluginData(pluginName);
          if (!Plugins.hasConfigFolder(pluginName)) {
            deleteConfig = false;
            new Log(sender, "deletePlugin.noConfigsFound").setPluginName(pluginName).log();
          }
        } catch (NoSuchPluginException | IOException e) {
          new Log(sender, "deletePlugin.noSuchPlugin").setException(e).setPluginName(pluginName).log();
          return true;
        }

        //modifier checks
        for (String arg : args) {
          if (!arg.startsWith("-")) continue;
          String modifiers = arg.substring(1);
          for (char letter : modifiers.toCharArray()) {
            if (letter == 'y') deleteConfig = true;
            if (letter == 'n') deleteConfig = false;
          }
        }

        //schedules plugins that depend on for deletion
        List<PluginData> whatDepends = Plugins.getWhatDependsOn(pluginName);
        ArrayList<PluginData> deleteEval = new ArrayList<>(whatDepends);
        deleteEval.add(0, pluginData);

        //prompt to delete config files
        if (deleteConfig == null) {
          new Log(sender, "deletePlugin.deleteConfig").setPluginName(pluginName).log();
          new Log(sender, "deletePlugin.note").log();
          ChatParams params = new ChatParams(sender, "deletePlugin").setDeleteQueue(new DeleteQueue(sender, "deletePlugin")).setToDeleteEval(deleteEval);
          if (sender instanceof Player) response.put(sender.getName(), params);
          else response.put("~", params);
          return true;
        }

        //checks what depends on this plugin
        if (!whatDepends.isEmpty()) {
          String[] names = new String[whatDepends.size()];
          for (int i = 0; i < whatDepends.size(); i++) names[i] = whatDepends.get(i).getName();

          new Log(sender, "deletePlugin.dependsOn").setPluginNames(names).setPluginName(pluginName).log();

          ChatParams params = new ChatParams(sender, "pluginsDeleteEval").setDeleteQueue(new DeleteQueue(sender, "deletePlugin")).setToDeleteEval(deleteEval).setDeleteConfigs(deleteConfig);
          if (sender instanceof Player) response.put(sender.getName(), params);
          else response.put("~", params);
          return true;
        }

        if (Plugins.deletePlugin(sender, "deletePlugin", pluginName, deleteConfig)) {
          new Log(sender, "deletePlugin.reloadWarn").log();
        }

      } else {
        new Log(sender, "deletePlugin.provideName").log();
      }
      return true;
    }
    case "browse" -> {
      if (!sender.hasPermission("cogworks.plugin.ins")) return true;
      new Thread(new Runnable() {

        private CommandSender sender;
        private int offset;

        public Runnable init(CommandSender sender, int offset) {
          this.sender = sender;
          this.offset = offset;
          return this;
        }

        @Override
        public void run() {
          ModrinthSearch modrinthSearch = Plugins.modrinthBrowse(sender, "pluginBrowse", offset);
          ArrayList<JsonObject> validPluginKeys = modrinthSearch.getValidPluginKeys();
          HashMap<JsonObject,JsonArray> validPlugins = modrinthSearch.getValidPlugins();

          if (validPluginKeys.isEmpty() || validPlugins.isEmpty()) return;

          new Log(sender, "pluginBrowse.pluginSelect").log();
          int i = 0;

          if (offset >= 1) {
            sender.sendMessage(ChatColor.GREEN+String.valueOf(i)+": ^");
            i++;
          }

          while (validPluginKeys.size() > i) {
            JsonObject project = validPluginKeys.get(i);
            TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
            projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
            projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            projectName.setUnderlined(true);
            sender.spigot().sendMessage(projectName);
            i++;
          }

          sender.sendMessage(ChatColor.GREEN+String.valueOf(i+1)+": v");

          ChatParams params = new ChatParams(sender, "pluginBrowse").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys).setOffset(offset);
          if (sender instanceof Player) response.put(sender.getName(), params);
          else response.put("~", params);

        }
      }.init(sender, 0)).start();
      return true;
    }
    case "reload" -> {
      Plugins.reloadPluginData();
      return true;
    }

    }
  }

  new Log(sender, "help.plugin.help").log();
  if (sender.hasPermission("cogworks.plugin.ins")) {
    new Log(sender, "help.plugin.install").log();
    new Log(sender, "help.plugin.browse").log();
  }
  if (sender.hasPermission("cogworks.plugin.reload")) new Log(sender, "help.plugin.reload").log();
  if (sender.hasPermission("cogworks.plugin.rm")) new Log(sender, "help.plugin.remove").log();

  return true;
}

}
