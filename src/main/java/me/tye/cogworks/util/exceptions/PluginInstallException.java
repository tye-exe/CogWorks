package me.tye.cogworks.util.exceptions;

public class PluginInstallException extends Exception {
    public PluginInstallException(String message) {
        super(message);
    }
    public PluginInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
