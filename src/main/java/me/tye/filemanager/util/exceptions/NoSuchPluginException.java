package me.tye.filemanager.util.exceptions;

public class NoSuchPluginException extends Exception {
    public NoSuchPluginException(String message) {
        super(message);
    }
    public NoSuchPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}