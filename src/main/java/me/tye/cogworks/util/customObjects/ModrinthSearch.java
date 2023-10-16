package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class ModrinthSearch {
ArrayList<JsonObject> validPluginKeys = new ArrayList<>();
HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();

/**
 Stores the result of ModrinthSearch.
 @param validPluginKeys The valid plugin keys.
 @param validPlugins    The valid plugins in order. */
public ModrinthSearch(@Nullable ArrayList<JsonObject> validPluginKeys, @Nullable HashMap<JsonObject,JsonArray> validPlugins) {
  if (validPluginKeys != null) this.validPluginKeys = validPluginKeys;
  if (validPlugins != null) this.validPlugins = validPlugins;
}

/**
 @return The valid plugin keys in order. */
public ArrayList<JsonObject> getValidPluginKeys() {
  return validPluginKeys;
}

/**
 @return The valid plugins. */
public HashMap<JsonObject,JsonArray> getValidPlugins() {
  return validPlugins;
}
}
