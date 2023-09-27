package me.tye.cogworks.util;

import me.tye.cogworks.CogWorks;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Level;

import static me.tye.cogworks.CogWorks.log;


public class Util {

    private static HashMap<String, String> lang = new HashMap<>();
    private static HashMap<String, String> config = new HashMap<>();

    public static void setLang(HashMap<String, String> lang) {
        Util.lang = lang;
        System.out.println(lang);
    }
    public static void setConfig(HashMap<String, String> config) {
        Util.config = config;
    }

    /**
     * Gets value from loaded lang file.
     * @param key key for lang response.
     * @param replace Should be inputted in "valueToReplace0", valueToReplaceWith0", "valueToReplace1", valueToReplaceWith2"... etc
     * @return Lang response with the specified values replaced.
     */
    public static String getLang(String key, String...replace) {
        String rawResponse = lang.get(key);
        if (rawResponse == null) {
            HashMap<String, String> defaultLang = new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource("langFiles/"+getConfig("lang")+".yml"));
            lang.put(key, defaultLang.get(key));
            log(null, Level.WARNING, "Unable to get external lang response for "+key+". Using internal value.");
            rawResponse = defaultLang.get(key);
        }

        for (int i = 0; i/2 < replace.length; i+=2) {
            rawResponse = rawResponse.replaceAll("{"+replace[i]+"}", replace[i+1]);
        }

        //capitalizes the first letter
        return rawResponse;
    }
    public static <T> T getConfig(String key) {
        String response;
        if (!config.containsKey(key)) {
            HashMap<String, String> defaultConfig = new Yaml().load(JavaPlugin.getPlugin(CogWorks.class).getResource("config.yml"));
            config.put(key, defaultConfig.get(key));
            log(null, Level.WARNING, "Unable to get external config for \""+key+"\". Using internal value.");
            response = defaultConfig.get(key);
        } else response = String.valueOf(config.get(key));

        switch (key) {
            case "lang": return (T) response;
            case "showErrors", "showErrorTrace", "showOpErrorSummary": return (T) Boolean.valueOf(response);
        }

        log(null, Level.WARNING, "Unable to find match for request config, returning true");
        return (T) Boolean.TRUE;
    }
}
