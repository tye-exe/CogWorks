package me.tye.cogworks.util;

import me.tye.cogworks.util.yamlClasses.PluginData;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static me.tye.cogworks.CogWorks.readPluginData;

public class Plugins {

    /**
     * Checks if a plugin is installed.
     * @param name Name of the plugin to check.
     * @return true only if the plugin was found to be installed & the data could be read.
     */
    public static boolean registered(String name) {
        try {
            ArrayList<PluginData> data = new ArrayList<>(readPluginData());
            for (PluginData plugin : data) {
                if (plugin.getName().equals(name)) return true;
            }
        } catch (IOException ignore) {}
        return false;
    }

    /**
     * Checks if a plugin is installed by the name specified in the plugin.yml.
     * @param pluginJar The plugin.jar file.
     * @return true only if the plugin was found to be installed & the data could be read.
     */
    public static boolean registered(File pluginJar) {
        String name = String.valueOf(getPluginYML(pluginJar).get("name"));
        return registered(name);
    }


    public static Map<String, Object> getPluginYML(File pluginJar) {
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
        } catch (Exception ignore) {}
        return new HashMap<>();
    }
}
