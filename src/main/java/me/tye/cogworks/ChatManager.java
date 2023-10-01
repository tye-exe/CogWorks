package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.*;
import me.tye.cogworks.util.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.CogWorks.log;
import static me.tye.cogworks.commands.PluginCommand.*;

public class ChatManager implements Listener {

    public static HashMap<String, ChatParams> response = new HashMap<>();

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
        String state = params.getModifier();
        if (message.startsWith("plugin")) return;
        if (state.equals("DeletePlugin")) {
            CommandSender sender = params.getSender();

            boolean deleteConfigs;
            if (message.equals("y")) deleteConfigs = true;
            else if (message.equals("n")) deleteConfigs = false;
            else {
                new Log(sender, state, "confirm").log();
                return;
            }
            //deletePlugins method needs to be synchronous.
            Bukkit.getScheduler().scheduleSyncDelayedTask(JavaPlugin.getPlugin(CogWorks.class), () -> {
                deletePlugin(sender, state, params.getPluginName(), deleteConfigs);
                response.remove(name);
            });
        }
        else {
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
                    if (state.equals("PluginSelect")) {
                        HashMap<JsonObject, JsonArray> validPlugins = params.getValidPlugins();
                        ArrayList<JsonObject> validPluginKeys = params.getValidPluginKeys();
                        CommandSender sender = params.getSender();
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

                        JsonObject plugin = validPluginKeys.get(chosenPlugin - 1);
                        JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosenPlugin - 1));
                        ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
                        if (compatibleFiles.isEmpty()) {
                            new Log(sender, state, "noFiles").log();
                        } else if (compatibleFiles.size() == 1) {

                            String title = plugin.get("title").getAsString();
                            new Log(sender, state, "start").setPluginName(title).log();

                            HashMap<String, JsonArray> dependencies  = getModrinthDependencies(sender, state, compatibleFiles.get(0).getAsJsonObject());
                            if (!dependencies.isEmpty()) {
                                new Log(sender, state, "installingDep").setPluginName(title).log();
                                for (JsonArray plugins : dependencies.values()) {
                                    if (plugins.isEmpty()) continue;
                                    installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray());
                                }
                                new Log(sender, state, "installedDep").log();
                            }

