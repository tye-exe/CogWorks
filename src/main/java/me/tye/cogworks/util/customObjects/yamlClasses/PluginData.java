package me.tye.cogworks.util.customObjects.yamlClasses;

import java.util.ArrayList;
import java.util.Map;

public class PluginData {

private String fileName;
private final String name;
private final String version;
private final ArrayList<DependencyInfo> dependencies = new ArrayList<>();
private final ArrayList<DependencyInfo> softDependencies = new ArrayList<>();
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
 Will only be set to true if the plugin was attempted to be deleted but was unsuccessful. */
public boolean isDeletePending() {
  return deletePending;
}

/**
 Replaces the dependency information of the contained dependency with the same name as the given dependency.<br>
 If there are no dependencies with this name then nothing will happen.
 @param newDependencyInfo The given dependency.
 @return The modified PluginData object. */
public PluginData modifyDependency(DependencyInfo newDependencyInfo) {
  for (int i = 0; i < dependencies.size(); i++) {
    DependencyInfo dependency = dependencies.get(i);
    if (!dependency.getName().equals(newDependencyInfo.getName()))
      continue;
    dependencies.set(i, newDependencyInfo);
  }
  return this;
}

/**
 Will only be set to true if the plugin was attempted to be deleted but was unsuccessful.
 @param deletePending Whether the plugin was attempted to be deleted. */
public void setDeletePending(boolean deletePending) {
  this.deletePending = deletePending;
}

@Override
public String toString() {
  return "File name: \""+fileName+"\". Name: \""+name+"\". Version: \""+version+"\". Dependencies: \""+dependencies+"\". Soft dependencies: \""+softDependencies+"\". Delete pending: "+deletePending;
}
}
