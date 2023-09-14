package me.tye.filemanager.util.exceptions;

public class PluginExistsException extends Exception {
    public PluginExistsException(String message) {
        super(message);
    }
    public PluginExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}