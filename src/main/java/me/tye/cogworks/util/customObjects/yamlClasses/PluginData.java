package me.tye.cogworks.util.customObjects.yamlClasses;

import java.util.ArrayList;
import java.util.Map;

public class PluginData {

String fileName;
String name;
String version;
ArrayList<DependencyInfo> dependencies = new ArrayList<>();
ArrayList<DependencyInfo> softDependencies = new ArrayList<>();
boolean deletePending = false;

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
 Will only be set to true if the plugin was attempted to be deleted but was unsuccessful.
 */
public boolean isDeletePending() {
  return deletePending;
}

/**
 Will only be set to true if the plugin was attempted to be deleted but was unsuccessful.
 * @param deletePending Whether the plugin was attempted to be deleted.
 */
public void setDeletePending(boolean deletePending) {
  this.deletePending = deletePending;
}

@Override
public String toString() {
  return "File name: \""+fileName+"\". Name: \""+name+"\". Version: \""+version+"\". Dependencies: \""+dependencies+"\". Soft dependencies: \""+softDependencies+"\".";
}
}
