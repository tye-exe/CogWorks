package me.tye.filemanager.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class ModrinthSearch {
    ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
    HashMap<JsonObject, JsonArray> validPlugins = new HashMap<>();

    public ModrinthSearch(@Nullable ArrayList<JsonObject> validPluginKeys, @Nullable HashMap<JsonObject, JsonArray> validPlugins) {
        if (validPluginKeys != null) this.validPluginKeys = validPluginKeys;
        if (validPlugins != null) this.validPlugins = validPlugins;
    }

    public ArrayList<JsonObject> getValidPluginKeys() {
        return validPluginKeys;
    }

    public HashMap<JsonObject, JsonArray> getValidPlugins() {
        return validPlugins;
    }
}
