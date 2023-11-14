package me.tye.cogworks.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.CogWorks;
import me.tye.cogworks.util.customObjects.ChatParams;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import me.tye.cogworks.util.customObjects.dataClasses.DeletePending;
import me.tye.cogworks.util.customObjects.dataClasses.DependencyInfo;
import me.tye.cogworks.util.customObjects.dataClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.util.Plugins.installModrinthDependencies;
import static me.tye.cogworks.util.Plugins.modrinthSearch;

public class Util {

//constants
/**
 This plugin. */
public static final JavaPlugin plugin = JavaPlugin.getPlugin(CogWorks.class);

/**The plugins folder for the server.*/
public static final File pluginFolder = new File(plugin.getDataFolder().getParentFile().getAbsolutePath());
/**The folder the server jar is in.*/
public static final File serverFolder = new File(pluginFolder.getParentFile().getAbsolutePath());
/**The config file for this plugin.*/
public static final File configFile = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+"config.yml");
/**The persistent data folder for this plugin.*/
public static final File dataStore = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+".data");
/**The persistent data for the plugins indexed by CogWorks.*/
public static final File pluginDataFile = new File(dataStore.getAbsolutePath()+File.separator+"pluginData.json");
/**The folder for lang files.*/
public static final File langFolder = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+"langFiles");
/**The folder for files that will be deleted on next reload/restart.*/
public static final File temp = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+".temp");
/**The folder used for ADR, this will be emptied on reload/restart.*/
public static final File ADR = new File(temp.getAbsolutePath()+File.separator+"ADR");
/**
 The file for storing data on the files have been deleted. */
public static final File deleteData = new File(dataStore.getAbsolutePath()+File.separator+"deleteData.json");
/**
 The folder for storing files that have been deleted. */
public static final File deletePending = new File(dataStore.getAbsolutePath()+File.separator+"deletePending");

public static final String mcVersion = Bukkit.getVersion().split(": ")[1].substring(0, Bukkit.getVersion().split(": ")[1].length()-1);
public static String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();


//lang & config
private static HashMap<String,Object> lang = new HashMap<>();
private static HashMap<String,Object> config = new HashMap<>();

/**
 Sets the lang responses the to the given HashMap.
 @param lang New lang map. */
public static void setLang(HashMap<String,Object> lang) {
  Util.lang = getKeysRecursive(lang);
}

/**
 Sets the config responses to the given HashMap.
 @param config New config map. */
public static void setConfig(HashMap<String,Object> config) {
  Util.config = getKeysRecursive(config);
}

/**
 Formats the Map returned from Yaml.load() into a hashmap where the exact key corresponds to the value.<br>
 E.G: key: "example.response" value: "test".
 @param baseMap The Map from Yaml.load().
 @return The formatted Map. */
public static HashMap<String,Object> getKeysRecursive(Map<?,?> baseMap) {
  HashMap<String,Object> map = new HashMap<>();
  if (baseMap == null)
    return map;
  for (Object key : baseMap.keySet()) {
    Object value = baseMap.get(key);
    if (value instanceof Map<?,?> subMap) {
      map.putAll(getKeysRecursive(String.valueOf(key), subMap));
    } else {
      map.put(String.valueOf(key), String.valueOf(value));
    }
  }
  return map;
}

/**
 Formats the Map returned from Yaml.load() into a hashmap where the exact key corresponds to the value.
 @param keyPath The path to append to the starts of the key. (Should only be called internally).
 @param baseMap The Map from Yaml.load().
 @return The formatted Map. */
public static HashMap<String,Object> getKeysRecursive(String keyPath, Map<?,?> baseMap) {
  if (!keyPath.isEmpty())
    keyPath += ".";
  HashMap<String,Object> map = new HashMap<>();
  for (Object key : baseMap.keySet()) {
    Object value = baseMap.get(key);
    if (value instanceof Map<?,?> subMap) {
      map.putAll(getKeysRecursive(keyPath+key, subMap));
    } else {
      map.put(keyPath+key, String.valueOf(value));
    }
  }
  return map;
}

