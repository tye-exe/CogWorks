package me.tye.filemanager.util.exceptions;

public class PluginInstallException extends Exception {
    public PluginInstallException(String message) {
        super(message);
    }
    public PluginInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
