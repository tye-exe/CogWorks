package me.tye.cogworks.util;

import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import me.tye.cogworks.util.customObjects.VersionGetThread;
import me.tye.cogworks.util.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.yamlClasses.DependencyInfo;
import me.tye.cogworks.util.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.CogWorks.encodeUrl;
import static me.tye.cogworks.util.Util.*;

public class Plugins {

/**
 Checks if a plugin is installed.
 @param name Name of the plugin to check.
 @return true only if the plugin was found to be installed & the data could be read. */
public static boolean registered(String name) {
  try {
    ArrayList<PluginData> data = new ArrayList<>(readPluginData());
    for (PluginData plugin : data) {
      if (plugin.getName().equals(name)) return true;
    }
  } catch (IOException e) {
    new Log("execution.dataReadError", Level.WARNING, e).log();
  }
  return false;
}

/**
 Checks if a plugin is installed by the name specified in the plugin.yml.
 @param pluginJar The plugin.jar file.
 @return true only if the plugin was found to be installed & the data could be read. */
public static boolean registered(File pluginJar) {
  String name = String.valueOf(getYML(pluginJar).get("name"));
  return registered(name);
}


/**
 Warning! The plugin needs to be installed to the file path for this to work!
 @param pluginJar File of the plugin to get the yml of
 @return The content of the yml file. */
public static Map<String,Object> getYML(File pluginJar) {
  try (ZipFile zip = new ZipFile(pluginJar)) {
    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
      ZipEntry entry = e.nextElement();
      if (!entry.getName().equals("plugin.yml")) continue;

      StringBuilder out = new StringBuilder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
      String line;
      while ((line = reader.readLine()) != null) out.append(line).append("\n");
      reader.close();

      Yaml yaml = new Yaml();
      return yaml.load(out.toString());
    }
  } catch (Exception e) {
    new Log("exceptions.noAccessPluginYML", Level.WARNING, e).log();
  }
  return new HashMap<>();
}


public static List<PluginData> getWhatDependsOn(String pluginName) {
  ArrayList<PluginData> whatDepends = new ArrayList<>();
  try {

    plugins:
    for (PluginData pluginData : readPluginData()) {
      for (DependencyInfo depInfo : pluginData.getDependencies()) {
        if (depInfo.getName().equals(pluginName)) {
          whatDepends.add(pluginData);
          continue plugins;
        }
      }
    }

    return whatDepends;

  } catch (IOException e) {
    new Log("execution.dataReadError", Level.WARNING, e);
  }
  return new ArrayList<>();
}


//low level

/**
 Removes a plugin from plugin data.
 @param pluginName The name of the plugin to remove.
 @throws NoSuchPluginException Thrown if the plugin cannot be found in the plugin data.
 @throws IOException           Thrown if the pluginData file can't be read from/written to. */
