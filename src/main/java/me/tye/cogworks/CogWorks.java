package me.tye.cogworks;

import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.cogworks.commands.FileCommand;
import me.tye.cogworks.commands.PluginCommand;
import me.tye.cogworks.commands.TabComplete;
import me.tye.cogworks.events.SendErrorSummary;
import me.tye.cogworks.util.ModrinthSearch;
import me.tye.cogworks.util.UrlFilename;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.yamlClasses.DependencyInfo;
import me.tye.cogworks.util.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
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

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.commands.PluginCommand.modrinthSearch;
import static me.tye.cogworks.util.Util.getKeysRecursive;

public final class CogWorks extends JavaPlugin {
    //TODO: convert plugin to use lang file

    //TODO: check if dependencies are already met before trying to install them?
    //TODO: convert install modrinth dependencies to use errors, not sender
    //TODO: run install dependencies on plugins installed from auto dependency resolve
    //TODO: add advise on how to fix errors in the error message.
    //TODO: when uninstalling plugins check if any other plugins depend on them.
    //TODO: when using plugin install, if you enter the select number for plugin version quick enough repetitively, the plugin will install twice (only one file will still show up).

    //TODO: Prompt for multiple files per version - i mean the ones where it's got a "primary".
    //TODO: allow to delete multiple plugins at once - separate by ","?
    //TODO: allow to install multiple plugins at once when using a url.

    @Override
    public void onEnable() {

        //Set up required config files
        File configFile = new File(getDataFolder().getAbsolutePath() + File.separator + "config.yml");
        File pluginStore = new File(getDataFolder().getAbsoluteFile() + File.separator + ".pluginStore");
        File plugins = new File(pluginStore.getAbsolutePath() + File.separator + "pluginData.json");

        File langFolder = new File(getDataFolder().getAbsoluteFile() + File.separator + "langFiles");
        File engLang = new File(langFolder.getAbsoluteFile() + File.separator + "eng.yml");

        //creates the essential config files
        createFile(getDataFolder(), null, false);
        createFile(configFile, "config.yml", true);
        createFile(langFolder, null, false);
        createFile(engLang, "langFiles/eng.yml", true);

        //loads the essential config files
        Util.setConfig(returnFileConfigs(configFile, "config.yml"));
        Util.setLang(returnFileConfigs(engLang, "langFiles/"+Util.getConfig("lang")+".yml"));

        //creates the other files
        createFile(pluginStore, null, false);
        createFile(plugins, null, true);


        //clears out leftover files in plugin store dir
        for (File file : pluginStore.listFiles()) {
            if (file.getName().equals(plugins.getName())) continue;
            try {
                if (file.isFile()) if (!file.delete()) throw new IOException();
                if (file.isDirectory()) FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                log(e, Level.WARNING, Util.getLang("exceptions.removeLeftoverFiles", "fileName", getName()));
            }
        }

        File pluginFolder = new File(getDataFolder().getParent());
        ArrayList<PluginData> identifiers = new ArrayList<>();
        try {
            identifiers = readPluginData();
        } catch (IOException e) {
            log(e, Level.SEVERE, Util.getLang("exceptions.noAccessPluginYML"));
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
                log(e, Level.WARNING, Util.getLang("exceptions.deletingRemovedPlugin","fileName", data.getName()));
            } catch (IOException e) {
                log(e, Level.WARNING,  Util.getLang("exceptions.noAccessDeleteRemovedPlugins","fileName", data.getName()));
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
                log(e, Level.WARNING, Util.getLang("exceptions.noAccessNewPlugin","fileName", file.getName()));
            }
        }

