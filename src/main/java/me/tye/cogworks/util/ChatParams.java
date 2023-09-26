package me.tye.cogworks.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatParams {

    String modifier;
    CommandSender sender;

    String pluginName = "";

    HashMap<JsonObject, JsonArray> validPlugins = new HashMap<>();
    ArrayList<JsonObject> validPluginKeys = new ArrayList<>();

    ArrayList<JsonObject> chooseableFiles = new ArrayList<>();

    JsonArray dependencies = new JsonArray();
    JsonArray files = new JsonArray();

    int offset = 0;

    public ChatParams(@NonNull CommandSender sender, @NonNull String modifier) {
        this.modifier = modifier;
        this.sender = sender;
    }

    public ChatParams setSender(CommandSender sender) {
        this.sender = sender;
        return this;
    }

    public ChatParams setModifier(String modifier) {
        this.modifier = modifier;
        return this;
    }

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

    public ChatParams setValidPluginKeys(ArrayList<JsonObject> validPluginKeys) {
        this.validPluginKeys = validPluginKeys;
        return this;
    }

    public ChatParams setValidPlugins(HashMap<JsonObject, JsonArray> validPlugins) {
        this.validPlugins = validPlugins;
        return this;
    }

    public ChatParams setPluginName(String pluginName) {
        this.pluginName = pluginName;
        return this;
    }

    public ChatParams setOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public ArrayList<JsonObject> getChooseableFiles() {
        return chooseableFiles;
    }

    public ArrayList<JsonObject> getValidPluginKeys() {
        return validPluginKeys;
    }

    public CommandSender getSender() {
        return sender;
    }

    public JsonArray getDependencies() {
        return dependencies;
    }

    public JsonArray getFiles() {
        return files;
    }

    public String getModifier() {
        return modifier;
    }

    public HashMap<JsonObject, JsonArray> getValidPlugins() {
        return validPlugins;
    }

    public String getPluginName() {
        return pluginName;
    }

    public int getOffset() {
        return offset;
    }
}
