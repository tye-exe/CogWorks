package me.tye.cogworks.util.customObjects;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static me.tye.cogworks.util.Util.serverFolder;

public class PathHolder {
Path serverPath = serverFolder.toPath().normalize();
Path currentPath;

/**
 Makes an object to store a users current position in the file system.
 @param currentPath The full current path to the file the users is viewing. */
public PathHolder(String currentPath) {
  this.currentPath = Path.of(currentPath).normalize();
}

/**
 Makes an object to store a users current position in the file system.<br>
 The current path is set to the server folder. */
public PathHolder() {
  this.currentPath = serverPath;
}

/**
 @return Path to the server folder. */
public String getServerPath() {
  return serverPath.normalize().toString();
}

/**
 @return Full path to current file. */
public String getCurrentPath() {
  //guards against people trying to go higher in the file system
  if (currentPath.normalize().startsWith(serverPath.normalize())) return currentPath.toString();
  else {
    this.currentPath = serverPath;
    return serverPath.toString();
  }
}

/**
 @return The current path, with the path to the server removed from the start. */
public String getRelativePath() {
  return getCurrentPath().substring(getServerPath().length())+File.separator;
}

/**
 @return The name of the file or folder that is current being accessed. */
public String getFileName() {
  return Path.of(getCurrentPath()).getFileName().toString();
}


/**
 Sets the current path to the given string.
 @param newPath The new path to set*/
public void setCurrentPath(String newPath) {
  Path currentPath = Path.of(newPath).normalize();
  //guards against people trying to go higher in the file system
  if (serverPath.startsWith(currentPath) && Files.exists(currentPath))
    this.currentPath = currentPath;
}
}