                            installModrinthPlugin(sender, state, compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray());
                            new Log(sender, state, "finish").setPluginName(title).log();

                        } else {
                            new Log(sender, state, "pluginSelect").log();
                            int i = 1;
                            for (JsonElement je : compatibleFiles) {
                                JsonObject jo = je.getAsJsonObject();
                                chooseableFiles.add(jo);
                                TextComponent projectName = new TextComponent(i + ": " + jo.get("name").getAsString() + " : " + jo.get("version_number").getAsString());
                                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/" + validPluginKeys.get(chosenPlugin - 1).get("project_type").getAsString() + "/" + validPluginKeys.get(chosenPlugin - 1).get("slug").getAsString() + "/version/" + jo.get("version_number").getAsString())));
                                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                projectName.setUnderlined(true);
                                sender.spigot().sendMessage(projectName);
                                i++;
                            }
                            ChatParams newParams = new ChatParams(sender, "PluginFileSelect").setChooseableFiles(chooseableFiles).setPlugin(plugin);
                            if (sender instanceof Player) response.put(sender.getName(), newParams);
                            else response.put("~", newParams);
                            return;
                        }
                        response.remove(name);
                    }
                    if (state.equals("PluginFileSelect")) {
                        ArrayList<JsonObject> chooseableFiles = params.getChooseableFiles();
                        JsonObject plugin = params.getPlugin();
                        CommandSender sender = params.getSender();
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

                        HashMap<String, JsonArray> dependencies  = getModrinthDependencies(sender, state, chosen);
                        if (!dependencies.isEmpty()) {
                            new Log(sender, state, "installingDep").setPluginName(title).log();
                            for (JsonArray plugins : dependencies.values()) {
                                if (plugins.isEmpty()) continue;
                                installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray());
                            }
                            new Log(sender, state, "installedDep").log();
                        }

                        installModrinthPlugin(sender, state, chosen.get("files").getAsJsonArray());
                        new Log(sender, state, "finish").setPluginName(title).log();
                        response.remove(name);
                    }
                    if (state.equals("PluginBrowse")) {
                        CommandSender sender = params.getSender();
                        HashMap<JsonObject, JsonArray> validPlugins = params.getValidPlugins();
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
                        if (chosen > validPluginKeys.size() + 1 || (offset <= 0 && chosen < 1) || (offset > 0 && chosen < 0)) {
                            new Log(sender, state, "NAN").log();
                            return;
                        }

                        Integer nextOffset = null;
                        if (chosen == 0) nextOffset = Math.max(offset - 10, 0);
                        if (chosen == validPluginKeys.size() + 1) nextOffset = offset + 10;

                        if (nextOffset != null) {
                            //if the user chooses to scroll
                            ModrinthSearch modrinthSearch = modrinthBrowse(sender, state, nextOffset);
                            ArrayList<JsonObject> newValidPluginKeys = modrinthSearch.getValidPluginKeys();
                            HashMap<JsonObject, JsonArray> newValidPlugins = modrinthSearch.getValidPlugins();
                            if (newValidPluginKeys.isEmpty() || newValidPlugins.isEmpty()) return;

                            new Log(sender, "PluginBrowse.pluginBrowse").log();
                            int i = 0;

                            if (nextOffset >= 1) {
                                sender.sendMessage(ChatColor.GREEN + String.valueOf(i) + ": ^");
                            }

                            while (newValidPluginKeys.size() > i) {
                                JsonObject project = newValidPluginKeys.get(i);
                                TextComponent projectName = new TextComponent(i + 1 + ": " + project.get("title").getAsString());
                                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/" + project.get("project_type").getAsString() + "/" + project.get("slug").getAsString())));
                                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                projectName.setUnderlined(true);
                                sender.spigot().sendMessage(projectName);
                                i++;
                            }

                            sender.sendMessage(ChatColor.GREEN + String.valueOf(i + 1) + ": v");

                            ChatParams newParams = new ChatParams(sender, "PluginBrowse").setValidPlugins(newValidPlugins).setValidPluginKeys(newValidPluginKeys).setOffset(nextOffset);
                            if (sender instanceof Player) response.put(sender.getName(), newParams);
                            else response.put("~", newParams);

                        } else {
                            //if the user decides to install a plugin
                            JsonObject plugin = validPluginKeys.get(chosen - 1);
                            JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosen - 1));
                            ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
                            if (compatibleFiles.isEmpty()) {
                                new Log(sender, state, "noFiles").log();
                            } else if (compatibleFiles.size() == 1) {
                                String title = plugin.get("title").getAsString();
                                new Log(sender, state, "start").setPluginName(title).log();

                                HashMap<String, JsonArray> dependencies  = getModrinthDependencies(sender, state, compatibleFiles.get(0).getAsJsonObject());
                                if (!dependencies.isEmpty()) {
                                    new Log(sender, state, "installingDep").setPluginName(title).log();
                                    for (JsonArray plugins : dependencies.values()) {
                                        if (plugins.isEmpty()) continue;
                                        installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray());
                                    }
                                    new Log(sender, state, "installedDep").log();
                                }

                                installModrinthPlugin(sender, state, compatibleFiles.get(0).getAsJsonObject().get("files").getAsJsonArray());
                                new Log(sender, state, "finish").setPluginName(title).log();

                            } else {
                                new Log(sender, state, "pluginSelect").log();
                                int i = 1;
                                for (JsonElement je : compatibleFiles) {
                                    JsonObject jo = je.getAsJsonObject();
                                    chooseableFiles.add(jo);
                                    TextComponent projectName = new TextComponent(i + ": " + jo.get("name").getAsString() + " : " + jo.get("version_number").getAsString());
                                    projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/" + validPluginKeys.get(chosen - 1).get("project_type").getAsString() + "/" + validPluginKeys.get(chosen - 1).get("slug").getAsString() + "/version/" + jo.get("version_number").getAsString())));
                                    projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                    projectName.setUnderlined(true);
                                    sender.spigot().sendMessage(projectName);
                                    i++;
                                }

                                ChatParams newParams = new ChatParams(sender, "PluginFileSelect").setChooseableFiles(chooseableFiles).setPlugin(plugin);
                                if (sender instanceof Player) response.put(sender.getName(), newParams);
                                else response.put("~", newParams);
                                return;
                            }
                            response.remove(name);
                        }
                    }

                    if (state.equals("Terminal")) {
//                        CommandSender sender = params.getSender();
//                        sender.sendMessage(ChatColor.GOLD + "-----------------");
//                        sender.sendMessage(ChatColor.BLUE + position.get(name).getRelativePath() + ChatColor.GOLD + " $");
//                        if (message.equals("help")) {
//                            sender.sendMessage(ChatColor.AQUA + "help - Shows this message.\n" +
//                                    "exit - Leaves the terminal.\n" +
//                                    "say - Passes the following text into the chat like normal. The \"say\" is removed.\n" +
//                                    "ls - Lists the current folders and files that are in a folder.\n" +
//                                    "cd - Changes the current folder to the one specified. Input \"..\" to go back a folder.\n");
//                        }
//                        if (message.equals("exit")) {
//                            sender.sendMessage(ChatColor.YELLOW + "Exited terminal.");
//                            response.remove(name);
//                        }
//                        if (message.startsWith("say")) {
//                            if (sender instanceof Player player) {
//                                String string = "<" + player.getName() + "> " + message.substring(3).trim();
//                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) onlinePlayer.sendMessage(string);
//                                Bukkit.getConsoleSender().sendMessage(string);
//                            }
//                        }
//                        if (message.equals("ls")) {
//                            try {
//                                List<Path> paths = Files.list(Path.of(position.get(name).getCurrentPath())).toList();
//                                StringBuilder files = new StringBuilder();
//                                int length = position.get(name).getServerPath().length();
//                                for (Path path : paths) files.append(path.toString().substring(length + 1)).append("\n");
//                                sender.sendMessage(ChatColor.AQUA + files.toString());
//                            } catch (Exception e) {
//                                log(e, sender, Level.WARNING, "Error trying to get files from current folder.");
//                            }
//                        }
//                        if (message.startsWith("cd")) {
//                            PathHolder pathHolder = position.get(name);
//                            pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + message.split(" ")[1]);
//                        }
                    }
                }
            }.init(name, message)).start();
        }
    }

}