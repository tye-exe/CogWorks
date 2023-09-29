package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.*;
import me.tye.cogworks.util.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.exceptions.PluginExistsException;
import me.tye.cogworks.util.exceptions.PluginInstallException;
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
        String modifier = params.getModifier();
        if (message.startsWith("plugin")) return;
        //deletePlugins method needs to be synchronous.
        if (modifier.equals("DeletePluginConfigs")) {
            CommandSender sender = params.getSender();

            boolean deleteConfigs;
            if (message.equals("y")) deleteConfigs = true;
            else if (message.equals("n")) deleteConfigs = false;
            else {
                sender.sendMessage(Util.getLang("chat.confirm"));
                return;
            }

            Bukkit.getScheduler().scheduleSyncDelayedTask(JavaPlugin.getPlugin(CogWorks.class), () -> {
                try {
                    deletePlugin(params.getPluginName(), deleteConfigs);
                    sender.sendMessage(Util.getLang("chat.deletePlugin.success", "pluginName", params.getPluginName()));
                } catch (NoSuchPluginException e) {
                    log(e, sender, Level.WARNING, Util.getLang("chat.deletePlugin.noSuchPlugin", "pluginName", params.getPluginName()));
                } catch (IOException e) {
                    log(e, sender, Level.WARNING, Util.getLang("chat.deletePlugin.couldNotDel", "pluginName", params.getPluginName()));
                }
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
                    if (modifier.equals("PluginSelect")) {
                        HashMap<JsonObject, JsonArray> validPlugins = params.getValidPlugins();
                        ArrayList<JsonObject> validPluginKeys = params.getValidPluginKeys();
                        CommandSender sender = params.getSender();
                        if (message.equals("q")) {
                            response.remove(name);
                            sender.sendMessage(Util.getLang("chat.quit"));
                            return;
                        }
                        int chosenPlugin;
                        try {
                            chosenPlugin = Integer.parseInt(message);
                        } catch (NumberFormatException e) {
                            log(e, sender, Level.WARNING, Util.getLang("chat.notAListedNumber"));
                            return;
                        }
                        if (chosenPlugin > validPluginKeys.size() || chosenPlugin < 1) {
                            log(null, sender, Level.WARNING, Util.getLang("chat.notAListedNumber"));
                            return;
                        }

                        JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosenPlugin - 1));
                        ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
                        if (compatibleFiles.isEmpty()) {
                            sender.sendMessage(Util.getLang("chat.noCompatibleFile"));
                        } else if (compatibleFiles.size() == 1) {
                            JsonObject jo = compatibleFiles.get(0).getAsJsonObject();
                            JsonArray files = jo.get("files").getAsJsonArray();
                            //getModrinthDependencies(jo);
                            return;
                        } else {
                            sender.sendMessage(Util.getLang("chat.pluginSelectPrompt"));
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
                            ChatParams newParams = new ChatParams(sender, "PluginFileSelect").setChooseableFiles(chooseableFiles);
                            if (sender instanceof Player) response.put(sender.getName(), newParams);
                            else response.put("~", newParams);
                            return;
                        }
                        response.remove(name);
                    }
                    if (modifier.equals("PluginFileSelect")) {
                        ArrayList<JsonObject> chooseableFiles = params.getChooseableFiles();
                        CommandSender sender = params.getSender();
                        if (message.equals("q")) {
                            response.remove(name);
                            sender.sendMessage(Util.getLang("chat.quit"));
                            return;
                        }

                        int chosenVersion;
                        try {
                            chosenVersion = Integer.parseInt(message);
                        } catch (NumberFormatException e) {
                            log(e, sender, Level.WARNING, Util.getLang("notAListedNumber"));
                            return;
                        }
                        if (chosenVersion > chooseableFiles.size() || chosenVersion < 1) {
                            log(null, sender, Level.WARNING, Util.getLang("notAListedNumber"));
                            return;
                        }

                        JsonObject jo = chooseableFiles.get(chosenVersion - 1);
                        JsonArray files = jo.get("files").getAsJsonArray();
                        try {
                            System.out.println(getModrinthDependencies(jo.get("dependencies").getAsJsonObject()));
                        } catch (ModrinthAPIException e) {
                            throw new RuntimeException(e);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                        return;
                    }
                    if (modifier.equals("ConfirmDependencies")) {
                        JsonArray dependencies = params.getDependencies();
                        JsonArray files = params.getFiles();
                        CommandSender sender = params.getSender();
                        if (message.equals("q")) {
                            response.remove(name);
                            sender.sendMessage(Util.getLang("chat.quit"));
                            return;
                        }

                        boolean installDependencies;
                        if (message.equals("y")) installDependencies = true;
                        else if (message.equals("n")) installDependencies = false;
                        else {
                            sender.sendMessage(Util.getLang("chat.confirm"));
                            return;
                        }

                        if (installDependencies) {
                            sender.sendMessage(Util.getLang("chat.dependencyInstall.start"));
                            //getModrinthDependencies(dependencies, null, sender, false);
                            sender.sendMessage(Util.getLang("chat.dependencyInstall.finish"));
                        } else {
                            sender.sendMessage(Util.getLang("chat.dependencyInstall.skip"));
                        }

                        sender.sendMessage(Util.getLang("chat.pluginInstall.start"));
                        for (JsonElement je : files) {
                            JsonObject file = je.getAsJsonObject();
                            String fileName = file.get("filename").getAsString();
                            try {
                                installPluginURL(new URL(file.get("url").getAsString()), fileName, false);
                                sender.sendMessage(Util.getLang("chat.pluginInstall.install", "fileName", fileName));
                            } catch (MalformedURLException e) {
                                log(e, sender, Level.WARNING, Util.getLang("chat.pluginInstall.badUrl", "fileName", fileName));
                            } catch (PluginExistsException e) {
                                log(e, sender, Level.WARNING, Util.getLang("chat.pluginInstall.alreadyExists", "fileName", fileName));
                            } catch (PluginInstallException e) {
                                log(e, sender, Level.WARNING, e.getMessage());
                            } catch (IOException e) {
                                log(e, sender, Level.WARNING, Util.getLang("exceptions.badYmlAccess", "fileName", fileName));
                            }
                        }
                        sender.sendMessage(Util.getLang("chat.pluginInstall.finish"));

                        response.remove(name);
                    }
                    if (modifier.equals("PluginBrowse")) {
                        CommandSender sender = params.getSender();
                        HashMap<JsonObject, JsonArray> validPlugins = params.getValidPlugins();
                        ArrayList<JsonObject> validPluginKeys = params.getValidPluginKeys();
                        int offset = params.getOffset();
                        if (message.equals("q")) {
                            response.remove(name);
                            sender.sendMessage(Util.getLang("chat.quit"));
                            return;
                        }
                        int chosen;
                        try {
                            chosen = Integer.parseInt(message);
                        } catch (NumberFormatException e) {
                            log(e, sender, Level.WARNING, Util.getLang("chat.notAListedNumber"));
                            return;
                        }
                        if (chosen > validPluginKeys.size() + 1 || (offset <= 0 && chosen < 1) || (offset > 0 && chosen < 0)) {
                            log(null, sender, Level.WARNING, Util.getLang("chat.notAListedNumber"));
                            return;
                        }

                        Integer nextOffset = null;
                        if (chosen == 0) nextOffset = Math.max(offset - 10, 0);
                        if (chosen == validPluginKeys.size() + 1) nextOffset = offset + 10;

                        if (nextOffset != null) {
                            try {
                                ModrinthSearch modrinthSearch = modrinthBrowse(nextOffset);
                                ArrayList<JsonObject> newValidPluginKeys = modrinthSearch.getValidPluginKeys();
                                HashMap<JsonObject, JsonArray> newValidPlugins = modrinthSearch.getValidPlugins();

                                sender.sendMessage(Util.getLang("chat.pluginSelectPrompt"));
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

                            } catch (MalformedURLException e) {
                                log(e, sender, Level.WARNING, Util.getLang("exceptions.badModrinthUrl"));
                            } catch (ModrinthAPIException e) {
                                log(e, sender, Level.WARNING, e.getMessage());
                            }

                        } else {
                            JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosen - 1));
                            ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
                            if (compatibleFiles.isEmpty()) {
                                sender.sendMessage(Util.getLang("chat.noCompatibleFile"));
                            } else if (compatibleFiles.size() == 1) {
                                JsonObject jo = compatibleFiles.get(0).getAsJsonObject();
                                JsonArray files = jo.get("files").getAsJsonArray();
                                //getModrinthDependencies(jo.get("dependencies").getAsJsonArray(), files, sender, false);
                                return;
                            } else {
                                sender.sendMessage(Util.getLang("chat.pluginSelectPrompt"));
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

                                ChatParams newParams = new ChatParams(sender, "PluginFileSelect").setChooseableFiles(chooseableFiles);
                                if (sender instanceof Player) response.put(sender.getName(), newParams);
                                else response.put("~", newParams);
                                return;
                            }
                            response.remove(name);
                        }
                    }

                    if (modifier.equals("Terminal")) {
                        CommandSender sender = params.getSender();
                        sender.sendMessage(ChatColor.GOLD + "-----------------");
                        sender.sendMessage(ChatColor.BLUE + position.get(name).getRelativePath() + ChatColor.GOLD + " $");
                        if (message.equals("help")) {
                            sender.sendMessage(ChatColor.AQUA + "help - Shows this message.\n" +
                                    "exit - Leaves the terminal.\n" +
                                    "say - Passes the following text into the chat like normal. The \"say\" is removed.\n" +
                                    "ls - Lists the current folders and files that are in a folder.\n" +
                                    "cd - Changes the current folder to the one specified. Input \"..\" to go back a folder.\n");
                        }
                        if (message.equals("exit")) {
                            sender.sendMessage(ChatColor.YELLOW + "Exited terminal.");
                            response.remove(name);
                        }
                        if (message.startsWith("say")) {
                            if (sender instanceof Player player) {
                                String string = "<" + player.getName() + "> " + message.substring(3).trim();
                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) onlinePlayer.sendMessage(string);
                                Bukkit.getConsoleSender().sendMessage(string);
                            }
                        }
                        if (message.equals("ls")) {
                            try {
                                List<Path> paths = Files.list(Path.of(position.get(name).getCurrentPath())).toList();
                                StringBuilder files = new StringBuilder();
                                int length = position.get(name).getServerPath().length();
                                for (Path path : paths) files.append(path.toString().substring(length + 1)).append("\n");
                                sender.sendMessage(ChatColor.AQUA + files.toString());
                            } catch (Exception e) {
                                log(e, sender, Level.WARNING, "Error trying to get files from current folder.");
                            }
                        }
                        if (message.startsWith("cd")) {
                            PathHolder pathHolder = position.get(name);
                            pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + message.split(" ")[1]);
                        }
                    }
                }
            }.init(name, message)).start();
        }
    }


    //TODO: replace with get modrinth dependencies.
    public static Collection<JsonArray> getModrinthDependencies(JsonObject pluginVersion) throws ModrinthAPIException, MalformedURLException{
        JsonArray pluginDependencies = pluginVersion.get("dependencies").getAsJsonArray();

        ArrayList<String> versions = new ArrayList<>();
        ArrayList<String> projects = new ArrayList<>();
        for (JsonElement je : pluginDependencies) {
            JsonObject jo = je.getAsJsonObject();
            String dependencyType = jo.get("dependency_type").getAsString();
            if (!dependencyType.equals("required")) continue;

            JsonElement versionID = jo.get("version_id");
            if (!versionID.isJsonNull()) {
                versions.add(versionID.getAsString());
                continue;
            }

            JsonElement projectID = jo.get("project_id");
            if (!projectID.isJsonNull()) {
                projects.add(projectID.getAsString());
            }
        }

        if (projects.isEmpty() && versions.isEmpty()) {
            return new ArrayList<>();
        }

        //gets the versions from the projects
        if (!projects.isEmpty()) {
            StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
            for (String projectID : projects) projectUrl.append("%22").append(projectID).append("%22").append(",");

            JsonElement pluginProjects = modrinthAPI(projectUrl.substring(0, projectUrl.length() - 1) + "]", "GET");
            if (pluginProjects == null) {
                throw new ModrinthAPIException("Error getting dependency plugins from Modrinth.");
            }

            for (JsonElement je : pluginProjects.getAsJsonArray()) {
                for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
                    versions.add(jel.getAsString());
                }
            }
        }


        //gets the information for all the versions
        JsonElement pluginVersions;
        StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
        for (String versionID : versions) {
            versionsUrl.append("%22").append(versionID).append("%22").append(",");
        }
        pluginVersions = modrinthAPI(versionsUrl.substring(0, versionsUrl.length() - 1) + "]", "GET");
        if (pluginVersions == null) {
            throw new ModrinthAPIException("Error getting dependency plugins from Modrinth.");
        }

        //makes sure the dependencies run on this version
        String mcVersion = Bukkit.getVersion().split(": ")[1];
        mcVersion = mcVersion.substring(0, mcVersion.length()-1);
        String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

        ArrayList<String> compatibleProjectIDs = new ArrayList<>();

        HashMap<String, JsonArray> compatibleFiles = new HashMap<>();
        for (JsonElement je : pluginVersions.getAsJsonArray()) {
            JsonObject jo = je.getAsJsonObject();
            boolean supportsVersion = false;
            boolean supportsServer = false;

            String projectID = jo.get("project_id").getAsString();

            for (JsonElement supportedVersions : jo.get("game_versions").getAsJsonArray()) {
                if (supportedVersions.getAsString().equals(mcVersion)) supportsVersion = true;
            }
            for (JsonElement supportedLoaders : jo.get("loaders").getAsJsonArray()) {
                if (supportedLoaders.getAsString().equals(serverSoftware)) supportsServer = true;
            }

            if (!(supportsVersion && supportsServer)) continue;


            JsonArray array;
            if (compatibleFiles.containsKey(projectID)) array = compatibleFiles.get(projectID);
            else array = new JsonArray();
            array.add(jo);
            compatibleFiles.put(projectID, array);

            if (!compatibleProjectIDs.contains(projectID)) compatibleProjectIDs.add(projectID);
        }

        for (String key : compatibleFiles.keySet()) {
            if (!compatibleProjectIDs.contains(key)) compatibleFiles.remove(key);
        }

        if (compatibleFiles.isEmpty()) {
            return new ArrayList<>();
        }

        return compatibleFiles.values();
    }
}