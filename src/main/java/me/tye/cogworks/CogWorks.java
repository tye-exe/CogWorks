package me.tye.cogworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tye.cogworks.commands.FileCommand;
import me.tye.cogworks.commands.PluginCommand;
import me.tye.cogworks.commands.TabComplete;
import me.tye.cogworks.util.StoredPlugins;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.ModrinthSearch;
import me.tye.cogworks.util.customObjects.yamlClasses.DependencyInfo;
import me.tye.cogworks.util.customObjects.yamlClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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

import static me.tye.cogworks.FileGui.position;
import static me.tye.cogworks.util.Plugins.installModrinthDependencies;
import static me.tye.cogworks.util.Plugins.modrinthSearch;
import static me.tye.cogworks.util.Util.*;

public final class CogWorks extends JavaPlugin {
//TODO: Check if lang file exists for string the user entered.
//TODO: Edit lang options based on available lang files.
//TODO: Add command to force stop ADR?
//TODO: Instead of deleting files, have them be moved to the .temp folder & either deleted upon reload | after a set time.
//TODO: Allow to install multiple plugins at once when using a url.
//TODO: Fix when using plugin install, if you enter the select number for plugin version quick enough repetitively, the plugin will install twice (only one file will still show up).
//TODO: Make to try & install plugins for the correct server version if the server is updated.
//TODO: Allow the sender to pass though an offset when using /plugin browse?
//TODO: Send lang update messages on op join.
//TODO: Allow user to exit/go back from all states.
//TODO: Make lang updates required to be confirmed to go away?
//TODO: Make user invulnerable whilst in ./file menu.
//TODO: Put char limit on file gui title so it doesn't overflow (take chars away from start of path?).
//TODO: Run ADR on plugins that are installed from a URL.

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

  //checks if the selected lang file is the correct one for this version of CogWorks
  langUpdate();

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


  StoredPlugins.reloadPluginData(null, "exceptions");

  //ADR
  if (Util.getConfig("ADR"))
    automaticDependencyResolution();

