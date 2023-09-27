package me.tye.cogworks.util;

import me.tye.cogworks.CogWorks;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

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
     * @param key key for lang response.
     * @param replace Should be inputted in "valueToReplace0", valueToReplaceWith0", "valueToReplace1", valueToReplaceWith2"... etc
     * @return Lang response with the specified values replaced.
     */
    public static String getLang(String key, String...replace) {
        String rawResponse = String.valueOf(lang.get(key));
        if (rawResponse == null || rawResponse.equals("null")) {
            try {
                HashMap<String, Object> defaultLang = new Yaml().load(new String(JavaPlugin.getPlugin(CogWorks.class).getResource("langFiles/" + getConfig("lang") + ".yml").readAllBytes(), StandardCharsets.UTF_8));
                lang.put(key, defaultLang.get(key));
                log(null, Level.WARNING, "Unable to get external lang response for " + key + ". Using internal value.");
                rawResponse = String.valueOf(defaultLang.get(key));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (rawResponse == null || rawResponse.equals("null")) {
            if (key.equals("exceptions.noSuchResponse")) return "Unable to get key \"exceptions.noSuchResponse\" from lang file. This message is in english to prevent a stack overflow error.";
            else getLang("exceptions.noSuchResponse", "key", key);
        }

        for (int i = 0; i <= replace.length-1; i+=2) {
            rawResponse = rawResponse.replaceAll("\\{"+replace[i]+"}", replace[i+1]);
        }

        return rawResponse;
    }
    public static <T> T getConfig(String key) {
        Object response;
        if (!config.containsKey(key)) {
            HashMap<String, Object> defaultConfig = new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource("config.yml"));
            config.put(key, defaultConfig.get(key));
            log(null, Level.WARNING, "Unable to get external config for \""+key+"\". Using internal value.");
            response = defaultConfig.get(key);
        } else response = String.valueOf(config.get(key));

        switch (key) {
            case "lang": return (T) response;
            case "showErrors", "showErrorTrace", "showOpErrorSummary": return (T) Boolean.valueOf(String.valueOf(response));
        }

        log(null, Level.WARNING, "Unable to find match for request config, returning true");
        return (T) Boolean.TRUE;
    }
}
