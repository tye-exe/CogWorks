package me.tye.cogworks.commands;

import me.tye.cogworks.FileGui;
import me.tye.cogworks.util.ChatParams;
import me.tye.cogworks.util.FileData;
import me.tye.cogworks.util.Log;
import me.tye.cogworks.util.PathHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.nio.file.Path;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.FileGui.*;
import static me.tye.cogworks.util.Util.plugin;


public class FileCommand implements CommandExecutor {

@Override
public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
  if (!sender.hasPermission("cogworks.file.nav")) return true;
  if (args.length == 1 && args[0].equals("chat")) {
    String serverFolder = Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().getParent().toString();
    if (sender instanceof Player) FileGui.position.put(sender.getName(), new PathHolder(serverFolder, serverFolder));
    else FileGui.position.put("~", new PathHolder(serverFolder, serverFolder));

    ChatParams newParams = new ChatParams(sender, "terminal");
    if (sender instanceof Player) response.put(sender.getName(), newParams);
    else response.put("~", newParams);

    new Log(sender, "terminal.init.0").log();
    new Log(sender, "terminal.init.1").log();
    new Log(sender, "terminal.WIP").log();
    new Log(sender, "terminal.path").setFilePath(position.get("~").getRelativePath()).log();

  } else if (args.length == 0 || args[0].equals("gui")) {
    if (sender instanceof Player player) {
      String serverFolder = Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().getParent().toString();
      FileGui.position.put(player.getName(), new PathHolder(serverFolder, serverFolder));
      fileData.put(player.getUniqueId(), new FileData(1, null, 1, false));
      open(player);
    } else {
      new Log(sender, "info.onlyPlayersFile");

      String serverFolder = Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().getParent().toString();
      FileGui.position.put("~", new PathHolder(serverFolder, serverFolder));
      response.put("~", new ChatParams(sender, "terminal"));

      new Log(sender, "terminal.init.0").log();
      new Log(sender, "terminal.init.1").log();
      new Log(sender, "terminal.WIP").log();
      new Log(sender, "terminal.path").setFilePath(position.get("~").getRelativePath()).log();
    }

  } else {
    new Log(sender, "help.file.help").log();
    new Log(sender, "help.file.chat").log();
    new Log(sender, "help.file.gui").log();
  }
  return true;
}


}