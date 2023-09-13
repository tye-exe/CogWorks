package me.tye.filemanager;

import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.filemanager.commands.FileCommand;
import me.tye.filemanager.commands.PluginCommand;
import me.tye.filemanager.commands.TabComplete;
import me.tye.filemanager.util.ModrinthSearch;
import me.tye.filemanager.util.UrlFilename;
import me.tye.filemanager.util.exceptions.ModrinthAPIException;
import me.tye.filemanager.util.exceptions.PluginExistsException;
import me.tye.filemanager.util.yamlClasses.DependencyInfo;
import me.tye.filemanager.util.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.filemanager.FileGui.position;
import static me.tye.filemanager.commands.PluginCommand.modrinthSearch;

public final class FileManager extends JavaPlugin {
    //TODO: add central lang file to allow for translation

    public static HashMap<String, Object> configs = new HashMap<>();
    @Override
    public void onEnable() {

        //Commands
        getCommand("plugin").setExecutor(new PluginCommand());
        getCommand("file").setExecutor(new FileCommand());

        getCommand("plugin").setTabCompleter(new TabComplete());
        getCommand("file").setTabCompleter(new TabComplete());

        //Listeners
        getServer().getPluginManager().registerEvents(new ChatManager(), this);
        getServer().getPluginManager().registerEvents(new FileGui(), this);

        //Set up required config files
        File configsFile = new File(getDataFolder().getAbsolutePath() + File.separator + "configs.yml");
        File pluginStore = new File(getDataFolder().getAbsoluteFile() + File.separator + "pluginStore");
        File plugins = new File(pluginStore.getAbsolutePath() + File.separator + "pluginData.json");
        try {
            if (!getDataFolder().exists()) if (!getDataFolder().mkdir()) throw new Exception();

            if (configsFile.exists()) {
                InputStream is = new FileInputStream(configsFile);
                HashMap<String, Object> map = new Yaml().load(is);
                if (map != null) configs = map;
            } else {
                if (!configsFile.createNewFile()) throw new Exception();
            }

            if (!pluginStore.exists()) if(!pluginStore.mkdir()) throw new Exception();
            if (!plugins.exists()) if (!plugins.createNewFile()) throw new Exception();
        }
        catch (Exception e) {
            log(null, Level.SEVERE, "Error initialising config folders, please report the following error!");
            throw new RuntimeException(e);
        }

        //checks that config file has the correct content.
        try {
            FileWriter fr = new FileWriter(configsFile);
            if (!configs.containsKey("showErrors")) fr.write("#Displays custom error messages to inform exactly what went wrong.\nshowErrors: true\n");
            if (!configs.containsKey("showErrorTrace")) fr.write("#Displays stack trace to help with debugging.\n#Turn this on before reporting a bug.\n#This will be enabled by default until release.\nshowErrorTrace: true\n");
            fr.close();

            InputStream is = new FileInputStream(configsFile);
            HashMap<String, Object> map = new Yaml().load(is);
            if (map != null) configs = map;
        } catch (IOException e) {
            log(null, Level.SEVERE, "Error writing configurations to config file, please report the following error!");
            throw new RuntimeException(e);
        }

        //clears out leftover files in plugin store dir
        for (File file : pluginStore.listFiles()) {
            if (file.getName().equals(plugins.getName())) continue;
            try {
                if (file.isFile()) if (!file.delete()) throw new IOException();
                if (file.isDirectory()) FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                log(e, Level.WARNING, "Unable to clean up unused file \"" + file.getName() + "\". Please manually delete this file at your earliest convenience.");
            }
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
                zip.close();
            } catch (Exception e) {
                log(e, Level.WARNING, "Unable to access plugin.yml file for \"" + file.getName() + "\". \"" + file.getName() + "\" won't work for many features of this plugin.");
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

        } catch (IOException e) {
            log(e, Level.SEVERE, "Unable to access \""+getDataFolder().getName()+File.separator+configsFile.getName()+"\". Many features of the plugin WILL break!");
        }

        //checks for uninstalled dependencies
        HashMap<DependencyInfo, PluginData> unmetDependencies = new HashMap<>();
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
            for (DependencyInfo dep : dependencies) unmetDependencies.put(dep, data);
        }

