package me.tye.filemanager.commands.fileCommand;

import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.PathHolder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static me.tye.filemanager.commands.fileCommand.FileGui.openFolder;
import static me.tye.filemanager.events.ChatEvent.params;
import static me.tye.filemanager.events.ChatEvent.responses;


public class FileCommand implements CommandExecutor {

    public static HashMap<String, PathHolder> position = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("help")) {
            sender.sendMessage(ChatColor.GREEN+"/file help - Shows this list.\n" +
                    "/file chat - (WIP) Turns your chat into a mock command line which lets you interact with files on the server.\n" +
                    "/file gui - (WIP) Opens an inventory that lets you interact with the files on the server visually.");

        } else if (args.length == 1 && args[0].equals("chat")) {
            String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
            if (sender instanceof Player) position.put(sender.getName(), new PathHolder(serverFolder, serverFolder));
            else position.put("~", new PathHolder(serverFolder, serverFolder));
            if (sender instanceof Player) responses.put(sender.getName(), "Terminal");
            else responses.put("~", "Terminal");
            if (sender instanceof Player) params.put(sender.getName(), List.of(sender));
            else params.put("~", List.of(sender));

            sender.sendMessage(ChatColor.GREEN+"You've entered the file manager terminal.\nType \"help\" in chat for help or \"exit\" to leave the terminal.\n");
            sender.sendMessage(ChatColor.GOLD+"-----------------"+ChatColor.BLUE+new PathHolder(serverFolder, serverFolder).getRelativePath()+ChatColor.GOLD+" $");

        } else {
            if (sender instanceof Player player) {
                String serverFolder = Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString();
                position.put(player.getName(), new PathHolder(serverFolder, serverFolder));
                openFolder(player);
            } else {
                sender.sendMessage(ChatColor.RED+"This command is only available to online players.");
            }
        }
        return true;
    }
}