  //checks for new lang files & installs them.
  newLangCheck();


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
 Automatic dependency resolution, also known as ADR, checks for any plugins that contain dependencies that aren't met.<br>
 If any are found to be not met, it uses modrinth search with the plugin name of the missing dependency and downloads the first ten results.<br>
 It will then check all the plugin names of all the plugins installed by ADR to see if any match the missing dependency name.<br>
 If one does it will be moved into the plugins folder and a success log will be sent. Otherwise, a fail log will be sent. */
private void automaticDependencyResolution() {
  ArrayList<PluginData> identifiers;
  try {
    identifiers = StoredPlugins.readPluginData();
  } catch (IOException e) {
    new Log("exceptions.noAccessPluginYML", Level.SEVERE, e).log();
    return;
  }

  //checks for uninstalled dependencies
  HashMap<DependencyInfo,ArrayList<PluginData>> unmetDependencies = new HashMap<>();
  for (PluginData data : identifiers) {
    if (data.isDeletePending())
      continue;

    ArrayList<DependencyInfo> dependencies = data.getDependencies();
    ArrayList<DependencyInfo> metDependencies = new ArrayList<>();

    for (DependencyInfo depInfo : data.getDependencies()) {
      for (PluginData data1 : identifiers) {
        if (!data1.getName().equals(depInfo.getName()))
          continue;
        metDependencies.add(depInfo);
      }
    }

    dependencies.removeAll(metDependencies);
    for (DependencyInfo dep : dependencies) {
      if (!dep.attemptADR())
        continue;

      if (unmetDependencies.containsKey(dep)) {
        ArrayList<PluginData> dependingPlugins = unmetDependencies.get(dep);
        dependingPlugins.add(data);
        unmetDependencies.put(dep, dependingPlugins);
      } else {
        unmetDependencies.put(dep, new ArrayList<>(List.of(data)));
      }
    }
  }

  //attempts to resolve unmet dependencies
  for (DependencyInfo unmetDepInfo : unmetDependencies.keySet()) {
    new Thread(new Runnable() {
      private DependencyInfo unmetDepInfo;
      private File ADRStore;
      private HashMap<DependencyInfo,ArrayList<PluginData>> unmetDependencies;

      public Runnable init(DependencyInfo unmetDepInfo, File pluginStore, HashMap<DependencyInfo,ArrayList<PluginData>> unmetDependencies) {
        this.unmetDepInfo = unmetDepInfo;
        //so multiple threads get their own folder.
        this.ADRStore = new File(pluginStore.getAbsolutePath()+File.separator+LocalDateTime.now().hashCode());
        this.unmetDependencies = unmetDependencies;
        return this;
      }

      @Override
      public void run() {
        ArrayList<PluginData> dependingPlugins = unmetDependencies.get(unmetDepInfo);
        ArrayList<String> dependingNames = new ArrayList<>(dependingPlugins.size());
        for (PluginData data : dependingPlugins)
          dependingNames.add(data.getName());

        if (!ADRStore.mkdir()) {
          new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
          return;
        }

        String unmetDepName = this.unmetDepInfo.getName();
        String unmetDepVersion = this.unmetDepInfo.getVersion();
        if (unmetDepVersion != null) {
          new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
          return;
        }
        ArrayList<JsonObject> validPluginKeys;
        HashMap<JsonObject,JsonArray> validPlugins;

        new Log("ADR.attempting", Level.WARNING, null).setDepName(unmetDepName).setFileNames(dependingNames).log();

        //searches the dependency name on modrinth
        ModrinthSearch search = modrinthSearch(null, null, unmetDepName);
        validPluginKeys = search.getValidPluginKeys();
        validPlugins = search.getValidPlugins();
        if (validPlugins.isEmpty() || validPluginKeys.isEmpty()) {
          new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
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
            new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
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
              new Log("ADR.downloadingErr", Level.WARNING, e).setFileName(dependecyFile.getName()).log();
            }

            try {
              ZipFile zip = new ZipFile(dependecyFile);
              for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().equals("plugin.yml"))
                  continue;

                StringBuilder out = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
                String line;
                while ((line = reader.readLine()) != null)
                  out.append(line).append("\n");
                reader.close();

                Yaml yaml = new Yaml();
                Map<String,Object> yamlData = yaml.load(out.toString());
                if (yamlData.get("name").equals(unmetDepName)) {
                  zip.close();
                  FileUtils.moveFile(dependecyFile, new File(Path.of(plugin.getDataFolder().getAbsolutePath()).getParent().toString()+File.separator+dependecyFile.getName()));
                  new Log("ADR.success", Level.WARNING, null).setFileNames(dependingNames).setDepName((String) yamlData.get("name")).log();
                  StoredPlugins.reloadPluginData(null, "exceptions");
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
            new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
            return;
          }
        } catch (InterruptedException e) {
          new Log("ADR.threadTime", Level.WARNING, e).log();
          new Log("ADR.fail", Level.WARNING, null).setFileNames(dependingNames).log();
          return;
        }

        //gets dependencies for the dependency installed & checks if ADR could resolve the missing dependency.
        boolean failed = true;
        for (Future<JsonObject> future : match) {
          try {
            JsonObject dependency = future.get();
            if (dependency == null)
              continue;

            failed = false;
            installModrinthDependencies(null, null, dependency, null);
          } catch (InterruptedException | ExecutionException e) {
            new Log("ADR.getErr", Level.WARNING, e).log();
          }
        }

        //if ADR couldn't resolve the missing dependency then it marks not to try and resolve it for next time
        if (failed) {
          new Log("ADR.notToRetry", Level.WARNING, null).setDepName(unmetDepName).setPluginNames(dependingNames).log();
          unmetDepInfo.setAttemptADR(false);
          for (PluginData dependingPlugin : dependingPlugins) {
            try {
              StoredPlugins.modifyPluginData(dependingPlugin.modifyDependency(unmetDepInfo));
            } catch (IOException e) {
              new Log("ADR.writeNoADR", Level.WARNING, e).setPluginName(dependingPlugin.getName()).setDepName(unmetDepName).log();
            }
          }
        }
      }
    }.init(unmetDepInfo, ADR, unmetDependencies)).start();
  }
}

/**
 Checks if the selected lang file is the correct one for this version of CogWorks.<br>
 If it isn't it sets the lang to the updated file inside the plugin or tries to download that lang for this version.<br>
 If the updated lang for this version can't be found the lang will default to english. */
