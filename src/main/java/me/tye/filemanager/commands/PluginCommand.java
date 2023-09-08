package me.tye.filemanager.commands;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tye.filemanager.FileManager;
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
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.filemanager.ChatManager.params;
import static me.tye.filemanager.ChatManager.responses;
import static me.tye.filemanager.FileManager.makeValidForUrl;

public class PluginCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("help")))
            sender.sendMessage(ChatColor.GREEN+"/plugin help - Shows this message.\n" +
                "/plugin install <Plugin Name | URL> - If a url is entered it downloads the file from the url to the plugins folder. If a name is given, it uses Modrinth to search the name given and returns the results, which can be chosen from to download.\n"+
                "/plugin remove <Plugin Name> - Disables and uninstalls the given plugin. If the plugin did not load it cannot be uninstalled.");
        if (args.length >= 1) {
            if (args[0].equals("install")) {
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
                        installPluginURL(url, fileName, true, sender);
                        sender.sendMessage(ChatColor.GREEN + "Reload or restart for the plugin to activate.");

                    } catch (MalformedURLException e) {
                        try {
                            String mcVersion = Bukkit.getVersion().split(": ")[1];
                            mcVersion = mcVersion.substring(0, mcVersion.length()-1);
                            String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

                            JsonElement relevantPlugins = modrinthAPI(new URL("https://api.modrinth.com/v2/search?query="+makeValidForUrl(args[1])+"&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]"), "GET", sender);
                            if (relevantPlugins == null) return true;
                            JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();

                            if (hits.size() == 0) {
                                sender.sendMessage(ChatColor.YELLOW+"No plugins matching that name could be found on Modrinth.");
                                return true;
                            }

                            //gets the projects
                            StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
                            for (JsonElement je :hits) {
                                projectUrl.append("%22").append(je.getAsJsonObject().get("slug").getAsString()).append("%22").append(",");
                            }
                            JsonElement pluginProjects = modrinthAPI(new URL(projectUrl.substring(0, projectUrl.length()-1)+"]"), "GET", sender);

                            //gets the versions from the projects
                            if (pluginProjects == null) {
                                sender.sendMessage(ChatColor.RED+"Error getting supported plugins from Modrinth.");
                                return true;
                            }

                            //gets the information for all the versions
                            StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
                            for (JsonElement je : pluginProjects.getAsJsonArray()) {
                                for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
                                    versionsUrl.append("%22").append(jel.getAsString()).append("%22").append(",");
                                }
                            }

                            JsonElement pluginVersions = modrinthAPI(new URL(versionsUrl.substring(0, versionsUrl.length()-1)+"]"), "GET", sender);
                            if (pluginVersions == null) {
                                sender.sendMessage(ChatColor.RED+"Error getting supported plugins from Modrinth.");
                                return true;
                            }

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

                            if (compatibleFiles.isEmpty()) {
                                sender.sendMessage(ChatColor.YELLOW+"No compatible plugins with this name were found on Modrinth.");
                                return true;
                            }

                            //hashmap of all valid plugins and there compatible versions
                            HashMap<JsonObject, JsonArray> validPlugins = new HashMap<>();
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
                            ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
                            for (JsonElement je : hits) if (validPlugins.containsKey(je.getAsJsonObject())) validPluginKeys.add(je.getAsJsonObject());

                            if (validPluginKeys.size() == 0) {
                                sender.sendMessage(ChatColor.YELLOW+"No compatible plugins with this name were found on Modrinth.");
                                return true;
                            }

                            sender.sendMessage(ChatColor.GREEN+"Send the number corresponding to the plugin to install it in chat, or send q to quit.");

                            int page = 1;
                            int i = 0;
                            int offset = 1;
                            if (page != 1) {
                                offset++;
                                sender.sendMessage(ChatColor.GREEN+"1: \u2191");
                            }
                            for (i = 0; 10 > i; i++) {
                                if (validPluginKeys.size() <= i) break;
                                JsonObject project = validPluginKeys.get(i*page);
                                TextComponent projectName = new TextComponent(i+offset+": "+project.get("title").getAsString());
                                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
                                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                projectName.setUnderlined(true);
                                sender.spigot().sendMessage(projectName);
                            }
                            if (validPlugins.size() > i*page) {
                                //sender.sendMessage(ChatColor.GREEN+String.valueOf(i+offset)+": \u2193");
                            }

                            if (sender instanceof Player) responses.put(sender.getName(), "PluginSelect");
                            else responses.put("~", "PluginSelect");

                            if (sender instanceof Player) params.put(sender.getName(), List.of(validPlugins, validPluginKeys, sender));
                            else params.put("~", List.of(validPlugins, validPluginKeys, sender));

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Please provide a plugin name to search or an url to download from!");
                }
            }
            if (args[0].equals("remove")) {
                if (args.length >= 2) {

                    deletePlugin(sender, args, null);

                } else {
                    sender.sendMessage(ChatColor.RED + "Please provide a running plugin name!");
                }
            }
        }

        return true;
    }

    public static boolean installPluginURL(URL downloadURL, String fileName, Boolean addFileHash, CommandSender sender) {

        File file = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+fileName);
        if (file.exists()) {
            sender.sendMessage(ChatColor.YELLOW + fileName + " is already installed: Skipping.");
            return false;
        }

        try {
            //downloads the file
            ReadableByteChannel rbc = Channels.newChannel(downloadURL.openStream());
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (FileNotFoundException noFile) {
            sender.sendMessage(ChatColor.RED+"Requested file could not be found at that url.");
            return false;
        } catch (Exception exception) {
            exception.printStackTrace();
            sender.sendMessage(ChatColor.RED+"Error installing plugin! Please see the console and report the error.");
            return false;
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
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Error naming plugin! Please see the console and report the error.");
                return false;
            }
        }


