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
import java.util.logging.Level;

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.commands.PluginCommand.*;
import static me.tye.cogworks.util.Util.*;

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
          int chosenPlugin = parseNumInput(sender, state, message, name, validPluginKeys.size(), 1);
          if (chosenPlugin == -1) return;

          JsonObject plugin = validPluginKeys.get(chosenPlugin-1);
          JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosenPlugin-1));
          ArrayList<JsonObject> chooseableFiles = new ArrayList<>();

          if (compatibleFiles.isEmpty()) {
            new Log(sender, state, "noFiles").log();
            return;
          }

          if (compatibleFiles.size() == 1) {
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
            if (files.isEmpty()) {
              new Log(sender, state, "noFiles").log();
              return;
            }

            if (files.size() == 1) {
              installed.add(installModrinthPlugin(sender, state, files));
              if (!installed.contains(false))
                new Log(sender, state, "finish").setPluginName(title).log();

              // if there are more than one file for that version you get prompted to choose which one(s) to install
            } else {
              new Log(sender, state, "versionFiles").log();

              int i = 1;
              for (JsonElement je : files) {
                JsonObject jo = je.getAsJsonObject();
                chooseableFiles.add(jo);
                TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                sender.spigot().sendMessage(projectName);
                i++;
              }
              ChatParams newParams = new ChatParams(sender, "pluginFileSelect").setChooseable(chooseableFiles).setPlugin(plugin).setPluginVersion(compatibleFiles.get(0).getAsJsonObject());
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", newParams);
              return;
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
            ChatParams newParams = new ChatParams(sender, "pluginVersionSelect").setChooseable(chooseableFiles).setPlugin(plugin);
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);
            return;
          }

          response.remove(name);
          break;
        }

        case "pluginVersionSelect": {
          ArrayList<JsonObject> chooseableFiles = params.getChooseableFiles();
          JsonObject plugin = params.getPlugin();
          int chosenVersion = parseNumInput(sender, state, message, name, chooseableFiles.size(), 1);
          if (chosenVersion == -1) return;

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

          JsonArray files = chosen.get("files").getAsJsonArray();
          if (files.isEmpty()) {
            new Log(sender, state, "noFiles").log();
            return;
          }

          if (files.size() == 1) {
            installed.add(installModrinthPlugin(sender, state, files));
            if (!installed.contains(false))
              new Log(sender, state, "finish").setPluginName(title).log();

            // if there are more than one file for that version you get prompted to choose which one(s) to install
          } else {
            new Log(sender, state, "versionFiles").log();

            int i = 1;
            for (JsonElement je : files) {
              JsonObject jo = je.getAsJsonObject();
              chooseableFiles.add(jo);
              TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
              projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
              sender.spigot().sendMessage(projectName);
              i++;
            }
            ChatParams newParams = new ChatParams(sender, "pluginFileSelect").setChooseable(chooseableFiles).setPlugin(plugin).setPluginVersion(chosen);
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
          JsonObject pluginVersion = params.getPluginVersion();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          JsonArray toInstall = new JsonArray();

          //allow multiple inpuits for parse
          for (String file : message.split(",")) {
            file = file.strip();
            if (file.isEmpty()) continue;

            try {
              toInstall.add(chooseableFiles.get(Integer.parseInt(file)-1));
            } catch (NumberFormatException e) {
              new Log(sender, state, "NAN").log();
            }
          }

          if (toInstall.isEmpty()) {
            new Log(sender, state, "noneSelected").log();
            return;
          }

          HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, pluginVersion);
          ArrayList<Boolean> installed = new ArrayList<>();
          String title = plugin.get("title").getAsString();

          if (!dependencies.isEmpty()) {
            new Log(sender, state, "installingDep").setPluginName(title).log();
            for (JsonArray plugins : dependencies.values()) {
              if (plugins.isEmpty()) continue;
              if (!installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray()))
                new Log(sender, state, "installed").setFileName(plugins.get(0).getAsJsonObject().get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
            }
            new Log(sender, state, "installedDep").log();
          }


          ArrayList<String> fileNames = new ArrayList<>();
          for (JsonElement file : toInstall) {
            String fileName = file.getAsJsonObject().get("filename").getAsString();
            fileNames.add(fileName);
          }

          new Log(sender, state, "start").setFileNames(fileNames).log();

          for (JsonElement file : toInstall) {
            JsonArray array = new JsonArray();
            array.add(file);
            installed.add(installModrinthPlugin(sender, state, array));
          }

          if (fileNames.size() != installed.size()) {
            new Log(sender, state, "finish").setFileNames(fileNames).log();
            new Log(sender, state, "restart").setFileNames(fileNames).log();
          } else {
            //removes the filenames that didn't install successfully from the log
            for (int i = 0; i < installed.size(); i++) {
              if (!installed.get(i)) fileNames.remove(i);
            }
          }

          new Log(sender, state, "finish").setFileNames(fileNames).log();
          new Log(sender, state, "restart").setFileNames(fileNames).log();

          response.remove(name);
          return;
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

              ChatParams newParams = new ChatParams(sender, "pluginVersionSelect").setChooseable(chooseableFiles).setPlugin(plugin);
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

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          boolean deleteConfig;
          if (message.equals("y")) deleteConfig = true;
          else if (message.equals("n")) deleteConfig = false;
          else {
            new Log(sender, state, "confirm").log();
            return;
          }

          //adds plugins that depend on the current one being deleted to the queue to be evald
          for (PluginData data : whatDepends) {
            if (!toDeleteEval.contains(data) && deleteQueue.isQueued(data.getName())) {
              toDeleteEval.add(data);
            }
          }

          if (toDeleteEval.size() <= 1) {
            deleteQueue.addPlugin(pluginName, deleteConfig);
            deleteQueue.executeDelete();
            response.remove(name);
          } else {
            deleteQueue.addPlugin(pluginName, deleteConfig);
            toDeleteEval.remove(0);

            if (!whatDepends.isEmpty()) {
              String[] names = new String[whatDepends.size()];
              for (int i = 0; i < whatDepends.size(); i++) names[i] = whatDepends.get(i).getName();

              new Log(sender, "deletePlugin.dependsOn").setPluginNames(names).setPluginName(pluginName).log();
              ChatParams newParams = new ChatParams(sender, "pluginsDeleteEval").setDeleteQueue(deleteQueue).setToDeleteEval(toDeleteEval);
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", newParams);
            } else {


              new Log(sender, "deletePlugin.deleteConfig").setPluginName(toDeleteEval.get(0).getName()).log();
              ChatParams newParams = new ChatParams(sender, "deletePlugin").setDeleteQueue(deleteQueue).setToDeleteEval(toDeleteEval);
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", params);
            }
          }
          break;
        }

        case "pluginsDeleteEval": {
          ArrayList<PluginData> deleteEval = params.getToDeleteEval();
          DeleteQueue deleteQueue = params.getDeleteQueue();
          Boolean deleteConfig = params.getDeleteConfigs();
          String pluginName = deleteEval.get(0).getName();
          List<PluginData> whatDependsOn = Plugins.getWhatDependsOn(pluginName);

          int chosen = parseNumInput(sender, state, message, name, 3, 1);
          if (chosen == -1) return;

          if (chosen == 3) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          if (chosen == 1) {
            //uses the plugin names as the objects don't match.
            for (int i = 1; i < deleteEval.size(); i++) {
              for (PluginData dependsData : whatDependsOn) {
                if (deleteEval.get(i).getName().equals(dependsData.getName())) deleteEval.remove(i);
              }
            }
          }

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
            if (Plugins.hasConfigFolder(pluginName)) {
              new Log(sender, state, "deleteConfig").setPluginName(pluginName).log();
              ChatParams newParams = new ChatParams(sender, "deletePlugin").setDeleteQueue(deleteQueue).setToDeleteEval(deleteEval);
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", newParams);
              return;
            }
          }

          deleteEval.remove(0);

          //sets the plugins that don't have config folder to deleteConfig false.
          for (int i = 0; i < deleteEval.size();i++) {
            String newPluginName = deleteEval.get(i).getName();
            if (!Plugins.hasConfigFolder(newPluginName)) {
              deleteQueue.addPlugin(newPluginName, false);
              deleteEval.addAll(Plugins.getWhatDependsOn(newPluginName));
              deleteEval.remove(i);
              i--;
              new Log(sender, state, "noConfigsFound").setPluginName(newPluginName).log();
            }
          }

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

            new Log(sender, "deletePlugin.dependsOn").setPluginNames(names).setPluginName(pluginName).log();
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
        new Log("exceptions.chatError", Level.WARNING, e).log();
        response.remove(name);
      }
    }
  }.init(name, message)).start();
}

}