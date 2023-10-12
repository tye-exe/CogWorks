package me.tye.cogworks.commands;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tye.cogworks.util.*;
import me.tye.cogworks.util.exceptions.ModrinthAPIException;
import me.tye.cogworks.util.exceptions.NoSuchPluginException;
import me.tye.cogworks.util.yamlClasses.PluginData;
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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.CogWorks.encodeUrl;
import static me.tye.cogworks.util.Util.*;

public class PluginCommand implements CommandExecutor {

@Override
public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
  if (args.length >= 1) {
    switch (args[0]) {
    case "install" -> {
      if (!sender.hasPermission("cogworks.plugin.ins")) return true;
      if (args.length >= 2) {
        new Thread(new Runnable() {

          private CommandSender sender;
          private String[] args;

          public Runnable init(CommandSender sender, String[] args) {
            this.sender = sender;
            this.args = args;
            return this;
          }

          @Override
          public void run() {
            try {
              //checks if the arg given is a valid url or not.
              new URL(encodeUrl(args[1])).toURI();

              //gets the filename from the url
              String fileName;
              String[] splits = args[1].split("/");
              fileName = splits[splits.length-1];
              if (!Files.getFileExtension(fileName).equals("jar")) {
                fileName += ".jar";
              }

              if (installPluginURL(sender, "pluginInstall", encodeUrl(args[1]), fileName, true)) {
                new Log(sender, "pluginInstall.finish").setFileName(fileName).log();
              }

            } catch (MalformedURLException | URISyntaxException ignore) {
              StringBuilder query = new StringBuilder();
              for (int i = 1; i < args.length; i++) query.append(" ").append(args[i]);
              ModrinthSearch search = modrinthSearch(sender, "pluginInstall", query.substring(1));
              HashMap<JsonObject,JsonArray> validPlugins = search.getValidPlugins();
              ArrayList<JsonObject> validPluginKeys = search.getValidPluginKeys();

              if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) return;

              new Log(sender, "pluginInstall", "pluginSelect").log();
              for (int i = 0; 10 > i; i++) {
                if (validPluginKeys.size() <= i) break;
                JsonObject project = validPluginKeys.get(i);
                TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
                projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
                projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                projectName.setUnderlined(true);
                sender.spigot().sendMessage(projectName);
              }

              ChatParams newParams = new ChatParams(sender, "pluginSelect").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys);
              if (sender instanceof Player) response.put(sender.getName(), newParams);
              else response.put("~", newParams);

            }
          }
        }.init(sender, args)).start();
      } else {
        new Log(sender, "pluginInstall.noInput").log();
      }
      return true;
    }
    case "remove" -> {
      if (!sender.hasPermission("cogworks.plugin.rm")) return true;
      if (args.length >= 2) {
        Boolean deleteConfig = null;
        String pluginName = args[1];

        //checks if the plugin & config folders exist
        PluginData pluginData;
        try {
          pluginData = Plugins.readPluginData(pluginName);
          if (!Plugins.hasConfigFolder(pluginName)) {
            deleteConfig = false;
            new Log(sender, "deletePlugin.noConfigsFound").setPluginName(pluginName).log();
          }
        } catch (NoSuchPluginException | IOException e) {
          new Log(sender, "deletePlugin.noSuchPlugin").setException(e).setPluginName(pluginName).log();
          return true;
        }

        //modifier checks
        for (String arg : args) {
          if (!arg.startsWith("-")) continue;
          String modifiers = arg.substring(1);
          for (char letter : modifiers.toCharArray()) {
            if (letter == 'y') deleteConfig = true;
            if (letter == 'n') deleteConfig = false;
          }
        }

        //schedules plugins that depend on for deletion
        List<PluginData> whatDepends = Plugins.getWhatDependsOn(pluginName);
        ArrayList<PluginData> deleteEval = new ArrayList<>(whatDepends);
        deleteEval.add(0, pluginData);

        //prompt to delete config files
        if (deleteConfig == null) {
          new Log(sender, "deletePlugin.deleteConfig").setPluginName(pluginName).log();
          new Log(sender, "deletePlugin.note").log();
          ChatParams params = new ChatParams(sender, "deletePlugin").setDeleteQueue(new DeleteQueue(sender, "deletePlugin")).setToDeleteEval(deleteEval);
          if (sender instanceof Player) response.put(sender.getName(), params);
          else response.put("~", params);
          return true;
        }

        //checks what depends on this plugin
        if (!whatDepends.isEmpty()) {
          String[] names = new String[whatDepends.size()];
          for (int i = 0; i < whatDepends.size(); i++) names[i] = whatDepends.get(i).getName();

          new Log(sender, "deletePlugin.dependsOn").setPluginNames(names).setPluginName(pluginName).log();

          ChatParams params = new ChatParams(sender, "pluginsDeleteEval").setDeleteQueue(new DeleteQueue(sender, "deletePlugin")).setToDeleteEval(deleteEval).setDeleteConfigs(deleteConfig);
          if (sender instanceof Player) response.put(sender.getName(), params);
          else response.put("~", params);
          return true;
        }

        if (deletePlugin(sender, "deletePlugin", pluginName, deleteConfig)) {
          new Log(sender, "deletePlugin.reloadWarn").log();
        }

      } else {
        new Log(sender, "deletePlugin.provideName").log();
      }
      return true;
    }
    case "browse" -> {
      if (!sender.hasPermission("cogworks.plugin.ins")) return true;
      new Thread(new Runnable() {

        private CommandSender sender;
        private int offset;

        public Runnable init(CommandSender sender, int offset) {
          this.sender = sender;
          this.offset = offset;
          return this;
        }

        @Override
        public void run() {
          ModrinthSearch modrinthSearch = modrinthBrowse(sender, "pluginBrowse", offset);
          ArrayList<JsonObject> validPluginKeys = modrinthSearch.getValidPluginKeys();
          HashMap<JsonObject,JsonArray> validPlugins = modrinthSearch.getValidPlugins();

          if (validPluginKeys.isEmpty() || validPlugins.isEmpty()) return;

          new Log(sender, "pluginBrowse.pluginSelect").log();
          int i = 0;

          if (offset >= 1) {
            sender.sendMessage(ChatColor.GREEN+String.valueOf(i)+": ^");
            i++;
          }

          while (validPluginKeys.size() > i) {
            JsonObject project = validPluginKeys.get(i);
            TextComponent projectName = new TextComponent(i+1+": "+project.get("title").getAsString());
            projectName.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ("https://modrinth.com/"+project.get("project_type").getAsString()+"/"+project.get("slug").getAsString())));
            projectName.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            projectName.setUnderlined(true);
            sender.spigot().sendMessage(projectName);
            i++;
          }

          sender.sendMessage(ChatColor.GREEN+String.valueOf(i+1)+": v");

          ChatParams newParams = new ChatParams(sender, "pluginBrowse").setValidPlugins(validPlugins).setValidPluginKeys(validPluginKeys).setOffset(offset);
          if (sender instanceof Player) response.put(sender.getName(), newParams);
          else response.put("~", newParams);

        }
      }.init(sender, 0)).start();
      return true;
    }
    case "reload" -> {
      reloadPluginData();
      return true;
    }

    }
  }

  new Log(sender, "help.plugin.help").log();
  if (sender.hasPermission("cogworks.plugin.ins")) {
    new Log(sender, "help.plugin.install").log();
    new Log(sender, "help.plugin.browse").log();
  }
  if (sender.hasPermission("cogworks.plugin.reload")) new Log(sender, "help.plugin.reload").log();
  if (sender.hasPermission("cogworks.plugin.rm")) new Log(sender, "help.plugin.remove").log();

  return true;
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

  try {
    URL Url = new URL(encodeUrl(stringUrl));

    if (new File(pluginFolder+File.separator+fileName).exists()) {
      new Log(sender, state, "alreadyExists").setLevel(Level.WARNING).setFileName(fileName).log();
      return false;
    }

    //downloads the file
    new Log(sender, state, "downloading").setFileName(fileName).log();
    ReadableByteChannel rbc = Channels.newChannel(Url.openStream());
    FileOutputStream fos = new FileOutputStream(newPlugin);
    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    fos.close();
    rbc.close();

    if (Plugins.registered(newPlugin)) {
      new Log(sender, state, "alreadyExists").setLevel(Level.WARNING).setFileName(fileName).log();
      newPlugin.delete();
      return false;
    }

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

    if (destination.exists()) {
      new Log(sender, state, "alreadyExists").setLevel(Level.WARNING).setFileName(fileName).log();
      newPlugin.delete();
      return false;
    }

    //moves the file to plugin folder
    FileUtils.moveFile(newPlugin, destination);

    Plugins.appendPluginData(destination);
    return true;

  } catch (FileNotFoundException noFile) {
    new Log(sender, state, "noFile").setLevel(Level.WARNING).setUrl(stringUrl).setException(noFile).log();
  } catch (MalformedURLException e) {
    new Log(sender, state, "badUrl").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).setFileName(fileName).log();
  } catch (IOException | NoSuchAlgorithmException e) {
    new Log(sender, state, "installError").setLevel(Level.WARNING).setUrl(stringUrl).setException(e).log();
  } finally {
    if (newPlugin.exists()) if (!newPlugin.delete())
      new Log(sender, state, "cleanUp").setLevel(Level.WARNING).setFilePath(newPlugin.getAbsolutePath()).log();
  }
  return false;
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

    PluginData data = Plugins.readPluginData(pluginName);
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

    Plugins.removePluginData(pluginName);
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
 @param URL           The url to send the request to.
 @param requestMethod The request method to use.
 @return The response from Modrinth.
 @throws MalformedURLException If the Url is invalid.
 @throws ModrinthAPIException  If there was a problem getting the response from Modrinth. */
public static JsonElement modrinthAPI(@Nullable CommandSender sender, String state, String URL, String requestMethod) throws MalformedURLException, ModrinthAPIException {
  try {
    URL url = new URL(encodeUrl(URL));
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
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
      throw new ModrinthAPIException(Util.getLang("ModrinthAPI.error"), new Throwable("URL: "+url.toExternalForm()+"\n request method: "+requestMethod+"\n response message:"+con.getResponseMessage()));
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
}