/**
 Gets value from loaded lang file.
 If no external value for this key can be found it will attempt to get the lang response from the internal file. If there is no internal file for the selected lang then it will fall back to english.<br>
 If there is still no response found for the key
 @param key     Key to the value from the loaded lang file.
 @param replace Should be inputted in "valueToReplace0", valueToReplaceWith0", "valueToReplace1", valueToReplaceWith2"... etc
 @return The lang response with the specified values replaced. */
public static String getLang(String key, String... replace) {
  String rawResponse = String.valueOf(lang.get(key));
  //if config doesn't contain the key it checks if it is present in default config files.
  if (rawResponse == null || rawResponse.equals("null")) {
    InputStream is = plugin.getResource("langFiles/"+getConfig("lang")+".yml");
    HashMap<String,Object> defaultLang;

    if (is != null)
      defaultLang = getKeysRecursive(new Yaml().load(is));
    else
      defaultLang = getKeysRecursive(new Yaml().load(plugin.getResource("langFiles/eng.yml")));

    rawResponse = String.valueOf(defaultLang.get(key));

    if (rawResponse == null || rawResponse.equals("null")) {
      if (key.equals("exceptions.noSuchResponse"))
        return "Unable to get key \"exceptions.noSuchResponse\" from lang file. This message is in english to prevent a stack overflow error.";
      else
        rawResponse = getLang("exceptions.noSuchResponse", "key", key);
    }

    lang.put(key, defaultLang.get(key));
    new Log("exceptions.noExternalResponse", Level.WARNING, null).setKey(key).log();
  }

  for (int i = 0; i <= replace.length-1; i += 2) {
    if (replace[i+1] == null || replace[i+1].equals("null"))
      continue;
    rawResponse = rawResponse.replaceAll("\\{"+replace[i]+"}", replace[i+1]);
  }

  //the A appears for some reason?
  return rawResponse.replaceAll("Â§", "§");
}

/**
 Gets a value from the config file.<br>
 If no external value can be found it will fall back onto the default internal value. If there is still no value it will return true and log a severe error.
 @param key Key for the config to get the value of.
 @return The value from the file. */
public static <T> T getConfig(String key) {
  Object response;
  //if config doesn't contain the key it checks if it is present in default config files.
  if (!config.containsKey(key)) {
    HashMap<String,Object> defaultConfig = getKeysRecursive(new Yaml().load(plugin.getResource("config.yml")));
    response = defaultConfig.get(key);

    if (response == null) {
      new Log("exceptions.noSuchResponse", Level.SEVERE, null).setKey(key).log();
      return (T) Boolean.TRUE;
    }

    config.put(key, response);
    new Log("exceptions.noExternalResponse", Level.WARNING, null).setKey(key).log();

  } else
    response = String.valueOf(config.get(key));

  switch (key) {
  case "lang", "keepDeleted.time", "keepDeleted.size" -> {
    return (T) String.valueOf(response);
  }
  case "showErrorTrace", "showOpErrorSummary", "ADR" -> {
    return (T) Boolean.valueOf(String.valueOf(response));
  }
  }

  new Log("exceptions.noConfigMatch", Level.WARNING, null).setKey(key).log();
  return (T) Boolean.TRUE;
}


/**
 Warning! The plugin needs to be installed to the file path for this to work!
 @param pluginJar File of the plugin to get the yml of
 @return The content of the yml file. */
public static Map<String,Object> getYML(File pluginJar) {
  try (ZipFile zip = new ZipFile(pluginJar)) {
    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
      ZipEntry entry = e.nextElement();
      if (!(entry.getName().equals("plugin.yml") || entry.getName().equals("paper-plugin.yml"))) {
        continue;
      }

      StringBuilder out = new StringBuilder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
      String line;
      while ((line = reader.readLine()) != null)
        out.append(line).append("\n");
      reader.close();

      Yaml yaml = new Yaml();
      return yaml.load(out.toString());
    }
  } catch (Exception e) {
    new Log("exceptions.noAccessPluginYML", Level.WARNING, e).log();
  }
  return new HashMap<>();
}

