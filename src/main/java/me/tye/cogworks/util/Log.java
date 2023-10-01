package me.tye.cogworks.util;

import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.logging.Level;

import static me.tye.cogworks.util.Util.getLang;

public class Log {

    CommandSender sender;
    String langPath;

    Level level = Level.INFO;
    Exception e = null;

    //possible keys
    String filePath = null;
    String fileName = null;
    String depName = null;
    String pluginName = null;
    String key = null;
    String Url = null;
    String severe = null;
    String isFile = null;


    public Log(CommandSender sender, String state, @NonNull String event) {
        this.sender = sender;
        this.langPath = state+"."+event;
    }

    public Log(CommandSender sender, @NonNull String langPath) {
        this.sender = sender;
        this.langPath = langPath;
    }

    public void log() {

        if (langPath == null) return;

        String message = getLang(langPath, "filePath", filePath, "fileName", fileName, "depName", depName, "pluginName", pluginName, "key", key, "URL", Url, "severe", severe, "isFile", isFile);

        if (sender != null)
            sender.sendMessage("[CogWorks] "+message);

    }

    public Log setDepName(String depName) {
        this.depName = depName;
        return this;
    }
    public Log setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }
    public Log setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }
    public Log setKey(String key) {
        this.key = key;
        return this;
    }
    public Log setPluginName(String pluginName) {
        this.pluginName = pluginName;
        return this;
    }
    public Log setUrl(String url) {
        this.Url = url;
        return this;
    }

    public Log setException(Exception e) {
        this.e = e;
        return this;
    }

    public Log setLevel(Level level) {
        this.level = level;
        return this;
    }

    public Log setSevere(int server) {
        this.severe = String.valueOf(server);
        return this;
    }

    public Log isFile(boolean isFile) {
        this.isFile = isFile ? "File" : "Folder";
        return this;
    }
}