//        File destinationFolder = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString());
//        List<File> preMove = Arrays.stream(destinationFolder.listFiles()).toList();

        //moves the file to plugin folder
        try {
            File destination = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+Files.getNameWithoutExtension(fileName)+hash+".jar");
            File downloadedFile = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().getParent().toString()+File.separator+fileName);
            Files.move(downloadedFile, destination);
        } catch (Exception exception) {
            exception.printStackTrace();
            sender.sendMessage(ChatColor.RED+"Error installing plugin! Please see the console and report the error.");
            return false;
        }

        sender.sendMessage(ChatColor.GREEN+fileName+" installed.");
        return true;
//        List<File> postMove = new ArrayList<>(Arrays.stream(destinationFolder.listFiles()).toList());
//        postMove.removeAll(preMove);
//
//        try {
//            ArrayList<String> classNames = new ArrayList<>();
//            try (JarFile jarFile = new JarFile(postMove.get(0))) {
//                Enumeration<JarEntry> e = jarFile.entries();
//                while (e.hasMoreElements()) {
//                    JarEntry jarEntry = e.nextElement();
//                    if (jarEntry.getName().endsWith(".class")) {
//                        String className = jarEntry.getName()
//                                .replace("/", ".")
//                                .replace(".class", "");
//                        classNames.add(className);
//                    }
//                }
//            }
//            Bukkit.getPluginManager().enablePlugin(JavaPlugin.getPlugin(Class.forName(classNames.get(0))));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public static void deletePlugin(CommandSender sender, String[] args, Boolean deleteConfig) {
        //gets plugin that will be deleted
        Plugin plugin = JavaPlugin.getPlugin(FileManager.class).getServer().getPluginManager().getPlugin(args[1]);
        if (plugin == null) {
            sender.sendMessage(ChatColor.RED + "Invalid plugin name.");
            return;
        }
        String pluginName = plugin.getName();
        File pluginDataFolder = plugin.getDataFolder();

        //modifier checks
        for (String arg : args) {
            if (!arg.startsWith("-")) continue;
            String modifiers = arg.substring(1);
            for (char letter : modifiers.toCharArray()) {
                if (letter == 'y') deleteConfig = true;
                if (letter == 'n') deleteConfig = false;
            }
        }

        //prompt to delete config files
        if (deleteConfig == null) {
            sender.sendMessage(ChatColor.YELLOW + "Do you wish to delete the "+pluginName+" config files?\nSend y or n in chat to choose.\nNote: You can add -y to the end of the command to confirm or -n to decline.");
            if (sender instanceof Player) { responses.put(sender.getName(), "DeletePluginConfigs"); params.put(sender.getName(), List.of(sender, args));}
            else { responses.put("~", "DeletePluginConfigs"); params.put("~", List.of(sender, args));}
            return;
        }

        //disables the plugin so that the file can be deleted
        plugin.getPluginLoader().disablePlugin(plugin);
        sender.sendMessage(ChatColor.GREEN+"Disabled "+pluginName+".");

        //deletes config files if specified
        if (deleteConfig) {
            if (pluginDataFolder.exists()) {
                try {
                    FileUtils.deleteDirectory(pluginDataFolder);
                    sender.sendMessage(ChatColor.GREEN + "Deleted "+pluginName+" configs.");
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Error deleting "+pluginName+" configs! Please report the error in the console.");
                    throw new RuntimeException(e);
                }
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW+"Preserving "+pluginName+" configs.");
        }

        //finds the file name of the disabled plugin by searching though all the plugin.yml files.
        //idk how else to find the corresponding file from the name.
        File pluginFolder = new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString());

        pluginFolderLoop:
        for (File file : pluginFolder.listFiles()) {
            if (file.isDirectory()) continue;
            if (!Files.getFileExtension(file.getName()).equals("jar")) continue;

            try {
                ZipFile zip = new ZipFile(file);
                for (Enumeration e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    if (!entry.getName().equals("plugin.yml")) continue;

                    StringBuilder out = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                    String line;
                    while ((line = reader.readLine()) != null) out.append(line+"\n");
                    reader.close();

                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(out.toString());
                    if (!data.get("name").equals(pluginName)) continue;
                    zip.close();

                    try {
                        java.nio.file.Files.delete(Path.of(file.getAbsolutePath()));
                        sender.sendMessage(ChatColor.GREEN+pluginName+" deleted.\n"+ChatColor.YELLOW+"Immediately reload or restart to avoid errors.");
                    } catch (IOException ex) {
                        sender.sendMessage(ChatColor.RED+"Error deleting "+pluginName+"! Please report the error in the console.");
                        throw new RuntimeException(ex);
                    }

                    break pluginFolderLoop;
                }

            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    public static JsonElement modrinthAPI(URL url, String requestMethod, CommandSender sender) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(requestMethod);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "FileManager: https://github.com/Mapty231 contact: tye.exe@gmail.com");

            int status = con.getResponseCode();
            if (status == 410) {
                sender.sendMessage(ChatColor.RED + "Outdated Modrinth api, please update your version of the plugin!");
                return null;
            }
            if (status != 200) {
                sender.sendMessage(ChatColor.RED + "There was a problem using the Modrinth API.");
                return null;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