/**
 Parses the value a user sent for selecting a single choice for a user interacting with the chat system.
 @return -1 if there was an error. Else the value parsed. */
public static int parseNumInput(CommandSender sender, String state, String message, String name, int max, int min) {
  if (message.equals("q")) {
    response.remove(name);
    new Log(sender, state, "quit").log();
    return -1;
  }

  int chosen;
  try {
    chosen = Integer.parseInt(message);
  } catch (NumberFormatException e) {
    new Log(sender, state, "NAN").log();
    return -1;
  }
  if (chosen > max || chosen < min) {
    new Log(sender, state, "NAN").log();
    return -1;
  }
  return chosen;
}

/**
 Parses the int value a user sent for selecting a single choice when interacting with the chat system.
 @return -1 if there was an error or the user quit. Else the value parsed is returned. */
public static int parseNumInput(CommandSender sender, String state, String message) {
  if (message.equals("q")) {
    clearResponse(sender);
    new Log(sender, state, "quit").log();
    return -1;
  }

  try {
    return Integer.parseInt(message);
  } catch (NumberFormatException e) {
    new Log(sender, state, "NAN").log();
    return -1;
  }
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
  if (itemMeta == null)
    return item;
  if (displayName != null)
    itemMeta.setDisplayName(displayName);
  if (lore != null)
    itemMeta.setLore(lore);
  if (identifier == null)
    identifier = "";
  itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING, identifier);
  item.setItemMeta(itemMeta);
  return item;
}

/**
 @param filepath Path to the file inside the resource folder.
 @return The default YAML values of the resource. */
public static HashMap<String,Object> getDefault(String filepath) {
  InputStream is = plugin.getResource(filepath);
  if (is != null)
    return new Yaml().load(is);
  return new HashMap<>();
}

/**
 Encodes the given string URL into a valid URL.
 @return The valid URL.
 @throws MalformedURLException When the given URL can't be encoded to a valid URL. */
public static URL encodeUrl(@NotNull String text) throws MalformedURLException {
  try {
    URL url = new URL(URLDecoder.decode(text, StandardCharsets.UTF_8));
    URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
    return uri.toURL();

  } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
    throw new MalformedURLException();
  }
}

/**
 Copies the content of an internal file to an external one.
 @param file     External file destination
 @param resource Input stream for the data to write, or null if target is an empty file/dir. */
public static void createFile(File file, @Nullable InputStream resource, boolean isFile) {
  if (file.exists())
    return;

  try {
    if (isFile) {
      if (!file.createNewFile())
        throw new IOException();
    }
    else if (!file.mkdir())
      throw new IOException();

    if (resource != null) {
      String text = new String(Objects.requireNonNull(resource).readAllBytes());
      FileWriter fw = new FileWriter(file);
      fw.write(text);
      fw.close();
    }

  } catch (IOException | NullPointerException e) {
    new Log("exceptions.fileCreation", Level.SEVERE, e).setFilePath(file.getAbsolutePath()).log();
  }
}