        identifiers = new ArrayList<>();
        try {
            identifiers = readPluginData();
        } catch (IOException e) {
            log(e, Level.SEVERE, Util.getLang("exceptions.noAccessPluginYML"));
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

                    log(null, Level.INFO, Util.getLang("info.ADR.attempting", "depName", unmetDepName, "fileName", unmetDependencies.get(unmetDepInfo).getName()));

                    //searches the dependency name on modrinth
                    try {
                        ModrinthSearch search = modrinthSearch(unmetDepName);
                        validPluginKeys = search.getValidPluginKeys();
                        validPlugins = search.getValidPlugins();
                    } catch (MalformedURLException | ModrinthAPIException e) {
                        log(e, Level.WARNING, Util.getLang("exceptions.ADR.ModrinthSearch")+Util.getLang("exceptions.skippingADR", "depName", unmetDepName));
                        return;
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
                                    log(e, Level.WARNING, Util.getLang("exceptions.ADR.downloading", "fileName", file.getName()));
                                }
                            }
                        });
                    }
                    executorService.shutdown();
                    try {
                        if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                            log(null, Level.WARNING, Util.getLang("exceptions.ADR.threadTime")+Util.getLang("exceptions.skippingADR", "depName", unmetDepName));
                            return;
                        }
                    } catch (InterruptedException e) {
                        log(e, Level.WARNING, Util.getLang("exceptions.ADR.threadInterrupted")+Util.getLang("exceptions.skippingADR", "depName", unmetDepName));
                        return;
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
                                    log(null, Level.INFO, Util.getLang("info.ADR.success", "fileName", unmetDependencies.get(unmetDepInfo).getName(), "depName", (String) yamlData.get("name")));
                                    zip.close();
                                    break dependencyCheck;
                                }
                            }
                            zip.close();
                        } catch (Exception e) {
                            log(e, Level.WARNING, Util.getLang("exceptions.ADR.pluginYMLCheck", "fileName", file.getName()));
                        }
                    }

                    //if one of the dependencies matched the required one it moves it to the ./plugins folder and deletes the rest of the plugins
                    try {
                        if (dependency != null) FileUtils.moveFile(dependency, new File(Path.of(JavaPlugin.getPlugin(CogWorks.class).getDataFolder().getAbsolutePath()).getParent().toString() + File.separator + dependency.getName()));
                        FileUtils.deleteDirectory(pluginStore);
                    } catch (IOException e) {
                        log(e, Level.WARNING,  Util.getLang("exceptions.ADR.cleanUpPossiblePlugins", "filePath", pluginStore.getAbsolutePath()));
                    }

                    if (dependency == null) log(null, Level.INFO, Util.getLang("info.ADR.fail", "fileName", unmetDependencies.get(unmetDepInfo).getName()));
                }
            }.init(unmetDepInfo, pluginStore, unmetDependencies)).start();
        }


        //Commands
        getCommand("plugin").setExecutor(new PluginCommand());
        getCommand("file").setExecutor(new FileCommand());

        getCommand("plugin").setTabCompleter(new TabComplete());
        getCommand("file").setTabCompleter(new TabComplete());

        //Listeners
        getServer().getPluginManager().registerEvents(new ChatManager(), this);
        getServer().getPluginManager().registerEvents(new FileGui(), this);
        getServer().getPluginManager().registerEvents(new SendErrorSummary(), this);

    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (position.containsKey(player.getName())) {
                player.closeInventory();
                log(null, player, Level.WARNING, Util.getLang("info.menuClose"));
            }
        }
    }

    /**
     * Copies the content of an internal file to an external one.
     * @param file External file destination
     * @param resourcePath Internal path to resource, or null if target is an empty file/dir.
     */
    public static void createFile(File file, @Nullable String resourcePath, boolean isFile) {
        try {
            if (!file.exists()) {
                if (isFile) {
                    if (!file.createNewFile()) throw new IOException();
                } else
                if (!file.mkdir()) throw new IOException();

                if (resourcePath != null) {
                    String text = new String(JavaPlugin.getPlugin(CogWorks.class).getResource(resourcePath).readAllBytes());
                    FileWriter fw = new FileWriter(file);
                    fw.write(text);
                    fw.close();
                }
            }
        }
        catch (IOException e) {
            log(null, Level.SEVERE, Util.getLang("exceptions.fileCreation", "fileName", file.getName(), "filePath", file.getAbsolutePath()));
        }
    }

    /**
     * Reads the data from an external specified yaml file and returns the data in a hashmap of Key, Value. Appending any missing values to the external file, making use of the resourcePath of the file inside the jar.
     * @param externalFile External config file.
     * @param resourcePath Path to the internal file from the resource folder.
     * @return The data from the external file with any missing values being loaded in as defaults.
     */
    public static HashMap<String, Object> returnFileConfigs(File externalFile, String resourcePath) {
        HashMap<String, Object> loadedValues;

        try {
            //reads data from config file and formats it
            FileReader fr = new FileReader(externalFile);
            HashMap<String, Object> unformattedloadedValues = new Yaml().load(fr);
            fr.close();
            if (unformattedloadedValues == null) unformattedloadedValues = new HashMap<>();

            loadedValues = getKeysRecursive(unformattedloadedValues);
            HashMap<String, Object> defaultValues = getKeysRecursive(getDefault(resourcePath));

            //checks if there is a key missing in the file
            if (loadedValues.keySet().containsAll(defaultValues.keySet())) return loadedValues;

            //gets the missing keys
            HashMap<String, Object> missing = new HashMap<>();
            for (String key : defaultValues.keySet()) {
                if (loadedValues.containsKey(key)) continue;
                missing.put(key, defaultValues.get(key));
            }

            StringBuilder toAppend = new StringBuilder();
            Object[] internalFileText = new String(JavaPlugin.getPlugin(CogWorks.class).getResource(resourcePath).readAllBytes(), StandardCharsets.UTF_8).lines().toArray();

            //appends the missing keys with default values and comments that are above them in the default file.
            for (String missingKey : missing.keySet()) {
                toAppend.append("\n");

                //splits the key into subkeys
                String[] missingKeySequence = missingKey.split("\\.");
                for (int key = 0; key < missingKeySequence.length; key++) {
                    missingKeySequence[key] = "  ".repeat(key)+missingKeySequence[key];
                }

                //searches though internal file to retrieve subkeys, values,  & comments
                int key = 0;
                for (int i = 0; i < internalFileText.length; i++) {
                    if (internalFileText[i].toString().startsWith(missingKeySequence[key])) {
                        if (key+1 < missingKeySequence.length) {
                            key++;
                            continue;
                        }
                        //search up for start of comments
                        int ii = 0;
                        while (i + ii-1 > 0 && internalFileText[i + ii-1].toString().startsWith("#")) {
                            ii--;
                        }
                        //appends all of the comments in correct order
                        while (ii < 0) {
                            toAppend.append(internalFileText[i + ii]).append("\n");
                            ii++;
                        }
                        toAppend.append(missingKey).append(internalFileText[i].toString().replaceAll(missingKeySequence[key], ""));

                    }
                }

            }

            //writes the missing data (if present) to the config file.
            if (!toAppend.isEmpty()) {
                loadedValues.putAll(missing);
                FileWriter fw = new FileWriter(externalFile, true);
                fw.write(toAppend.toString());
                fw.close();
            }
        } catch (Exception e) {
            loadedValues = getKeysRecursive(getDefault(resourcePath));
            if (resourcePath.equals("config.yml")) Util.setConfig(getDefault(resourcePath));
            log(e, Level.SEVERE, Util.getLang("exceptions.errorWritingConfigs"));
        }
        return loadedValues;
    }

    /**
     * @param filepath Path to the file inside the resource folder.
     * @return The default YAML values of the resource.
     */
    public static HashMap<String, Object> getDefault(String filepath) {
        return new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource(filepath));
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
     * @param identifier Gives an item a persistent data with the tag "identifier". This is used to uniquely identify items when using guis.
     * @return The modified item.
     */
    public static ItemStack itemProperties(ItemStack item, @Nullable String displayName, @Nullable List<String> lore, @Nullable String identifier) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return item;
        if (displayName != null) itemMeta.setDisplayName(displayName);
        if (lore != null) itemMeta.setLore(lore);
        if (identifier == null) identifier = "";
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(CogWorks.class), "identifier"), PersistentDataType.STRING, identifier);
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
            throw new NoSuchPluginException(Util.getLang("exceptions.pluginNotRegistered", "pluginName", pluginName));
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
        FileReader fr =  new FileReader(JavaPlugin.getPlugin(CogWorks.class).getDataFolder().getAbsolutePath() + File.separator + ".pluginStore" + File.separator + "pluginData.json");
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
        throw new NoSuchPluginException(Util.getLang("exceptions.pluginNotRegistered", "pluginName", pluginName));
    }

    /**
     * WARNING: this method will overwrite any data stored in the pluginData.json file!<br>
     * If you want to append data use appendPluginData().
     * @param pluginData Plugin data to write to the file.
     * @throws IOException If the plugin data can't be written to the pluginData file.
     */
    public static void writePluginData(ArrayList<PluginData> pluginData) throws IOException {
        File plugins = new File(JavaPlugin.getPlugin(CogWorks.class).getDataFolder().getAbsolutePath() + File.separator + ".pluginStore" + File.separator + "pluginData.json");
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
        if (Util.getConfig("showErrors")) {
            ChatColor colour;
            if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
            else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
            else colour = ChatColor.GREEN;

            Bukkit.getLogger().log(level, MessageFormat.format("[{0}]: {1}" ,JavaPlugin.getPlugin(CogWorks.class).getName(), message));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) continue;
                String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(CogWorks.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
                if ((Boolean) Util.getConfig("showErrorTrace") && e != null) formattedMessage+=Util.getLang("exceptions.seeConsole");
                player.sendMessage(formattedMessage);
            }
        }
        if ((Boolean) Util.getConfig("showErrorTrace") && e != null) e.printStackTrace();
    }
    /**
     * Sends log message to specified Player.
     */
    public static void log(@Nullable Exception e, Player player, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
        else colour = ChatColor.GREEN;

        if (Util.getConfig("showErrors")) {
            String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(CogWorks.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
            if ((Boolean) Util.getConfig("showErrorTrace") && e != null) formattedMessage+=Util.getLang("exceptions.seeConsole");
            player.sendMessage(formattedMessage);
        }
        if ((Boolean) Util.getConfig("showErrorTrace") && e != null) e.printStackTrace();
    }
    /**
     * Sends log message to specified CommandSender.
     */
    public static void log(@Nullable Exception e, CommandSender sender, Level level, String message) {
        ChatColor colour;
        if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
        else if (level.getName().equals("SEVERE")) {colour = ChatColor.RED; SendErrorSummary.severe++;}
        else colour = ChatColor.GREEN;

        if (Util.getConfig("showErrors")) {
            String formattedMessage = MessageFormat.format("[{0}]: {1}{2}: {3}", JavaPlugin.getPlugin(CogWorks.class).getName(), colour, level.getLocalizedName().toLowerCase(Locale.getDefault()), message);
            if ((Boolean) Util.getConfig("showErrorTrace") && e != null) formattedMessage+=Util.getLang("exceptions.seeConsole");
            sender.sendMessage(formattedMessage);
        }

        if ((Boolean) Util.getConfig("showErrorTrace") && e != null) e.printStackTrace();
    }
}