public static void removePluginData(String pluginName) throws NoSuchPluginException, IOException {
  ArrayList<PluginData> pluginData = readPluginData();
  PluginData pluginToRemove = null;

  for (PluginData data : pluginData) {
    if (data.getName().equals(pluginName)) pluginToRemove = data;
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
 Reads the data from the pluginData.json file
 @return The data of all the plugins in the pluginData.json file.
 @throws IOException Thrown if there is an error reading from the pluginData file. */
public static ArrayList<PluginData> readPluginData() throws IOException {
  ArrayList<PluginData> pluginData = new ArrayList<>();
  FileReader fr = new FileReader(Util.pluginDataFile);
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
 Gets the data of a specified plugin.
 @param pluginName Name of the plugin to get data for.
 @return Data of the plugin.
 @throws NoSuchPluginException Thrown if the plugin couldn't be found in the pluginData file.
 @throws IOException           Thrown if there was an error reading from the pluginData file. */
public static PluginData readPluginData(String pluginName) throws NoSuchPluginException, IOException {
  ;
  for (PluginData data : readPluginData()) {
    if (data.getName().equals(pluginName)) return data;
  }
  throw new NoSuchPluginException(getLang("exceptions.pluginNotRegistered", "pluginName", pluginName));
}

/**
 WARNING: this method will overwrite any data stored in the pluginData.json file!<br>
 If you want to append data use appendPluginData().
 @param pluginData Plugin data to write to the file.
 @throws IOException If the plugin data can't be written to the pluginData file. */
public static void writePluginData(ArrayList<PluginData> pluginData) throws IOException {
  GsonBuilder gson = new GsonBuilder();
  gson.setPrettyPrinting();
  FileWriter fileWriter = new FileWriter(pluginDataFile);
  gson.create().toJson(pluginData, fileWriter);
  fileWriter.close();
}

public static boolean hasConfigFolder(String pluginName) {
  return new File(pluginFolder + File.separator + pluginName).exists();
}


/**
 Installs a plugin from a given url. There are NO restriction on the url used, however ".jar" will always be appended.
 @param sender      The sender to send the log messages to.
 @param state       The path to get the lang responses from.
 @param stringUrl   The Url as a string to download the file from.
 @param fileName    Name of the file to download. Sometimes the file is stored under a name different to the desired file name.
 @param addFileHash If downloading from a non api source the file hash can be added to the end of the file, as many downloads have generic names such as "download".
 @return True if and only if the file installed successfully. */
public static boolean installPluginURL(@Nullable CommandSender sender, String state, String stringUrl, String fileName, Boolean addFileHash) {
  File newPlugin = new File(temp.getAbsolutePath()+File.separator+fileName);
  File destination;
  boolean installed = false;

  try {
    URL Url = encodeUrl(stringUrl);

    if (new File(pluginFolder+File.separator+fileName).exists())
      throw new FileAlreadyExistsException(newPlugin.getAbsolutePath());

    //downloads the file
    new Log(sender, state, "downloading").setFileName(fileName).log();
    ReadableByteChannel rbc = Channels.newChannel(Url.openStream());
    FileOutputStream fos = new FileOutputStream(newPlugin);
    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    fos.close();
    rbc.close();

    if (registered(newPlugin)) throw new FileAlreadyExistsException(newPlugin.getAbsolutePath());

    //adds the file hash to the name since alot of urls just have a generic filename like "download"
    String hash = "";
    if (addFileHash) {
      InputStream is = new FileInputStream(newPlugin);
      DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("MD5"));
      dis.readAllBytes();
      dis.close();
      is.close();
      hash += "-";
      hash += String.format("%032X", new BigInteger(1, dis.getMessageDigest().digest()));
    }

    destination = new File(Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+Files.getNameWithoutExtension(fileName)+hash+".jar");

    if (destination.exists()) throw new FileAlreadyExistsException(newPlugin.getAbsolutePath());

    //moves the file to plugin folder
    FileUtils.moveFile(newPlugin, destination);

    appendPluginData(destination);
    installed = true;

  } catch (FileNotFoundException noFile) {
    new Log(sender, state, "noFile").setLevel(Level.WARNING).setUrl(stringUrl).setException(noFile).log();
  } catch (MalformedURLException e) {
    new Log(sender, state, "badUrl").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).setFileName(fileName).log();
  } catch (FileAlreadyExistsException e) {
    new Log(sender, state, "alreadyExists").setLevel(Level.WARNING).setFileName(fileName).log();
  } catch (IOException | NoSuchAlgorithmException e) {
    new Log(sender, state, "installError").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).log();
  } finally {
    if (newPlugin.exists()) if (!newPlugin.delete())
      new Log(sender, state, "cleanUp").setLevel(Level.WARNING).setFilePath(newPlugin.getAbsolutePath()).log();
  }
  return installed;
}

/**
 Deletes the given plugin from the plugins folder & its configs if requested.
 @param sender       The sender to send the log messages to.
 @param state        The path to get the lang responses from.
 @param pluginName   Name of the plugin to delete. (The name specified in the plugin.yml file).
 @param deleteConfig Whether to delete the plugins configs alongside the jar. If null & no sender is specified the config folder will be preserved.
 @return True - Returned if the file installed successfully.<br>
 False - Returned if the deletion failed for any reason. */
