package me.tye.cogworks;

import me.tye.cogworks.util.Util;
import me.tye.cogworks.util.customObjects.FileData;
import me.tye.cogworks.util.customObjects.Log;
import me.tye.cogworks.util.customObjects.PathHolder;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static me.tye.cogworks.util.Util.itemProperties;
import static me.tye.cogworks.util.Util.plugin;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class FileGui implements Listener {
public static HashMap<UUID,FileData> fileData = new HashMap<>();
public static HashMap<String,PathHolder> position = new HashMap<>();

@EventHandler
public void clickEvent(InventoryClickEvent e) {
  if (!(e.getWhoClicked() instanceof Player)) return;
  Player player = (Player) e.getWhoClicked();
  if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
  String identifier = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING);

  ItemStack itemStack = e.getCurrentItem();
  ItemMeta itemMeta = itemStack.getItemMeta();
  if (itemMeta == null)
    return;
  String itemDisplay = itemMeta.getDisplayName();

  //Prevents stealing
  if (identifier != null) {
    e.setCancelled(true);
  } else return;

  if (!position.containsKey(player.getName())) return;

  FileData data = fileData.get(player.getUniqueId());
  PathHolder pathHolder = position.get(player.getName());
  if (identifier.equals("file")) {
    if (itemDisplay.startsWith(String.valueOf(ChatColor.YELLOW)))
      pathHolder.setCurrentPath(pathHolder.getCurrentPath()+File.separator+itemDisplay.substring(2));
    else
      pathHolder.setCurrentPath(pathHolder.getCurrentPath()+File.separator+itemDisplay);
  }
  File lastFileClicked = new File(pathHolder.getCurrentPath());


  //file navigation
  switch (identifier) {
  case "up" -> {
    pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
    open(player);
  }

  case "exit" -> {
    player.closeInventory();
    position.remove(player.getName());
    fileData.remove(player.getUniqueId());
  }

  case "file" -> {
    if (data.getDeleteMode()) {
      if (!player.hasPermission("cogworks.file.rm")) return;
      Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, Util.getLang("fileGui.confirmDel.title"));

      ArrayList<ItemStack> content = new ArrayList<>();
      for (int i = 0; i <= 8; i++) {
        if (i == 3) {
          content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), Util.getLang("fileGui.confirmDel.deny"), null, "up"));
        } else if (i == 5) {
          String newIdentifier = "confirmedDelete";
          if (lastFileClicked.isDirectory() && (lastFileClicked.list() == null || Objects.requireNonNull(lastFileClicked.list()).length != 0))
            newIdentifier = "confirmDirDelete";

          content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), Util.getLang("fileGui.confirmDel.confirm"), null, newIdentifier));
        } else {
          content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
        }
      }

      gui.setContents(content.toArray(new ItemStack[0]));
      player.openInventory(gui);
    } else {
      pathHolder.setCurrentPath(lastFileClicked.getAbsolutePath());
      fileData.put(player.getUniqueId(), data.setCurrentLine(1));
      open(player);
    }
  }

  case "scrollUp", "scrollDown" -> {
    if (identifier.equals("scrollDown"))
      data.setCurrentLine(data.getCurrentLine()+5);
    if (identifier.equals("scrollUp"))
      data.setCurrentLine(Math.max(1, data.getCurrentLine()-5));
    data.setSearchInstance(0);
    fileData.put(player.getUniqueId(), data);
    open(player);
  }

  case "goto" ->
      new AnvilGUI.Builder()
          .plugin(plugin)
          .preventClose()
          .title(Util.getLang("fileGui.goto.title"))
          .itemLeft(itemProperties(new ItemStack(Material.PAPER), String.valueOf(data.getCurrentLine()), null, null))
          .onClick((slot, stateSnapshot) -> {
            if (slot == AnvilGUI.Slot.OUTPUT) {
              return List.of(AnvilGUI.ResponseAction.close());
            }
            return Collections.emptyList();
          })
          .onClose(stateSnapshot -> {
            if (stateSnapshot.getOutputItem().getItemMeta() != null) {
              try {
                int line = Integer.parseInt(stateSnapshot.getOutputItem().getItemMeta().getDisplayName().trim());
                if (line < 1)
                  line = 1;
                fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setCurrentLine(line));
              } catch (Exception ignore) {
              }
            }
            open(stateSnapshot.getPlayer());
          })
          .open(player);

  case "search" -> {
    if (e.isLeftClick()) {

      ItemStack paper = itemProperties(new ItemStack(Material.PAPER), "\u200B", null, null);

      new AnvilGUI.Builder()
          .plugin(plugin)
          .preventClose()
          .title(Util.getLang("fileGui.search.title"))
          .itemLeft(paper)
          .itemOutput(paper)
          .onClick((slot, stateSnapshot) -> {
            if (slot == AnvilGUI.Slot.OUTPUT) {
              return List.of(AnvilGUI.ResponseAction.close());
            }
            return Collections.emptyList();
          })
          .onClose(stateSnapshot -> {
            fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setSearchPhrase(Objects.requireNonNull(stateSnapshot.getOutputItem().getItemMeta()).getDisplayName()));
            open(stateSnapshot.getPlayer());
          })
          .open(player);
    } else {
      try {
        String searchPhrase = data.getSearchPhrase();
        if (searchPhrase.isEmpty()) {
          player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
          return;
        }
        FileReader reader = new FileReader(position.get(player.getName()).getCurrentPath());
        BufferedReader fileReader = new BufferedReader(reader);

        String text;
        int i = 0;
        int instance = data.getSearchInstance();
        int instances = 1;
        int firstInstanceLine = 0;

        while ((text = fileReader.readLine()) != null) {
          i++;
          if (instance == 0) {
            if (text.contains(searchPhrase)) {
              if (firstInstanceLine == 0)
                firstInstanceLine = i;
              instances++;
              if (i < data.getCurrentLine()) continue;
              fileData.put(player.getUniqueId(), data.setCurrentLine(i).setSearchInstance(instances));
              open(player);
              fileReader.close();
              reader.close();
              return;
            }
          } else {
            if (text.contains(searchPhrase)) {
              if (firstInstanceLine == 0)
                firstInstanceLine = i;
              if (instances == instance) {
                fileData.put(player.getUniqueId(), data.setSearchInstance(instance+1).setCurrentLine(i));
                open(player);
                fileReader.close();
                reader.close();
                return;
              }
              instances++;
            }
          }
        }

        fileReader.close();
        reader.close();

        if (firstInstanceLine == 0) {
          player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
          return;
        }

        fileData.put(player.getUniqueId(), data.setCurrentLine(firstInstanceLine).setSearchInstance(2));
        open(player);

      } catch (IOException ex) {
        new Log(player, "fileGui.search.readingErr").setFilePath(position.get(player.getName()).getCurrentPath()).log();
      }
    }
  }

  //file creation
  case "createFileMenu" -> {
    if (!player.hasPermission("cogworks.file.mk")) return;
    player.closeInventory();
    Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, Util.getLang("fileGui.createFileMenu.title"));

    ArrayList<ItemStack> content = new ArrayList<>();
    for (int i = 0; i <= 8; i++) {
      if (i == 3) {
        content.add(itemProperties(new ItemStack(Material.WHITE_WOOL), Util.getLang("fileGui.createFileMenu.file"), null, "createFile"));
      } else if (i == 5) {
        content.add(itemProperties(new ItemStack(Material.YELLOW_WOOL), Util.getLang("fileGui.createFileMenu.folder"), null, "createFolder"));
      } else {
        content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
      }
    }

    gui.setContents(content.toArray(new ItemStack[0]));
    player.openInventory(gui);
  }

  case "createFile", "createFolder" -> {
    if (!player.hasPermission("cogworks.file.mk")) return;
    player.closeInventory();
    if (identifier.equals("createFolder")) {
      if (itemDisplay.startsWith(String.valueOf(ChatColor.YELLOW)))
        itemMeta.setDisplayName(itemDisplay.substring(2));
      itemStack.setItemMeta(itemMeta);
      itemProperties(itemStack, null, null, "confirmCreateFolder");
    } else {
      itemProperties(itemStack, null, null, "confirmCreateFile");
    }
    new AnvilGUI.Builder()
        .plugin(plugin)
        .preventClose()
        .title(Util.getLang("fileGui.createFile.title"))
        .itemLeft(itemStack)
        .onClick((slot, stateSnapshot) -> {
          if (slot == AnvilGUI.Slot.OUTPUT) {
            return List.of(AnvilGUI.ResponseAction.close());
          }
          return Collections.emptyList();
        })
        .onClose(stateSnapshot -> {
          if (stateSnapshot.getOutputItem().getItemMeta() == null) return;
          try {
            if (checkIdentifier(stateSnapshot.getOutputItem(), "confirmCreateFolder")) {
              Files.createDirectory(Path.of(position.get(stateSnapshot.getPlayer().getName()).getCurrentPath()+File.separator+stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
            } else if (checkIdentifier(stateSnapshot.getOutputItem(), "confirmCreateFile")) {
              Files.createFile(Path.of(position.get(stateSnapshot.getPlayer().getName()).getCurrentPath()+File.separator+stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
            } else {
              return;
            }
          } catch (FileAlreadyExistsException ex) {
            new Log(player, "fileGui.createFile.fileExists").setException(ex).isFile(checkIdentifier(stateSnapshot.getOutputItem(), "confirmCreateFile")).setFileName(stateSnapshot.getOutputItem().getItemMeta().getDisplayName()).log();
          } catch (IOException ex) {
            new Log(player, "fileGui.createFile.creationErr").setException(ex).isFile(checkIdentifier(stateSnapshot.getOutputItem(), "confirmCreateFile")).setFileName(stateSnapshot.getOutputItem().getItemMeta().getDisplayName()).log();
          } catch (InvalidPathException ex) {
            new Log(player, "fileGui.createFile.invalidName").setException(ex).setFileName(stateSnapshot.getOutputItem().getItemMeta().getDisplayName()).log();
          }
          open(stateSnapshot.getPlayer());
        })
        .open(player);
  }

  //file deleting
  case "deleteFileToggle" -> {
    if (!player.hasPermission("cogworks.file.rm")) return;
    fileData.put(player.getUniqueId(), data.setDeleteMode(!data.getDeleteMode()));
    open(player);
  }

  case "confirmedDelete" -> {
    if (!player.hasPermission("cogworks.file.rm")) return;
    try {
      if (lastFileClicked.isFile()) FileUtils.delete(lastFileClicked);
      if (lastFileClicked.isDirectory()) FileUtils.deleteDirectory(lastFileClicked);
    } catch (IOException ex) {
      new Log(player, "fileGui.confirmedDelete.error").setFileName(lastFileClicked.getName()).log();
    }
    pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
    open(player);
  }

  case "confirmDirDelete" -> {
    if (!player.hasPermission("cogworks.file.rm")) return;
    player.closeInventory();
    Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, Util.getLang("fileGui.confirmDirDelete.title"));
    ArrayList<ItemStack> content = new ArrayList<>();
    for (int i = 0; i <= 8; i++) {
      if (i == 1) {
        content.add(itemProperties(new ItemStack(Material.OAK_SIGN), Util.getLang("fileGui.confirmDirDelete.sign"), null, null));
      } else if (i == 3) {
        content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), Util.getLang("fileGui.confirmDirDelete.deny"), null, "up"));
      } else if (i == 5) {
        content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), Util.getLang("fileGui.confirmDirDelete.confirm"), null, "confirmedDelete"));
      } else {
        content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
      }
    }

    gui.setContents(content.toArray(new ItemStack[0]));
    player.openInventory(gui);
  }

  //file editing
  case "text" -> {
    if (!player.hasPermission("cogworks.file.edit")) return;
    if (Boolean.parseBoolean(itemMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "edited"), PersistentDataType.STRING)))
      return;
    itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "edited"), PersistentDataType.STRING, "true");
    itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING, "edit");
    itemStack.setItemMeta(itemMeta);
    new AnvilGUI.Builder()
        .plugin(plugin)
        .preventClose()
        .title(Util.getLang("fileGui.fileEditor.title"))
        .itemLeft(e.getCurrentItem())
        .itemOutput(itemStack)
        .onClick((slot, stateSnapshot) -> {
          if (slot == AnvilGUI.Slot.OUTPUT) {
            return List.of(AnvilGUI.ResponseAction.close());
          }
          return Collections.emptyList();
        })
        .onClose(stateSnapshot -> {
          PathHolder localPathHolder = position.get(stateSnapshot.getPlayer().getName());
          if (stateSnapshot.getOutputItem().getItemMeta() == null) return;
          Integer line = stateSnapshot.getOutputItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "line"), PersistentDataType.INTEGER);
          Integer offset = stateSnapshot.getOutputItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "offset"), PersistentDataType.INTEGER);
          if (line == null || offset == null) return;

          try {
            BufferedReader br = new BufferedReader(new FileReader(localPathHolder.getCurrentPath()));
            StringBuilder content = new StringBuilder();

            String text;
            int i = 1;
            while ((text = br.readLine()) != null) {
              if (line == i) {
                int startOfReplace = 35*offset;
                String start = text.substring(0, startOfReplace);
                String end = text.substring(Math.min(startOfReplace+35, text.length()));
                text = start+stateSnapshot.getOutputItem().getItemMeta().getDisplayName()+end;
              }
              i++;
              content.append(text).append("\n");
            }
            ;
            br.close();
            Files.writeString(Path.of(localPathHolder.getCurrentPath()), content.toString());
          } catch (IOException ex) {
            new Log(player, "fileGui.fileEditor.editingErr").setFilePath(position.get(player.getName()).getRelativePath()).log();
          }
          open(stateSnapshot.getPlayer());
        })
        .open(player);
  }

  case "fileBackground" -> {
    if (!player.hasPermission("cogworks.file.edit")) return;
    Integer line = itemMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "line"), PersistentDataType.INTEGER);
    if (line == null) return;
    ItemStack paper = itemProperties(new ItemStack(Material.PAPER), "\u200B", null, "");
    ItemMeta meta = paper.getItemMeta();
    assert meta != null;
    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "line"), PersistentDataType.INTEGER, line);
    paper.setItemMeta(meta);
    new AnvilGUI.Builder()
        .plugin(getPlugin(CogWorks.class))
        .preventClose()
        .title(Util.getLang("fileGui.fileEditor.title"))
        .itemLeft(paper)
        .onClick((slot, stateSnapshot) -> {
          if (slot == AnvilGUI.Slot.OUTPUT) {
            return List.of(AnvilGUI.ResponseAction.close());
          }
          return Collections.emptyList();
        })
        .onClose(stateSnapshot -> {
          PathHolder localPathHolder = position.get(stateSnapshot.getPlayer().getName());
          if (stateSnapshot.getOutputItem().getItemMeta() == null) return;
          Integer localLine = stateSnapshot.getOutputItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(getPlugin(CogWorks.class), "line"), PersistentDataType.INTEGER);
          if (localLine == null) return;

          String newText = stateSnapshot.getOutputItem().getItemMeta().getDisplayName();
          if (newText.startsWith("\u200B")) newText = newText.substring(1);

          try {
            BufferedReader br = new BufferedReader(new FileReader(localPathHolder.getCurrentPath()));
            StringBuilder content = new StringBuilder();

            int i = 1;
            boolean textAdded = false;
            while (true) {
              String text = br.readLine();
              if (localLine == i) {
                if (text == null) {
                  content.append(newText);
                  break;
                } else {
                  text += newText;
                  textAdded = true;
                }
              }
              i++;
              if (!textAdded) {
                if (text == null) text = "";
                content.append(text).append("\n");
              } else {
                if (text == null) break;
                content.append(text).append("\n");
              }
            }
            br.close();
            Files.writeString(Path.of(localPathHolder.getCurrentPath()), content.toString());
          } catch (IOException ex) {
            new Log(player, "fileGui.fileEditor.editingErr").setFilePath(position.get(player.getName()).getRelativePath()).log();
          }
          open(stateSnapshot.getPlayer());
        })
        .open(player);
  }
  }

  fileData.put(player.getUniqueId(), data);
  position.put(player.getName(), pathHolder);
}

