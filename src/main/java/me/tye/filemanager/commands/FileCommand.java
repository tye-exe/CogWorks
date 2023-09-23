package me.tye.filemanager.commands;

import me.tye.filemanager.FileGui;
import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.ChatParams;
import me.tye.filemanager.util.FileData;
import me.tye.filemanager.util.PathHolder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.nio.file.Path;

import static me.tye.filemanager.ChatManager.response;
import static me.tye.filemanager.FileGui.fileData;
import static me.tye.filemanager.FileGui.openFolder;


public class FileCommand implements CommandExecutor {
    //TODO: redirect to terminal if you try to use the gui when in console.
    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (!sender.hasPermission("fileman.file.nav")) return true;
        if (args.length == 1 && args[0].equals("chat")) {
            String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
            if (sender instanceof Player) FileGui.position.put(sender.getName(), new PathHolder(serverFolder, serverFolder));
            else FileGui.position.put("~", new PathHolder(serverFolder, serverFolder));

            ChatParams newParams = new ChatParams(sender, "Terminal");
            if (sender instanceof Player) response.put(sender.getName(), newParams);
            else response.put("~", newParams);

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
                sender.sendMessage(ChatColor.YELLOW + "This command is only available to online players, being redirected to terminal.");

                String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
                FileGui.position.put("~", new PathHolder(serverFolder, serverFolder));
                response.put("~", new ChatParams(sender, "Terminal"));

                sender.sendMessage(ChatColor.GREEN+"You've entered the file manager terminal.\nType \"help\" in chat for help or \"exit\" to leave the terminal.\n");
                sender.sendMessage(ChatColor.RED + "WARNING: THIS A VERY MUCH A WIP AND NOT YET IMPLEMENTED");
                sender.sendMessage(ChatColor.GOLD+"-----------------"+ChatColor.BLUE+new PathHolder(serverFolder, serverFolder).getRelativePath()+ChatColor.GOLD+" $");
            }

        } else {
            sender.sendMessage(ChatColor.BLUE+"/file help -"+ChatColor.GREEN+" Shows this list."+ChatColor.GRAY+"\n" + ChatColor.BLUE +
                    "/file chat -"+ChatColor.GREEN+" (WIP) Turns your chat into a mock command line which lets you interact with files on the server."+ChatColor.GRAY+"\n" + ChatColor.BLUE +
                    "/file gui -"+ChatColor.GREEN+" Opens an inventory that lets you interact with the files on the server visually.");
        }
        return true;
    }


}