package me.tye.cogworks.util;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.dataClasses.DependencyInfo;
import me.tye.cogworks.util.customObjects.dataClasses.PluginData;
import me.tye.cogworks.util.customObjects.exceptions.NoSuchPluginException;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static me.tye.cogworks.util.Util.*;

public class StoredPlugins {

/**
 Reads the data from the pluginData.json file
 @return The data of all the plugins in the pluginData.json file.
 @throws IOException Thrown if there is an error reading from the pluginData file. */
public static ArrayList<PluginData> readPluginData() throws IOException {
  ArrayList<PluginData> pluginData = new ArrayList<>();
  FileReader fr = new FileReader(Util.pluginDataFile);
  JsonReader jr = new JsonReader(fr);
  JsonElement jsonElement = JsonParser.parseReader(jr);
  if (jsonElement.isJsonNull())
    return pluginData;
  Gson gsonReader = new Gson();
  for (JsonElement je : jsonElement.getAsJsonArray()) {
    pluginData.add(gsonReader.fromJson(je, PluginData.class));
  }
  jr.close();
  fr.close();
  return pluginData;
}

/**
 Gets the data of a specified plugin.
 @param pluginName Name of the plugin to get data for.
 @return Data of the plugin.
 @throws NoSuchPluginException Thrown if the plugin couldn't be found in the pluginData file.
 @throws IOException           Thrown if there was an error reading from the pluginData file. */
public static PluginData readPluginData(String pluginName) throws NoSuchPluginException, IOException {
  for (PluginData data : readPluginData()) {
    if (data.getName().equals(pluginName))
      return data;
  }
  throw new NoSuchPluginException(getLang("exceptions.pluginNotRegistered", "pluginName", pluginName));
}

/**
 WARNING: this method will overwrite any data stored in the pluginData.json file!<br>
 If you want to append data use appendPluginData().
 @param pluginData Plugin data to write to the file.
 @throws IOException If the plugin data can't be written to the pluginData file. */
public static void writePluginData(Collection<PluginData> pluginData) throws IOException {
  GsonBuilder gson = new GsonBuilder();
  gson.setPrettyPrinting();
  FileWriter fileWriter = new FileWriter(pluginDataFile);
  gson.create().toJson(pluginData, fileWriter);
  fileWriter.close();
}

/**
 Removes a plugin from plugin data.
 @param pluginName The name of the plugin to remove.
 @throws NoSuchPluginException Thrown if the plugin cannot be found in the plugin data.
 @throws IOException           Thrown if the pluginData file can't be read from/written to. */
public static void removePluginData(String pluginName) throws NoSuchPluginException, IOException {
  ArrayList<PluginData> pluginData = readPluginData();
  PluginData pluginToRemove = null;

  for (PluginData data : pluginData) {
    if (data.getName().equals(pluginName))
      pluginToRemove = data;
  }

  if (pluginToRemove == null) {
    throw new NoSuchPluginException(getLang("exceptions.pluginNotRegistered", "pluginName", pluginName));
  }

  pluginData.remove(pluginToRemove);
  writePluginData(pluginData);
}

/**
 Adds a plugin to pluginData.
 @param newPlugin The new plugin file to be added.
 @throws IOException Thrown if there is an error accessing the pluginData file, or if there is an error accessing the plugin.yml file of the new plugin. */
public static void appendPluginData(File newPlugin) throws IOException {
  ArrayList<PluginData> identifiers = readPluginData();

  //reads data from new plugin
  try {
    ZipFile zip = new ZipFile(newPlugin);
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
 Replaces the existing data plugin data of a plugin with the new plugin data.<br>
 For the replace to work the new plugin data must have the same "name" value as the old plugin data, otherwise nothing will be replaced.
 * @param newPluginData The new plugin data to replace the old one with.
 * @throws IOException If there was an error reading from or writing to the plugin data file.
 */
public static void modifyPluginData(PluginData newPluginData) throws IOException {
  List<PluginData> pluginData = readPluginData();

  for (int i = 0; pluginData.size() > i; i++) {
    if (!pluginData.get(i).getName().equals(newPluginData.getName()))
      continue;
    pluginData.set(i, newPluginData);
    break;
  }

  writePluginData(pluginData);
}

/**
 Rescans the ./plugins folder for any changes and updates the stored plugin data accordingly.
 * @param sender The sender to log to or null for no logging.
 * @param state The state the user is in or null for no logging.
 */
public static void reloadPluginData(@Nullable CommandSender sender, @Nullable String state) {
  ArrayList<PluginData> identifiers = new ArrayList<>();
  try {
    identifiers = readPluginData();
  } catch (IOException e) {
    new Log(sender, state, "noAccessPluginYML").setLevel(Level.SEVERE).setException(e).log();
  }

  //removes any plugin from plugin data that have been deleted
  try {
    PluginLoop:
    for (PluginData data : identifiers) {
      for (File file : Objects.requireNonNull(pluginFolder.listFiles())) {
        if (file.isDirectory())
          continue;
        if (!Files.getFileExtension(file.getName()).equals("jar"))
          continue;

        if (data.getFileName().equals(file.getName())) {
          continue PluginLoop;
        }
      }
      try {
        removePluginData(data.getName());
      } catch (NoSuchPluginException e) {
        new Log(sender, state, "deletingRemovedPlugin").setLevel(Level.SEVERE).setException(e).setPluginName(data.getName()).log();
      } catch (IOException e) {
        new Log(sender, state, "noAccessDeleteRemovedPlugins").setLevel(Level.SEVERE).setException(e).setPluginName(data.getName()).log();
      }
    }

    //adds any new plugins to the pluginData
    for (File file : Objects.requireNonNull(pluginFolder.listFiles())) {
      if (file.isDirectory())
        continue;
      if (!Files.getFileExtension(file.getName()).equals("jar"))
        continue;
      try {
        appendPluginData(file);
      } catch (IOException e) {
        new Log(sender, state, "badYmlAccess").setLevel(Level.SEVERE).setException(e).setFileName(file.getName()).log();
      }
    }
  } catch (NullPointerException e) {
    new Log(sender, state, "gettingFilesErr").setLevel(Level.WARNING).setException(e).setFilePath(pluginFolder.getAbsolutePath()).log();
  }
}


/**
 Checks if a plugin is registered.
 @param pluginName Name of the plugin to check.
 @return True only if the plugin was found to be installed & the data could be read. */
public static boolean registered(String pluginName) {
  try {
    ArrayList<PluginData> data = new ArrayList<>(readPluginData());
    for (PluginData plugin : data) {
      if (plugin.getName().equals(pluginName))
        return true;
    }
  } catch (IOException e) {
    new Log("execution.dataReadError", Level.WARNING, e).log();
  }
  return false;
}

/**
 Checks if a plugin is registered by the name specified in the plugin.yml.
 @param pluginJar The plugin.jar file.
 @return True only if the plugin was found to be installed & the data could be read. */
public static boolean registered(File pluginJar) {
  String name = String.valueOf(Util.getYML(pluginJar).get("name"));
  return registered(name);
}

/**
 Gets the plugins that depend on this one to function.
 @param pluginName The name of the plugin to check if anything depends on it.
 @return A list of the pluginData for the plugins that depend on this one to function. */
public static List<PluginData> getWhatDependsOn(String pluginName) {
  ArrayList<PluginData> whatDepends = new ArrayList<>();
  try {

    plugins:
    for (PluginData pluginData : readPluginData()) {
      if (pluginData.isDeletePending())
        continue;
      for (DependencyInfo depInfo : pluginData.getDependencies()) {
        if (depInfo.getName().equals(pluginName)) {
          whatDepends.add(pluginData);
          continue plugins;
        }
      }
    }

    return whatDepends;

  } catch (IOException e) {
    new Log("execution.dataReadError", Level.WARNING, e).log();
  }
  return new ArrayList<>();
}

}
