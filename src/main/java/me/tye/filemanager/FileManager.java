package me.tye.filemanager;

import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.filemanager.commands.FileCommand;
import me.tye.filemanager.commands.PluginCommand;
import me.tye.filemanager.commands.TabComplete;
import me.tye.filemanager.events.SendErrorSummary;
import me.tye.filemanager.util.ModrinthSearch;
import me.tye.filemanager.util.UrlFilename;
import me.tye.filemanager.util.exceptions.ModrinthAPIException;
import me.tye.filemanager.util.exceptions.NoSuchPluginException;
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
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static me.tye.filemanager.FileGui.position;
import static me.tye.filemanager.commands.PluginCommand.modrinthSearch;

public final class FileManager extends JavaPlugin {
    //TODO: /plugin brows
    //TODO: editing files in /file by adding toggle to separate mode - new permission : add check before deleting or creating anything

    //TODO: convert install modrinth dependencies to use errors, not sender
    //TODO: run install dependencies on plugins installed from auto dependency resolve
    //TODO: add advise on how to fix errors in the error message.
    //TODO: add central lang file to allow for translation.

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
        getServer().getPluginManager().registerEvents(new SendErrorSummary(), this);

        //Set up required config files
        File configsFile = new File(getDataFolder().getAbsolutePath() + File.separator + "configs.yml");
        File pluginStore = new File(getDataFolder().getAbsoluteFile() + File.separator + ".pluginStore");
        File plugins = new File(pluginStore.getAbsolutePath() + File.separator + "pluginData.json");
        try {
            if (!getDataFolder().exists()) if (!getDataFolder().mkdir()) throw new Exception();

            if (configsFile.exists()) {
                InputStream is = new FileInputStream(configsFile);
                HashMap<String, Object> map = new Yaml().load(is);
                if (map != null) configs = map;
            } else {
                if (!configsFile.createNewFile()) throw new Exception("Unable to create config.yml file in ./plugins/FileManager");
            }

            if (!pluginStore.exists()) if(!pluginStore.mkdir()) throw new Exception("Unable to create .pluginStore file in ./plugins/FileManager");
            if (!plugins.exists()) if (!plugins.createNewFile()) throw new Exception("Unable to create pluginData.json file in ./plugins/FileManager/.pluginStore");
        }
        catch (Exception e) {
            setConfigsToDefault();
            log(e, Level.SEVERE, "Error initialising config folders. Reverting to default settings. Please report the following error.");
        }

