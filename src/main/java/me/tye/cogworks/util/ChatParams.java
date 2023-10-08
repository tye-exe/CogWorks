package me.tye.cogworks.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tye.cogworks.util.yamlClasses.PluginData;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatParams {

String state;
CommandSender sender;

String pluginName = "";

HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();
ArrayList<JsonObject> validPluginKeys = new ArrayList<>();

ArrayList<JsonObject> chooseableFiles = new ArrayList<>();
JsonObject plugin = new JsonObject();

JsonArray dependencies = new JsonArray();
JsonArray files = new JsonArray();

ArrayList<PluginData> toDeleteEval = new ArrayList<>();
Boolean deleteConfigs = null;
DeleteQueue deleteQueue = new DeleteQueue(null, null);


int offset = 0;

public ChatParams(@NonNull CommandSender sender, @NonNull String state) {
  this.state = state;
  this.sender = sender;
}

public ChatParams setSender(CommandSender sender) {
  this.sender = sender;
  return this;
}

public ChatParams setState(String state) {
  this.state = state;
  return this;
}

/**
 Sets the files that are able to be chosen during pluginFileSelect.
 @param chooseableFiles Files that can be chosen from.
 @return Edited ChatParams object. */
public ChatParams setChooseableFiles(ArrayList<JsonObject> chooseableFiles) {
  this.chooseableFiles = chooseableFiles;
  return this;
}

public ChatParams setDependencies(JsonArray dependencies) {
  this.dependencies = dependencies;
  return this;
}

public ChatParams setFiles(JsonArray files) {
  this.files = files;
  return this;
}

/**
 @param validPluginKeys Ordered array of the plugin information from Modrinth.
 @return Edited ChatParams object. */
public ChatParams setValidPluginKeys(ArrayList<JsonObject> validPluginKeys) {
  this.validPluginKeys = validPluginKeys;
  return this;
}

/**
 @param validPlugins A map of the plugin information to an array of the compatible file information.
 @return Edited ChatParams object. */
public ChatParams setValidPlugins(HashMap<JsonObject,JsonArray> validPlugins) {
  this.validPlugins = validPlugins;
  return this;
}

/**
 @param pluginName The name of the plugin.
 @return Edited ChatParams object. */
public ChatParams setPluginName(String pluginName) {
  this.pluginName = pluginName;
  return this;
}

/**
 @param offset Specifies the offset used when getting plugins from Modrinth.
 @return Edited ChatParams object. */
public ChatParams setOffset(int offset) {
  this.offset = offset;
  return this;
}

/**
 @param plugin Contains the plugin data from Modrinth of a certain plugin.
 @return Edited ChatParams object. */
public ChatParams setPlugin(JsonObject plugin) {
  this.plugin = plugin;
  return this;
}

/**
 @param toDeleteEval An array of the data from plugins that depend on a plugin to function.
 @return Edited ChatParams object. */
public ChatParams setToDeleteEval(ArrayList<PluginData> toDeleteEval) {
  this.toDeleteEval = toDeleteEval;
  return this;
}

/**
 @param deleteConfigs Specifies whether the plugin config folders should be deleted alongside the plugin.
 @return Edited ChatParams object. */
public ChatParams setDeleteConfigs(Boolean deleteConfigs) {
  this.deleteConfigs = deleteConfigs;
  return this;
}

/**
 @param deleteQueue What plugins to delete & with what parameters for each deletion
 @return ChatParams object with this value edited. */
public ChatParams setDeleteQueue(DeleteQueue deleteQueue) {
  this.deleteQueue = deleteQueue;
  return this;
}


/**
 /@return Gets the files that are able to be chosen during pluginFileSelect. */
public ArrayList<JsonObject> getChooseableFiles() {
  return chooseableFiles;
}

/**
 @return An ordered array of the plugin information from Modrinth. */
public ArrayList<JsonObject> getValidPluginKeys() {
  return validPluginKeys;
}

/**
 @return The command sender executing the action. */
public CommandSender getSender() {
  return sender;
}

public JsonArray getDependencies() {
  return dependencies;
}

public JsonArray getFiles() {
  return files;
}

/**
 @return The state that the command sender is in. */
public String getState() {
  return state;
}

/**
 @return A map of the plugin information to an array of the compatible file information. */

public HashMap<JsonObject,JsonArray> getValidPlugins() {
  return validPlugins;
}

/**
 @return The name of the plugin. */
public String getPluginName() {
  return pluginName;
}

/**
 @return The offset to browse with. */
public int getOffset() {
  return offset;
}

/**
 @return Get the plugin info from Modrinth. */
public JsonObject getPlugin() {
  return plugin;
}

/**
 @return An array of the data of plugins that depend on this plugin to function. */
public ArrayList<PluginData> getToDeleteEval() {
  return toDeleteEval;
}

/**
 @return Whether the plugin configs should be deleted or not */
public Boolean getDeleteConfigs() {
  return deleteConfigs;
}

/**
 @return The plugins scheduled for deletion. */
public DeleteQueue getDeleteQueue() {
  return deleteQueue;
}
}
