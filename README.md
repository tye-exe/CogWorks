# CogWorks:
Allows for easy and frictionless management of plugins right from Minecraft!


## Important:
Only files and folders located in same folder in the server jar, and in folders within said folder, can be accessed with this plugin!

This plugin is still in very early alpha! There will be bugs and tons of them. If you find any please report them to the [discord server](https://discord.gg/3CC6kVcdQH).


## General Info:

### Download:
The latest public version of the plugin will always be available on [Modrinth](https://modrinth.com/plugin/file-manager). Or you can join on the [discord](https://discord.gg/3CC6kVcdQH) to see if there are any snapshot builds you can mess around with!

### Commands:
- /plugin - The base command for installing or removing plugins from the server. <br>
- /file - Opens a GUI for navigating around the file system, alongside editing, creating, & deleting files.

### Currently Supports:
- Installing jar files from web links.
- Installing jar files from Modrinth.
- Will prompt to install plugin dependencies, if present.
- Deleting plugins.
- GUI file explorer.
- Editing of files.
- Creation of files & folders.
- Deleting files & folders.
- Automatically attempts to resolve unmet dependencies on start up if a plugin has unmet dependencies.
- Permissions.

### Permissions:

- cogworks.plugin.ins.gen - Able to install plugins from any URL.
- cogworks.plugin.ins.modrinth - Able to install plugins from Modrinth.
- cogworks.plugin.reload - Able to make CogWorks rescan the "./plugins" folder for any changes.
- cogworks.plugin.rm - Able to remove plugins.
- cogworks.file.nav - Able to use the "/file" command, view files and folders, & navigate though folders.
- cogworks.file.read - Able to read the content of files.
- cogworks.file.edit - Able to edit the content of files.
- cogworks.file.mk - Able to create new files.
- cogworks.file.rm - Able to delete files.


### Future Plans:
- Bug fixes.
- Attempts at back-porting to older mc versions.
- Supporting downloading plugins from other websites.
- Chat based file manager.