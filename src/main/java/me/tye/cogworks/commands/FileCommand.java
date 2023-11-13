package me.tye.cogworks.commands;

import me.tye.cogworks.FileGui;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.FileData;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PathHolder;
import me.tye.cogworks.util.customObjects.dataClasses.DeletePending;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static me.tye.cogworks.FileGui.*;
import static me.tye.cogworks.util.Util.setResponse;


public class FileCommand implements CommandExecutor {

@Override
public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (args.length == 1 && args[0].equals("chat")) {
    if (!sender.hasPermission("cogworks.file.nav")) {
      return true;
    }

    chatBasedExplorer(sender);
    return true;
  }

  if (args.length == 0 || args[0].equals("gui")) {
    if (!sender.hasPermission("cogworks.file.nav")) {
      return true;
    }

    if (sender instanceof Player player) {
      FileGui.position.put(player.getName(), new PathHolder());
      fileData.put(player.getUniqueId(), new FileData(1, null, 1, false));
      open(player);

    } else {
      new Log(sender, "terminal.noGui").log();
      chatBasedExplorer(sender);
    }

    return true;
  }

  if (args.length >= 2 && args[0].equals("recover")) {
    if (!sender.hasPermission("cogworks.file.rec")) {
      return true;
    }

    try {
      DeletePending delete = DeletePending.getDelete(args[1]);
      if (delete == null) {
        new Log(sender, "recover.noneMatching").log();
        return true;
      }

      //if no path was provided, then the file is restored to the server folder.
      Path restorePath = delete.getFilePath().getFileName();
      if (args.length >= 3) {
        restorePath = Path.of(args[2]);
      }

      delete.restore(restorePath);

      new Log(sender, "recover.fileRecovered").setFileName(restorePath.getFileName().toString()).setFilePath(restorePath.toString()).log();

    } catch (IOException e) {
      new Log(sender, "recover.readFail").setException(e).log();
    } catch (InvalidPathException e) {
      new Log(sender, "recover.invalidPath").setException(e).log();
    }
    return true;
  }

  new Log(sender, "help.file.help").log();

  if (!sender.hasPermission("cogworks.file.nav")) {
    new Log(sender, "help.file.chat").log();
    new Log(sender, "help.file.gui").log();
  }

  if (sender.hasPermission("cogworks.file.rec")) {
    new Log(sender, "help.file.recover").log();
  }

  return true;
}

private static void chatBasedExplorer(CommandSender sender) {
  if (sender instanceof Player) {
    FileGui.position.put(sender.getName(), new PathHolder());
  }
  else {
    FileGui.position.put("~", new PathHolder());
  }

  setResponse(sender, new ChatParams(sender, "terminal"));

  new Log(sender, "terminal.init").log();
  new Log(sender, "terminal.WIP").log();
  new Log(sender, "terminal.path").setFilePath(position.get("~").getRelativePath()).log();
}


}