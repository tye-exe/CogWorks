package me.tye.filemanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.filemanager.util.PathHolder;
import me.tye.filemanager.util.exceptions.ModrinthAPIException;
import me.tye.filemanager.util.exceptions.PluginExistsException;
import me.tye.filemanager.util.exceptions.PluginInstallException;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import static me.tye.filemanager.FileGui.position;
import static me.tye.filemanager.FileManager.configs;
import static me.tye.filemanager.FileManager.log;
import static me.tye.filemanager.commands.PluginCommand.*;

public class ChatManager implements Listener {

    public static HashMap<String, String> responses = new HashMap<>();
    public static HashMap<String, List<Object>> params = new HashMap<>();

    @EventHandler
    public void onPlayerMessage(AsyncPlayerChatEvent e) {
        if (responses.containsKey(e.getPlayer().getName())) e.setCancelled(true);
        checks(e.getPlayer().getName(), e.getMessage());
        if (responses.containsKey(e.getPlayer().getName())) e.setCancelled(true);
    }

    @EventHandler
    public void onConsoleMessage(ServerCommandEvent e) {
        if (responses.containsKey("~")) e.setCancelled(true);
        checks("~", e.getCommand());
        if (responses.containsKey("~")) e.setCancelled(true);
    }

    public static void checks(String name, String message) {
        new BukkitRunnable(){
            @Override
            public void run() {
                if (!responses.containsKey(name)) return;
                String modifier = responses.get(name);
                if (message.startsWith("plugin")) return;
                if (modifier.equals("DeletePluginConfigs")) {
                    List<Object> param = params.get(name);
                    boolean deleteConfig;
                    if (message.equals("y")) deleteConfig = true;
                    else if (message.equals("n")) deleteConfig = false;
                    else deleteConfig = false;
                    responses.remove(name);
                    params.remove(name);
                    deletePlugin((CommandSender) param.get(0), (String[]) param.get(1), deleteConfig);
                }
                if (modifier.equals("PluginSelect")) {
                    List<Object> param = params.get(name);
                    @SuppressWarnings("unchecked") HashMap<JsonObject, JsonArray> validPlugins = (HashMap<JsonObject, JsonArray>) param.get(0);
                    @SuppressWarnings("unchecked") ArrayList<JsonObject> validPluginKeys = (ArrayList<JsonObject>) param.get(1);
                    CommandSender sender = (CommandSender) param.get(2);
                    if (message.equals("q")) {
                        responses.remove(name);
                        params.remove(name);
                        sender.sendMessage(ChatColor.YELLOW+"Quitting.");
                        return;
                    }
                    int chosenPlugin;
                    try {
                        chosenPlugin = Integer.parseInt(message);
                    } catch (NumberFormatException e) {
                        log(e, sender, Level.WARNING, "Please enter a listed number!");
                        return;
                    }
                    if (chosenPlugin > validPluginKeys.size() || chosenPlugin < 1) {
                        log(null, sender, Level.WARNING, "Please enter a listed number!");
                        return;
                    }

                    JsonArray compatibleFiles = validPlugins.get(validPluginKeys.get(chosenPlugin-1)).get(0).getAsJsonArray();
                    ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
                    if (compatibleFiles.size() == 0) {
                        sender.sendMessage(ChatColor.YELLOW+"Failed to find compatible file to download.");
                    } else if (compatibleFiles.size() == 1) {
                        JsonObject jo = compatibleFiles.get(0).getAsJsonObject();
                        JsonArray files = jo.get("files").getAsJsonArray();
                        installModrinthDependencies(jo.get("dependencies").getAsJsonArray(), true, files, sender, false);
                        return;
                    } else {
                        sender.sendMessage(ChatColor.GREEN+"Send the number corresponding to the plugin to install it in chat, or send q to quit.");
                        int i = 1;
                        for (JsonElement je : compatibleFiles) {
                            JsonObject jo = je.getAsJsonObject();
                            chooseableFiles.add(jo);
                            TextComponent projectName = new TextComponent(i+": "+jo.get("name").getAsString()+" : "+jo.get("version_number").getAsString());
                            projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+validPluginKeys.get(chosenPlugin-1).get("project_type").getAsString()+"/"+validPluginKeys.get(chosenPlugin-1).get("slug").getAsString()+"/version/"+jo.get("version_number").getAsString())));
                            projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                            projectName.setUnderlined(true);
                            sender.spigot().sendMessage(projectName);
                            i++;
                        }
                        if (sender instanceof Player) responses.put(sender.getName(), "PluginFileSelect");
                        else responses.put("~", "PluginFileSelect");

                        if (sender instanceof Player) params.put(sender.getName(), List.of(chooseableFiles, sender));
                        else params.put("~", List.of(chooseableFiles, sender));
                        return;
                    }
                    responses.remove(name);
                    params.remove(name);
                }
                if (modifier.equals("PluginFileSelect")) {
                    List<Object> param = params.get(name);
                    @SuppressWarnings("unchecked") ArrayList<JsonObject> chooseableFiles = (ArrayList<JsonObject>) param.get(0);
                    CommandSender sender = (CommandSender) param.get(1);
                    if (message.equals("q")) {
                        responses.remove(name);
                        params.remove(name);
                        sender.sendMessage(ChatColor.YELLOW+"Quitting.");
                        return;
                    }

                    int chosenVersion;
                    try {
                        chosenVersion = Integer.parseInt(message);
                    } catch (NumberFormatException e) {
                        log(e, sender, Level.WARNING, "Please enter a listed number!");
                        return;
                    }
                    if (chosenVersion > chooseableFiles.size() || chosenVersion < 1) {
                        log(null, sender, Level.WARNING, "Please enter a listed number!");
                        return;
                    }

                    JsonObject jo = chooseableFiles.get(chosenVersion-1);
                    JsonArray files = jo.get("files").getAsJsonArray();
                    installModrinthDependencies(jo.get("dependencies").getAsJsonArray(), true, files, sender, false);
                    return;
                }
                if (modifier.equals("ConfirmDependencies")) {
                    List<Object> param = params.get(name);
                    JsonArray dependencies = (JsonArray) param.get(0);
                    JsonArray files = (JsonArray) param.get(1);
                    CommandSender sender = (CommandSender) param.get(2);
                    if (message.equals("q")) {
                        responses.remove(name);
                        params.remove(name);
                        sender.sendMessage(ChatColor.YELLOW + "Quitting.");
                        return;
                    }

                    boolean installDependencies;
                    if (message.equals("y")) installDependencies = true;
                    else if (message.equals("n")) installDependencies = false;
                    else {
                        sender.sendMessage(ChatColor.YELLOW + "Please enter \"y\" or \"n\".");
                        return;
                    }

                    if (installDependencies) {
                        sender.sendMessage(ChatColor.GREEN + "Installing required dependencies...");
                        installModrinthDependencies(dependencies, false, null, sender, false);
                        sender.sendMessage(ChatColor.GREEN + "Finished installing required dependencies.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "Skipping required dependencies.");
                    }

                    sender.sendMessage(ChatColor.GREEN + "Installing plugin(s)...");
                    for (JsonElement je : files) {
                        JsonObject file = je.getAsJsonObject();
                        try {
                            if (installPluginURL(new URL(file.get("url").getAsString()), file.get("filename").getAsString(), false)) sender.sendMessage(ChatColor.GREEN + file.get("filename").getAsString() + " installed successfully.");
                        } catch (MalformedURLException e) {
                            log(e, sender, Level.WARNING, "Skipping " + file.get("filename").getAsString() + ": Invalid download ulr");
                        } catch (PluginExistsException e) {
                            log(e, sender, Level.WARNING, "The Plugin is already installed: Skipping");
                        } catch (PluginInstallException e) {
                            log(e, sender, Level.WARNING, e.getMessage());
                        }
                    }
                    sender.sendMessage(ChatColor.GREEN + "Finished installing plugin(s): Reload or restart for the plugin(s) to activate.");

                    responses.remove(name);
                    params.remove(name);
                }
                if (modifier.equals("Terminal")) {
                    CommandSender sender = (CommandSender) params.get(name).get(0);
                    sender.sendMessage(ChatColor.GOLD+"-----------------");
                    sender.sendMessage(ChatColor.BLUE+position.get(name).getRelativePath()+ChatColor.GOLD+" $");
                    if (message.equals("help")) {
                        sender.sendMessage(ChatColor.AQUA+"help - Shows this message.\n" +
                                "exit - Leaves the terminal.\n" +
                                "say - Passes the following text into the chat like normal. The \"say\" is removed.\n" +
                                "ls - Lists the current folders and files that are in a folder.\n" +
                                "cd - Changes the current folder to the one specified. Input \"..\" to go back a folder.\n");
                    }
                    if (message.equals("exit")) {
                        sender.sendMessage(ChatColor.YELLOW+"Exited terminal.");
                        params.remove(name);
                        responses.remove(name);
                    }
                    if (message.startsWith("say")) {
                        if (sender instanceof Player player) {
                            String string = "<"+player.getName()+"> "+message.substring(3).trim();
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) onlinePlayer.sendMessage(string);
                            Bukkit.getConsoleSender().sendMessage(string);
                        }
                    }
                    if (message.equals("ls")) {
                        try {
                            List<Path> paths = Files.list(Path.of(position.get(name).getCurrentPath())).toList();
                            StringBuilder files = new StringBuilder();
                            int length = position.get(name).getServerPath().length();
                            for (Path path : paths) files.append(path.toString().substring(length+1)+"\n");
                            sender.sendMessage(ChatColor.AQUA+files.toString());
                        } catch (Exception e) {
                            log(e, sender, Level.WARNING, "Error trying to get files from current folder.");
                        }
                    }
                    if (message.startsWith("cd")) {
                        PathHolder pathHolder = position.get(name);
                        System.out.println(pathHolder.getCurrentPath());
                        pathHolder.setCurrentPath(pathHolder.getCurrentPath()+File.separator+message.split(" ")[1]);
                        System.out.println(pathHolder.getCurrentPath());
                    }
                }
            }
        }.runTask(JavaPlugin.getPlugin(FileManager.class));
    }


    public static void installModrinthDependencies(JsonArray dependencies, boolean confirmDependencyInstallation, JsonArray files, CommandSender sender, boolean recursion) {

        //TODO: needs to check if dependency is already installed

        ArrayList<String> versions = new ArrayList<>();
        ArrayList<String> projects = new ArrayList<>();
        for (JsonElement je : dependencies) {
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
            if (!recursion) {
                sender.sendMessage(ChatColor.GREEN + "Installing plugin(s)...");
                for (JsonElement je : files) {
                    JsonObject file = je.getAsJsonObject();
                    try {
                        if (installPluginURL(new URL(file.get("url").getAsString()), file.get("filename").getAsString(), false)) sender.sendMessage(ChatColor.GREEN + file.get("filename").getAsString() + " installed successfully.");
                    } catch (MalformedURLException e) {
                        log(e, sender, Level.WARNING, "Skipping " + file.get("filename").getAsString() + ": Invalid download ulr");
                    } catch (PluginExistsException e) {
                        log(e, sender, Level.WARNING, "The Plugin is already installed: Skipping");
                    } catch (PluginInstallException e) {
                        log(e, sender, Level.WARNING, e.getMessage());
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Finished installing plugin(s): Reload or restart for the plugin(s) to activate.");
                responses.clear();
                params.clear();
            }
            return;
        }

        if (confirmDependencyInstallation) {
            if (sender instanceof Player) responses.put(sender.getName(), "ConfirmDependencies");
            else responses.put("~", "ConfirmDependencies");

            if (sender instanceof Player) params.put(sender.getName(), List.of(dependencies, files, sender));
            else params.put("~", List.of(dependencies, files, sender));

            sender.sendMessage(ChatColor.YELLOW + "Do you wish to install the required dependencies for this plugin?\nSend y or n in chat to choose.");
            return;
        }

        //gets the versions from the projects
        if (!projects.isEmpty()) {
            StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
            for (String projectID : projects) projectUrl.append("%22").append(projectID).append("%22").append(",");

            try {
                JsonElement pluginProjects = modrinthAPI(new URL(projectUrl.substring(0, projectUrl.length() - 1) + "]"), "GET");
                if (pluginProjects == null) {
                    sender.sendMessage(ChatColor.RED + "Error getting dependency plugins from Modrinth.");
                    return;
                }

                for (JsonElement je : pluginProjects.getAsJsonArray()) {
                    for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
                        versions.add(jel.getAsString());
                    }
                }

            } catch (MalformedURLException | ModrinthAPIException e) {
                log(e, sender, Level.SEVERE, "Error getting dependency plugins from Modrinth.");
                return;
            }
        }


        //gets the information for all of the versions
        JsonElement pluginVersions;
        StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
        for (String versionID : versions) {
            versionsUrl.append("%22").append(versionID).append("%22").append(",");
        }
        try {
            pluginVersions = modrinthAPI(new URL(versionsUrl.substring(0, versionsUrl.length() - 1) + "]"), "GET");
            if (pluginVersions == null) {
                sender.sendMessage(ChatColor.RED + "Error getting dependency versions from Modrinth.");
                return;
            }
        } catch (MalformedURLException | ModrinthAPIException e) {
            log(e, sender, Level.SEVERE, "Error getting dependency plugins from Modrinth.");
            return;
        }

        //makes sure the dependencies run on this version
        String mcVersion = Bukkit.getVersion().split(": ")[1];
        mcVersion = mcVersion.substring(0, mcVersion.length()-1);
        String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

        ArrayList<String> allProjectIDs = new ArrayList<>();
        ArrayList<String> compatibleProjectIDs = new ArrayList<>();

        HashMap<String, JsonArray> compatibleFiles = new HashMap<>();
        for (JsonElement je : pluginVersions.getAsJsonArray()) {
            JsonObject jo = je.getAsJsonObject();
            boolean supportsVersion = false;
            boolean supportsServer = false;

            String projectID = jo.get("project_id").getAsString();
            if (!allProjectIDs.contains(projectID)) allProjectIDs.add(projectID);

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

        //checks
        if (compatibleFiles.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No compatible dependencies are available!");
            return;
        }
        if (allProjectIDs.size() != compatibleProjectIDs.size()) {
            allProjectIDs.removeAll(compatibleProjectIDs);
            //TODO: figure out what this is doing
            StringBuilder incompatibleProjects = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
            for (String projectID : allProjectIDs) {
                incompatibleProjects.append("%22").append(projectID).append("%22").append(",");
            }
            try {
                JsonElement incompatiblePlugins = modrinthAPI(new URL(versionsUrl.substring(0, versionsUrl.length() - 1) + "]"), "GET");
                StringBuilder incompatibleTitles = new StringBuilder();
                for (JsonElement je : incompatiblePlugins.getAsJsonArray()) {
                    incompatibleTitles.append(je.getAsJsonObject().get("title").getAsString()).append(", ");
                }

                sender.sendMessage(ChatColor.YELLOW + "Error installing dependencies: "+incompatibleTitles.substring(incompatibleTitles.length()-2)+".\nEither doesn't support version or server software.");
            } catch (MalformedURLException | ModrinthAPIException e){
                log(e, sender, Level.WARNING, "Error installing some dependencies!\nError getting incompatible dependencies!");
            }
        }

        //installing files
        for (String projectID : compatibleFiles.keySet()) {
            JsonArray ja = compatibleFiles.get(projectID);
            if (ja.size() < 1) {
                continue;
            }
            JsonObject jo = ja.get(0).getAsJsonObject();
            JsonArray dependencyFiles = jo.get("files").getAsJsonArray();
            installModrinthDependencies(jo.get("dependencies").getAsJsonArray(), false, dependencyFiles, sender, true);

            for (JsonElement je : dependencyFiles) {
                JsonObject file = je.getAsJsonObject();
                try {
                    if (installPluginURL(new URL(file.get("url").getAsString()), file.get("filename").getAsString(), false)) sender.sendMessage(ChatColor.GREEN + file.get("filename").getAsString() + " installed successfully.");
                } catch (MalformedURLException e) {
                    log(e, sender, Level.WARNING, "Skipping " + file.get("filename").getAsString() + ": Invalid download ulr");
                } catch (PluginExistsException e) {
                    log(e, sender, Level.WARNING, "The Plugin is already installed: Skipping");
                } catch (PluginInstallException e) {
                    log(e, sender, Level.WARNING, e.getMessage());
                }
            }
        }
    }
}