package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

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
 @return The valid plugin projects in order of relevance. */
public ArrayList<JsonObject> getValidPluginKeys() {
  return validPluginKeys;
}

/**
 @return A HashMap with the valid plugins projects as the keys & their valid files(?) as the values. */
public HashMap<JsonObject,JsonArray> getValidPlugins() {
  return validPlugins;
}
}