        //attempts to resolve unmet dependencies
        for (DependencyInfo unmetDepInfo : unmetDependencies.keySet()) {
            new Thread(new Runnable() {
                private DependencyInfo unmetDepInfo;
                private File pluginStore;
                private HashMap<DependencyInfo, PluginData> unmetDependencies;

                public Runnable init(DependencyInfo unmetDepInfo, File pluginStore, HashMap<DependencyInfo, PluginData> unmetDependencies) {
                    this.unmetDepInfo = unmetDepInfo;
                    //so multiple threads get their own folder.
                    this.pluginStore = new File(pluginStore.getAbsolutePath() + File.separator + LocalDateTime.now().hashCode());
                    this.pluginStore.mkdir();

                    this.unmetDependencies = unmetDependencies;
                    return this;
                }

                @Override
                public void run() {
                    String unmetDepName = this.unmetDepInfo.getName();
                    String unmetDepVersion = this.unmetDepInfo.getVersion();
                    if (unmetDepVersion != null) return;
                    ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
                    HashMap<JsonObject, JsonArray> validPlugins = new HashMap<>();

                    log(null, Level.INFO, "Attempting to automatically resolve missing dependency \""+unmetDepInfo.getName()+"\" for \""+unmetDependencies.get(unmetDepInfo).getName()+"\".");

                    //searches the dependency name on modrinth
                    try {
                        ModrinthSearch search = modrinthSearch(unmetDepName);
                        validPluginKeys = search.getValidPluginKeys();
                        validPlugins = search.getValidPlugins();
                    } catch (MalformedURLException | ModrinthAPIException e) {
                        log(null, Level.WARNING, "Error querying Modrinth for automatic dependency resolution.");
                        log(e, Level.WARNING, "Skipping resolving for: " + unmetDepName);
                    }

                    //gets the urls to download from
                    ArrayList<UrlFilename> downloads = new ArrayList<>();
                    for (JsonObject jo : validPluginKeys) {
                        JsonArray ja = validPlugins.get(jo);
                        JsonObject latestValidPlugin = null;

                        //gets the latest version of a compatible plugin
                        for (JsonElement je : ja) {
                            if (latestValidPlugin == null) {
                                latestValidPlugin = je.getAsJsonArray().get(0).getAsJsonObject();
                            } else {
                                LocalDateTime newDT = LocalDateTime.parse(je.getAsJsonObject().get("date_published").getAsString());
                                LocalDateTime dt = LocalDateTime.parse(latestValidPlugin.get("date_published").getAsString());
                                if (dt.isBefore(newDT)) {
                                    latestValidPlugin = je.getAsJsonObject();
                                }
                            }
                        }

                        JsonArray files = latestValidPlugin.get("files").getAsJsonArray();
                        int primaryIndex = 0;
                        int i = 0;
                        for (JsonElement je : files) {
                            if (je.getAsJsonObject().get("primary").getAsBoolean()) primaryIndex = i;
                            i++;
                        }
                        downloads.add(new UrlFilename(files.get(primaryIndex).getAsJsonObject().get("url").getAsString(), files.get(primaryIndex).getAsJsonObject().get("filename").getAsString()));
                    }

                    //installs possible dependencies
                    ExecutorService executorService = Executors.newCachedThreadPool();
                    for (UrlFilename downloadData : downloads) {
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                //This seems to work correctly for accessing the var outside of the run method
                                File file = new File(pluginStore.getAbsolutePath()+File.separator+downloadData.getFilename());
                                try {
                                    ReadableByteChannel rbc = Channels.newChannel(new URL(downloadData.getUrl()).openStream());
                                    FileOutputStream fos = new FileOutputStream(file);
                                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                    fos.close();
                                    rbc.close();
                                } catch (IOException e) {
                                    log(e, Level.WARNING, "Error downloading \""+file.getName()+"\" to check plugin name in \"plugin.yml\" for automatic dependency resolution. Skipping plugin.");
                                }
                            }
                        });
                    }
                    executorService.shutdown();
                    try {
                        executorService.awaitTermination(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        log(null, Level.WARNING, "Threads attempting to download plugins for longer than 60 seconds.");
                        log(e, Level.WARNING, "Skipping automatic dependency resolution for \""+unmetDepName+"\".");
                    }

                    File dependency = null;
                    //gets the plugin.yml data from the plugins
                    dependencyCheck:
                    for (File file : pluginStore.listFiles()) {
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
                                Map<String, Object> yamlData = yaml.load(out.toString());
                                if (yamlData.get("name").equals(unmetDepName)) {
                                    dependency = file;
                                    log(null, Level.INFO, "Unmet dependency for \"" + unmetDependencies.get(unmetDepInfo).getName() + "\" successfully resolved by installing \"" + yamlData.get("name") + "\".");
                                    break dependencyCheck;
                                }
                            }
                            zip.close();
                        } catch (Exception e) {
                            log(e, Level.WARNING, "Error checking plugin name from \"plugin.yml\" for \""+file.getName()+"\". Skipping possible dependency.");
                        }
                    }