public static boolean deletePlugin(@Nullable CommandSender sender, String state, String pluginName, boolean deleteConfig) {

  try {

    PluginData data = readPluginData(pluginName);
    File pluginDataFolder = new File(pluginFolder+File.separator+data.getName());

    //disables the plugin so that the file can be deleted
    Plugin removePlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
    if (removePlugin != null) {
      removePlugin.getPluginLoader().disablePlugin(removePlugin);
    }

    //deletes config files if specified
    if (deleteConfig) {
      if (pluginDataFolder.exists()) {
        try {
          FileUtils.deleteDirectory(pluginDataFolder);
        } catch (IOException e) {
          //marks configs for deletion on server stop, as another process is using the files
          pluginDataFolder.deleteOnExit();
        } finally {
          new Log(sender, state, "configRemove").setPluginName(pluginName).log();
        }
      }
    }

    try {
      FileUtils.delete(new File(pluginFolder+File.separator+data.getFileName()));
    } catch (IOException e) {
      new Log(sender, state, "deleteError").setLevel(Level.WARNING).setException(e).setPluginName(pluginName).log();
      new Log(sender, state, "scheduleDelete").setLevel(Level.WARNING).setPluginName(pluginName).log();
      FileUtils.forceDeleteOnExit(new File(pluginFolder+File.separator+data.getFileName()));
      return false;
    }

    removePluginData(pluginName);
    new Log(sender, state, "pluginDelete").setPluginName(pluginName).log();
    return true;

  } catch (NoSuchPluginException e) {
    new Log(sender, state, "noSuchPlugin").setLevel(Level.WARNING).setException(e).setPluginName(pluginName).log();
  } catch (IOException e) {
    new Log(sender, state, "deleteError").setLevel(Level.WARNING).setException(e).setPluginName(pluginName).log();
  }
  return false;
}

/**
 Finds compatible plugins on modrinth for your server.
 @param sender      The sender to send the log messages to.
 @param state       The path to get the lang responses from.
 @param searchQuery the name of the plugin to search Modrinth for.
 @return A ModrinthSearch object. */
public static ModrinthSearch modrinthSearch(@Nullable CommandSender sender, String state, String searchQuery) {
  ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
  HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();

  try {

    String mcVersion = Bukkit.getVersion().split(": ")[1];
    mcVersion = mcVersion.substring(0, mcVersion.length()-1);
    String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

    JsonElement relevantPlugins = modrinthAPI(sender, "ModrinthAPI", "https://api.modrinth.com/v2/search?query="+searchQuery+"&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]", "GET");
    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
    if (hits.isEmpty()) throw new ModrinthAPIException(Util.getLang("ModrinthAPI.empty"));

    //gets the projects
    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (JsonElement je : hits) {
      projectUrl.append("%22").append(je.getAsJsonObject().get("slug").getAsString()).append("%22").append(",");
    }
    JsonArray pluginProjects = modrinthAPI(sender, "ModrinthAPI", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET").getAsJsonArray();
    if (hits.isEmpty()) throw new ModrinthAPIException(Util.getLang("ModrinthAPI.empty"));

    //gets the information for all the versions
    String baseUrl = "https://api.modrinth.com/v2/versions?ids=[";
    JsonArray pluginVersions = new JsonArray();

    StringBuilder versionsUrl = new StringBuilder(baseUrl);
    for (JsonElement je : pluginProjects) {
      for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
        if (versionsUrl.length() > 20000) {
          pluginVersions.addAll(modrinthAPI(sender, "ModrinthAPI", versionsUrl.substring(0, versionsUrl.length()-1)+"]", "GET").getAsJsonArray());
          versionsUrl.delete(baseUrl.length(), versionsUrl.length());
        }
        versionsUrl.append("%22").append(jel.getAsString()).append("%22").append(",");
      }
    }

    pluginVersions.addAll(modrinthAPI(sender, "ModrinthAPI", versionsUrl.substring(0, versionsUrl.length()-1)+"]", "GET").getAsJsonArray());

    //filters out incompatible versions/plugins
    HashMap<String,JsonArray> compatibleFiles = new HashMap<>();
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

    //hashmap of all valid plugins and there compatible versions
    for (JsonElement je : hits) {
      JsonObject jo = je.getAsJsonObject();
      String projectID = jo.get("project_id").getAsString();
      if (!compatibleFiles.containsKey(projectID)) continue;

      JsonArray array = validPlugins.get(jo);
      if (array == null) array = new JsonArray();
      for (JsonElement jel : compatibleFiles.get(projectID).getAsJsonArray()) {
        array.add(jel);
      }

      validPlugins.put(jo, array);
    }

    //adds them to the list in the order they were returned by Modrinth
    for (JsonElement je : hits)
      if (validPlugins.containsKey(je.getAsJsonObject())) validPluginKeys.add(je.getAsJsonObject());

  } catch (ModrinthAPIException | MalformedURLException e) {
    new Log(sender, state, "modrinthSearchErr").setLevel(Level.WARNING).setException(e).log();
  }

  return new ModrinthSearch(validPluginKeys, validPlugins);
}

