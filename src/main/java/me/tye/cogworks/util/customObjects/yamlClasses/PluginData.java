package me.tye.cogworks.util.customObjects.yamlClasses;

import java.util.ArrayList;
import java.util.Map;

public class PluginData {

String fileName;
String name;
String version;
Integer deleteState = 0;
ArrayList<DependencyInfo> dependencies = new ArrayList<>();
ArrayList<DependencyInfo> softDependencies = new ArrayList<>();

/**
 Contains information about a plugin.
 @param fileName      The file name of the plugin.
 @param rawPluginData The raw data of the "plugin.yml" file. */
public PluginData(String fileName, Map<String,Object> rawPluginData) {
  this.fileName = fileName;
  this.name = rawPluginData.get("name").toString();
  this.version = rawPluginData.get("version").toString();

  if (rawPluginData.get("depend") != null) {
    for (String dependency : (ArrayList<String>) rawPluginData.get("depend")) {
      this.dependencies.add(new DependencyInfo(dependency, null));
    }
  }
  if (rawPluginData.get("softdepend") != null) {
    for (String dependency : (ArrayList<String>) rawPluginData.get("softdepend")) {
      this.softDependencies.add(new DependencyInfo(dependency, null));
    }
  }
}

/**
 @return The file name of the plugin. */
public String getFileName() {
  return fileName;
}

/**
 @return The internal plugin name. */
public String getName() {
  return name;
}

/**
 @return The internal plugin version. */
public String getVersion() {
  return version;
}

/**
 @return The internal dependencies for the plugin. */
public ArrayList<DependencyInfo> getDependencies() {
  return dependencies;
}

/**
 @return The internal soft dependencies for the plugin. */
public ArrayList<DependencyInfo> getSoftDependencies() {
  return softDependencies;
}

/**
 Sets the delete state of the plugin. This will only be relevant during failed deletions.<br>
 0 - Not to delete<br>
 1 - Delete the plugin.<br>
 2 - Delete the plugin & config folders.<br>
 If an int which isn't in the list is entered then no change to the delete state will occur.
 @param deleteState The delete state.
 @return The modified PluginData. */
public PluginData setDeleteState(int deleteState) {
  if (!(deleteState == 0 || deleteState == 1 || deleteState == 2))
    return this;
  this.deleteState = deleteState;
  return this;
}

/**
 Gets the delete state of the plugin.<br>
 0 - Not to delete<br>
 1 - Delete the plugin.<br>
 2 - Delete the plugin & config folders.<br>
 @return The delete state. */
public int getDeleteState() {
  return this.deleteState;
}


@Override
public String toString() {
  return "File name: \""+fileName+"\". Name: \""+name+"\". Version: \""+version+"\". Dependencies: \""+dependencies+"\". Soft dependencies: \""+softDependencies+"\".";
}
}
