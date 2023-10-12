package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.commands.FileCommand;
import me.tye.cogworks.commands.PluginCommand;
import me.tye.cogworks.commands.TabComplete;
import me.tye.cogworks.events.SendErrorSummary;
import me.tye.cogworks.util.Log;
import me.tye.cogworks.util.ModrinthSearch;
import me.tye.cogworks.util.Plugins;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.yamlClasses.DependencyInfo;
import me.tye.cogworks.util.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.commands.PluginCommand.*;
import static me.tye.cogworks.util.Util.*;

public final class CogWorks extends JavaPlugin {
//TODO: lang version check
//TODO: mark if plugins were installed by user or as a dependency
//TODO: mark plugins for attempted ADR / when some were deleted to not attempt ADR

//TODO: allow to delete multiple plugins at once - separate by ","?
//TODO: check if lang file exists for string the user entered
//TODO: edit lang options based on available lang files.
//TODO: add configs options for ADR
//TODO: add command to force stop ADR?
//TODO: instead of deleting files, have them be moved to the .temp folder & either deleted upon reload | after a set time
//TODO: Prompt for multiple files per version - i mean the ones where it's got a "primary".
//TODO: allow to install multiple plugins at once when using a url.
//TODO: when using plugin install, if you enter the select number for plugin version quick enough repetitively, the plugin will install twice (only one file will still show up).
//TODO: make to try & install plugins for the correct server version if the server is updated

@Override
public void onEnable() {
  //creates the essential config files
  createFile(getDataFolder(), null, false);
  createFile(configFile, plugin.getResource("config.yml"), true);
  createFile(langFolder, null, false);
  createFile(new File(langFolder.getAbsoluteFile()+File.separator+"eng.yml"), plugin.getResource("langFiles/eng.yml"), true);

  //loads the essential config files
  Util.setConfig(returnFileConfigs(configFile, "config.yml"));
  Util.setLang(returnFileConfigs(new File(langFolder.getAbsoluteFile()+File.separator+Util.getConfig("lang")+".yml"), "langFiles/"+Util.getConfig("lang")+".yml"));

  //deletes temp if present
  try {
    FileUtils.deleteDirectory(temp);
  } catch (IOException e) {
    new Log(null, "exceptions.tempClear");
  }

  //creates the other files
  createFile(dataStore, null, false);
  createFile(temp, null, false);
  createFile(ADR, null, false);

  createFile(pluginDataFile, null, true);

  //hides non-config files
  try {
    java.nio.file.Files.setAttribute(Path.of(dataStore.getAbsolutePath()), "dos:hidden", true);
    java.nio.file.Files.setAttribute(Path.of(temp.getAbsolutePath()), "dos:hidden", true);
  } catch (Exception ignore) {
  }

  Util.reloadPluginData();

  //ADR
  ArrayList<PluginData> identifiers = new ArrayList<>();
  try {
    identifiers = Plugins.readPluginData();
  } catch (IOException e) {
    new Log("exceptions.noAccessPluginYML", Level.SEVERE, e).log();
  }

  //checks for uninstalled dependencies
  HashMap<DependencyInfo,PluginData> unmetDependencies = new HashMap<>();
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
      private File ADRStore;
      private HashMap<DependencyInfo,PluginData> unmetDependencies;

      public Runnable init(DependencyInfo unmetDepInfo, File pluginStore, HashMap<DependencyInfo,PluginData> unmetDependencies) {
        this.unmetDepInfo = unmetDepInfo;
        //so multiple threads get their own folder.
        this.ADRStore = new File(pluginStore.getAbsolutePath()+File.separator+LocalDateTime.now().hashCode());

        this.unmetDependencies = unmetDependencies;
        return this;
      }

      @Override
      public void run() {
        if (!ADRStore.mkdir()) {
          new Log("ADR.fail", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();
          return;
        }

        String unmetDepName = this.unmetDepInfo.getName();
        String unmetDepVersion = this.unmetDepInfo.getVersion();
        if (unmetDepVersion != null) return;
        ArrayList<JsonObject> validPluginKeys;
        HashMap<JsonObject,JsonArray> validPlugins;

        new Log("ADR.attempting", Level.WARNING, null).setDepName(unmetDepName).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();

        //searches the dependency name on modrinth
        ModrinthSearch search = modrinthSearch(null, null, unmetDepName);
        validPluginKeys = search.getValidPluginKeys();
        validPlugins = search.getValidPlugins();
        if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) {
          new Log("ADR.fail", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();
          return;
        }

        //gets the urls to download from
        ExecutorService executorService = Executors.newCachedThreadPool();
        ArrayList<Future<JsonObject>> match = new ArrayList<>();

        for (JsonObject jo : validPluginKeys) {
          JsonObject latestValidPlugin = null;

          //gets the latest version of a compatible plugin
          for (JsonElement je : validPlugins.get(jo)) {
            if (latestValidPlugin == null) {
              latestValidPlugin = je.getAsJsonObject();
            } else {
              Date newDT = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(je.getAsJsonObject().get("date_published").getAsString())));
              Date dt = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(latestValidPlugin.get("date_published").getAsString())));
              if (dt.after(newDT)) {
                latestValidPlugin = je.getAsJsonObject();
              }
            }
          }

