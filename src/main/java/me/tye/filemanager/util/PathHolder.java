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

    public String getServerPath() {
        return serverPath;
    }
    public String getCurrentPath() {
        //guards against people trying to go higher in the file system
        String trueServerPath = Path.of(serverPath).normalize().toString();
        String trueCurrentPosition = Path.of(currentPath).normalize().toString();
        if (trueCurrentPosition.startsWith(trueServerPath)) return currentPath;
        else return trueServerPath;
    }

    public void setCurrentPath(String currentPath) {
        //guards against people trying to go higher in the file system
        String trueServerPath = Path.of(serverPath).normalize().toString();
        String trueCurrentPosition = Path.of(currentPath).normalize().toString();
        if (trueCurrentPosition.startsWith(trueServerPath) && Files.exists(Path.of(currentPath))) this.currentPath = currentPath;
        else this.currentPath = trueServerPath;
    }

    public String getRelativePath() {
        return getCurrentPath().substring(getServerPath().length()) + File.separator;
    }
}
