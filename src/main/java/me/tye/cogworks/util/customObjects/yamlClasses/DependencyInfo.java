package me.tye.cogworks.util.customObjects.yamlClasses;

public class DependencyInfo {

private final String name;
private final String version;

private boolean failedADR = false;

/**
 Stores information about the dependencies of a plugin.
 @param name    The name of the plugin.
 @param version The version of the plugin. */
public DependencyInfo(String name, String version) {
  this.name = name;
  this.version = version;
}

/**
 @return The plugin name of the dependency. */
public String getName() {
  return name;
}

/**
 @return The plugin version of the dependency. */
public String getVersion() {
  return version;
}

/**
 Will be set to true if ADR has been attempted on this plugin & failed. */
public boolean hasFailedADR() {
  return failedADR;
}

/**
 Will be set to true if ADR has been attempted on this plugin & failed.
 @param failedADR Whether the plugin failed at ADR. */
public void setFailedADR(boolean failedADR) {
  this.failedADR = failedADR;
}

@Override
public String toString() {
  return "Name: \""+name+"\". Version: \""+version+"\".";
}
}
