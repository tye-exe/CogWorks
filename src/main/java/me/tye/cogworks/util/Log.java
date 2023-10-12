package me.tye.cogworks.util;

import me.tye.cogworks.events.SendErrorSummary;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.logging.Level;

import static me.tye.cogworks.util.Util.getConfig;
import static me.tye.cogworks.util.Util.getLang;

public class Log {

CommandSender sender;
String langPath;

Level level = Level.INFO;
Exception e = null;

//possible keys
String filePath = null;
String fileName = null;
String fileNames = null;
String depName = null;
String pluginName = null;
String pluginNames = null;
String key = null;
String Url = null;
String severe = null;
String isFile = null;
String state = null;

/**
 Creates an object which can be used for logging. If state or event is null, no message will be sent.
 @param sender Sender to output the log to.
 @param state  First part of the lang path for the response, useful for methods.
 @param event  Last part of the lang path fot the response. */
public Log(CommandSender sender, String state, String event) {
  if (state == null || event == null) {
    this.langPath = null;
  } else {
    this.sender = sender;
    this.langPath = state+"."+event;
  }
}

/**
 Creates an object which can be used for logging. If langPath is null, no message will be sent.
 @param sender   Sender to output the log to.
 @param langPath The lang path for the response. */
public Log(CommandSender sender, @NonNull String langPath) {
  this.sender = sender;
  this.langPath = langPath;
}

/**
 Creates an object which can be used for logging. This method is intended for outputting any messages that do not originate from a users actions.
 @param langPath The lang path for the response.
 @param level    The level of the message.
 @param e        The error that caused the message. */
public Log(String langPath, @NonNull Level level, @Nullable Exception e) {
  this.langPath = langPath;
  this.level = level;
  this.e = e;
}

public void log() {
  if (langPath == null) return;

  String message = getLang(langPath, "filePath", filePath, "fileName", fileName, "depName", depName, "pluginName", pluginName, "key", key, "URL", Url, "severe", severe, "isFile", isFile, "pluginNames", pluginNames, "state", state, "fileNames", fileNames);

  if (sender != null) {
    for (String line : message.split("\\{n}")) {
      sender.sendMessage("[CogWorks] "+line);
    }

  } else {
    for (String line : message.split("\n")) {
      ChatColor colour;
      if (level.getName().equals("WARNING")) colour = ChatColor.YELLOW;
      else if (level.getName().equals("SEVERE")) {
        colour = ChatColor.RED;
        SendErrorSummary.severe++;
      } else colour = ChatColor.GREEN;

      Bukkit.getLogger().log(level, "[CogWorks] "+line);
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (!player.isOp()) continue;
        String formattedMessage = "[CogWorks] "+colour+line;
        if ((Boolean) Util.getConfig("showErrorTrace") && e != null)
          formattedMessage += Util.getLang("exceptions.seeConsole");
        player.sendMessage(formattedMessage);
      }
    }
  }

  if ((Boolean) getConfig("showErrorTrace") && e != null) e.printStackTrace();
}

public Log setDepName(String depName) {
  this.depName = depName;
  return this;
}

public Log setFileName(String fileName) {
  this.fileName = fileName;
  return this;
}

/**
 Takes a List & formats it into a string for the lang. */
public Log setFileNames(List<String> fileNames) {
  StringBuilder names = new StringBuilder();
  for (String fileName : fileNames) {
    names.append(fileName).append(", ");
  }
  this.fileNames = names.substring(0, names.length()-2);
  return this;
}

public Log setFileNames(String fileNames) {
  this.fileNames = fileNames;
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

/**
 Takes a string array & formats it into a string for the lang. */
public Log setPluginNames(String[] pluginNames) {
  StringBuilder names = new StringBuilder();
  for (String pluginName : pluginNames) {
    names.append(pluginName).append(", ");
  }
  this.pluginNames = names.substring(0, names.length()-2);
  return this;
}

public Log setPluginNames(String pluginNames) {
  this.pluginNames = pluginNames;
  return this;
}

/**
 Sets the key value for state, not the state for the lang path */
public Log setState(String state) {
  this.state = state;
  return this;
}
}

