package me.tye.cogworks.commands;

import me.tye.cogworks.FileGui;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.FileData;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PathHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.FileGui.*;


public class FileCommand implements CommandExecutor {

@Override
public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
  if (!sender.hasPermission("cogworks.file.nav")) return true;

  if (args.length == 1 && args[0].equals("chat")) {
    if (sender instanceof Player) FileGui.position.put(sender.getName(), new PathHolder());
    else FileGui.position.put("~", new PathHolder());

    ChatParams params = new ChatParams(sender, "terminal");
    if (sender instanceof Player) response.put(sender.getName(), params);
    else response.put("~", params);

    new Log(sender, "terminal.init").log();
    new Log(sender, "terminal.WIP").log();
    new Log(sender, "terminal.path").setFilePath(position.get("~").getRelativePath()).log();

  } else if (args.length == 0 || args[0].equals("gui")) {
    if (sender instanceof Player player) {
      FileGui.position.put(player.getName(), new PathHolder());
      fileData.put(player.getUniqueId(), new FileData(1, null, 1, false));
      open(player);

    } else {
      new Log(sender, "terminal.noGui").log();

      FileGui.position.put("~", new PathHolder());
      response.put("~", new ChatParams(sender, "terminal"));

      new Log(sender, "terminal.init").log();
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