package me.tye.cogworks.util;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import me.tye.cogworks.util.customObjects.VersionGetThread;
import me.tye.cogworks.util.customObjects.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.customObjects.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.customObjects.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static me.tye.cogworks.util.Util.*;

public class Plugins {

/**
 Installs a plugin from a given url. There are NO restriction on the url used, however ".jar" will always be appended.
 Fail / success logging is handled within this method.
 @param sender      The sender to send the log messages to.
 @param state       The path to get the lang responses from.
 @param stringUrl   The Url as a string to download the file from.
 @param fileName    Name of the file to download. Sometimes the file is stored under a name different to the desired file name.
 @param addFileHash If downloading from a non api source the file hash can be added to the end of the file, as many downloads have generic names such as "download".
 @return True if and only if the file installed successfully. */
public static boolean installPluginURL(@Nullable CommandSender sender, String state, String stringUrl, String fileName, boolean addFileHash) {
  File tempPlugin = new File(temp.getAbsolutePath()+File.separator+fileName);
  File installedPlugin;
  boolean installed = false;

  try {
    URL Url = encodeUrl(stringUrl);

    if (new File(pluginFolder+File.separator+fileName).exists())
      throw new FileAlreadyExistsException(tempPlugin.getAbsolutePath());

    //downloads the file
    new Log(sender, state, "downloading").setFileName(fileName).log();
    ReadableByteChannel rbc = Channels.newChannel(Url.openStream());
    FileOutputStream fos = new FileOutputStream(tempPlugin);
    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    fos.close();
    rbc.close();

    if (StoredPlugins.registered(tempPlugin))
      throw new FileAlreadyExistsException(tempPlugin.getAbsolutePath());

    //adds the file hash to the name since alot of urls just have a generic filename like "download"
    String hash = "";
    if (addFileHash) {
      InputStream is = new FileInputStream(tempPlugin);
      DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("MD5"));
      dis.readAllBytes();
      dis.close();
      is.close();
      hash += "-";
      hash += String.format("%032X", new BigInteger(1, dis.getMessageDigest().digest()));
    }

    installedPlugin = new File(Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+Files.getNameWithoutExtension(fileName)+hash+".jar");

    if (installedPlugin.exists())
      throw new FileAlreadyExistsException(tempPlugin.getAbsolutePath());

    //moves the file to plugin folder
    FileUtils.moveFile(tempPlugin, installedPlugin);
    StoredPlugins.appendPluginData(installedPlugin);
    new Log(sender, state, "installed").setFileName(fileName).log();

    try {
      Plugin pluginInstance = Bukkit.getPluginManager().loadPlugin(installedPlugin);
      if (pluginInstance == null)
        throw new Exception("Plugin is null.");
      Bukkit.getPluginManager().enablePlugin(pluginInstance);

    } catch (Exception e) {
      new Log(sender, state, "noEnable").setFileName(fileName);
    }

    installed = true;

  } catch (FileNotFoundException noFile) {
    new Log(sender, state, "noFiles").setLevel(Level.WARNING).setUrl(stringUrl).setException(noFile).log();

  } catch (MalformedURLException e) {
    new Log(sender, state, "badUrl").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).setFileName(fileName).log();

  } catch (FileAlreadyExistsException e) {
    new Log(sender, state, "alreadyExists").setLevel(Level.WARNING).setFileName(fileName).log();

  } catch (IOException | NoSuchAlgorithmException e) {
    new Log(sender, state, "installError").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).log();

  } finally {
    if (tempPlugin.exists() && !tempPlugin.delete()) {
      new Log(sender, state, "cleanUp").setLevel(Level.WARNING).setFilePath(tempPlugin.getAbsolutePath()).log();
    }
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

    PluginData pluginData = StoredPlugins.readPluginData(pluginName);
    File pluginDataFolder = new File(pluginFolder+File.separator+pluginData.getName());

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
      FileUtils.delete(new File(pluginFolder+File.separator+pluginData.getFileName()));
    } catch (IOException e) {
      new Log(sender, state, "deleteError").setLevel(Level.WARNING).setException(e).setPluginName(pluginName).log();
      new Log(sender, state, "scheduleDelete").setLevel(Level.WARNING).setPluginName(pluginName).log();
      pluginData.setDeletePending(true);
      StoredPlugins.modifyPluginData(pluginData);
      return false;
    }

    StoredPlugins.removePluginData(pluginName);
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
    JsonElement relevantPlugins = modrinthAPI(sender, "ModrinthAPI", "https://api.modrinth.com/v2/search?query="+searchQuery+"&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]", "GET");
    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
    if (hits.isEmpty()) {
      new Log(sender, "ModrinthAPI.empty").log();
      return new ModrinthSearch(validPluginKeys, validPlugins);
    }

    //gets the projects
    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (JsonElement je : hits) {
      projectUrl.append("%22").append(je.getAsJsonObject().get("slug").getAsString()).append("%22").append(",");
    }
    JsonArray pluginProjects = modrinthAPI(sender, "ModrinthAPI", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET").getAsJsonArray();
    if (hits.isEmpty()) {
      new Log(sender, "ModrinthAPI.empty").log();
      return new ModrinthSearch(validPluginKeys, validPlugins);
    }

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
        if (supportedVersions.getAsString().equals(mcVersion))
          supportsVersion = true;
      }
      for (JsonElement supportedLoaders : jo.get("loaders").getAsJsonArray()) {
        if (supportedLoaders.getAsString().equals(serverSoftware))
          supportsServer = true;
      }

      if (!(supportsVersion && supportsServer))
        continue;

      String projectID = jo.get("project_id").getAsString();

      JsonArray array;
      if (compatibleFiles.containsKey(projectID))
        array = compatibleFiles.get(projectID);
      else
        array = new JsonArray();
      array.add(jo);
      compatibleFiles.put(projectID, array);
    }

    //hashmap of all valid plugins and there compatible versions
    for (JsonElement je : hits) {
      JsonObject jo = je.getAsJsonObject();
      String projectID = jo.get("project_id").getAsString();
      if (!compatibleFiles.containsKey(projectID))
        continue;

      JsonArray array = validPlugins.get(jo);
      if (array == null)
        array = new JsonArray();
      for (JsonElement jel : compatibleFiles.get(projectID).getAsJsonArray()) {
        array.add(jel);
      }

      validPlugins.put(jo, array);
    }

    //adds them to the list in the order they were returned by Modrinth
    for (JsonElement je : hits)
      if (validPlugins.containsKey(je.getAsJsonObject()))
        validPluginKeys.add(je.getAsJsonObject());

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

    JsonElement relevantPlugins = modrinthAPI(sender, "ModrinthAPI", "https://api.modrinth.com/v2/search?query=&facets=[[%22versions:"+mcVersion+"%22],[%22categories:"+serverSoftware+"%22]]&offset="+offset, "GET");
    JsonArray hits = relevantPlugins.getAsJsonObject().get("hits").getAsJsonArray();
    if (hits.isEmpty())
      return new ModrinthSearch(validPluginKeys, validPlugins);

    StringBuilder projectUrl = new StringBuilder("https://api.modrinth.com/v2/projects?ids=[");
    for (JsonElement je : hits) {
      JsonObject hit = je.getAsJsonObject();
      projectUrl.append("%22").append(hit.get("project_id").getAsString()).append("%22,");
    }

    JsonArray pluginProjects = modrinthAPI(sender, "ModrinthAPI", projectUrl.substring(0, projectUrl.length()-1)+"]", "GET").getAsJsonArray();
    if (hits.isEmpty())
      return new ModrinthSearch(validPluginKeys, validPlugins);
    ExecutorService executorService = Executors.newCachedThreadPool();
    ArrayList<Future<JsonElement>> futures = new ArrayList<>();

    for (JsonElement je : pluginProjects) {
      futures.add(executorService.submit(new VersionGetThread(je.getAsJsonObject().get("id").getAsString())));
    }

    for (Future<JsonElement> future : futures) {
      JsonArray validVersions = future.get().getAsJsonArray();
      if (validVersions.isEmpty())
        continue;

      for (JsonElement projectElement : pluginProjects) {
        JsonObject project = projectElement.getAsJsonObject();
        if (project.get("id").equals(validVersions.get(0).getAsJsonObject().get("project_id"))) {
          validPluginKeys.add(project);

          for (JsonElement jel : validVersions) {
            JsonArray array = validPlugins.get(project);
            if (array == null)
              array = new JsonArray();
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
    } else if (status == 429) {
      new Log(sender, state, "ApiLimit").setLevel(Level.WARNING).log();
    } else if (status != 200) {
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
 Installs the given plugins from Modrinth.
 Fail / success logging is handled within this method.
 @param sender The command sender performing this action.
 @param state  The lang path to get the responses from.
 @param files  The files to install.
 @return True if all of the plugins installed successfully. */
public static boolean installModrinthPlugin(@Nullable CommandSender sender, String state, JsonArray files) {
  ArrayList<Boolean> installed = new ArrayList<>();

    for (JsonElement je : files) {
      JsonObject file = je.getAsJsonObject();
      installed.add(installPluginURL(sender, state, file.get("url").getAsString(), file.get("filename").getAsString(), false));
    }

  return !installed.contains(false);
}

/**
 Gets the compatible dependencies for a version from Modrinth and installs them.
 @param sender        The command sender performing this action.
 @param state         The lang path to get the responses from.
 @param pluginVersion The plugin version to get the dependencies from.
 @param pluginName    The name of the plugin to use ion log messages.
 @return True if all of the plugins installed successfully. */
public static boolean installModrinthDependencies(@Nullable CommandSender sender, String state, JsonObject pluginVersion, String pluginName) {
  HashMap<String,JsonArray> dependencies = getModrinthDependencies(sender, state, pluginVersion);
  ArrayList<Boolean> installed = new ArrayList<>();

  if (!dependencies.isEmpty()) {
    new Log(sender, state, "installingDep").setPluginName(pluginName).log();

    for (JsonArray plugins : dependencies.values()) {
      if (plugins.isEmpty()) {
        continue;
      }

      installed.add(installModrinthPlugin(sender, state, plugins.get(0).getAsJsonObject().get("files").getAsJsonArray()));
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
    if (!dependencyType.equals("required"))
      continue;

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
    for (String projectID : projects)
      projectUrl.append("%22").append(projectID).append("%22").append(",");

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

  if (versions.isEmpty())
    return compatibleFiles;

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
  for (JsonElement je : pluginVersions.getAsJsonArray()) {
    JsonObject jo = je.getAsJsonObject();
    boolean supportsVersion = false;
    boolean supportsServer = false;

    String projectID = jo.get("project_id").getAsString();

    for (JsonElement supportedVersions : jo.get("game_versions").getAsJsonArray()) {
      if (supportedVersions.getAsString().equals(mcVersion))
        supportsVersion = true;
    }
    for (JsonElement supportedLoaders : jo.get("loaders").getAsJsonArray()) {
      if (supportedLoaders.getAsString().equals(serverSoftware))
        supportsServer = true;
    }

    if (!(supportsVersion && supportsServer))
      continue;


    JsonArray array;
    if (compatibleFiles.containsKey(projectID))
      array = compatibleFiles.get(projectID);
    else
      array = new JsonArray();
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
 Checks if the config folder ./plugins/{pluginName} exists.
 @param pluginName The name of the plugin to check the config folder of.
 @return True if the config folder for this plugin exists. */
public static boolean hasConfigFolder(String pluginName) {
  return new File(pluginFolder+File.separator+pluginName).exists();
}
}
