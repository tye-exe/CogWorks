package me.tye.filemanager;

import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.filemanager.commands.FileCommand;
import me.tye.filemanager.commands.PluginCommand;
import me.tye.filemanager.commands.TabComplete;
import me.tye.filemanager.util.yamlClasses.DependencyInfo;
import me.tye.filemanager.util.yamlClasses.PluginData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.filemanager.FileGui.position;
import static me.tye.filemanager.commands.PluginCommand.modrinthAPI;

public final class FileManager extends JavaPlugin {


    @Override
    public void onEnable() {

        //getLogger().log(Level.INFO, "Beep boop!");

        //Commands
        getCommand("plugin").setExecutor(new PluginCommand());
        getCommand("file").setExecutor(new FileCommand());

        getCommand("plugin").setTabCompleter(new TabComplete());
        getCommand("file").setTabCompleter(new TabComplete());

        //Listeners
        getServer().getPluginManager().registerEvents(new ChatManager(), this);
        getServer().getPluginManager().registerEvents(new FileGui(), this);

        //Make required config files
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File plugins = new File(getDataFolder().getAbsolutePath() + File.separator + "pluginData.json");
        try {
            if (!plugins.exists()) plugins.createNewFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //Gets the plugin.yml files from each plugin
        File pluginFolder = new File(getDataFolder().getParent());
        ArrayList<Map<String, Object>> pluginData = new ArrayList<>();
        ArrayList<String> pluginFileName = new ArrayList<>();

        for (File file : pluginFolder.listFiles()) {
            if (file.isDirectory()) continue;
            if (!Files.getFileExtension(file.getName()).equals("jar")) continue;

            try {
                ZipFile zip = new ZipFile(file);
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    if (!entry.getName().equals("plugin.yml")) continue;

                    StringBuilder out = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                    String line;
                    while ((line = reader.readLine()) != null) out.append(line).append("\n");
                    reader.close();

                    Yaml yaml = new Yaml();
                    pluginData.add(yaml.load(out.toString()));
                    pluginFileName.add(file.getName());
                }

            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        //Writes plugin information to file.
        ArrayList<PluginData> identifiers = new ArrayList<>();
        try {
            GsonBuilder gson = new GsonBuilder();
            gson.setPrettyPrinting();
            FileWriter fileWriter = new FileWriter((plugins));
            for (int i = 0; i < pluginData.size(); i++) {
                identifiers.add(new PluginData(pluginFileName.get(i), pluginData.get(i)));
            }
            gson.create().toJson(identifiers, fileWriter);
            fileWriter.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //checks for uninstalled dependencies
        ArrayList<DependencyInfo> unmetDependencies = new ArrayList<>();
        for (PluginData data : identifiers) {
            ArrayList<DependencyInfo> dependencies = data.getDependencies();
            ArrayList<DependencyInfo> metDependencies = new ArrayList<>();
            for (DependencyInfo depInfo : data.getDependencies()) {
                for (PluginData data1 : identifiers) {
                    if (!data1.getName().equals(depInfo.getName())) continue;
                    metDependencies.add(depInfo);
                }
            }
            dependencies.removeAll(metDependencies);
            unmetDependencies.addAll(dependencies);
        }

        //attempts to resolve unmet dependencies
        //TODO: change logging
        for (DependencyInfo depInfo : unmetDependencies) {
            String name = depInfo.getName();
            String version = depInfo.getVersion();
            if (version == null) {
                ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
                try {
                    String mcVersion = Bukkit.getVersion().split(": ")[1];
                    mcVersion = mcVersion.substring(0, mcVersion.length() - 1);
                    String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

                    JsonElement relevantPlugins = modrinthAPI(new URL("https://api.modrinth.com/v2/search?query=" + makeValidForUrl(name) + "&facets=[[%22versions:" + mcVersion + "%22],[%22categories:" + serverSoftware + "%22]]"), "GET");
                    if (relevantPlugins == null) return;
                    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();

                    if (hits.size() == 0) {
                        getLogger().log(Level.WARNING, "No plugins matching that "+name+" could be found on Modrinth.");
                        return;
                    }

                    //gets the projects
                    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
                    for (JsonElement je : hits) {
                        projectUrl.append("%22").append(je.getAsJsonObject().get("slug").getAsString()).append("%22").append(",");
                    }
                    JsonElement pluginProjects = modrinthAPI(new URL(projectUrl.substring(0, projectUrl.length() - 1) + "]"), "GET");

                    //gets the versions from the projects
                    if (pluginProjects == null) {
                        getLogger().log(Level.WARNING, "Error getting supported plugins from Modrinth.");
                        return;
                    }

                    //gets the information for all the versions
                    StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
                    for (JsonElement je : pluginProjects.getAsJsonArray()) {
                        for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
                            versionsUrl.append("%22").append(jel.getAsString()).append("%22").append(",");
                        }
                    }

                    JsonElement pluginVersions = modrinthAPI(new URL(versionsUrl.substring(0, versionsUrl.length() - 1) + "]"), "GET");
                    if (pluginVersions == null) {
                        //sender.sendMessage(ChatColor.RED + "Error getting supported plugins from Modrinth.");
                        return;
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
                        //sender.sendMessage(ChatColor.YELLOW + "No compatible plugins with this name were found on Modrinth.");
                        return;
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
                    for (JsonElement je : hits)
                        if (validPlugins.containsKey(je.getAsJsonObject())) validPluginKeys.add(je.getAsJsonObject());

                    if (validPluginKeys.size() == 0) {
                        //sender.sendMessage(ChatColor.YELLOW + "No compatible plugins with this name were found on Modrinth.");
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println(validPluginKeys);
                //TODO: check if plugin resolves dependency and if not install the next one. Maybe multithreading in the future?
            }
        }

    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (position.containsKey(player.getName())) {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "[FileManager] Menu closed due to reload.");
            }
        }
    }

    public static String makeValidForUrl(String text) {
        return text.replaceAll("[^A-z0-9s-]", "").replaceAll(" ", "%20");
    }
    public static ItemStack itemProperties(ItemStack item, String displayName, List<String> lore) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return item;
        itemMeta.setDisplayName(displayName);
        if (lore != null) itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
        return item;
    }

    public static void appendPluginData(ArrayList<String> fileNames, ArrayList<Map<String, Object>> pluginData) {
        File plugins = new File(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + "pluginData.json");
        try {
            //appends data to file then writes it
            ArrayList<PluginData> identifiers = readPluginData();
            GsonBuilder gson = new GsonBuilder();
            gson.setPrettyPrinting();
            FileWriter fileWriter = new FileWriter((plugins));

            dataLoop:
            for (int i = 0; i < pluginData.size(); i++) {
                for (PluginData data : identifiers) {
                    if (pluginData.get(i).get("name").equals(data.getName())) continue dataLoop;
                }
                identifiers.add(new PluginData(fileNames.get(i), pluginData.get(i)));
            }
            gson.create().toJson(identifiers, fileWriter);
            fileWriter.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static ArrayList<PluginData> readPluginData() {
        ArrayList<PluginData> pluginData = new ArrayList<>();
        try {
            FileReader fr =  new FileReader(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + "pluginData.json");
            JsonReader jr = new JsonReader(fr);
            JsonElement jsonElement = JsonParser.parseReader(jr);
            if (jsonElement.isJsonNull()) return pluginData;
            Gson gsonReader = new Gson();
            for (JsonElement je : jsonElement.getAsJsonArray()) {
                pluginData.add(gsonReader.fromJson(je, PluginData.class));
            }
            jr.close();
            fr.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pluginData;
    }
}