/**
 Gets the most 10 popular plugins from modrinth with the specified offset
 @param sender The sender to send the log messages to.
 @param state  The path to get the lang responses from.
 @param offset The offset to get the plugins from.
 @return The plugins at that offset in a ModrinthSearch object. */
public static ModrinthSearch modrinthBrowse(@Nullable CommandSender sender, String state, int offset) {
  ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
  HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();

  try {
    String mcVersion = Bukkit.getVersion().split(": ")[1];
    mcVersion = mcVersion.substring(0, mcVersion.length()-1);
    String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

    JsonElement relevantPlugins = modrinthAPI(sender, "ModrinthAPI", "https://api.modrinth.com/v2/search?query=&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]&offset="+offset, "GET");
    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
    if (hits.isEmpty()) return new ModrinthSearch(validPluginKeys, validPlugins);

    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (JsonElement je : hits) {
      JsonObject hit = je.getAsJsonObject();
      projectUrl.append("%22").append(hit.get("project_id").getAsString()).append("%22,");
    }

    JsonArray pluginProjects = modrinthAPI(sender, "ModrinthAPI", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET").getAsJsonArray();
    if (hits.isEmpty()) return new ModrinthSearch(validPluginKeys, validPlugins);
    ExecutorService executorService = Executors.newCachedThreadPool();
    ArrayList<Future<JsonElement>> futures = new ArrayList<>();

    for (JsonElement je : pluginProjects) {
      futures.add(executorService.submit(new VersionGetThread(je.getAsJsonObject().get("id").getAsString())));
    }

    for (Future<JsonElement> future : futures) {
      JsonArray validVersions = future.get().getAsJsonArray();
      if (validVersions.isEmpty()) continue;

      for (JsonElement projectElement : pluginProjects) {
        JsonObject project = projectElement.getAsJsonObject();
        if (project.get("id").equals(validVersions.get(0).getAsJsonObject().get("project_id"))) {
          validPluginKeys.add(project);

          for (JsonElement jel : validVersions) {
            JsonArray array = validPlugins.get(project);
            if (array == null) array = new JsonArray();
            array.add(jel.getAsJsonObject());
            validPlugins.put(project, array);
          }
        }
      }
    }

  } catch (MalformedURLException | ModrinthAPIException | ExecutionException | InterruptedException e) {
    new Log(sender, state, "browsePluginErr").setLevel(Level.WARNING).setException(e).log();
  }

  return new ModrinthSearch(validPluginKeys, validPlugins);
}

/**
 @param sender        The sender to send the log messages to.
 @param state         The path to get the lang responses from.
 @param stringURL     The url to send the request to.
 @param requestMethod The request method to use.
 @return The response from Modrinth.
 @throws MalformedURLException If the Url is invalid.
 @throws ModrinthAPIException  If there was a problem getting the response from Modrinth. */
public static JsonElement modrinthAPI(@Nullable CommandSender sender, String state, String stringURL, String requestMethod) throws MalformedURLException, ModrinthAPIException {
  try {
    HttpURLConnection con = (HttpURLConnection) encodeUrl(stringURL).openConnection();
    con.setRequestMethod(requestMethod);
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("User-Agent", "CogWorks: https://github.com/Mapty231 contact: tye.exe@gmail.com");

    int status = con.getResponseCode();
    if (status == 410) {
      new Log(sender, state, "outDated").setLevel(Level.WARNING).log();
    }
    if (status == 429) {
      new Log(sender, state, "ApiLimit").setLevel(Level.WARNING).log();
    }
    if (status != 200) {
      throw new ModrinthAPIException(Util.getLang("ModrinthAPI.error"), new Throwable("URL: "+stringURL+"\n request method: "+requestMethod+"\n response message:"+con.getResponseMessage()));
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
    throw new ModrinthAPIException(Util.getLang("ModrinthAPI.error"), e.getCause());
  }
}

/**
 Installed plugins & their dependencies from Modrinth.
 @param sender The command sender performing this action.
 @param state  The lang path to get the responses from.
 @param files  The files to install.
 @return True if all of the plugins installed successfully. */
public static boolean installModrinthPlugin(@Nullable CommandSender sender, String state, JsonArray files) {
  ArrayList<Boolean> installed = new ArrayList<>();

  if (files.size() == 1) {
    JsonObject file = files.get(0).getAsJsonObject();
    String fileName = file.get("filename").getAsString();
    installed.add(installPluginURL(sender, state, file.get("url").getAsString(), fileName, false));

  } else if (!files.isEmpty()) {

    for (JsonElement je : files) {
      JsonObject file = je.getAsJsonObject();
      if (file.get("primary").getAsBoolean()) {
        installed.add(installPluginURL(sender, state, file.get("url").getAsString(), file.get("filename").getAsString(), false));

      }
    }

    JsonObject file = files.get(0).getAsJsonObject();
    String fileName = file.get("filename").getAsString();
    installed.add(installPluginURL(sender, state, file.get("url").getAsString(), fileName, false));

  }
  return !installed.contains(false);
}

/**
 Gets the compatible dependencies for a version from Modrinth and installs them.
 @param sender        The command sender performing this action.
 @param state         The lang path to get the responses from.
 @param pluginVersion The plugin version to get the dependencies from.
 @return True if all of the plugins installed successfully. */
public static boolean installModrinthDependencies(@Nullable CommandSender sender, String state, JsonObject pluginVersion, String pluginName) {
  HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, pluginVersion);
  ArrayList<Boolean> installed = new ArrayList<>();

  if (!dependencies.isEmpty()) {
    new Log(sender, state, "installingDep").setPluginName(pluginName).log();
    for (JsonArray plugins : dependencies.values()) {
      if (plugins.isEmpty()) continue;
      boolean install = installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray());
      installed.add(install);
      if (install)
        new Log(sender, state, "installed").setFileName(plugins.get(0).getAsJsonObject().get("files").getAsJsonArray().get(0).getAsJsonObject().get("filename").getAsString());
    }
    new Log(sender, state, "installedDep").log();
  }

  return !installed.contains(false);
}