/**
 Reads the data from an external specified yaml file and returns the data in a hashmap of Key, Value. Appending any missing values to the external file, making use of the resourcePath of the file inside the jar.<br>
 If the resource path doesn't return any files then no repairing will be done to the file.
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
    if (unformattedloadedValues == null)
      unformattedloadedValues = new HashMap<>();

    loadedValues = getKeysRecursive(unformattedloadedValues);
    HashMap<String,Object> defaultValues = getKeysRecursive(getDefault(resourcePath));

    //checks if there is a key missing in the file
    if (loadedValues.keySet().containsAll(defaultValues.keySet()))
      return loadedValues;

    //gets the missing keys
    HashMap<String,Object> missing = new HashMap<>();
    for (String key : defaultValues.keySet()) {
      if (loadedValues.containsKey(key))
        continue;
      missing.put(key, defaultValues.get(key));
    }

    StringBuilder toAppend = new StringBuilder();
    InputStream is = plugin.getResource(resourcePath);
    if (is == null)
      return new HashMap<>();
    Object[] internalFileText = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8).lines().toArray();


    //appends the missing keys with default values and comments that are above them in the default file.
    for (String missingKey : missing.keySet()) {
      toAppend.append("\n");

      if (missingKey.contains(".")) {
        toAppend.append(missingKey).append(": \"").append(defaultValues.get(missingKey).toString().replace("\"", "\\\"")).append("\"");
      } else {
        //searches though internal file to retrieve keys, values,  & comments
        for (int i = 0; i < internalFileText.length; i++) {
          if (!internalFileText[i].toString().startsWith(missingKey))
            continue;
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
    if (resourcePath.equals("config.yml"))
      Util.setConfig(getDefault(resourcePath));
    new Log("exceptions.errorWritingConfigs", Level.SEVERE, e).setFilePath(externalFile.getAbsolutePath()).log();
  }
  return loadedValues;
}

/**
 Used to check if the given collection contains the given plugin data within it by checking if it contains an object with a name that is the same as the given plugins name. This method is required as checking if a collection contains the given plugin data will return false, even if it contains pluginData with the same name.
 @param collection The given collection
 @param pluginData The given plugin data.
 @return True if the collection contains plugin data with the same name as the given plugin data.<br> Otherwise, false will be returned. */
public static boolean containsPluginName(Collection<PluginData> collection, PluginData pluginData) {
  for (PluginData data : collection) {
    if (data.getName().equals(pluginData.getName()))
      return true;
  }
  return false;
}

/**
 Sets the current state of the user when interacting with the CogWorks chat system.
 @param sender    The command sender.
 @param newParams The new params to set. */
public static void setResponse(CommandSender sender, ChatParams newParams) {
  if (sender instanceof Player) {
    response.put(sender.getName(), newParams);
  } else {
    response.put("~", newParams);
  }
}

/**
 Removes the current user from the CogWorks chat system.
 @param sender The command sender. */
public static void clearResponse(CommandSender sender) {
  if (sender instanceof Player) {
    response.remove(sender.getName());
  } else {
    response.remove("~");
  }
}

public static void delete(@NotNull File file) throws IOException {
  if (!file.exists()) {
    return;
  }

  new DeletePending(Path.of(file.getAbsolutePath()).normalize()).append();
}

/**
 Parses the string given in the format "{number}{unit}". There can be any length of number-unit pares.<br>
 Units:<br>
 g - gigabyte.<br>
 m - megabyte.<br>
 k - kilobytes.<br>
 b - bytes.
 @param sizeString The size string.
 @return The parsed size represented by the given size string. */
public static long parseSize(String sizeString) {
  long size = 0;
  char[] sizeChars = sizeString.toLowerCase().toCharArray();

  for (int i = 0; i < sizeChars.length; i++) {

    switch (sizeChars[i]) {
    case 'g' -> {
      size += getProceeding(0L, sizeChars, i)*1024*1024*1024;
    }

    case 'm' -> {
      size += getProceeding(0L, sizeChars, i)*1024*1024;
    }

    case 'k' -> {
      size += getProceeding(0L, sizeChars, i)*1024;
    }

    case 'b' -> {
      size += getProceeding(0L, sizeChars, i);
    }

    }
  }

  return size;
}

/**
 Gets all the number chars before the unit specifier.
 The unit specifier would be a letter denoting a value. E.g: w for week.
 @param time      Current stored value for this unit.
 @param timeChars The array to parse.
 @param index     The index in the array that the unit specifier was found.
 @return The number the user entered before the unit specifier. */
