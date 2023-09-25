package me.tye.filemanager;

import me.tye.filemanager.util.FileData;
import me.tye.filemanager.util.PathHolder;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

import static me.tye.filemanager.FileManager.itemProperties;
import static me.tye.filemanager.FileManager.log;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class FileGui implements Listener {
    public static HashMap<UUID, FileData> fileData = new HashMap<>();
    public static HashMap<String, PathHolder> position = new HashMap<>();

    @EventHandler
    public void clickEvent(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
        String identifier = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "identifier"), PersistentDataType.STRING);

        //Prevents stealing
        if (identifier != null) {
            e.setCancelled(true);
        } else return;

        if (!position.containsKey(player.getName())) return;

        FileData data = fileData.get(player.getUniqueId());
        PathHolder pathHolder = position.get(player.getName());
        if (identifier.equals("file")) {
            if (e.getCurrentItem().getItemMeta().getDisplayName().startsWith(String.valueOf(ChatColor.YELLOW)))
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName().substring(2));
            else
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName());
        }
        File lastFileClicked = new File(pathHolder.getCurrentPath());


        //general
        if (identifier.equals("up")) {
            pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
            open(player);
        }

        if (identifier.equals("exit")) {
            player.closeInventory();
            position.remove(player.getName());
            fileData.remove(player.getUniqueId());
        }

        if (identifier.equals("file")) {
            if (data.getDeleteMode()) {
                if (!player.hasPermission("fileman.file.rm")) return;
                Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, "Confirm Deletion");

                ArrayList<ItemStack> content = new ArrayList<>();
                for (int i = 0; i <= 8; i++) {
                    if (i == 3) {
                        content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), ChatColor.RED + "Deny", null, "up"));
                    } else if (i == 5) {
                        String newIdentifier = "confirmedDelete";
                        if (lastFileClicked.isDirectory() && (lastFileClicked.list() == null || lastFileClicked.list().length != 0)) newIdentifier = "confirmDirDelete";

                        content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), ChatColor.GREEN + "Confirm", null, newIdentifier));
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

        //file creation
        if (identifier.equals("createFileMenu")) {
            if (!player.hasPermission("fileman.file.mk")) return;
            player.closeInventory();
            Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, "File or Folder");

            ArrayList<ItemStack> content = new ArrayList<>();
            for (int i = 0; i <= 8; i++) {
                if (i == 3) {
                    content.add(itemProperties(new ItemStack(Material.WHITE_WOOL), "File", null, "createFile"));
                } else if (i == 5) {
                    content.add(itemProperties(new ItemStack(Material.YELLOW_WOOL), ChatColor.YELLOW + "Folder", null, "createFolder"));
                } else {
                    content.add(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
                }
            }

            gui.setContents(content.toArray(new ItemStack[0]));
            player.openInventory(gui);
        }

        if (identifier.equals("createFile") || identifier.equals("createFolder")) {
            if (!player.hasPermission("fileman.file.mk")) return;
            player.closeInventory();

            ItemStack item = e.getCurrentItem();
            if (identifier.equals("createFolder")) {
                ItemMeta meta = item.getItemMeta();
                if (meta.getDisplayName().startsWith(String.valueOf(ChatColor.YELLOW))) meta.setDisplayName(meta.getDisplayName().substring(2));
                item.setItemMeta(meta);
                itemProperties(item, null, null, "confirmCreateFolder");
            } else {
                itemProperties(item, null, null, "confirmCreateFile");
            }

            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("Name:")
                    .itemLeft(item)
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
                                Files.createDirectory(Path.of(position.get(stateSnapshot.getPlayer().getName()).getCurrentPath() + File.separator + stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
                            } else if (checkIdentifier(stateSnapshot.getOutputItem(), "confirmCreateFile")) {
                                Files.createFile(Path.of(position.get(stateSnapshot.getPlayer().getName()).getCurrentPath() + File.separator + stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
                            } else {
                                return;
                            }
                        } catch (FileAlreadyExistsException ex) {
                            log(ex, stateSnapshot.getPlayer(), Level.WARNING, "There is already a file/folder with that name.");
                        } catch (IOException ex) {
                            log(ex, stateSnapshot.getPlayer(), Level.WARNING, "There was an error creating that file/folder.");
                        }
                        open(stateSnapshot.getPlayer());
                    })
                    .open(player);
        }

        //file deleting
        if (identifier.equals("deleteFileToggle")) {
            if (!player.hasPermission("fileman.file.rm")) return;
            fileData.put(player.getUniqueId(), data.setDeleteMode(!data.getDeleteMode()));
            open(player);
        }

        if (identifier.equals("confirmedDelete")) {
            if (!player.hasPermission("fileman.file.rm")) return;
            try {
                if (lastFileClicked.isFile()) FileUtils.delete(lastFileClicked);
                if (lastFileClicked.isDirectory()) FileUtils.deleteDirectory(lastFileClicked);
            } catch (IOException ex) {
                log(ex, player, Level.WARNING, "Failed to delete "+lastFileClicked.getName()+".");
            }
            pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
            open(player);
        }

        if (identifier.equals("confirmDirDelete")) {
            if (!player.hasPermission("fileman.file.rm")) return;
            player.closeInventory();
            Inventory gui = Bukkit.createInventory(player, InventoryType.DROPPER, "See sign.");
            ArrayList<ItemStack> content = new ArrayList<>();
            for (int i = 0; i <= 8; i++) {
                if (i == 1) {
                    content.add(itemProperties(new ItemStack(Material.OAK_SIGN), ChatColor.BOLD.toString() + ChatColor.RED + "This folder contains files within it! Are you sure?", null, null));
                }else if (i == 3) {
                    content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), ChatColor.RED + "No", null, "up"));
                } else if (i == 5) {
                    content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), ChatColor.GREEN + "Yes", null, "confirmedDelete"));
                } else {
                    content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
                }
            }

            gui.setContents(content.toArray(new ItemStack[0]));
            player.openInventory(gui);
        }

        //file editing
        if (identifier.equals("scrollUp") || identifier.equals("scrollDown")) {
            if (identifier.equals("scrollDown"))
                data.setCurrentLine(Math.min(data.getMaxLine(), data.getCurrentLine()+5));
            if (identifier.equals("scrollUp"))
                data.setCurrentLine(Math.max(1, data.getCurrentLine()-5));

            data.setSearchInstance(0);
            fileData.put(player.getUniqueId(), data);
            open(player);
        }

        if (identifier.equals("goto")) {
            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("Min: 1, Max: "+data.getMaxLine())
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
                                fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setCurrentLine(line));
                            } catch (Exception ignore) {}
                        }
                        open(stateSnapshot.getPlayer());
                    })
                    .open(player);
        }

        if (identifier.equals("search")) {
            if (e.isLeftClick()) {
                ItemStack paper = itemProperties(new ItemStack(Material.PAPER), "\u200B", null, null);

                new AnvilGUI.Builder()
                        .plugin(JavaPlugin.getPlugin(FileManager.class))
                        .preventClose()
                        .title("Search:")
                        .itemLeft(paper)
                        .itemOutput(paper)
                        .onClick((slot, stateSnapshot) -> {
                            if (slot == AnvilGUI.Slot.OUTPUT) {
                                return List.of(AnvilGUI.ResponseAction.close());
                            }
                            return Collections.emptyList();
                        })
                        .onClose(stateSnapshot -> {
                            fileData.put(stateSnapshot.getPlayer().getUniqueId(), fileData.get(stateSnapshot.getPlayer().getUniqueId()).setSearchPhrase(stateSnapshot.getOutputItem().getItemMeta().getDisplayName()));
                            open(stateSnapshot.getPlayer());
                        })
                        .open(player);
            }
            else {
                try {
                    String searchPhrase = data.getSearchPhrase();
                    if (searchPhrase.isEmpty())  {
                        player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
                        return;
                    }

                    BufferedReader fileReader = new BufferedReader(new FileReader(position.get(player.getName()).getCurrentPath()));

                    String text;
                    int i = 0;
                    int instance = data.getSearchInstance();
                    int instances = 1;
                    int firstInstance = 0;

                    while ((text = fileReader.readLine()) != null) {
                        i++;
                        if (instance == 0) {
                            if (text.contains(searchPhrase)) {
                                if (firstInstance == 0) firstInstance = i;
                                instances++;
                                if (i < data.getCurrentLine()) continue;
                                fileData.put(player.getUniqueId(), data.setCurrentLine(i).setSearchInstance(instances));
                                open(player);
                                return;
                            }
                        } else {
                            if (text.contains(searchPhrase)) {
                                if (firstInstance == 0) firstInstance = i;
                                if (instances == instance) {
                                    fileData.put(player.getUniqueId(), data.setSearchInstance(instance + 1).setCurrentLine(i));
                                    open(player);
                                    return;
                                }
                                instances++;
                            }
                        }
                    }

                    if (firstInstance == 0) {
                        player.playNote(player.getLocation(), Instrument.PLING, Note.sharp(2, Note.Tone.F));
                        return;
                    }

                    fileData.put(player.getUniqueId(), data.setSearchInstance(firstInstance));
                    open(player);

                } catch (IOException ex) {
                    log(ex, player, Level.WARNING, "There was an error reading \""+position.get(player.getName()).getRelativePath()+"\" while trying to search.");
                }
            }
        }

        if (identifier.equals("text")) {
            if (Boolean.TRUE.equals(e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN))) return;

            ItemStack paper = e.getCurrentItem();
            ItemMeta meta = paper.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "identifier"), PersistentDataType.STRING, "edit");
            paper.setItemMeta(meta);

            new AnvilGUI.Builder()
                    .plugin(JavaPlugin.getPlugin(FileManager.class))
                    .preventClose()
                    .title("File editor:")
                    .itemLeft(e.getCurrentItem())
                    .itemOutput(paper)
                    .onClick((slot, stateSnapshot) -> {
                        if (slot == AnvilGUI.Slot.OUTPUT) {
                            return List.of(AnvilGUI.ResponseAction.close());
                        }
                        return Collections.emptyList();
                    })
                    .onClose(stateSnapshot -> {
                        editFile(stateSnapshot.getPlayer(), stateSnapshot.getOutputItem());
                        open(stateSnapshot.getPlayer());
                    })
                    .open(player);
        }


        fileData.put(player.getUniqueId(), data);
        position.put(player.getName(), pathHolder);
    }

    /**
     * Opens the file / folder that is stored in the position HashMap.
     * @param player player to open the file / folder for.
     */
    public static void open(Player player) {
        if (!player.hasPermission("fileman.file.nav")) {
            fileData.remove(player.getUniqueId());
            position.remove(player.getName());
            return;
        }
        FileData data = fileData.get(player.getUniqueId());
        PathHolder pathHolder = position.get(player.getName());
        File file = new File(pathHolder.getCurrentPath());

        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+"~"+position.get(player.getName()).getRelativePath()+ChatColor.GOLD+" $");
        ArrayList<ItemStack> content = new ArrayList<>();

        if (file.isDirectory()) {
            List<Path> paths;
            try {
                paths = Files.list(Path.of(pathHolder.getCurrentPath())).toList();
            } catch (Exception e) {
                log(e, player, Level.WARNING, "There was an error trying to get the files in that folder.");
                return;
            }

            //creates file and folder objects, then sorts them
            ArrayList<ItemStack> files = new ArrayList<>();
            ArrayList<ItemStack> folders = new ArrayList<>();
            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    ItemStack item = new ItemStack(Material.YELLOW_WOOL);
                    if (data.getDeleteMode()) item = new ItemStack(Material.RED_WOOL);
                    folders.add(itemProperties(item, ChatColor.YELLOW + path.getFileName().toString(), List.of("Folder"), "file"));
                }
                else {
                    ItemStack item = new ItemStack(Material.WHITE_WOOL);
                    if (data.getDeleteMode()) item = new ItemStack(Material.ORANGE_WOOL);
                    files.add(itemProperties(item, path.getFileName().toString(), List.of("File"), "file"));
                }
            }
            folders.addAll(files);

            for (int i = 0; i <= 53; i++) {
                if (i == 0) {
                    content.add(itemProperties(new ItemStack(Material.ARROW), "Up", List.of("Goes up a file."), "up"));
                } else if (i == 4) {
                    content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui."), "exit"));
                } else if (i == 7 && player.hasPermission("fileman.file.rm")) {
                    content.add(itemProperties(new ItemStack(Material.RED_CONCRETE), "Delete", List.of("Toggles delete select."), "deleteFileToggle"));
                } else if (i == 8 && player.hasPermission("fileman.file.mk")) {
                    content.add(itemProperties(new ItemStack(Material.GREEN_CONCRETE), "Create", List.of("Creates a new file/folder in the current folder."), "createFileMenu"));
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
                log(e, player, Level.WARNING, "There was an error trying to read \""+position.get(player.getName()).getRelativePath()+"\".");
                try {
                    assert fileReader != null;
                    fileReader.close();
                } catch (Exception ex) {
                    log(e, player, Level.WARNING, "There was an error closing the file \""+file.getName()+"\". This file will not be able to be used until the server is restarted.");
                    return;
                }
                return;
            }
            try {
                fileReader.close();
            } catch (IOException e) {
                log(e, player, Level.WARNING, "There was an error closing the file \""+file.getName()+"\". This file will not be able to be used until the server is restarted.");
            }

            if (lineNumber > lines.size()) lineNumber = lines.size();
            if (lineNumber == 0) lineNumber++;
            if (lineNumber < 0) lineNumber = 1;

            data.setMaxLine(lines.size());

            for (int i = 0; i <= 8; i++) {
                if (i == 0) {
                    content.add(itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Down", List.of("Scrolls down in the file."), "scrollDown"));
                }
                else if (i == 1) {
                    content.add(itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Up", List.of("Scrolls up in the file."), "scrollUp"));
                }
                else if (i == 2) {
                    content.add(itemProperties(new ItemStack(Material.OAK_SIGN), "Line number: "+lineNumber, List.of("The line number of the first visible line."), null));
                }
                else if (i == 3) {
                    String name;
                    if (searchPhrase.isEmpty()) name = "Search";
                    else name = "Search: "+searchPhrase;
                    content.add(itemProperties(new ItemStack(Material.WRITABLE_BOOK), name, List.of("Finds instances of certain words.","Left click: select search word.","Right click: moves to searched words."), "search"));
                }
                else if (i == 4) {
                    content.add(itemProperties(new ItemStack(Material.SPECTRAL_ARROW), "Go to", List.of("Go to a certain line by number."), "goto"));
                }
                else if (i == 7) {
                    content.add(itemProperties(new ItemStack(Material.ARROW), "Back", List.of("Exit the file."), "up"));
                }
                else if (i == 8) {
                    content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui."), "exit"));
                }
                else {
                    content.add(itemProperties(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null, null));
                }
            }

            for (int i = 0; i <= 4; i++) {
                if (lines.size()-1 >= i+lineNumber-1) {
                    //max 50 chars per item
                    String line = lines.get(i+lineNumber-1);
                    for (int ii = 0; ii <= 8; ii++) {
                        String paperName = line.substring(0, Math.min(35, line.length()));
                        line = line.substring(Math.min(35, line.length()));
                        if (!paperName.isEmpty()) {
                            ItemStack paper = itemProperties(new ItemStack(Material.PAPER), paperName, null, null);
                            if (!searchPhrase.isEmpty() && paperName.contains(searchPhrase)) {
                                paper = itemProperties(new ItemStack(Material.FILLED_MAP), paperName.replace(searchPhrase, ChatColor.YELLOW + searchPhrase + ChatColor.WHITE), null, "text");
                            }

                            ItemMeta paperMeta = paper.getItemMeta();
                            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, i+lineNumber);
                            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER, ii);
                            paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "edited"), PersistentDataType.BOOLEAN, false);
                            paper.setItemMeta(paperMeta);
                            content.add(paper);
                        }
                        else content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
                    }
                } else {
                    for (int ii = 0; ii <= 8; ii++) {
                        content.add(itemProperties(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), " ", null, null));
                    }
                }
            }

            fileData.put(player.getUniqueId(), data);
        }

        if (content.isEmpty()) return;

        gui.setContents(content.toArray(new ItemStack[0]));
        player.openInventory(gui);
    }

    public static void editFile(Player player, ItemStack editedText) {
        if (!player.hasPermission("fileman.file.edit")) {
            fileData.remove(player.getUniqueId());
            position.remove(player.getName());
            return;
        }

        if (editedText.getItemMeta() == null) return;
        PathHolder pathHolder = position.get(player.getName());
        Integer line = editedText.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
        Integer offset = editedText.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER);
        if (line == null|| offset == null) return;

        try {
            BufferedReader br = new BufferedReader(new FileReader(pathHolder.getCurrentPath()));
            StringBuilder content = new StringBuilder();

            String text;
            int i = 1;
            while ((text = br.readLine()) != null) {
                if (line == i) {
                    int startOfReplace = 35*offset;
                    String start = text.substring(0, startOfReplace);
                    String end = text.substring(Math.min(startOfReplace+35, text.length()));
                    text = start+editedText.getItemMeta().getDisplayName()+end;
                }
                i++;
                content.append(text).append("\n");
            };
            br.close();
            Files.writeString(Path.of(pathHolder.getCurrentPath()), content.toString());
        } catch (IOException e) {
            log(e, player, Level.WARNING, "There was an error reading/writing to \""+position.get(player.getName()).getRelativePath()+"\".");
        }
    }

    public static boolean checkIdentifier(ItemStack item, String identifier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return identifier.equals(meta.getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "identifier"), PersistentDataType.STRING));
    }
}