/**
 Gets the compatible dependencies for a version from Modrinth.
 @param sender        The command sender performing this action.
 @param state         The lang path to get the responses from.
 @param pluginVersion The plugin version to get the dependencies from.
 @return A HashMap with the pluginId as a key and the compatible files to download in a json array. */
public static HashMap<String,JsonArray> getModrinthDependencies(@Nullable CommandSender sender, String state, JsonObject pluginVersion) {
  HashMap<String,JsonArray> compatibleFiles = new HashMap<>();
  JsonArray pluginDependencies = pluginVersion.get("dependencies").getAsJsonArray();

  ArrayList<String> versions = new ArrayList<>();
  ArrayList<String> projects = new ArrayList<>();
  for (JsonElement je : pluginDependencies) {
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
    return compatibleFiles;
  }

  //gets the versions from the projects
  if (!projects.isEmpty()) {
    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (String projectID : projects) projectUrl.append("%22").append(projectID).append("%22").append(",");

    try {
      JsonElement pluginProjects = modrinthAPI(sender, "", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET");

      for (JsonElement je : pluginProjects.getAsJsonArray()) {
        for (JsonElement jel : je.getAsJsonObject().get("versions").getAsJsonArray()) {
          versions.add(jel.getAsString());
        }
      }

    } catch (MalformedURLException | ModrinthAPIException e) {
      new Log(sender, state, "getPluginErr").setLevel(Level.WARNING).setException(e).log();
    }
  }

  if (versions.isEmpty()) return compatibleFiles;

  //gets the information for all the versions
  StringBuilder versionsUrl = new StringBuilder("https://api.modrinth.com/v2/versions?ids=[");
  for (String versionID : versions) {
    versionsUrl.append("%22").append(versionID).append("%22").append(",");
  }

  JsonElement pluginVersions;
  try {
    pluginVersions = modrinthAPI(sender, "", versionsUrl.substring(0, versionsUrl.length()-1)+"]", "GET");
  } catch (MalformedURLException | ModrinthAPIException e) {
    new Log(sender, state, "allPluginErr").setLevel(Level.WARNING).setException(e).log();
    return compatibleFiles;
  }

  //makes sure the dependencies run on this version
  String mcVersion = Bukkit.getVersion().split(": ")[1];
  mcVersion = mcVersion.substring(0, mcVersion.length()-1);
  String serverSoftware = Bukkit.getServer().getVersion().split("-")[1].toLowerCase();

  for (JsonElement je : pluginVersions.getAsJsonArray()) {
    JsonObject jo = je.getAsJsonObject();
    boolean supportsVersion = false;
    boolean supportsServer = false;

    String projectID = jo.get("project_id").getAsString();

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
  }

  //recursive
  HashMap<String,JsonArray> dependencyDependencies = new HashMap<>();
  for (JsonArray plugin : compatibleFiles.values()) {
    dependencyDependencies.putAll(getModrinthDependencies(sender, state, plugin.get(0).getAsJsonObject()));
  }
  compatibleFiles.putAll(dependencyDependencies);

  return compatibleFiles;
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

public static void reloadPluginData() {
  ArrayList<PluginData> identifiers = new ArrayList<>();
  try {
    identifiers = readPluginData();
  } catch (IOException e) {
    new Log("exceptions.noAccessPluginYML", Level.SEVERE, e).log();
  }

  //removes any plugin from plugin data that have been deleted
  try {
    PluginLoop:
    for (PluginData data : identifiers) {
      for (File file : Objects.requireNonNull(pluginFolder.listFiles())) {
        if (file.isDirectory()) continue;
        if (!Files.getFileExtension(file.getName()).equals("jar")) continue;

        if (data.getFileName().equals(file.getName())) {
          continue PluginLoop;
        }
      }
      try {
        removePluginData(data.getName());
      } catch (NoSuchPluginException e) {
        new Log("exceptions.deletingRemovedPlugin", Level.WARNING, e).setPluginName(data.getName()).log();
      } catch (IOException e) {
        new Log("exceptions.noAccessDeleteRemovedPlugins", Level.WARNING, e).setPluginName(data.getName()).log();
      }
    }

    //adds any new plugins to the pluginData
    for (File file : Objects.requireNonNull(pluginFolder.listFiles())) {
      if (file.isDirectory()) continue;
      if (!Files.getFileExtension(file.getName()).equals("jar")) continue;
      try {
        appendPluginData(file);
      } catch (IOException e) {
        new Log("exceptions.badYmlAccess", Level.WARNING, e).setFileName(file.getName()).log();
      }
    }
  } catch (NullPointerException e) {
    new Log("exceptions.gettingFilesErr", Level.WARNING, e).setFilePath(pluginFolder.getAbsolutePath()).log();
  }
}

}
