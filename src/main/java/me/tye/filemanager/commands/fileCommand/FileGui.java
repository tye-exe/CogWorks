package me.tye.filemanager.commands.fileCommand;

import me.tye.filemanager.FileManager;
import me.tye.filemanager.util.PathHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static me.tye.filemanager.FileManager.itemProperties;
import static me.tye.filemanager.commands.fileCommand.FileCommand.position;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class FileGui implements Listener {
    public static void openFolder(Player player) {
        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+position.get(player.getName()).getRelativePath()+ChatColor.GOLD+" $");
        List<Path> paths;
        try {
            paths = Files.list(Path.of(position.get(player.getName()).getCurrentPath())).toList();
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED+"There was an error trying to get the files in that folder.\nPlease see the console for error message and report this.");
            e.printStackTrace();
            return;
        }

        //creates file and folder objects, then sorts them
        ArrayList<ItemStack> files = new ArrayList<>();
        ArrayList<ItemStack> folders = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                ItemStack item = itemProperties(new ItemStack(Material.YELLOW_WOOL), path.getFileName().toString(), List.of("Folder"));
                folders.add(item);
            }
            else {
                ItemStack item = itemProperties(new ItemStack(Material.WHITE_WOOL), path.getFileName().toString(), List.of("File"));
                files.add(item);
            }
        }
        folders.addAll(files);

        ArrayList<ItemStack> content = new ArrayList<>();
        for (int i = 0; i <= 53; i++) {
            if (i == 0) {
                content.add(itemProperties(new ItemStack(Material.ARROW), "Up", List.of("Goes up a file.")));
            } else if (i == 4) {
                content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui.")));
            } else if (i > 8 && folders.size() > i-9) {
                content.add(folders.get(i-9));
            } else {
                content.add(new ItemStack(Material.AIR));
            }
        }
        gui.setContents(content.toArray(new ItemStack[0]));
        player.openInventory(gui);
    }

    public static void openFile(Player player, int lineNumber) {
        Inventory gui = Bukkit.createInventory(player, 54, ChatColor.BLUE+position.get(player.getName()).getRelativePath().substring(0, position.get(player.getName()).getRelativePath().length()-1)+ChatColor.GOLD+" $");

        File file = new File(position.get(player.getName()).getCurrentPath());
        if (!file.isFile()) return;

        ArrayList<String> lines = new ArrayList<>();
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(file));

            String text;
            while ((text = fileReader.readLine()) != null)
                lines.add(text);

        } catch (IOException e) {
            player.sendMessage(ChatColor.RED+"There was an error trying to open that file.\nPlease see the console for error message and report this.");
            e.printStackTrace();
            return;
        }

        ArrayList<ItemStack> content = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            if (i == 0) {
                content.add(itemProperties(new ItemStack(Material.ARROW), "Back", List.of("Exits the file.")));
            }
            else if (i == 2) {
                content.add(itemProperties(new ItemStack(Material.OAK_SIGN), "Line number: "+lineNumber, List.of("The line number of the first line.")));
            }
            else if (i == 3) {
                content.add(itemProperties(new ItemStack(Material.WRITABLE_BOOK), "Search (WIP)", List.of("Finds instances of certain words.")));
            }
            else if (i == 4) {
                content.add(itemProperties(new ItemStack(Material.BARRIER), "Exit", List.of("Closes the gui.")));
            }
            else if (i == 5) {
                ItemStack goTo = itemProperties(new ItemStack(Material.SPECTRAL_ARROW), "Go to", List.of("Go to a certain line by number."));
                ItemMeta goToMeta = goTo.getItemMeta();
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER, lines.size()-4);
                goToMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "currentLine"), PersistentDataType.INTEGER, lineNumber);
                goTo.setItemMeta(goToMeta);
                content.add(goTo);
            }
            else if(i == 7) {
                ItemStack up = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Up", List.of("Scrolls up in the file."));
                ItemMeta upMeta = up.getItemMeta();
                upMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, Math.max(1, lineNumber-5));
                up.setItemMeta(upMeta);
                content.add(up);
            }
            else if (i == 8) {
                ItemStack down = itemProperties(new ItemStack(Material.TIPPED_ARROW), "Scroll Down", List.of("Scrolls down in the file."));
                ItemMeta downMeta = down.getItemMeta();
                downMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, Math.min(lines.size()-4, lineNumber+5));
                down.setItemMeta(downMeta);
                content.add(down);
            }
            else {
                content.add(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        for (int i = 0; i <= 4; i++) {
            if (lines.size()-1 >= i+lineNumber-1) {
                //max 50 chars per item
                String line = lines.get(i+lineNumber-1);
                for (int ii = 0; ii <= 8; ii++) {
                    String paperName = line.substring(0, Math.min(35, line.length()));
                    line = line.substring(Math.min(35, line.length()));
                    if (!paperName.equals("")) {
                        ItemStack paper = itemProperties(new ItemStack(Material.PAPER), paperName, null);
                        ItemMeta paperMeta = paper.getItemMeta();
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER, i+lineNumber-1);
                        paperMeta.getPersistentDataContainer().set(new NamespacedKey(getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER, ii);
                        paper.setItemMeta(paperMeta);
                        content.add(paper);
                    }
                    else content.add(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
                }
            } else {
                for (int ii = 0; ii <= 8; ii++) {
                    content.add(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
                }
            }
        }

        gui.setContents(content.toArray(new ItemStack[0]));
        player.openInventory(gui);
    }

    @EventHandler
    public void stopStealing(InventoryClickEvent e) {
        if (position.containsKey(e.getWhoClicked().getName())) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.BARRIER || !position.containsKey(player.getName())) return;
            player.closeInventory();
            position.remove(player.getName());
        }
    }

    @EventHandler
    public void useFolder(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getItemMeta().getLore() == null || !position.containsKey(player.getName())) return;
            if (e.getCurrentItem().getItemMeta().getLore().get(0).equals("Folder")) {
                PathHolder pathHolder = position.get(player.getName());
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName());
                openFolder(player);
            }
        }
    }

    @EventHandler
    public void upFolder(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.ARROW || !position.containsKey(player.getName())) return;
            if (!e.getCurrentItem().getItemMeta().getDisplayName().equals("Back") && !e.getCurrentItem().getItemMeta().getDisplayName().equals("Up")) return;
            PathHolder pathHolder = position.get(player.getName());
            pathHolder.setCurrentPath(Path.of(pathHolder.getCurrentPath()).getParent().toString());
            openFolder(player);
        }
    }

    @EventHandler
    public void useFile(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getItemMeta().getLore() == null || !position.containsKey(player.getName())) return;
            if (e.getCurrentItem().getItemMeta().getLore().get(0).equals("File")) {
                PathHolder pathHolder = position.get(player.getName());
                pathHolder.setCurrentPath(pathHolder.getCurrentPath() + File.separator + e.getCurrentItem().getItemMeta().getDisplayName());
                openFile(player, 1);
            }
        }
    }

    @EventHandler
    public void fileScroll(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.TIPPED_ARROW || !position.containsKey(player.getName())) return;
            int line = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
            openFile(player, line);
        }
    }

    @EventHandler
    public void fileGoTo(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.SPECTRAL_ARROW || !position.containsKey(player.getName())) return;
            int max = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER);
            int currentLine = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "max"), PersistentDataType.INTEGER);
            ItemStack book = itemProperties(new ItemStack(Material.WRITTEN_BOOK), null, null);
            BookMeta bookMeta = (BookMeta) book.getItemMeta();
            bookMeta.setTitle("Go To");
            bookMeta.addPage("First line: 1\nCurrent line: "+currentLine+"\nFinial line: "+max+"\n\nGo To: ");
            book.setItemMeta(bookMeta);
            player.openBook(book);
        }
    }

    @EventHandler
    public void edit(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null || e.getCurrentItem().getType() != Material.PAPER || !position.containsKey(player.getName())) return;
            int line = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "line"), PersistentDataType.INTEGER);
            int offset = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getPlugin(FileManager.class), "offset"), PersistentDataType.INTEGER);
            PathHolder pathHolder = position.get(player.getName());

            player.closeInventory();
            player.openInventory(Bukkit.createInventory(null, InventoryType.ANVIL));


            try {
                BufferedReader br = new BufferedReader(new FileReader(pathHolder.getCurrentPath()));
                BufferedWriter bw = new BufferedWriter(new FileWriter(pathHolder.getCurrentPath()+"temp"));

                String text;
                int i = 0;
                while ((text = br.readLine()) != null) {
                    if (line == i) {
                        String start = text.substring(0, 35*offset);
                        String replace = text.substring(35*offset, 35*offset+1);
                        String end = text.substring(35*offset+1);
                        System.out.println(start);
                        System.out.println(replace);
                        System.out.println(end);
                    }
                    bw.write(text);
                    i++;
                }

            } catch (Exception ex) {
                player.sendMessage(ChatColor.RED+"There was an error trying to edit that file.\nPlease see the console for error message and report this.");
                ex.printStackTrace();
                return;
            }
        }
    }
}