/**
 Opens the file / folder that is stored in the position HashMap.
 @param player player to open the file / folder for. */
public static void open(Player player) {
  if (!player.hasPermission("cogworks.file.nav")) {
    fileData.remove(player.getUniqueId());
    position.remove(player.getName());
    return;
  }
  FileData data = fileData.get(player.getUniqueId());
  PathHolder pathHolder = position.get(player.getName());
  File file = new File(pathHolder.getCurrentPath());

  Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+"~"+position.get(player.getName()).getRelativePath()+ChatColor.GOLD+" $");
  ArrayList<ItemStack> content = new ArrayList<>();
  ArrayList<ItemStack> files = new ArrayList<>();
  ArrayList<ItemStack> folders = new ArrayList<>();

  if (file.isDirectory()) {
    try (Stream<Path> paths = Files.list(Path.of(pathHolder.getCurrentPath()))) {
      //sorts the files & folders
      for (Path path : paths.toList()) {
      if (Files.isDirectory(path)) {
        ItemStack item = new ItemStack(Material.YELLOW_WOOL);
        if (data.getDeleteMode()) item = new ItemStack(Material.RED_WOOL);
        folders.add(itemProperties(item, ChatColor.YELLOW+path.getFileName().toString(), List.of(Util.getLang("fileGui.open.folder")), "file"));
      } else {
        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        if (data.getDeleteMode()) item = new ItemStack(Material.ORANGE_WOOL);
        files.add(itemProperties(item, path.getFileName().toString(), List.of(Util.getLang("fileGui.open.file")), "file"));
      }
    }
    folders.addAll(files);
    } catch (Exception e) {
      new Log(player, "fileGui.open.getFilesErr").setFilePath(file.getAbsolutePath()).log();
      return;
    }

    for (int i = 0; i <= 53; i++) {
      if (i == 0) {
        content.add(itemProperties(new ItemStack(Material.ARROW), Util.getLang("fileGui.open.up"), List.of(Util.getLang("fileGui.open.upDesc")), "up"));
      } else if (i == 4) {
        content.add(itemProperties(new ItemStack(Material.BARRIER), Util.getLang("fileGui.open.exit"), List.of(Util.getLang("fileGui.open.exitDesc")), "exit"));
      } else if (i == 7 && player.hasPermission("cogworks.file.rm")) {
        content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), Util.getLang("fileGui.open.delete"), List.of(Util.getLang("fileGui.open.deleteDesc")), "deleteFileToggle"));
      } else if (i == 8 && player.hasPermission("cogworks.file.mk")) {
        content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), Util.getLang("fileGui.open.create"), List.of(Util.getLang("fileGui.open.createDesc")), "createFileMenu"));
      } else if (i > 8 && folders.size() > i-9) {
        content.add(folders.get(i-9));
      } else {
        content.add(new ItemStack(Material.AIR));
      }
    }
  }

  if (file.isFile()) {
    int lineNumber = data.getCurrentLine();
    String searchPhrase = data.getSearchPhrase();

    ArrayList<String> lines = new ArrayList<>();
    BufferedReader fileReader = null;
    try {
      fileReader = new BufferedReader(new FileReader(file));

      String text;
      while ((text = fileReader.readLine()) != null)
        lines.add(text);

    } catch (IOException e) {
      new Log(player, "fileGui.open.readErr").setFilePath(position.get(player.getName()).getRelativePath()).setException(e).log();
      try {
        assert fileReader != null;
        fileReader.close();
      } catch (Exception ex) {
        new Log(player, "fileGui.open.fileCloseErr").setFilePath(position.get(player.getName()).getRelativePath()).setException(e).log();
        return;
      }
      return;
    }
    try {
      fileReader.close();
    } catch (IOException e) {
      new Log(player, "fileGui.open.fileCloseErr").setFilePath(position.get(player.getName()).getRelativePath()).setException(e).log();
    }

    if (lineNumber < 1) lineNumber = 1;

    for (int i = 0; i <= 8; i++) {
      if (i == 0) {
        content.add(itemProperties(new ItemStack(Material.TIPPED_ARROW), Util.getLang("fileGui.open.scrollDown"), List.of(Util.getLang("fileGui.open.scrollDownDesc")), "scrollDown"));
      } else if (i == 1) {
        content.add(itemProperties(new ItemStack(Material.TIPPED_ARROW), Util.getLang("fileGui.open.scrollUp"), List.of(Util.getLang("fileGui.open.scrollUpDesc")), "scrollUp"));
      } else if (i == 2) {
        content.add(itemProperties(new ItemStack(Material.OAK_SIGN), Util.getLang("fileGui.open.lineNum", "lineNum", String.valueOf(lineNumber)), List.of(Util.getLang("fileGui.open.lineNumDesc")), null));
      } else if (i == 3) {
        content.add(itemProperties(new ItemStack(Material.WRITABLE_BOOK), Util.getLang("fileGui.open.search", "searchPhrase", searchPhrase), List.of(Util.getLang("fileGui.open.searchDesc0"), Util.getLang("fileGui.open.searchDesc1"), Util.getLang("fileGui.open.searchDesc2")), "search"));
      } else if (i == 4) {
        content.add(itemProperties(new ItemStack(Material.SPECTRAL_ARROW), Util.getLang("fileGui.open.goto"), List.of(Util.getLang("fileGui.open.gotoDesc")), "goto"));
      } else if (i == 7) {
        content.add(itemProperties(new ItemStack(Material.ARROW), Util.getLang("fileGui.open.back"), List.of(Util.getLang("fileGui.open.backDesc")), "up"));
      } else if (i == 8) {
        content.add(itemProperties(new ItemStack(Material.BARRIER), Util.getLang("fileGui.open.exit"), List.of(Util.getLang("fileGui.open.exitDesc")), "exit"));
      } else {
        content.add(itemProperties(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null, null));
      }
    }

    for (int i = 0; i < 5; i++) {
      if (lines.size()-1 >= i+lineNumber-1) {
        //max 50 chars per item
        String line = lines.get(i+lineNumber-1);
        for (int ii = 0; ii <= 8; ii++) {
          String paperName = line.substring(0, Math.min(35, line.length()));
          line = line.substring(Math.min(35, line.length()));
          if (!paperName.isEmpty()) {
            ItemStack paper = itemProperties(new ItemStack(Material.PAPER), paperName, null, "text");
            if (!searchPhrase.isEmpty() && paperName.contains(searchPhrase)) {
              paper = itemProperties(new ItemStack(Material.FILLED_MAP), paperName.replace(searchPhrase, ChatColor.YELLOW+searchPhrase+ChatColor.WHITE), null, "text");
            }

            ItemMeta paperMeta = paper.getItemMeta();
            assert paperMeta != null;
            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(CogWorks.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(CogWorks.class), "offset"), PersistentDataType.INTEGER, ii);
            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(CogWorks.class), "edited"), PersistentDataType.STRING, "false");
            paper.setItemMeta(paperMeta);
            content.add(paper);
          } else {
            ItemStack background = itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, "fileBackground");
            ItemMeta meta = background.getItemMeta();
            assert meta != null;
            meta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(CogWorks.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
            background.setItemMeta(meta);
            content.add(background);
          }
        }
      } else {
        for (int ii = 0; ii <= 8; ii++) {
          ItemStack background = itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, "fileBackground");
          ItemMeta meta = background.getItemMeta();
          assert meta != null;
          meta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(CogWorks.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
          background.setItemMeta(meta);
          content.add(background);
        }
      }
    }

    fileData.put(player.getUniqueId(), data);
  }

  if (content.isEmpty()) return;

  gui.setContents(content.toArray(new ItemStack[0]));
  player.openInventory(gui);
}

public static boolean checkIdentifier(ItemStack item, String identifier) {
  ItemMeta meta = item.getItemMeta();
  if (meta == null) return false;
  return identifier.equals(meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "identifier"), PersistentDataType.STRING));
}
}