private void langUpdate() {
  //checks lang version & installs the correct lang version.
  HashMap<String,Object> defaultValues = getKeysRecursive(getDefault("plugin.yml"));
  if (!defaultValues.get("version").equals(getLang("langVer"))) {
    try {
      Files.move(Path.of(langFolder.getAbsolutePath()+File.separator+Util.getConfig("lang")+".yml"), Path.of(langFolder.getAbsolutePath()+File.separator+getLang("langVer")+" - "+Util.getConfig("lang")+".yml"));

      //Set the lang to the updated file inside the plugin or tries to download that lang for this version. If the updated lang for this version can't be found the lang will default to english.
      if (getDefault("langFiles/"+Util.getConfig("lang")+".yml") != null) {
        Util.setLang(returnFileConfigs(new File(langFolder.getAbsoluteFile()+File.separator+Util.getConfig("lang")+".yml"), "langFiles/"+Util.getConfig("lang")+".yml"));
        new Log("info.updatedLang", Level.WARNING, null).log();

      } else {
        //checks for new lang files & installs them.
        new Thread(() -> {
          try {
            HashMap<String,Object> pluginMap = getKeysRecursive(new Yaml().load(new String(Objects.requireNonNull(getResource("plugin.yml")).readAllBytes())));
            String indexText = new String(new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/index.yml").openStream().readAllBytes());
            HashMap<String,Object> indexMap = getKeysRecursive(new Yaml().load(indexText));

            String files = String.valueOf(indexMap.get(String.valueOf(pluginMap.get("version"))));
            if (!files.equals("null")) {
              files = files.substring(0, files.length()-1).substring(1);
              String[] filesNames = files.split(", ");

              for (String fileName : filesNames) {
                File langFile = new File(langFolder.getAbsolutePath()+File.separator+fileName);
                if (langFile.exists())
                  continue;

                try {
                  createFile(langFile, new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/"+pluginMap.get("version")+"/"+fileName).openStream(), true);
                  new Log("info.newLang", Level.WARNING, null).setFileName(fileName).log();
                } catch (IOException e) {
                  new Log("exceptions.newLangInstall", Level.WARNING, e).setFileName(langFile.getName()).setUrl("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/"+pluginMap.get("version")+"/"+fileName).log();
                }
              }

              //checks if the new lang file exists yet
              for (String fileName : filesNames) {
                if (Util.getConfig("lang").equals(fileName)) {
                  setLang(returnFileConfigs(new File(langFolder.getAbsoluteFile()+File.separator+Util.getConfig("lang")+".yml"), "langFiles/"+Util.getConfig("lang")+".yml"));
                  new Log("info.updatedLang", Level.WARNING, null).log();
                  return;
                }
              }
            }

            setLang(returnFileConfigs(new File(langFolder.getAbsoluteFile()+File.separator+Util.getConfig("lang")+".yml"), "langFiles/eng.yml"));
            new Log("exceptions.langUpdateFail", Level.WARNING, null).log();

          } catch (IOException e) {
            new Log("exceptions.newLangCheck", Level.WARNING, e).log();
          }
        }).start();
      }
    } catch (IOException e) {
      new Log("exceptions.langUpdateFail", Level.WARNING, null).log();
    }
  }
}

/**
 Checks if there are any new lang files available for this version of CogWorks. */
private void newLangCheck() {
  new Thread(() -> {
    try {
      HashMap<String,Object> pluginMap = getKeysRecursive(new Yaml().load(new String(Objects.requireNonNull(getResource("plugin.yml")).readAllBytes())));
      String indexText = new String(new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/index.yml").openStream().readAllBytes());
      HashMap<String,Object> indexMap = getKeysRecursive(new Yaml().load(indexText));

      String files = String.valueOf(indexMap.get(String.valueOf(pluginMap.get("version"))));
      if (!files.equals("null")) {
        files = files.substring(0, files.length()-1).substring(1);
        String[] filesNames = files.split(", ");

        for (String fileName : filesNames) {
          File langFile = new File(langFolder.getAbsolutePath()+File.separator+fileName);
          if (langFile.exists())
            continue;

          try {
            createFile(langFile, new URL("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/"+pluginMap.get("version")+"/"+fileName).openStream(), true);
            new Log("info.newLang", Level.WARNING, null).setFileName(fileName).log();
          } catch (IOException e) {
            new Log("exceptions.newLangInstall", Level.WARNING, e).setFileName(langFile.getName()).setUrl("https://raw.githubusercontent.com/Mapty231/CogWorks/master/langFiles/"+pluginMap.get("version")+"/"+fileName).log();
          }
        }
      }

    } catch (
        IOException e) {
      new Log("exceptions.newLangCheck", Level.WARNING, e).log();
    }
  }).start();
}

}
