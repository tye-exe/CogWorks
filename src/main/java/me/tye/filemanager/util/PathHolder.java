package me.tye.filemanager.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathHolder {
    String serverPath;
    String currentPath;

    public PathHolder(String serverPath, String currentPath) {
        this.serverPath = serverPath;
        this.currentPath = currentPath;
    }

    /**
     * @return Path to the server folder.
     */
    public String getServerPath() {
        return serverPath;
    }

    /**
     * @return Full path to current file.
     */
    public String getCurrentPath() {
        //guards against people trying to go higher in the file system
        String trueServerPath = Path.of(serverPath).normalize().toString();
        String trueCurrentPosition = Path.of(currentPath).normalize().toString();
        if (trueCurrentPosition.startsWith(trueServerPath)) return currentPath;
        else return trueServerPath;
    }

    /**
     * @return The current path, with the path to the server removed from the start.
     */
    public String getRelativePath() {
        return getCurrentPath().substring(getServerPath().length()) + File.separator;
    }

    /**
     * @return The name of the file or folder that is current being accessed.
     */
    public String getFileName() {
        return Path.of(getCurrentPath()).getFileName().toString();
    }


    /**
     * Sets the current path to the given string.
     */
    public void setCurrentPath(String currentPath) {
        //guards against people trying to go higher in the file system
        String trueServerPath = Path.of(serverPath).normalize().toString();
        String trueCurrentPosition = Path.of(currentPath).normalize().toString();
        if (trueCurrentPosition.startsWith(trueServerPath) && Files.exists(Path.of(currentPath))) this.currentPath = currentPath;
        else this.currentPath = trueServerPath;
    }
}
