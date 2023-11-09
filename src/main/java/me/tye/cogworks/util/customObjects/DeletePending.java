package me.tye.cogworks.util.customObjects;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static me.tye.cogworks.util.Util.*;

public class DeletePending implements Serializable {
@Serial
private static final long serialVersionUID = 87120L;

private final Path relativePath;
private final Path filePath;
private final String randName;
private final LocalDateTime deleteTime;

/**
 An object used for storing data on deleted files, so they could be restored before full deletion.
 @param relativePath The path to the file from the server folder.
 @param filePath     The true name of the file.
 @param randName     The random name of the file inside the delete pending folder. */
public DeletePending(Path relativePath, Path filePath, String randName) {
  this.relativePath = relativePath;
  this.filePath = filePath;
  this.randName = randName;
  this.deleteTime = LocalDateTime.now();
}


/**
 Appends this object to the deleteData file.<br>
 If the addition causes the file size to go over the limit, older files will be deleted to clear space.<br>
 If the file itself is bigger than the limit then no reserve files will be clear & the file will be deleted outright.*/
public void append() throws IOException {
  if (!deleteData.exists()) {
    return;
  }

  long maxSize = parseSize(getConfig("keepDeleted.size"));
  //if the deleted file is bigger than the max size specified by the user then it isn't reserved.
  if (maxSize < Files.size(filePath)) {
    Files.delete(filePath);
    return;
  }

  //TODO: delete old files if size is too big

  ArrayList<DeletePending> deletePendings = read();
  deletePendings.add(this);
  write(deletePendings);

  //moves the file, but deletes the copy if it failed to fully move.
  try {
    Files.move(filePath, Path.of(deletePending.getPath()+File.separator+randName));
  } catch (IOException e) {
    remove(this);
    if (!new File(deletePending.getPath()+File.separator+randName).exists()) {
      throw new IOException(e);
    }

    Files.delete(Path.of(deletePending.getPath()+File.separator+randName));
  }
}

/**
 Deletes the file restore file & the entry in the deleteData file. */
public void delete() {
  try {
    Files.delete(Path.of(deletePending.getAbsolutePath()+File.separator+getRandName()));

    ArrayList<DeletePending> pendings = read();
    for (int i = 0; i < pendings.size(); i++) {
      if (!pendings.get(i).getRandName().equals(getRandName())) {
        continue;
      }

      pendings.remove(i);
      break;
    }

    write(pendings);

  } catch (IOException e) {
    throw new RuntimeException(e);
  }
}

public Path getRelativePath() {
  return relativePath;
}

public Path getFilePath() {
  return filePath;
}

public String getRandName() {
  return randName;
}

public LocalDateTime getDeleteTime() {
  return deleteTime;
}


/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read(@Nullable CommandSender sender) {
  if (!deleteData.exists()) {
    return new ArrayList<>();
  }

  try (FileInputStream fileInputStream = new FileInputStream(deleteData)) {
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    return (ArrayList<DeletePending>) objectInputStream.readObject();

  } catch (ClassNotFoundException | SecurityException | IOException e) {
    new Log(sender, "delete.readFail").log();
    return new ArrayList<>();
  }
}

/**
 Reads the data from the deleteData file.
 @return The data of the files that are pending deletion. */
public static ArrayList<DeletePending> read() throws IOException {
  if (!deleteData.exists()) {
    return new ArrayList<>();
  }

  try (FileInputStream fileInputStream = new FileInputStream(deleteData)) {
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    return (ArrayList<DeletePending>) objectInputStream.readObject();

  } catch (ClassNotFoundException | SecurityException | IOException e) {
    throw new IOException(e);
  }
}

/**
 Writes data to the deleteData file.<br><b>Warning! This will overwrite any data currently in the file!</b><br>Use "new DeleteData().append()" to add an entry to the file.
 * @param deletePendings The data to write to the file.
 * @throws IOException If there was an error writing data to the file.
 */
private static void write(Collection<DeletePending> deletePendings) throws IOException {

  try (FileOutputStream fileOutputStream = new FileOutputStream(deleteData)) {
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    objectOutputStream.writeObject(deletePendings);
  }
}

/**
 Removes the given deletePending object from the delete pending file.
 * @param deletePending The given deletePending object to remove.
 * @throws IOException If there was an error reading or writing to the file.
 */
private static void remove(DeletePending deletePending) throws IOException {
  ArrayList<DeletePending> deletePendings = read();
  for (int i = 0; i < deletePendings.size(); i++) {
    if (!deletePendings.get(i).getRandName().equals(deletePending.getRandName())) {
      continue;
    }

    deletePendings.remove(i);
    break;
  }
  write(deletePendings);
}


/**
 Parses the string given in the format "{number}{unit}". There can be any length of number-unit pares.<br>
 Units:<br>
 g - gigabyte.<br>
 m - megabyte.<br>
 k - kilobytes.<br>
 b - bytes.
 @param sizeString The size string.
 @return The parsed size represented by the given size string. */
public static long parseSize(String sizeString) {
  long size = 0;
  char[] sizeChars = sizeString.toLowerCase().toCharArray();

  for (int i = 0; i < sizeChars.length; i++) {

    switch (sizeChars[i]) {
    case 'g' -> {
      size += getProceeding(0L, sizeChars, i)*1024*1024*1024;
    }

    case 'm' -> {
      size += getProceeding(0L, sizeChars, i)*1024*1024;
    }

    case 'k' -> {
      size += getProceeding(0L, sizeChars, i)*1024;
    }

    case 'b' -> {
      size += getProceeding(0L, sizeChars, i);
    }

    }
  }

  return size;
}

/**
 Gets all the number chars before the unit specifier.
 The unit specifier would be a letter denoting a value. E.g: w for week.
 * @param time Current stored value for this unit.
 * @param timeChars The array to parse.
 * @param index The index in the array that the unit specifier was found.
 * @return The number the user entered before the unit specifier.
 */
public static long getProceeding(long time, char[] timeChars, int index) {

  for (int ii = -1; Character.isDigit(timeChars[index-ii]); ii--) {
    //if the index to get would be below 0 ends the loop
    if ((index-ii)-1 < 0) {
      break;
    }

    int parsedVal = Integer.parseInt(String.valueOf(timeChars[index-ii]));
    //sets the number at the next order of magnitude to the parsed one
    time = (long) (time+parsedVal*(Math.pow(10d, ii*-1)));
  }

  return time;
}

}
