package me.tye.cogworks.util;

import me.tye.cogworks.CogWorks;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static me.tye.cogworks.CogWorks.log;


public class Util {

    private static HashMap<String, Object> lang = new HashMap<>();
    private static HashMap<String, Object> config = new HashMap<>();

    public static void setLang(HashMap<String, Object> lang) {
        Util.lang = getKeysRecursive(lang);
    }

    public static void setConfig(HashMap<String, Object> config) {
        Util.config = getKeysRecursive(config);
    }

    /**
     * Formats the Map returned from Yaml.load() into a hashmap where the exact key corresponds to the value.
     * @param baseMap The Map from Yaml.load().
     * @return The formatted Map.
     */
    public static HashMap<String,Object> getKeysRecursive(Map<?,?> baseMap) {
        HashMap<String, Object> map = new HashMap<>();
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
     * Formats the Map returned from Yaml.load() into a hashmap where the exact key corresponds to the value.
     * @param keyPath The path to append to the starts of the key. (Should only be called internally).
     * @param baseMap The Map from Yaml.load().
     * @return The formatted Map.
     */
    public static HashMap<String,Object> getKeysRecursive(String keyPath, Map<?,?> baseMap) {
        if (!keyPath.isEmpty()) keyPath+=".";
        HashMap<String, Object> map = new HashMap<>();
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
     * Gets value from loaded lang file.
     * @param key Key to the value from the loaded lang file.
     * @param replace Should be inputted in "valueToReplace0", valueToReplaceWith0", "valueToReplace1", valueToReplaceWith2"... etc
     * @return Lang response with the specified values replaced.
     */
    public static String getLang(String key, String...replace) {
        String rawResponse = String.valueOf(lang.get(key));
        //if config doesn't contain the key it checks if it is present in default config files.
        if (rawResponse == null || rawResponse.equals("null")) {
            HashMap<String, Object> defaultLang = getKeysRecursive(new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource("langFiles/" + getConfig("lang") + ".yml")));
            rawResponse = String.valueOf(defaultLang.get(key));

            if (rawResponse == null || rawResponse.equals("null")) {
                if (key.equals("exceptions.noSuchResponse"))
                    return "Unable to get key \"exceptions.noSuchResponse\" from lang file. This message is in english to prevent a stack overflow error.";
                else getLang("exceptions.noSuchResponse", "key", key);
            }

            lang.put(key, defaultLang.get(key));
            log(null, Level.WARNING, "Unable to get external lang response for \"" + key + "\". Using internal value.");
        }

        for (int i = 0; i <= replace.length-1; i+=2) {
            rawResponse = rawResponse.replaceAll("\\{"+replace[i]+"}", replace[i+1]);
        }

        return rawResponse;
    }

    /**
     * Gets a value from the config file.
     * @param key Key for the config to get the value of.
     * @return The value from the file.
     */
    public static <T> T getConfig(String key) {
        Object response;
        //if config doesn't contain the key it checks if it is present in default config files.
        if (!config.containsKey(key)) {
            HashMap<String, Object> defaultConfig = getKeysRecursive(new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource("config.yml")));
            response = defaultConfig.get(key);

            if (response == null) {
                log(null, Level.WARNING, Util.getLang("exceptions.noSuchResponse", "key", key));
                return (T) Boolean.TRUE;
            }

            config.put(key, response);
            log(null, Level.WARNING, "Unable to get external config for \""+key+"\". Using internal value.");

        } else response = String.valueOf(config.get(key));

        switch (key) {
            case "lang": return (T) String.valueOf(response);
            case "showErrors", "showErrorTrace", "showOpErrorSummary": return (T) Boolean.valueOf(String.valueOf(response));
        }

        log(null, Level.WARNING, "Unable to find match for request config, returning true");
        return (T) Boolean.TRUE;
    }
}
