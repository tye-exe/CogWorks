package me.tye.cogworks.util.exceptions;

public class ModrinthAPIException extends Exception {
public ModrinthAPIException(String message) {
  super(message);
}

public ModrinthAPIException(String message, Throwable cause) {
  super(message, cause);
}
}
