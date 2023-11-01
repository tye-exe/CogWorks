package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.customObjects.*;
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
import static me.tye.cogworks.util.Util.clearResponse;
import static me.tye.cogworks.util.Util.parseNumInput;

public class ChatManager implements Listener {

public static HashMap<String,ChatParams> response = new HashMap<>();

@EventHandler
public void onPlayerMessage(AsyncPlayerChatEvent e) {
  if (response.containsKey(e.getPlayer().getName()))
    e.setCancelled(true);
  checks(e.getPlayer().getName(), e.getMessage());
  if (response.containsKey(e.getPlayer().getName()))
    e.setCancelled(true);
}

@EventHandler
public void onConsoleMessage(ServerCommandEvent e) {
  if (response.containsKey("~"))
    e.setCancelled(true);
  checks("~", e.getCommand());
  if (response.containsKey("~"))
    e.setCancelled(true);
}

public static void checks(String name, String message) {
  if (!response.containsKey(name))
    return;
  if (message.startsWith("plugin"))
    return;

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
        case "pluginSelect" -> {
          PluginInstall pluginInstall = params.getPluginSearch();
          pluginInstall.setChosen(parseNumInput(sender, state, message));
          pluginInstall.execute();
        }
        case "pluginVersionSelect" -> {
          PluginInstall install = params.getPluginInstall();

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

          //checks that the response is within the choice limits
          if (chosen > install.getVersionSize()-1 || chosen > 0) {
            new Log(sender, state, "NAN").log();
            return;
          }

          //          ArrayList<JsonObject> chooseableFiles = params.getChooseableFiles();
          //          JsonObject plugin = params.getPlugin();
          //          int chosenVersion = parseNumInput(sender, state, message, name, chooseableFiles.size(), 1);
          //          if (chosenVersion == -1)
          //            return;
          //
          //          JsonObject chosen = chooseableFiles.get(chosenVersion).getAsJsonObject();
          //          String title = plugin.get("title").getAsString();
          //          new Log(sender, state, "start").setPluginName(title).log();
          //
          //
          //          JsonArray files = chosen.get("files").getAsJsonArray();
          //          if (files.isEmpty()) {
          //            new Log(sender, state, "noFiles").log();
          //            return;
          //          }
          //
          //          if (files.size() == 1) {
          //            Plugins.installModrinthDependencies(sender, state, chosen, title);
          //            if (Plugins.installModrinthPlugin(sender, state, files))
          //              new Log(sender, state, "finish").setPluginName(title).log();
          //
          //            // if there are more than one file for that version you get prompted to choose which one(s) to install
          //          } else {
          //            new Log(sender, state, "versionFiles").log();
          //
          //            int i = 1;
          //            for (JsonElement je : files) {
          //              JsonObject jo = je.getAsJsonObject();
          //              chooseableFiles.add(jo);
          //              TextComponent projectName = new TextComponent(i+": "+(jo.get("primary").getAsBoolean() ? net.md_5.bungee.api.ChatColor.BLUE+getLang("pluginFileSelect.primary")+net.md_5.bungee.api.ChatColor.GREEN+" " : "")+jo.get("filename").getAsString());
          //              projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
          //              sender.spigot().sendMessage(projectName);
          //              i++;
          //            }
          //            params.reset(sender, "pluginFileSelect").setChooseable(chooseableFiles).setPlugin(plugin).setPluginVersion(chosen);
          //            if (sender instanceof Player)
          //              response.put(sender.getName(), params);
          //            else
          //              response.put("~", params);
          //            return;
          //          }
          //
          //          response.remove(name);
        }
        case "pluginFileSelect" -> {
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
            if (file.isEmpty())
              continue;

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

          Plugins.installModrinthDependencies(sender, state, pluginVersion, plugin.get("title").getAsString());

          ArrayList<String> fileNames = new ArrayList<>();
          for (JsonElement file : toInstall) {
            String fileName = file.getAsJsonObject().get("filename").getAsString();
            fileNames.add(fileName);
          }

          new Log(sender, state, "start").setFileNames(fileNames).log();

          ArrayList<Boolean> installed = new ArrayList<>();
          for (JsonElement file : toInstall) {
            JsonArray array = new JsonArray();
            array.add(file);
            installed.add(Plugins.installModrinthPlugin(sender, state, array));
          }

          //removes the filenames that didn't install successfully from the log
          for (int i = 0; i < installed.size(); i++) {
            if (!installed.get(i)) {
              fileNames.remove(i);
              installed.remove(i);
              i--;
            }
          }

          new Log(sender, state, "finish").setFileNames(fileNames).log();
          response.remove(name);
        }
        case "pluginBrowse" -> {
          PluginBrowse browse = params.getPluginBrowse();

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

          //checks that the response is within the choice limits
          if (chosen > browse.getMaxChoice() || (browse.getOffset() <= 0 && chosen < 1) || (browse.getOffset() > 0 && chosen < 0)) {
            new Log(sender, state, "NAN").log();
            return;
          }

          //checks if the user decided to scroll up or down
          if (chosen == 0 || chosen == browse.getMaxChoice()) {
            browse.execute(chosen);
            return;
          }

          clearResponse(sender);
          browse.install(chosen);
        }

        case "deletePluginConfig" -> {
          DeleteQueue deleteQueue = params.getDeleteQueue();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          if (message.equals("y")) {
            deleteQueue.setCurrentEvalDeleteConfig(true);

          } else if (message.equals("n")) {
            deleteQueue.setCurrentEvalDeleteConfig(false);

          } else {
            new Log(sender, state, "confirm").log();
            return;
          }

          deleteQueue.evaluatePlugins();
        }
        case "deletePluginsDepend" -> {
          DeleteQueue deleteQueue = params.getDeleteQueue();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, state, "quit").log();
            return;
          }

          if (message.equals("y")) {
            deleteQueue.setCurrentEvalDeleteDepends(true);

          } else if (message.equals("n")) {
            deleteQueue.setCurrentEvalDeleteDepends(false);

          } else {
            new Log(sender, state, "confirm").log();
            return;
          }

          deleteQueue.evaluatePlugins();
        }

        case "terminal" -> {
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
        }
        default -> {
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