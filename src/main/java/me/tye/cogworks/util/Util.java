package me.tye.cogworks.util;

import me.tye.cogworks.CogWorks;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static me.tye.cogworks.ChatManager.response;
import static me.tye.cogworks.CogWorks.log;


public class Util {

//constants
public static final JavaPlugin plugin = JavaPlugin.getPlugin(CogWorks.class);

public static final File pluginFolder = new File(plugin.getDataFolder().getParent());
public static final File configFile = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+"config.yml");
public static final File dataStore = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+".data");
public static final File pluginDataFile = new File(dataStore.getAbsolutePath()+File.separator+"pluginData.json");
public static final File langFolder = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+"langFiles");
public static final File temp = new File(plugin.getDataFolder().getAbsolutePath()+File.separator+".temp");
public static final File ADR = new File(temp.getAbsolutePath()+File.separator+"ADR");


//lang & config
private static HashMap<String,Object> lang = new HashMap<>();
private static HashMap<String,Object> config = new HashMap<>();

public static void setLang(HashMap<String,Object> lang) {
  Util.lang = getKeysRecursive(lang);
}

public static void setConfig(HashMap<String,Object> config) {
  Util.config = getKeysRecursive(config);
}

/**
 Formats the Map returned from Yaml.load() into a hashmap where the exact key corresponds to the value.
 @param baseMap The Map from Yaml.load().
 @return The formatted Map. */
public static HashMap<String,Object> getKeysRecursive(Map<?,?> baseMap) {
  HashMap<String,Object> map = new HashMap<>();
  if (baseMap == null) return map;
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
  if (!keyPath.isEmpty()) keyPath += ".";
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
 @param key     Key to the value from the loaded lang file.
 @param replace Should be inputted in "valueToReplace0", valueToReplaceWith0", "valueToReplace1", valueToReplaceWith2"... etc
 @return Lang response with the specified values replaced. */
public static String getLang(String key, String... replace) {
  String rawResponse = String.valueOf(lang.get(key));
  //if config doesn't contain the key it checks if it is present in default config files.
  if (rawResponse == null || rawResponse.equals("null")) {
    InputStream is = plugin.getResource("langFiles/"+getConfig("lang")+".yml");
    HashMap<String,Object> defaultLang;

    if (is != null) defaultLang = getKeysRecursive(new Yaml().load(is));
    else defaultLang = getKeysRecursive(new Yaml().load(plugin.getResource("langFiles/eng.yml")));

    rawResponse = String.valueOf(defaultLang.get(key));

    if (rawResponse == null || rawResponse.equals("null")) {
      if (key.equals("exceptions.noSuchResponse"))
        return "Unable to get key \"exceptions.noSuchResponse\" from lang file. This message is in english to prevent a stack overflow error.";
      else rawResponse = getLang("exceptions.noSuchResponse", "key", key);
    }

    lang.put(key, defaultLang.get(key));
    log(null, Level.WARNING, getLang("exceptions.noExternalResponse", "key", key));
  }

  for (int i = 0; i <= replace.length-1; i += 2) {
    if (replace[i+1] == null || replace[i+1].equals("null")) continue;
    rawResponse = rawResponse.replaceAll("\\{"+replace[i]+"}", replace[i+1]);
  }

  //the A appears for some reason?
  return rawResponse.replaceAll("Â§", "§");
}

/**
 Gets a value from the config file.
 @param key Key for the config to get the value of.
 @return The value from the file. */
public static <T> T getConfig(String key) {
  Object response;
  //if config doesn't contain the key it checks if it is present in default config files.
  if (!config.containsKey(key)) {
    HashMap<String,Object> defaultConfig = getKeysRecursive(new Yaml().load(plugin.getResource("config.yml")));
    response = defaultConfig.get(key);

    if (response == null) {
      log(null, Level.WARNING, Util.getLang("exceptions.noSuchResponse", "key", key));
      return (T) Boolean.TRUE;
    }

    config.put(key, response);
    log(null, Level.WARNING, "Unable to get external config for \""+key+"\". Using internal value.");

  } else response = String.valueOf(config.get(key));

  switch (key) {
  case "lang":
    return (T) String.valueOf(response);
  case "showErrors", "showErrorTrace", "showOpErrorSummary":
    return (T) Boolean.valueOf(String.valueOf(response));
  }

  log(null, Level.WARNING, "Unable to find match for request config, returning true");
  return (T) Boolean.TRUE;
}


/**
 * @return -1 if there was an error. Else the value parsed.
 */
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



}
