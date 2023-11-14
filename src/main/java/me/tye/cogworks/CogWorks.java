package me.tye.cogworks;

import me.tye.cogworks.commands.FileCommand;
import me.tye.cogworks.commands.PluginCommand;
import me.tye.cogworks.commands.TabComplete;
import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.dataClasses.DeletePending;
import me.tye.cogworks.util.customObjects.dataClasses.PluginData;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;

import static me.tye.cogworks.util.Util.*;
import static me.tye.cogworks.util.customObjects.dataClasses.PluginData.read;

public final class CogWorks extends JavaPlugin {

//TODO: /file gui_guide
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

  //creates the other files
  createFile(dataStore, null, false);
  createFile(temp, null, false);
  createFile(ADR, null, false);

  createFile(deletePending, null, false);
  createFile(deleteData, null, true);
  createFile(pluginDataFile, null, true);

  //hides non-config files
  try {
    java.nio.file.Files.setAttribute(Path.of(dataStore.getAbsolutePath()), "dos:hidden", true);
    java.nio.file.Files.setAttribute(Path.of(temp.getAbsolutePath()), "dos:hidden", true);
  } catch (Exception ignore) {
  }

  PluginData.reload(null, "exceptions");

  //ADR
  if (Util.getConfig("ADR")) {
    ADR();
  }

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

  deletePending();

  //Custom override asked for by canvas creator.
  if (serverSoftware.equals("canvas")) {
    serverSoftware = "purpur";
  }
}

@Override
public void onDisable() {
  //clears temp folder
  try {
    FileUtils.deleteDirectory(temp);
  } catch (IOException e) {
    new Log(null, "exceptions.tempClear").setException(e).log();
  }

  //If the player has a CogWorks gui open it is closed.
  for (Player player : Bukkit.getOnlinePlayers()) {
    Inventory firstSlot = player.getOpenInventory().getInventory(0);
    if (firstSlot == null) {
      return;
    }

    ItemStack[] contents = firstSlot.getContents();
    if (contents[0].getType().equals(Material.AIR)) {
      return;
    }

    ItemMeta firstMeta = contents[0].getItemMeta();
    if (firstMeta == null) {
      return;
    }

    String firstIdentifier = firstMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING);
    if (firstIdentifier == null) {
      return;
    }

    player.closeInventory();
    new Log(player, "info.menuClose").log();
  }
}

/**
 Uses Util.ADR to resolve any missing dependencies for any installed plugins. */
private static void ADR() {
  ArrayList<PluginData> unmets = new ArrayList<>();

  try {
    //adds the unmet plugins to the list
    for (PluginData pluginData : read()) {
      if (pluginData.getUnmetDependencies().isEmpty()) {
        continue;
      }

      unmets.add(pluginData);
    }
  } catch (IOException e) {
    new Log("ADR.genFail", Level.WARNING, e).setPluginName("any plugins that might be missing some.").log();
  }

  for (PluginData unmet : unmets) {
    new Thread(new Runnable() {
      PluginData data;

      public Runnable init(PluginData data) {
        this.data = data;
        return this;
      }
      @Override
      public void run() {
        Util.automaticDependencyResolution(null, data);

        //tires to enable the plugin
        try {
          new Log("ADR.attemptEnable", Level.WARNING, null).setPluginName(data.getName()).log();

          Plugin loadedPlugin = plugin.getPluginLoader().loadPlugin(new File(pluginFolder.toPath()+data.getFileName()));
          plugin.getPluginLoader().enablePlugin(loadedPlugin);

        } catch (Exception ignore) {
          new Log("ADR.noEnable", Level.WARNING, null).setPluginName(data.getName()).log();
        }
      }
    }.init(unmet)).start();
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


/**
 Starts a new thread that checks for out of date files in the restore folder every 10 mins. If a file is older than the limit specified by the user in the config the file will be fully deleted. */
private static void deletePending() {
  //If the delete pending thread is already running then don't start a new one.
  //Added for compatibility with reloads.
  for (Thread runningThread : Thread.getAllStackTraces().keySet()) {
    if (runningThread.getName().equals("DeleteTime")) {
      return;
    }
  }

  //deletes any files that are older than the limit
  new Thread(() -> {
    String entered = Util.getConfig("keepDeleted.time");
    if (entered.strip().equals("-1")) {
      return;
    }
    long[] times = parseTime(entered);

    while (true) {

      for (DeletePending deletePending : DeletePending.read(null)) {
        LocalDateTime deleteTime = deletePending.getDeleteTime();
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime deleteByDate = now.minusWeeks(times[0]).minusDays(times[1]).minusHours(times[2]).minusMinutes(times[3]);

        if (!deleteTime.isBefore(deleteByDate)) {
          continue;
        }

        try {
          deletePending.delete();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      try {
        Thread.sleep(600000);
      } catch (InterruptedException ignore) {}
    }

  }, "DeleteTime").start();
}

/**
 Parses the time value entered by the user in the format "{number}{unit}". There can be any length of number-unit pares.<br>
 Units:<br>
 w - weeks.<br>
 d - days.<br>
 h - hours.<br>
 m - minutes.
 @param time The time stored in the config file.
 @return The parsed units as a long array:<br>index 0 - weeks<br>index 1 - days<br>index 2 - hours<br>index 3 - minutes. */
private static long[] parseTime(String time) {
  long[] times = new long[4];
  char[] timeChars = time.toLowerCase().toCharArray();

  for (int i = 0; i < timeChars.length; i++) {

    switch (timeChars[i]) {
    case 'w' -> {
      times[0] = getProceeding(times[0], timeChars, i);
    }

    case 'd' -> {
      times[1] = getProceeding(times[1], timeChars, i);
    }

    case 'h' -> {
      times[2] = getProceeding(times[2], timeChars, i);
    }

    case 'm' -> {
      times[3] = getProceeding(times[3], timeChars, i);
    }

    }
  }

  return times;
}

}