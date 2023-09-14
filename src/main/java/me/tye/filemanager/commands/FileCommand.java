package me.tye.filemanager.commands;

import me.tye.filemanager.FileGui;
import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.FileData;
import me.tye.filemanager.util.PathHolder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import static me.tye.filemanager.ChatManager.params;
import static me.tye.filemanager.ChatManager.responses;
import static me.tye.filemanager.FileGui.fileData;
import static me.tye.filemanager.FileGui.openFolder;
import static me.tye.filemanager.FileManager.log;


public class FileCommand implements CommandExecutor {
    //TODO: redirect to terminal if you try to use the gui when in console.
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("chat")) {
            String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
            if (sender instanceof Player) FileGui.position.put(sender.getName(), new PathHolder(serverFolder, serverFolder));
            else FileGui.position.put("~", new PathHolder(serverFolder, serverFolder));
            if (sender instanceof Player) responses.put(sender.getName(), "Terminal");
            else responses.put("~", "Terminal");
            if (sender instanceof Player) params.put(sender.getName(), List.of(sender));
            else params.put("~", List.of(sender));

            sender.sendMessage(ChatColor.GREEN+"You've entered the file manager terminal.\nType \"help\" in chat for help or \"exit\" to leave the terminal.\n");
            sender.sendMessage(ChatColor.RED + "WARNING: THIS A VERY MUCH A WIP AND NOT YET IMPLEMENTED");
            sender.sendMessage(ChatColor.GOLD+"-----------------"+ChatColor.BLUE+new PathHolder(serverFolder, serverFolder).getRelativePath()+ChatColor.GOLD+" $");

        } else if (args.length == 0 || args[0].equals("gui")) {
            if (sender instanceof Player player) {
                String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
                FileGui.position.put(player.getName(), new PathHolder(serverFolder, serverFolder));
                fileData.put(player.getUniqueId(), new FileData(1, 1, null, 1));
                openFolder(player);
            } else {
                log(null, sender, Level.WARNING, "This command is only available to online players.");
            }

        } else {
            sender.sendMessage(ChatColor.GREEN+"/file help - Shows this list."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                    "/file chat - (WIP) Turns your chat into a mock command line which lets you interact with files on the server."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                    "/file gui - Opens an inventory that lets you interact with the files on the server visually.");
        }
        return true;
    }


}