          if (latestValidPlugin == null) {
            new Log("ADR.fail", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();
            return;
          }

          JsonArray files = latestValidPlugin.get("files").getAsJsonArray();
          int primaryIndex = 0;
          int i = 0;
          for (JsonElement je : files) {
            if (je.getAsJsonObject().get("primary").getAsBoolean()) primaryIndex = i;
            i++;
          }

          final int givenIndex = primaryIndex;
          final JsonObject versionInfo = latestValidPlugin;

          //installs possible dependencies & checks if they resolve the missing dependency.
          match.add(executorService.submit(() -> {
            JsonObject downloadInfo = files.get(givenIndex).getAsJsonObject();
            File dependecyFile = new File(ADRStore.getAbsolutePath()+File.separator+downloadInfo.get("filename").getAsString());
            try {
              InputStream inputStream = new URL(downloadInfo.get("url").getAsString()).openStream();
              ReadableByteChannel rbc = Channels.newChannel(inputStream);
              FileOutputStream fos = new FileOutputStream(dependecyFile);
              fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
              fos.close();
              rbc.close();
              inputStream.close();
            } catch (IOException e) {
              new Log("ADR.downloadingErr", Level.WARNING, e).setFileName(dependecyFile.getName()).log();
            }

            try {
              ZipFile zip = new ZipFile(dependecyFile);
              for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().equals("plugin.yml")) continue;

                StringBuilder out = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append("\n");
                reader.close();

                Yaml yaml = new Yaml();
                Map<String,Object> yamlData = yaml.load(out.toString());
                if (yamlData.get("name").equals(unmetDepName)) {
                  zip.close();
                  FileUtils.moveFile(dependecyFile, new File(Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+dependecyFile.getName()));
                  new Log("ADR.success", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).setDepName((String) yamlData.get("name")).log();
                  Util.reloadPluginData();
                  return versionInfo;
                }
              }
              zip.close();
            } catch (Exception e) {
              new Log("ADR.pluginYMLCheck", Level.WARNING, e).setFileName(dependecyFile.getName()).log();
            }
            return null;
          }));

        }

        executorService.shutdown();
        try {
          if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
            new Log("ADR.threadTime", Level.WARNING, null).log();
            new Log("ADR.fail", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();
            return;
          }
        } catch (InterruptedException e) {
          new Log("ADR.threadTime", Level.WARNING, e).log();
          new Log("ADR.fail", Level.WARNING, null).setFileName(unmetDependencies.get(unmetDepInfo).getName()).log();
          return;
        }

        //gets dependencies for the dependency installed
        for (Future<JsonObject> future : match) {
          try {
            JsonObject dependency = future.get();
            if (dependency == null) continue;

            HashMap<String,JsonArray> depDeps = getModrinthDependencies(null, "ADR", dependency);
            if (!depDeps.isEmpty()) {
              for (JsonArray plugins : depDeps.values()) {
                if (plugins.isEmpty()) continue;
                installModrinthPlugin(null, null, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray());
              }
            }
          } catch (InterruptedException | ExecutionException e) {
            new Log("ADR.getErr", Level.WARNING, e).log();
          }
        }

        try {
          FileUtils.deleteDirectory(ADRStore);
        } catch (IOException e) {
          new Log("ADR.cleanUpPossiblePlugins", Level.WARNING, e).setFilePath(ADRStore.getAbsolutePath()).log();
        }
      }
    }.init(unmetDepInfo, ADR, unmetDependencies)).start();
  }

  //checks for new lang files & installs them.
  try {
    HashMap<String,Object> pluginMap = getKeysRecursive(new Yaml().load(new String(getResource("plugin.yml").readAllBytes())));
    String indexText = new String(new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/dev/langFiles/index.yml").openStream().readAllBytes());
    HashMap<String,Object> indexMap = getKeysRecursive(new Yaml().load(indexText));

    String files = String.valueOf(indexMap.get(String.valueOf(pluginMap.get("version"))));
    if (!files.equals("null")) {
      files = files.substring(0, files.length()-1).substring(1);
      String[] filesNames = files.split(", ");

      for (String fileName : filesNames) {
        File langFile = new File(langFolder.getAbsolutePath()+File.separator+fileName);
        if (langFile.exists()) continue;

        try {
          createFile(langFile, new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/dev/langFiles/"+pluginMap.get("version")+"/"+fileName).openStream(), true);
          new Log("info.newLang", Level.WARNING, null).setFileName(fileName).log();
        } catch (IOException e) {
          new Log("exceptions.newLangInstall", Level.WARNING, e).setFileName(langFile.getName()).setUrl("https://raw.githubusercontent.com/Mapty231/CogWorks/dev/langFiles/"+pluginMap.get("version")+"/"+fileName).log();
        }
      }
    }

  } catch (IOException e) {
    new Log("exceptions.newLangCheck", Level.WARNING, e).log();
  }


  //Commands
  Objects.requireNonNull(getCommand("plugin")).setExecutor(new PluginCommand());
  Objects.requireNonNull(getCommand("file")).setExecutor(new FileCommand());

  Objects.requireNonNull(getCommand("plugin")).setTabCompleter(new TabComplete());
  Objects.requireNonNull(getCommand("file")).setTabCompleter(new TabComplete());

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
      new Log(player, "info.menuClose").log();
    }
  }
}

