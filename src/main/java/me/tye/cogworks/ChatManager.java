package me.tye.cogworks;

import me.tye.cogworks.operationHandlers.DeleteQueue;
import me.tye.cogworks.operationHandlers.PluginBrowse;
import me.tye.cogworks.operationHandlers.PluginInstall;
import me.tye.cogworks.operationHandlers.PluginSearch;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PathHolder;
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
          PluginSearch search = params.getPluginSearch();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, "pluginInstall.quit").log();
            return;
          }

          int chosen;

          try {
            chosen = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, "pluginInstall.NAN").setChosen(message).log();
            return;
          }

          //checks that the response is within the choice limits
          if (chosen > search.getKeysSize()-1 || chosen < 0) {
            new Log(sender, "pluginInstall.NAN").setChosen(String.valueOf(chosen)).log();
            return;
          }


          search.selectPlugin(chosen);
        }

        case "pluginVersionSelect" -> {
          PluginInstall install = params.getPluginInstall();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, "pluginInstall.quit").log();
            return;
          }

          int chosen;

          try {
            chosen = Integer.parseInt(message);
          } catch (NumberFormatException e) {
            new Log(sender, "pluginInstall.NAN").setChosen(message).log();
            return;
          }

          //checks that the response is within the choice limits
          if (chosen > install.getVersionSize()-1 || chosen < 0) {
            new Log(sender, "pluginInstall.NAN").setChosen(String.valueOf(chosen)).log();
            return;
          }

          install.setChosenVersion(chosen-1);
          install.execute();
        }

        case "pluginFileSelect" -> {
          PluginInstall install = params.getPluginInstall();

          if (message.equals("q")) {
            response.remove(name);
            new Log(sender, "pluginInstall.quit").log();
            return;
          }

          ArrayList<Integer> chosenFiles = new ArrayList<>();

          for (String choice : message.split(",")) {
            int chosen;

            try {
              chosen = Integer.parseInt(choice.strip())-1;
            } catch (NumberFormatException e) {
              new Log(sender, "pluginInstall.NAN").setChosen(choice.strip()).log();
              return;
            }

            if (chosen < 1 && chosen > install.getFilesAmount()-1) {
              new Log(sender, "pluginInstall.NAN").setChosen(choice.strip()).log();
              return;
            }
          }

          install.setChosenFiles(chosenFiles);
          install.execute();
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