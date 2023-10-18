package me.tye.cogworks.util.customObjects;

import me.tye.cogworks.SendErrorSummary;
import me.tye.cogworks.util.Util;
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
 Creates an object which can be used for logging. If langPath is null, no message will be sent.<br>
 The .log method will need to be used to output the log message.
 @param sender   Sender to output the log to.
 @param langPath The lang path for the response. */
public Log(CommandSender sender, @NonNull String langPath) {
  this.sender = sender;
  this.langPath = langPath;
}

/**
 Creates an object which can be used for logging. This method is intended for outputting any messages that do not originate from a users actions.<br>
 The .log method will need to be used to output the log message.
 @param langPath The lang path for the response.
 @param level    The level of the message.
 @param e        The error that caused the message. */
public Log(String langPath, @NonNull Level level, @Nullable Exception e) {
  this.langPath = langPath;
  this.level = level;
  this.e = e;
}

/**
 Outputs the log message. */
public void log() {
  if (level.getName().equals("SEVERE"))
    SendErrorSummary.severe++;

  if (langPath == null) return;

  String message = getLang(langPath, "filePath", filePath, "fileName", fileName, "depName", depName, "pluginName", pluginName, "key", key, "URL", Url, "severe", severe, "isFile", isFile, "pluginNames", pluginNames, "state", state, "fileNames", fileNames);

  if (sender != null) {
    if (sender instanceof Player)
      sender.sendMessage(message.split("\\{n}"));

    else
      for (String line : message.split("\\{n}"))
        sender.sendMessage("[CogWorks] "+line);


  } else {
    //removes the colour formatting codes when sending to the console logger
    char[] messageChars = message.toCharArray();
    for (int i = 0; i < messageChars.length; i++) {
      if (messageChars[i] == '§') {
        messageChars[i] = 0;
        messageChars[i+1] = 0;
      }
    }

    message = new String(messageChars);

    for (String line : message.split("\\{n}")) {
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

  if ((Boolean) getConfig("showErrorTrace") && e != null) {
    if (sender != null) sender.sendMessage(getLang("exceptions.seeConsole"));
    e.printStackTrace();
  }
}

/**
 /@param depName The dependency name
 @return The modified Log object. */
public Log setDepName(String depName) {
  this.depName = depName;
  return this;
}

/**
 @param fileName The fileName.
 @return The modified Log object. */
public Log setFileName(String fileName) {
  this.fileName = fileName;
  return this;
}

/**
 @param fileNames A list of file names.
 @return The modified Log object. */
public Log setFileNames(List<String> fileNames) {
  StringBuilder names = new StringBuilder();
  for (String fileName : fileNames) {
    names.append(fileName).append(", ");
  }
  this.fileNames = names.substring(0, names.length()-2);
  return this;
}

/**
 @param fileNames An already formatted string of fileNames.
 @return The modified Log object. */
public Log setFileNames(String fileNames) {
  this.fileNames = fileNames;
  return this;
}

/**
 @param filePath The filePath.
 @return The modified Log object. */
public Log setFilePath(String filePath) {
  this.filePath = filePath;
  return this;
}

/**
 @param key The key.
 @return The modified Log object. */
public Log setKey(String key) {
  this.key = key;
  return this;
}

/**
 @param pluginName The pluginName.
 @return The modified Log object. */
public Log setPluginName(String pluginName) {
  this.pluginName = pluginName;
  return this;
}

/**
 @param url The URL.
 @return The modified Log object. */
public Log setUrl(String url) {
  this.Url = url;
  return this;

}

/**
 @param e The exception.
 @return The modified Log object. */
public Log setException(Exception e) {
  this.e = e;
  return this;
}

/**
 @param level The level of the log message.
 @return The modified Log object. */
public Log setLevel(Level level) {
  this.level = level;
  return this;
}

/**
 @param severe The amount of severe error messages that have occurred since last reload/restart.
 @return The modified Log object. */
public Log setSevere(int severe) {
  this.severe = String.valueOf(severe);
  return this;
}

/**
 @param isFile Whether it is a file or a dir. True for file.
 @return The modified Log object. */
public Log isFile(boolean isFile) {
  this.isFile = isFile ? "File" : "Folder";
  return this;
}

/**
 @param pluginNames A string array of pluginNames.
 @return The modified Log object. */
public Log setPluginNames(String[] pluginNames) {
  StringBuilder names = new StringBuilder();
  for (String pluginName : pluginNames) {
    names.append(pluginName).append(", ");
  }
  this.pluginNames = names.substring(0, names.length()-2);
  return this;
}

/**
 @param pluginNames The string of pluginNames that has already been formatted.
 @return The modified Log object. */
public Log setPluginNames(String pluginNames) {
  this.pluginNames = pluginNames;
  return this;
}

/**
 Sets the key value for state, not the state for the lang path.
 @param state The state
 @return The modified Log object. */
public Log setState(String state) {
  this.state = state;
  return this;
}
}