                    //copy dependency
                    try {
                        if (dependency != null) FileUtils.copyFile(dependency, new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString() + File.separator + dependency.getName()));

                        //continuously attempts to delete file until it has finished copying.
                        //TODO: check if the file actually becomes deletable after copying.
                        new Thread((new Runnable() {
                            File pluginStore;

                            public Runnable init(File pluginStore) {
                                this.pluginStore = pluginStore;
                                return this;
                            }
                            public void run() {
                                int i = 0;
                                while (pluginStore.exists() && i < 10) {
                                    i++;
                                    try {
                                        Thread.sleep(1000);
                                        FileUtils.deleteDirectory(pluginStore);
                                    } catch (Exception ignore) {}
                                }
                            }
                        }).init(pluginStore)).start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (dependency == null) log(null, Level.WARNING, "Unmet dependency for \""+unmetDependencies.get(unmetDepInfo).getName()+"\" could not be automatically resolved.");
                }
            }.init(unmetDepInfo, pluginStore, unmetDependencies)).start();
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
        File plugins = new File(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + "pluginStore" + File.separator + "pluginData.json");
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

        } catch (IOException e) {
            log(null, Level.SEVERE, "Error appending data to \""+JavaPlugin.getPlugin(FileManager.class).getDataFolder().getName() + File.separator + "pluginStore" + File.separator + "pluginData.json\".");
            log(e, Level.SEVERE, "Parts of the plugin WILL break unpredictably.");
        }
    }
    public static ArrayList<PluginData> readPluginData() {
        ArrayList<PluginData> pluginData = new ArrayList<>();
        try {
            FileReader fr =  new FileReader(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + "pluginStore" + File.separator + "pluginData.json");
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
            log(null, Level.WARNING, "Error reading data from \""+JavaPlugin.getPlugin(FileManager.class).getDataFolder().getName() + File.separator + "pluginStore" + File.separator + "pluginData.json\".");
            log(e, Level.WARNING, "Whatever is trying to read from the file will produce unpredictable results.");
        }
        return pluginData;
    }
    public static PluginData readPluginData(String pluginName) throws PluginExistsException {;
        for (PluginData data : readPluginData()) {
            if (data.getName().equals(pluginName)) return data;
        }
        throw new PluginExistsException(pluginName + " either not installed or not indexed by " + JavaPlugin.getPlugin(FileManager.class).getName());
    }

    public static void log(@Nullable Exception e, Level level, String message) {
        if ((Boolean) configs.get("showErrors")) {
            ChatColor colour;
            if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
            else if (level.getName().equals("SEVER")) colour = ChatColor.RED;
            else colour = ChatColor.GREEN;

            Bukkit.getLogger().log(level, message);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) continue;
                player.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + level.getLocalizedName() + ": " + message);
                if ((Boolean) configs.get("showErrorTrace") && e != null) player.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + "Please see the console for stack trace.");
            }
        }
        if ((Boolean) configs.get("showErrorTrace") && e != null) e.printStackTrace();
    }
    public static void log(@Nullable Exception e, Player player, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVER")) colour = ChatColor.RED;
        else colour = ChatColor.GREEN;

        if ((Boolean) configs.get("showErrors")) {
            player.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + level.getLocalizedName() + ": " + message);
        }
        if ((Boolean) configs.get("showErrorTrace") && e != null) {
            player.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + "Please see the console for stack trace.");
            e.printStackTrace();
        }
    }
    public static void log(@Nullable Exception e, CommandSender sender, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVER")) colour = ChatColor.RED;
        else colour = ChatColor.GREEN;

        if ((Boolean) configs.get("showErrors")) {
            sender.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + level.getLocalizedName() + ": " + message);
        }
        if ((Boolean) configs.get("showErrorTrace") && e != null) {
            sender.sendMessage(colour + "[" + JavaPlugin.getPlugin(FileManager.class).getName() + "] " + "Please see the console for stack trace.");
            e.printStackTrace();
        }
    }
}
