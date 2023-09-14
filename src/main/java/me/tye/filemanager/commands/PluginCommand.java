package me.tye.filemanager.commands;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.ModrinthSearch;
import me.tye.filemanager.util.exceptions.ModrinthAPIException;
import me.tye.filemanager.util.exceptions.NoSuchPluginException;
import me.tye.filemanager.util.exceptions.PluginExistsException;
import me.tye.filemanager.util.exceptions.PluginInstallException;
import me.tye.filemanager.util.yamlClasses.PluginData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;

import static me.tye.filemanager.ChatManager.params;
import static me.tye.filemanager.ChatManager.responses;
import static me.tye.filemanager.FileManager.*;

public class PluginCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equals("install")) {
                //TODO: Prompt for multiple files per version - i mean the ones where it's got a "primary".
                if (args.length >= 2) {
                    try {
                        URL url = new URL(args[1]);
                        //gets the filename from the url
                        String fileName;
                        String[] splits = args[1].split("/");
                        fileName = splits[splits.length -1];
                        if (!Files.getFileExtension(fileName).equals("jar")) {
                            fileName+=".jar";
                        }
                        try {
                            installPluginURL(url, fileName, true);
                        } catch (PluginExistsException e) {
                            log(e, sender, Level.WARNING, "The Plugin is already installed.");
                            return true;
                        } catch (PluginInstallException e) {
                            log(e, sender, Level.WARNING, e.getMessage());
                            return true;
                        } catch (IOException e) {
                            log(e, sender, Level.WARNING, "Unable to access plugin.yml file for \"" + fileName + "\". \"" + fileName + "\" won't work for many features of this plugin.");
                            return true;
                        }
                        sender.sendMessage(ChatColor.GREEN + "Reload or restart for the plugin to activate.");

                    } catch (MalformedURLException ignore) {
                        try {
                            ModrinthSearch search = modrinthSearch(args[1]);
                            HashMap<JsonObject, JsonArray> validPlugins = search.getValidPlugins();
                            ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();

                            sender.sendMessage(ChatColor.GREEN+"Send the number corresponding to the plugin to install it in chat, or send q to quit.");
                            for (int i = 0; 10 > i; i++) {
                                if (validPluginKeys.size() <= i) break;
                                JsonObject project = validPluginKeys.get(i);
                                TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
                                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
                                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                projectName.setUnderlined(true);
                                sender.spigot().sendMessage(projectName);
                            }

                            if (sender instanceof Player) responses.put(sender.getName(), "PluginSelect");
                            else responses.put("~", "PluginSelect");

                            if (sender instanceof Player) params.put(sender.getName(), List.of(validPlugins, validPluginKeys, sender));
                            else params.put("~", List.of(validPlugins, validPluginKeys, sender));

                        } catch (MalformedURLException e) {
                            log(e, sender, Level.WARNING, "Error creating url to send api request to Modrinth.");
                        } catch (ModrinthAPIException e) {
                            log(e, sender, Level.WARNING, e.getMessage());
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Please provide a plugin name to search or an url to download from!");
                }
            } else if (args[0].equals("remove")) {
                if (args.length >= 2) {
                    Boolean deleteConfigs = null;

                    //checks if the plugin exists
                    try {
                        PluginData data = readPluginData(args[1]);
                        if (!new File(Bukkit.getServer().getWorldContainer().getAbsolutePath() + File.separator + "plugins" + File.separator + data.getName()).exists()) deleteConfigs = false;
                    } catch (NoSuchPluginException e) {
                        log(e, sender, Level.WARNING, "No plugin with this name could be found on your system.");
                    } catch (IOException e) {
                        log(e, sender, Level.WARNING, "There was an error reading from the pluginData file.");
                    }

                    //modifier checks
                    for (String arg : args) {
                        if (!arg.startsWith("-")) continue;
                        String modifiers = arg.substring(1);
                        for (char letter : modifiers.toCharArray()) {
                            if (letter == 'y') deleteConfigs = true;
                            if (letter == 'n') deleteConfigs = false;
                        }
                    }

                    try {
                        if (deleteConfigs != null) {
                            deletePlugin(args[1], deleteConfigs);
                            sender.sendMessage(ChatColor.GREEN+args[1]+" deleted."+ChatColor.GRAY+"\n"+ChatColor.YELLOW+"Immediately reload or restart to avoid errors.");
                            return true;
                        }
                    } catch (NoSuchPluginException e) {
                        log(e, sender, Level.WARNING, "No plugin with this name could be found on your system.");
                    } catch (IOException e) {
                        log(e, sender, Level.WARNING, args[1] + " could not be deleted.");
                    }

                    //prompt to delete config files
                    if (deleteConfigs == null) {
                        sender.sendMessage(ChatColor.YELLOW + "Do you wish to delete the "+args[1]+" config files?\nSend y or n in chat to choose.\nNote: You can add -y to the end of the command to confirm or -n to decline.");
                        if (sender instanceof Player) { responses.put(sender.getName(), "DeletePluginConfigs"); params.put(sender.getName(), List.of(sender, args[1]));}
                        else { responses.put("~", "DeletePluginConfigs"); params.put("~", List.of(sender, args[1]));}
                    }

                } else {
                    sender.sendMessage(ChatColor.RED + "Please provide a plugin name!");
                }
            } else {
                sender.sendMessage(ChatColor.GREEN+"/plugin help - Shows this message."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                        "/plugin install <Plugin Name | URL> - If a url is entered it downloads the file from the url to the plugins folder. If a name is given, it uses Modrinth to search the name given and returns the results, which can be chosen from to download."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                        "/plugin remove <Plugin Name> - Disables and uninstalls the given plugin. If the plugin did not load it cannot be uninstalled.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.GREEN+"/plugin help - Shows this message."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                "/plugin install <Plugin Name | URL> - If a url is entered it downloads the file from the url to the plugins folder. If a name is given, it uses Modrinth to search the name given and returns the results, which can be chosen from to download."+ChatColor.GRAY+"\n" + ChatColor.GREEN +
                "/plugin remove <Plugin Name> - Disables and uninstalls the given plugin. If the plugin did not load it cannot be uninstalled.");
        return true;
    }

    /**
     * Installs a plugin from a given url. There are NO restriction on the url used, however ".jar" will always be appended.
     * @param downloadURL Url to download file from.
     * @param fileName Name of the file to download. Sometimes the file is stored under a name different to the desired file name.
     * @param addFileHash If downloading from a non api source the file hash can be added to the end of the file, as many downloads have generic names such as "download".
     * @throws PluginExistsException If the plugin you're trying to install is already installed.
     * @throws PluginInstallException If there is an error when installing the plugin.
     * @throws IOException If there was an error appending the data to the pluginData file.
     */
    public static void installPluginURL(URL downloadURL, String fileName, Boolean addFileHash) throws PluginExistsException, PluginInstallException, IOException {

        File file = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+fileName);
        if (file.exists()) {
            throw new PluginExistsException(fileName + " is already installed: Skipping.");
        }

        try {
            //downloads the file
            ReadableByteChannel rbc = Channels.newChannel(downloadURL.openStream());
            //has to downloaded to a generic places before the hash can be generated from the file.
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (FileNotFoundException noFile) {
            throw new PluginInstallException("Requested file could not be found at that url.", noFile.getCause());
        } catch (IOException ioException) {
            throw new PluginInstallException("Error installing plugin!", ioException.getCause());
        }

        //adds the file hash to the name since alot of urls just have a generic filename like "download"
        String hash = "";
        if (addFileHash) {
            try {
                InputStream is = new FileInputStream(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString() + File.separator + fileName);
                DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("MD5"));
                dis.readAllBytes();
                dis.close();
                is.close();
                hash += "-";
                hash += String.format("%032X", new BigInteger(1, dis.getMessageDigest().digest()));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new PluginInstallException("Error naming plugin!\n Context: Plugins installed with \"vague\" names have the file-hash appended to them to uniquely identify them.", e.getCause());
            }
        }

        //moves the file to plugin folder
        File destination = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+Files.getNameWithoutExtension(fileName)+hash+".jar");
        File downloadedFile = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString()+File.separator+fileName);
        try {
            Files.move(downloadedFile, destination);
        } catch (IOException ioException) {
            throw new PluginExistsException("Error moving the plugin into the \""+File.separator+"plugins\" folder.", ioException.getCause());
        }

        appendPluginData(destination);
    }

    /**
     * Deletes the given plugin from the plugins folder & its configs if requested.
     * @param pluginName Name of the plugin to delete. (The name specified in the plugin.yml file).
     * @param deleteConfig Whether to delete the plugins configs alongside the jar.
     * @throws NoSuchPluginException If the plugin can't be found in the pluginData file.
     * @throws IOException If the plugin can't be deleted.
     */
    public static void deletePlugin(String pluginName, boolean deleteConfig) throws NoSuchPluginException, IOException {
        //TODO: allow to delete multiple plugins at once - separate by ","?

        //gets plugin that will be deleted
        PluginData data = readPluginData(pluginName);
        File pluginDataFolder = new File(Bukkit.getServer().getWorldContainer().getAbsolutePath() + File.separator + "plugins" + File.separator + data.getName());

        //disables the plugin so that the file can be deleted
        Plugin plugin = JavaPlugin.getPlugin(FileManager.class).getServer().getPluginManager().getPlugin(pluginName);
        if (plugin != null) {
            plugin.getPluginLoader().disablePlugin(plugin);
        }

        //deletes config files if specified
        if (deleteConfig) {
            if (pluginDataFolder.exists()) {
                try {
                    FileUtils.deleteDirectory(pluginDataFolder);
                } catch (IOException e) {
                    //marks configs for deletion on server stop, as another process is using the files
                    pluginDataFolder.deleteOnExit();
                }
            }
        }


        FileUtils.delete(new File(Bukkit.getServer().getWorldContainer().getAbsolutePath() + File.separator + "plugins" + File.separator + data.getFileName()));
        removePluginData(pluginName);
    }

    public static JsonElement modrinthAPI(URL url, String requestMethod) throws ModrinthAPIException {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(requestMethod);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "FileManager: https://github.com/Mapty231 contact: tye.exe@gmail.com");

            int status = con.getResponseCode();
            if (status == 410) {
                throw new ModrinthAPIException("The FileManager plugin is using an outdated version of the Modrinth api. Please update your version of the plugin!");
            }
            if (status != 200) {
                throw new ModrinthAPIException("There was a problem using the Modrinth API.", new Throwable("URL: "+url.toExternalForm()+"\n request method: "+requestMethod + "\n response message:" + con.getResponseMessage()));
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            con.disconnect();

            return JsonParser.parseString(content.toString());
        } catch (IOException e) {
            throw new ModrinthAPIException("There was a problem accessing the modrinth api website.", e.getCause());
        }
    }


    /**
     * Finds compatible plugins on modrinth for your server.
     * @param searchQuery the name of the plugin to search Modrinth for.
     * @return A ModrinthSearch object.
     * @throws MalformedURLException If the request urls to teh modrinth api are invalid.
     * @throws ModrinthAPIException if there is an error using the Modrinth API.
     */
    public static ModrinthSearch modrinthSearch(String searchQuery) throws MalformedURLException, ModrinthAPIException {
        ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
        HashMap<JsonObject, JsonArray> validPlugins = new HashMap<>();

        String mcVersion = Bukkit.getVersion().split(": ")[1];
        mcVersion = mcVersion.substring(0, mcVersion.length() - 1);
        String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

        JsonElement relevantPlugins = modrinthAPI(new URL("https://api.modrinth.com/v2/search?query=" + makeValidForUrl(searchQuery) + "&facets=[[%22versions:" + mcVersion + "%22],[%22categories:" + serverSoftware + "%22]]"), "GET");
        if (relevantPlugins == null) throw new ModrinthAPIException("Error getting relevant plugins from Modrinth.");
        JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
        if (hits.isEmpty()) return new ModrinthSearch(null, null);

        //gets the projects
        StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
        for (JsonElement je : hits) {
            projectUrl.append("%22").append(je.getAsJsonObject().get("slug").getAsString()).append("%22").append(",");
        }
        JsonElement pluginProjects = modrinthAPI(new URL(projectUrl.substring(0, projectUrl.length() - 1) + "]"), "GET");

        //gets the versions from the projects
        if (pluginProjects == null) throw new ModrinthAPIException("Error getting supported plugins from Modrinth.");

        //gets the information for all the versions
        StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
        for (JsonElement je : pluginProjects.getAsJsonArray()) {
            for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
                versionsUrl.append("%22").append(jel.getAsString()).append("%22").append(",");
            }
        }

        JsonElement pluginVersions = modrinthAPI(new URL(versionsUrl.substring(0, versionsUrl.length() - 1) + "]"), "GET");
        if (pluginVersions == null) throw new ModrinthAPIException("Error getting supported versions from Modrinth.");

        //filters out incompatible versions/plugins
        HashMap<String, JsonArray> compatibleFiles = new HashMap<>();
        for (JsonElement je : pluginVersions.getAsJsonArray()) {
            JsonObject jo = je.getAsJsonObject();
            boolean supportsVersion = false;
            boolean supportsServer = false;

            for (JsonElement supportedVersions : jo.get("game_versions").getAsJsonArray()) {
                if (supportedVersions.getAsString().equals(mcVersion)) supportsVersion = true;
            }
            for (JsonElement supportedLoaders : jo.get("loaders").getAsJsonArray()) {
                if (supportedLoaders.getAsString().equals(serverSoftware)) supportsServer = true;
            }

            if (!(supportsVersion && supportsServer)) continue;

            String projectID = jo.get("project_id").getAsString();

            JsonArray array;
            if (compatibleFiles.containsKey(projectID)) array = compatibleFiles.get(projectID);
            else array = new JsonArray();
            array.add(jo);
            compatibleFiles.put(projectID, array);
        }

        if (compatibleFiles.isEmpty()) return new ModrinthSearch(null, null);

        //hashmap of all valid plugins and there compatible versions
        for (JsonElement je : hits) {
            JsonObject jo = je.getAsJsonObject();
            String projectID = jo.get("project_id").getAsString();
            if (!compatibleFiles.containsKey(projectID)) continue;

            JsonArray array;
            if (validPlugins.containsKey(jo)) array = validPlugins.get(jo);
            else array = new JsonArray();
            array.add(compatibleFiles.get(projectID));
            validPlugins.put(jo, array);
        }

        //adds them to the list in the order they were returned by Modrinth
        for (JsonElement je : hits)
            if (validPlugins.containsKey(je.getAsJsonObject())) validPluginKeys.add(je.getAsJsonObject());

        if (validPluginKeys.isEmpty()) return new ModrinthSearch(null, null);
        return new ModrinthSearch(validPluginKeys, validPlugins);
    }
}