        //checks that config file has the correct content.
        //TODO: remove lines of text that don't conform to the notes/keys that aren't valid?
        try {
            FileWriter fr = new FileWriter(configsFile);
            if (!configs.containsKey("showErrors")) fr.write("#Displays custom error messages to inform exactly what went wrong.\nshowErrors: true\n\n");
            if (!configs.containsKey("showErrorTrace")) fr.write("#Displays stack trace to help with debugging.\n#Turn this on before reporting a bug.\n#This will be enabled by default until release.\nshowErrorTrace: true\n\n");
            if (!configs.containsKey("showOpErrorSummary")) fr.write("#On join send op players the amount of errors and warnings have happened with this plugin since last reload/restart.\nshowOpErrorSummary: true\n\n");
            fr.close();

            InputStream is = new FileInputStream(configsFile);
            HashMap<String, Object> map = new Yaml().load(is);
            if (map != null) configs = map;
        } catch (IOException e) {
            setConfigsToDefault();
            log(null, Level.SEVERE, "Error writing configurations to config file. Reverting to default settings. Please report the following error.");
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

        File pluginFolder = new File(getDataFolder().getParent());
        ArrayList<PluginData> identifiers = new ArrayList<>();
        try {
            identifiers = readPluginData();
        } catch (IOException e) {
            log(e, Level.SEVERE, "Unable to access plugin.yml file, many features of this plugin WILL break.");
        }

        //removes any plugin from plugin data that have been deleted
        PluginLoop:
        for (PluginData data : identifiers) {

            for (File file : pluginFolder.listFiles()) {
                if (file.isDirectory()) continue;
                if (!Files.getFileExtension(file.getName()).equals("jar")) continue;

                if (data.getFileName().equals(file.getName())) {
                    continue PluginLoop;
                }
            }
            try {
                removePluginData(data.getName());
            } catch (NoSuchPluginException e) {
                log(e, Level.WARNING, "Error deleting removed plugin from pluginData.");
            } catch (IOException e) {
                log(e, Level.WARNING, "Unable to access pluginData file to remove "+data.getName()+". This will cause the plugn to appear present when interacting with "+this.getDataFolder().getName());
            }
        }

        //adds any new plugins to the pluginData

        pluginFolder = new File(getDataFolder().getParent());
        for (File file : pluginFolder.listFiles()) {
            if (file.isDirectory()) continue;
            if (!Files.getFileExtension(file.getName()).equals("jar")) continue;
            try {
                appendPluginData(file);
            } catch (IOException e) {
                log(e, Level.WARNING, "Unable to access plugin.yml file for \"" + file.getName() + "\". \"" + file.getName() + "\" won't work for many features of this plugin.");
            }
        }

        identifiers = new ArrayList<>();
        try {
            identifiers = readPluginData();
        } catch (IOException e) {
            log(e, Level.SEVERE, "Unable to access plugin.yml file, many features of this plugin WILL break.");
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
                                    InputStream inputStream = new URL(downloadData.getUrl()).openStream();
                                    ReadableByteChannel rbc = Channels.newChannel(inputStream);
                                    FileOutputStream fos = new FileOutputStream(file);
                                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                    fos.close();
                                    rbc.close();
                                    inputStream.close();
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
                                    zip.close();
                                    break dependencyCheck;
                                }
                            }
                            zip.close();
                        } catch (Exception e) {
                            log(e, Level.WARNING, "Error checking plugin name from \"plugin.yml\" for \""+file.getName()+"\". Skipping possible dependency.");
                        }
                    }

                    //if one of the dependencies matched teh required one it moves it to the ./plugins folder and deletes the rest of the plugins
                    try {
                        if (dependency != null) FileUtils.moveFile(dependency, new File(Path.of(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath()).getParent().toString() + File.separator + dependency.getName()));
                        FileUtils.deleteDirectory(pluginStore);
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
                log(null, Level.WARNING, "Menu closed due to reload to prevent errors.");
            }
        }
    }

    public static void setConfigsToDefault() {
        configs.put("showErrors", true);
        configs.put("showErrorTrace", true);
        configs.put("showOpErrorSummary", true);
    }

    /**
     * Filters out character that are invalid in an url & replaces " " chars with %22.
     * @return Filtered output
     */
    public static String makeValidForUrl(String text) {
        return text.replaceAll("[^A-z0-9s-]", "").replaceAll(" ", "%20");
    }

    /**
     * Easy method for setting some basic item properties.
     * @param item The item to apply the properties to.
     * @param displayName The desired item name.
     * @param lore The desired item lore.
     * @return The modified item.
     */
    public static ItemStack itemProperties(ItemStack item, @Nullable String displayName, @Nullable List<String> lore) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return item;
        if (displayName != null) itemMeta.setDisplayName(displayName);
        if (lore != null) itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
        return item;
    }


    /**
     * Removes a plugin from plugin data.
     * @param pluginName The name of the plugin to remove.
     * @throws NoSuchPluginException Thrown if the plugin cannot be found in the plugin data.
     * @throws IOException Thrown if the pluginData file can't be read from/written to.
     */
    public static void removePluginData(String pluginName) throws NoSuchPluginException, IOException {
        ArrayList<PluginData> pluginData = readPluginData();
        PluginData pluginToRemove = null;

        for (PluginData data : pluginData) {
            if (data.getName().equals(pluginName)) pluginToRemove = data;
        }

        if (pluginToRemove == null) {
            throw new NoSuchPluginException(pluginName + " either not installed or not indexed by " + JavaPlugin.getPlugin(FileManager.class).getName());
        }

        pluginData.remove(pluginToRemove);
        writePluginData(pluginData);
    }

    /**
     * Adds a plugin to pluginData.
     * @param newPlugin The new plugin file to be added.
     * @throws IOException Thrown if there is an error accessing the pluginData file, or if there is an error accessing the plugin.yml file of the new plugin.
     */
    public static void appendPluginData(File newPlugin) throws IOException {
        ArrayList<PluginData> identifiers = readPluginData();

        //reads data from new plugin
        try {
            ZipFile zip = new ZipFile(newPlugin);
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().equals("plugin.yml")) continue;

                StringBuilder out = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append("\n");
                reader.close();

                Yaml yaml = new Yaml();
                PluginData newPluginData = new PluginData(newPlugin.getName(), yaml.load(out.toString()));
                //uses the plugin name to check if it is a copy of an already installed plugin
                for (PluginData data : identifiers) {
                    if (data.getName().equals(newPluginData.getName())) {
                        zip.close();
                        return;
                    }
                }
                identifiers.add(newPluginData);
            }
            zip.close();
        } catch (ZipException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }

        writePluginData(identifiers);
    }

    /**
     * Reads the data from the pluginData.json file
     * @return The data of all the plugins in the pluginData.json file.
     * @throws IOException Thrown if there is an error reading from the pluginData file.
     */
    public static ArrayList<PluginData> readPluginData() throws IOException {
        ArrayList<PluginData> pluginData = new ArrayList<>();
        FileReader fr =  new FileReader(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + ".pluginStore" + File.separator + "pluginData.json");
        JsonReader jr = new JsonReader(fr);
        JsonElement jsonElement = JsonParser.parseReader(jr);
        if (jsonElement.isJsonNull()) return pluginData;
        Gson gsonReader = new Gson();
        for (JsonElement je : jsonElement.getAsJsonArray()) {
            pluginData.add(gsonReader.fromJson(je, PluginData.class));
        }
        jr.close();
        fr.close();
        return pluginData;
    }

    /**
     * Gets the data of a specified plugin.
     * @param pluginName Name of the plugin to get data for.
     * @return Data of the plugin.
     * @throws NoSuchPluginException Thrown if the plugin couldn't be found in the pluginData file.
     * @throws IOException Thrown if there was an error reading from the pluginData file.
     */
    public static PluginData readPluginData(String pluginName) throws NoSuchPluginException, IOException {;
        for (PluginData data : readPluginData()) {
            if (data.getName().equals(pluginName)) return data;
        }
        throw new NoSuchPluginException(pluginName + " either not installed or not indexed by " + JavaPlugin.getPlugin(FileManager.class).getName());
    }

    /**
     * WARNING: this method will overwrite any data stored in the pluginData.json file!<br>
     * If you want to append data use appendPluginData().
     * @param pluginData Plugin data to write to the file.
     * @throws IOException If the plugin data can't be written to the pluginData file.
     */
    public static void writePluginData(ArrayList<PluginData> pluginData) throws IOException {
        File plugins = new File(JavaPlugin.getPlugin(FileManager.class).getDataFolder().getAbsolutePath() + File.separator + ".pluginStore" + File.separator + "pluginData.json");
            GsonBuilder gson = new GsonBuilder();
            gson.setPrettyPrinting();
            FileWriter fileWriter = new FileWriter((plugins));
            gson.create().toJson(pluginData, fileWriter);
            fileWriter.close();
    }


    /**
     * Sends log message to console and all online op players.
     */
    public static void log(@Nullable Exception e, Level level, String message) {
        if ((Boolean) configs.get("showErrors")) {
            ChatColor colour;
            if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
            else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
            else colour = ChatColor.GREEN;

            Bukkit.getLogger().log(level, MessageFormat.format("[{0}]: {1}" ,JavaPlugin.getPlugin(FileManager.class).getName(), message));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) continue;
                String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(FileManager.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
                if ((Boolean) configs.get("showErrorTrace") && e != null) formattedMessage+=" Please see the console for stack trace.";
                player.sendMessage(formattedMessage);
            }
        }
        if ((Boolean) configs.get("showErrorTrace") && e != null) e.printStackTrace();
    }
    /**
     * Sends log message to specified Player.
     */
    public static void log(@Nullable Exception e, Player player, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
        else colour = ChatColor.GREEN;

        if ((Boolean) configs.get("showErrors")) {
            String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(FileManager.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
            if ((Boolean) configs.get("showErrorTrace") && e != null) formattedMessage+=" Please see the console for stack trace.";
            player.sendMessage(formattedMessage);
        }
    }
    /**
     * Sends log message to specified CommandSender.
     */
    public static void log(@Nullable Exception e, CommandSender sender, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
        else colour = ChatColor.GREEN;

        if ((Boolean) configs.get("showErrors")) {
            String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(FileManager.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
            if ((Boolean) configs.get("showErrorTrace") && e != null) formattedMessage+=" Please see the console for stack trace.";
            sender.sendMessage(formattedMessage);
        }
    }
}