public static long getProceeding(long time, char[] timeChars, int index) {

  for (int ii = 1; Character.isDigit(timeChars[index-ii]); ii++) {

    int parsedVal = Integer.parseInt(String.valueOf(timeChars[index-ii]));
    //sets the number at the next order of magnitude to the parsed one
    time = (long) (time+parsedVal*(Math.pow(10d, ii-1)));

    //if the index to get would be below 0 ends the loop
    if ((index-ii)-1 < 0) {
      break;
    }
  }

  return time;
}

public static void deleteFileOrFolder(File file) throws IOException {
  if (file.isDirectory()) {
    FileUtils.deleteDirectory(file);
  }
  else {
    Files.delete(file.toPath());
  }
}

public static void deleteFileOrFolder(Path pathToFile) throws IOException {
  if (pathToFile.toFile().isDirectory()) {
    FileUtils.deleteDirectory(pathToFile.toFile());
  }
  else {
    Files.delete(pathToFile);
  }
}

/**
 Automatic dependency resolution, also known as ADR, checks for any missing dependencies that the given plugin has.<br>
 If there are missing deps then, Modrinth will be searched with the plugin name of the missing dependency and downloads the first ten results.
 It will then check all the plugin names of all the plugins installed by ADR to see if any match the missing dependency name.<br>
 If one does it will be moved into the plugins folder and a success log will be sent. Otherwise, a fail log will be sent.
 * @param sender The command sender to send the logs to.
 * @param data The plugin data to check for missing dependencies of.
 */