/**
 Copies the content of an internal file to an external one.
 @param file     External file destination
 @param resource Input stream for the data to write, or null if target is an empty file/dir. */
public static void createFile(File file, @Nullable InputStream resource, boolean isFile) {
  try {
    if (!file.exists()) {
      if (isFile) {
        if (!file.createNewFile()) throw new IOException();
      } else if (!file.mkdir()) throw new IOException();

      if (resource != null) {
        String text = new String(Objects.requireNonNull(resource).readAllBytes());
        FileWriter fw = new FileWriter(file);
        fw.write(text);
        fw.close();
      }
    }
  } catch (IOException | NullPointerException e) {
    new Log("exceptions.fileCreation", Level.SEVERE, e).setFilePath(file.getAbsolutePath()).log();
  }
}

/**
 Reads the data from an external specified yaml file and returns the data in a hashmap of Key, Value. Appending any missing values to the external file, making use of the resourcePath of the file inside the jar.
 @param externalFile External config file.
 @param resourcePath Path to the internal file from the resource folder.
 @return The data from the external file with any missing values being loaded in as defaults. */
public static HashMap<String,Object> returnFileConfigs(File externalFile, String resourcePath) {
  HashMap<String,Object> loadedValues;

  try {
    //reads data from config file and formats it
    FileReader fr = new FileReader(externalFile);
    HashMap<String,Object> unformattedloadedValues = new Yaml().load(fr);
    fr.close();
    if (unformattedloadedValues == null) unformattedloadedValues = new HashMap<>();

    loadedValues = getKeysRecursive(unformattedloadedValues);
    HashMap<String,Object> defaultValues = getKeysRecursive(getDefault(resourcePath));

    //checks if there is a key missing in the file
    if (loadedValues.keySet().containsAll(defaultValues.keySet())) return loadedValues;

    //gets the missing keys
    HashMap<String,Object> missing = new HashMap<>();
    for (String key : defaultValues.keySet()) {
      if (loadedValues.containsKey(key)) continue;
      missing.put(key, defaultValues.get(key));
    }

    StringBuilder toAppend = new StringBuilder();
    InputStream is = plugin.getResource(resourcePath);
    if (is == null) return new HashMap<>();
    Object[] internalFileText = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8).lines().toArray();


    //appends the missing keys with default values and comments that are above them in the default file.
    for (String missingKey : missing.keySet()) {
      toAppend.append("\n");

      if (missingKey.contains(".")) {
        toAppend.append(missingKey).append(": \"").append(defaultValues.get(missingKey).toString().replace("\"", "\\\"")).append("\"");
      } else {
        //searches though internal file to retrieve keys, values,  & comments
        for (int i = 0; i < internalFileText.length; i++) {
          if (!internalFileText[i].toString().startsWith(missingKey)) continue;
          //search up for start of comments
          int ii = 0;
          while (i+ii-1 > 0 && internalFileText[i+ii-1].toString().startsWith("#")) {
            ii--;
          }
          //appends all of the comments in correct order
          while (ii < 0) {
            toAppend.append(internalFileText[i+ii]).append("\n");
            ii++;
          }
          toAppend.append(internalFileText[i].toString());
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
    new Log("exceptions.errorWritingConfigs", Level.SEVERE, e).log();
  }
  return loadedValues;
}

/**
 @param filepath Path to the file inside the resource folder.
 @return The default YAML values of the resource. */
public static HashMap<String,Object> getDefault(String filepath) {
  InputStream is = plugin.getResource(filepath);
  if (is != null) return new Yaml().load(is);
  return new HashMap<>();
}

/**
 Filters out character that are invalid in an url & replaces " " chars with %22.
 @return Filtered output */
public static String makeValidForUrl(String text) {
  return text.replaceAll("[^A-z0-9s-]", "").replaceAll(" ", "%20");
}

/**
 Easy method for setting some basic item properties.
 @param item        The item to apply the properties to.
 @param displayName The desired item name.
 @param lore        The desired item lore.
 @param identifier  Gives an item a persistent data with the tag "identifier". This is used to uniquely identify items when using guis.
 @return The modified item. */
public static ItemStack itemProperties(ItemStack item, @Nullable String displayName, @Nullable List<String> lore, @Nullable String identifier) {
  ItemMeta itemMeta = item.getItemMeta();
  if (itemMeta == null) return item;
  if (displayName != null) itemMeta.setDisplayName(displayName);
  if (lore != null) itemMeta.setLore(lore);
  if (identifier == null) identifier = "";
  itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING, identifier);
  item.setItemMeta(itemMeta);
  return item;
}

}
