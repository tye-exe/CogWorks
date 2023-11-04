package me.tye.cogworks.util.customObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tye.cogworks.operationHandlers.DeleteQueue;
import me.tye.cogworks.operationHandlers.PluginBrowse;
import me.tye.cogworks.operationHandlers.PluginInstall;
import me.tye.cogworks.operationHandlers.PluginSearch;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatParams {

String state;
CommandSender sender;

HashMap<JsonObject,JsonArray> validPlugins = new HashMap<>();
ArrayList<JsonObject> validPluginKeys = new ArrayList<>();

DeleteQueue deleteQueue = null;
PluginInstall pluginInstall = null;
PluginBrowse pluginBrowse = null;
PluginSearch pluginSearch = null;


/**
 This object is used to pass variables across one state to another within the chat interactions.
 @param sender The command sender.
 @param state  The state the sender is in. */
public ChatParams(@NonNull CommandSender sender, @NonNull String state) {
  this.state = state;
  this.sender = sender;
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
 @param deleteQueue What plugins to delete & with what parameters for each deletion.
 @return ChatParams object with this value edited. */
public ChatParams setDeleteQueue(DeleteQueue deleteQueue) {
  this.deleteQueue = deleteQueue;
  return this;
}
/**
 @return The command sender executing the action. */
public CommandSender getSender() {
  return sender;
}

/**
 @return The state that the command sender is in. */
public String getState() {
  return state;
}

/**
 @return The plugins scheduled for deletion. */
public DeleteQueue getDeleteQueue() {
  return deleteQueue;
}

public PluginInstall getPluginInstall() {
  return pluginInstall;
}

public ChatParams setPluginInstall(PluginInstall pluginInstall) {
  this.pluginInstall = pluginInstall;
  return this;
}

public PluginBrowse getPluginBrowse() {
  return pluginBrowse;
}

public ChatParams setPluginBrowse(PluginBrowse pluginBrowse) {
  this.pluginBrowse = pluginBrowse;
  return this;
}

public PluginSearch getPluginSearch() {
  return pluginSearch;
}

public ChatParams setPluginSearch(PluginSearch pluginSearch) {
  this.pluginSearch = pluginSearch;
  return this;
}
}