public static void automaticDependencyResolution(CommandSender sender, PluginData data) {

  List<DependencyInfo> unmetDependencies;
  try {
    unmetDependencies = data.getUnmetDependencies();
  } catch (IOException e) {
    new Log(sender, "ADR.genFail", Level.WARNING, null).setPluginName(data.getName()).log();
    return;
  }

  File ADRStore = new File(ADR.getAbsolutePath()+File.separator+LocalDateTime.now().hashCode());

  //attempts to resolve unmet dependencies
  for (DependencyInfo unmetDep : unmetDependencies) {

    ArrayList<String> dependingNames = new ArrayList<>();
    try {
      unmetDep.getUsers().forEach((plugin) -> dependingNames.add(plugin.getName()));
    } catch (IOException e) {
      new Log(sender, "ADR.genFail", Level.WARNING, null).setPluginName(data.getName()).log();
      return;
    }

    if (!ADRStore.mkdir()) {
      new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
      return;
    }

    String unmetDepName = unmetDep.getName();
    String unmetDepVersion = unmetDep.getVersion();
    //TODO: remember this when i add versions
    if (unmetDepVersion != null) {
      new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
      return;
    }

    new Log(sender, "ADR.attempting", Level.WARNING, null).setDepName(unmetDepName).setFileNames(dependingNames).log();

    //searches the dependency name on modrinth
    ModrinthSearch search = modrinthSearch(null, null, unmetDepName);
    ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();
    HashMap<JsonObject,JsonArray> validPlugins = search.getValidPlugins();

    if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) {
      new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
      return;
    }

    //gets the urls to download from
    ExecutorService executorService = Executors.newCachedThreadPool();
    ArrayList<Future<JsonObject>> match = new ArrayList<>();

    for (JsonObject validKey : validPluginKeys) {
      JsonObject latestValidPlugin = null;

      if (validPlugins.get(validKey).isEmpty()) {
        new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
        return;
      }

      //gets the latest version of a compatible plugin
      for (JsonElement je : validPlugins.get(validKey)) {
        if (latestValidPlugin == null) {
          latestValidPlugin = je.getAsJsonObject();
        }
        else {
          Date newDT = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(je.getAsJsonObject().get("date_published").getAsString())));
          Date dt = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(latestValidPlugin.get("date_published").getAsString())));
          if (dt.after(newDT)) {
            latestValidPlugin = je.getAsJsonObject();
          }
        }
      }

      if (latestValidPlugin == null) {
        new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
        return;
      }

      JsonArray files = latestValidPlugin.get("files").getAsJsonArray();
      int primaryIndex = 0;
      int i = 0;
      for (JsonElement je : files) {
        if (je.getAsJsonObject().get("primary").getAsBoolean())
          primaryIndex = i;
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
          new Log(sender, "ADR.downloadingErr", Level.WARNING, e).setFileName(dependecyFile.getName()).log();
        }

        try {
          ZipFile zip = new ZipFile(dependecyFile);
          for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();

            //if the file isn't the plugin data file then continue
            if (!(entry.getName().equals("plugin.yml") || entry.getName().equals("paper-plugin.yml"))) {
              continue;
            }

            //parses the entire content of the file into content
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
            String line;
            while ((line = reader.readLine()) != null) {
              content.append(line).append("\n");
            }
            reader.close();


            Yaml yaml = new Yaml();
            Map<String,Object> yamlData = yaml.load(content.toString());

            if (!yamlData.get("name").equals(unmetDepName)) {
              break;
            }

            zip.close();

            //moves the plugin to plugins folder & indexes the plugin
            File newPlugin = new File(pluginFolder.toPath()+File.separator+dependecyFile.getName());
            FileUtils.moveFile(dependecyFile, newPlugin);
            PluginData.reload(null, "exceptions");
            new Log(sender, "ADR.success", Level.WARNING, null).setFileNames(dependingNames).setDepName((String) yamlData.get("name")).log();

            return versionInfo;
          }

          zip.close();
        } catch (Exception e) {
          new Log(sender, "ADR.pluginYMLCheck", Level.WARNING, e).setFileName(dependecyFile.getName()).log();
        }
        return null;
      }));

    }

    executorService.shutdown();
    //gives the downloads one minuet to completed & be indexed.
    try {
      if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
        new Log(sender, "ADR.threadTime", Level.WARNING, null).log();
        new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
        return;
      }
    } catch (InterruptedException e) {
      new Log(sender, "ADR.threadTime", Level.WARNING, e).log();
      new Log(sender, "ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
      return;
    }

    //gets dependencies for the dependency installed & checks if ADR could resolve the missing dependency.
    boolean failed = true;
    for (Future<JsonObject> future : match) {
      try {
        JsonObject dependency = future.get();
        if (dependency == null) {
          continue;
        }

        failed = !installModrinthDependencies(null, null, dependency, null);
      } catch (InterruptedException | ExecutionException e) {
        new Log(sender, "ADR.getErr", Level.WARNING, e).log();
      }
    }

    //if ADR couldn't resolve the missing dependency then it marks not to try and resolve it for next time
    if (failed) {
      new Log(sender, "ADR.notToRetry", Level.WARNING, null).setDepName(unmetDepName).setPluginNames(dependingNames).log();
      unmetDep.setAttemptADR(false);

      List<PluginData> users;
      try {
        users = unmetDep.getUsers();
      } catch (IOException e) {
        new Log(sender, "ADR.writeNoADR", Level.WARNING, e).setPluginName("depending plugins").setDepName(unmetDepName).log();
        return;
      }

      for (PluginData dependingPlugin : users) {
        try {
          PluginData.modify(dependingPlugin.modifyDependency(unmetDep));
        } catch (IOException e) {
          new Log(sender, "ADR.writeNoADR", Level.WARNING, e).setPluginName(dependingPlugin.getName()).setDepName(unmetDepName).log();
        }
      }

      continue;
    }

    //tries to load the plugin immediately.
    try {
      PluginData metDependency = PluginData.getFromName(unmetDepName);
      if (metDependency == null) {
        continue;
      }

      File newPlugin = new File(pluginFolder.toPath()+metDependency.getFileName());
      Plugin loadedPlugin = Bukkit.getPluginManager().loadPlugin(newPlugin);
      if (loadedPlugin == null) {
        continue;
      }

      Bukkit.getPluginManager().enablePlugin(loadedPlugin);
    } catch (Exception ignore) {}
  }
}

}
