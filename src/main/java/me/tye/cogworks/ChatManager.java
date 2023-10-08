package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.*;
import me.tye.cogworks.util.yamlClasses.PluginData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.commands.PluginCommand.*;
import static me.tye.cogworks.util.Util.pluginFolder;

public class ChatManager implements Listener {

public static HashMap<String,ChatParams> response = new HashMap<>();

@EventHandler
public void onPlayerMessage(AsyncPlayerChatEvent e) {
  if (response.containsKey(e.getPlayer().getName())) e.setCancelled(true);
  checks(e.getPlayer().getName(), e.getMessage());
  if (response.containsKey(e.getPlayer().getName())) e.setCancelled(true);
}

@EventHandler
public void onConsoleMessage(ServerCommandEvent e) {
  if (response.containsKey("~")) e.setCancelled(true);
  checks("~", e.getCommand());
  if (response.containsKey("~")) e.setCancelled(true);
}

public static void checks(String name, String message) {
  if (!response.containsKey(name)) return;
  ChatParams params = response.get(name);
  String state = params.getState();
  if (message.startsWith("plugin")) return;

  new Thread(new Runnable() {

    private String name;
    private String message;

    public Runnable init(String name, String message) {
      this.name = name;
      this.message = message;
      return this;
    }

    @Override
    public void run() {
      try {
        ChatParams params = response.get(name);
        CommandSender sender = params.getSender();
        String state = params.getState();

        switch (state) {
        case "pluginSelect": {
          HashMap<JsonObject,JsonArray> validPlugins = params.getValidPlugins();
          ArrayList<JsonObject> validPluginKeys = params.getValidPluginKeys();
          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }
          int chosenPlugin;
          try {
            chosenPlugin = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, state, "NAN").log();
            return;
          }
          if (chosenPlugin > validPluginKeys.size() || chosenPlugin < 1) {
            new Log(sender, state, "NAN").log();
            return;
          }

          JsonObject plugin = validPluginKeys.get(chosenPlugin-1);
          JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosenPlugin-1));
          ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
          if (compatibleFiles.isEmpty()) {
            new Log(sender, state, "noFiles").log();
          } else if (compatibleFiles.size() == 1) {

            String title = plugin.get("title").getAsString();
            new Log(sender, state, "start").setPluginName(title).log();

            HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, compatibleFiles.get(0).getAsJsonObject());
            ArrayList<Boolean> installed = new ArrayList<>();

            if (!dependencies.isEmpty()) {
              new Log(sender, state, "installingDep").setPluginName(title).log();
              for (JsonArray plugins : dependencies.values()) {
                if (plugins.isEmpty()) continue;
                if (!installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray()))
                  new Log(sender, state, "installed").setFileName(plugins.get(0).getAsJsonObject().get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
              }
              new Log(sender, state, "installedDep").log();
            }

            JsonArray files = compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray();
            if (files.size() > 1) {
              installed.add(installModrinthPlugin(sender, state, files));
              if (!installed.contains(false))
                new Log(sender, state, "finish").setPluginName(title).log();
              // if there are more than one file for that version you get prompted to choose which one(s) to install
            } else {

            }

          } else {
            new Log(sender, state, "pluginSelect").log();
            int i = 1;
            for (JsonElement je : compatibleFiles) {
              JsonObject jo = je.getAsJsonObject();
              chooseableFiles.add(jo);
              TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
              projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+validPluginKeys.get(chosenPlugin-1).get("project_type").getAsString()+"/"+validPluginKeys.get(chosenPlugin-1).get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
              projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
              projectName.setUnderlined(true);
              sender.spigot().sendMessage(projectName);
              i++;
            }
            ChatParams newParams = new ChatParams(sender, "pluginFileSelect").setChooseableFiles(chooseableFiles).setPlugin(plugin);
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);
            return;
          }
          response.remove(name);
          break;
        }

        case "pluginFileSelect": {
          ArrayList<JsonObject> chooseableFiles = params.getChooseableFiles();
          JsonObject plugin = params.getPlugin();
          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          int chosenVersion;
          try {
            chosenVersion = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, state, "NAN").log();
            return;
          }
          if (chosenVersion > chooseableFiles.size() || chosenVersion < 1) {
            new Log(sender, state, "NAN").log();
            return;
          }

          JsonObject chosen = chooseableFiles.get(chosenVersion).getAsJsonObject();
          String title = plugin.get("title").getAsString();
          new Log(sender, state, "start").setPluginName(title).log();

          HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, chosen);
          ArrayList<Boolean> installed = new ArrayList<>();

          if (!dependencies.isEmpty()) {
            new Log(sender, state, "installingDep").setPluginName(title).log();
            for (JsonArray plugins : dependencies.values()) {
              if (plugins.isEmpty()) continue;
              if (!installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray()))
                new Log(sender, state, "installed").setFileName(plugins.get(0).getAsJsonObject().get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
            }
            new Log(sender, state, "installedDep").log();
          }

          installed.add(installModrinthPlugin(sender, state, chosen.get("files").getAsJsonArray()));
          if (!installed.contains(false))
            new Log(sender, state, "finish").setPluginName(title).log();
          response.remove(name);
          break;
        }

        case "pluginBrowse": {
          HashMap<JsonObject,JsonArray> validPlugins = params.getValidPlugins();
          ArrayList<JsonObject> validPluginKeys = params.getValidPluginKeys();
          int offset = params.getOffset();
          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }
          int chosen;
          try {
            chosen = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, state, "NAN").log();
            return;
          }
          if (chosen > validPluginKeys.size()+1 || (offset <= 0 && chosen < 1) || (offset > 0 && chosen < 0)) {
            new Log(sender, state, "NAN").log();
            return;
          }

          Integer nextOffset = null;
          if (chosen == 0) nextOffset = Math.max(offset-10, 0);
          if (chosen == validPluginKeys.size()+1) nextOffset = offset+10;

          if (nextOffset != null) {
            //if the user chooses to scroll
            ModrinthSearch modrinthSearch = modrinthBrowse(sender, state, nextOffset);
            ArrayList<JsonObject> newValidPluginKeys = modrinthSearch.getValidPluginKeys();
            HashMap<JsonObject,JsonArray> newValidPlugins = modrinthSearch.getValidPlugins();
            if (newValidPluginKeys.isEmpty() || newValidPlugins.isEmpty()) return;

            new Log(sender, "pluginBrowse.pluginBrowse").log();
            int i = 0;

            if (nextOffset >= 1) {
              sender.sendMessage(ChatColor.GREEN+String.valueOf(i)+": ^");
            }

            while (newValidPluginKeys.size() > i) {
              JsonObject project = newValidPluginKeys.get(i);
              TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
              projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
              projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
              projectName.setUnderlined(true);
              sender.spigot().sendMessage(projectName);
              i++;
            }

            sender.sendMessage(ChatColor.GREEN+String.valueOf(i+1)+": v");

            ChatParams newParams = new ChatParams(sender, "pluginBrowse").setValidPlugins(newValidPlugins).setValidPluginKeys(newValidPluginKeys).setOffset(nextOffset);
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);

          } else {
            //if the user decides to install a plugin
            JsonObject plugin = validPluginKeys.get(chosen-1);
            JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosen-1));
            ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
            if (compatibleFiles.isEmpty()) {
              new Log(sender, state, "noFiles").log();
            } else if (compatibleFiles.size() == 1) {
              String title = plugin.get("title").getAsString();
              new Log(sender, state, "start").setPluginName(title).log();

              HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, compatibleFiles.get(0).getAsJsonObject());
              ArrayList<Boolean> installed = new ArrayList<>();

              if (!dependencies.isEmpty()) {
                new Log(sender, state, "installingDep").setPluginName(title).log();
                for (JsonArray plugins : dependencies.values()) {
                  if (plugins.isEmpty()) continue;
                  if (!installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray()))
                    new Log(sender, state, "installed").setFileName(plugins.get(0).getAsJsonObject().get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
                }
                new Log(sender, state, "installedDep").log();
              }

              installed.add(installModrinthPlugin(sender, state, compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray()));
              if (!installed.contains(false))
                new Log(sender, state, "finish").setPluginName(title).log();

            } else {
              new Log(sender, state, "pluginSelect").log();
              int i = 1;
              for (JsonElement je : compatibleFiles) {
                JsonObject jo = je.getAsJsonObject();
                chooseableFiles.add(jo);
                TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+validPluginKeys.get(chosen-1).get("project_type").getAsString()+"/"+validPluginKeys.get(chosen-1).get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                projectName.setUnderlined(true);
                sender.spigot().sendMessage(projectName);
                i++;
              }

              ChatParams newParams = new ChatParams(sender, "pluginFileSelect").setChooseableFiles(chooseableFiles).setPlugin(plugin);
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", newParams);
              return;
            }
            response.remove(name);
          }
          break;
        }

        case "deletePlugin": {
          DeleteQueue deleteQueue = params.getDeleteQueue();
          ArrayList<PluginData> toDeleteEval = params.getToDeleteEval();
          String pluginName = toDeleteEval.get(0).getName();
          List<PluginData> whatDepends = Plugins.getWhatDependsOn(pluginName);

          boolean deleteConfig;
          if (message.equals("y")) deleteConfig = true;
          else if (message.equals("n")) deleteConfig = false;
          else {
            new Log(sender, state, "confirm").log();
            return;
          }

          for (PluginData data : whatDepends) {
            if (!toDeleteEval.contains(data) && deleteQueue.isQueued(data.getName()))
              toDeleteEval.add(data);
          }

          if (toDeleteEval.size() == 1) {
            deleteQueue.addPlugin(pluginName, deleteConfig);
            deleteQueue.executeDelete();
            response.remove(name);
          } else {
            deleteQueue.addPlugin(pluginName, deleteConfig);
            String[] names = new String[whatDepends.size()];
            for (int i = 0; i < whatDepends.size(); i++) names[i] = whatDepends.get(i).getName();

            new Log(sender, "deletePlugin.dependsOn.0").setPluginNames(names).setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.1").setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.2").setPluginNames(names).setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.3").log();
            ChatParams newParams = new ChatParams(sender, "pluginsDeleteEval").setDeleteQueue(deleteQueue).setToDeleteEval(new ArrayList<>(whatDepends));
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);
          }
          break;
        }

        case "pluginsDeleteEval": {
          ArrayList<PluginData> deleteEval = params.getToDeleteEval();
          DeleteQueue deleteQueue = params.getDeleteQueue();
          Boolean deleteConfig = params.getDeleteConfigs();
          String pluginName = deleteEval.get(0).getName();
          List<PluginData> whatDependsOn = Plugins.getWhatDependsOn(pluginName);

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          int chosen;
          try {
            chosen = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, state, "NAN").log();
            return;
          }
          if (chosen < 1 || chosen > 3) {
            new Log(sender, state, "NAN").log();
            return;
          }

          if (chosen == 3) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          if (chosen == 1) deleteEval.removeAll(whatDependsOn);
          if (chosen == 2) {
            ArrayList<PluginData> toAppend = new ArrayList<>();
            dependLoop:
            for (PluginData data : whatDependsOn) {
              for (PluginData evalData : deleteEval) {
                if (evalData.getName().equals(data.getName())) continue dependLoop;
              }
              toAppend.add(data);
            }
            deleteEval.addAll(toAppend);
          }

          if (deleteConfig != null)
            deleteQueue.addPlugin(pluginName, deleteConfig);
          else {
            new Log(sender, state, "deleteConfig.0").setPluginName(pluginName).log();
            new Log(sender, state, "deleteConfig.1").log();
            ChatParams newParams = new ChatParams(sender, "deletePlugin").setDeleteQueue(deleteQueue).setToDeleteEval(deleteEval);
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);
            return;
          }

          deleteEval.remove(0);

          //checks if plugins to be evaluated have config folders
          ArrayList<PluginData> newEvals = new ArrayList<>();
          for (int i = 0; i < deleteEval.size(); i++) {
            String evalName = deleteEval.get(i).getName();
            if (!new File(pluginFolder.getAbsolutePath()+File.separator+evalName).exists()) {
              deleteEval.remove(i);
              newEvals.addAll(Plugins.getWhatDependsOn(evalName));
              deleteQueue.addPlugin(evalName, false);
            }
          }

          deleteEval.addAll(newEvals);

          if (deleteEval.isEmpty()) {
            deleteQueue.executeDelete();
            response.remove(name);
          } else {
            ChatParams newParams = new ChatParams(sender, "deletePlugin").setDeleteQueue(deleteQueue).setToDeleteEval(deleteEval);
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);

            String[] names = new String[whatDependsOn.size()];
            for (int i = 0; i < whatDependsOn.size(); i++)
              names[i] = whatDependsOn.get(i).getName();

            new Log(sender, "deletePlugin.dependsOn.0").setPluginNames(names).setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.1").setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.2").setPluginNames(names).setPluginName(pluginName).log();
            new Log(sender, "deletePlugin.dependsOn.3").log();
          }
          break;
        }

        case "terminal": {
          PathHolder pathHolder = position.get(name);
          new Log(sender, state, "path").setFilePath(pathHolder.getRelativePath());

          if (message.equals("help")) {
            new Log(sender, "terminal.help.help").log();
            new Log(sender, "terminal.help.exit").log();
            new Log(sender, "terminal.help.say").log();
            new Log(sender, "terminal.help.ls").log();
            new Log(sender, "terminal.help.cd").log();
          }
          if (message.equals("exit")) {
            new Log(sender, state, "exit").log();
            response.remove(name);
          }
          if (message.startsWith("say")) {
            if (sender instanceof Player player) {
              String string = "<"+player.getName()+"> "+message.substring(3).trim();
              for (Player onlinePlayer : Bukkit.getOnlinePlayers())
                onlinePlayer.sendMessage(string);
              Bukkit.getConsoleSender().sendMessage(string);
            }
          }
          if (message.equals("ls")) {
            try {
              List<Path> paths = Files.list(Path.of(position.get(name).getCurrentPath())).toList();
              StringBuilder files = new StringBuilder();
              int length = position.get(name).getServerPath().length();
              for (Path path : paths)
                files.append(path.toString().substring(length+1)).append("\n");
              sender.sendMessage(ChatColor.AQUA+files.toString());
            } catch (Exception e) {
              new Log(sender, state, "ls.error").setException(e).log();
            }
          }
          if (message.startsWith("cd")) {
            pathHolder.setCurrentPath(pathHolder.getCurrentPath()+File.separator+message.split(" ")[1]);
          }
          position.put(name, pathHolder);
          break;
        }

        default: {
          new Log(sender, "exceptions.stateNotFound").setState(state).log();
          response.remove(name);
        }
        }
      } catch (Exception e) {
        response.remove(name);
      }
    }
  }.init(name, message)).start();
}

}