# CogWorks:
Allows for easy and frictionless management of plugins & files right from Minecraft!


## Important:
Only files and folders located in same folder in the server jar, and in folders within said folder, can be accessed with this plugin!  
Or to put it in more technical terms, the plugin can only access files or folder whose absolute path starts with the absolute path to the server folder.

Furthermore, CogWorks is still in alpha, so there will be bugs. If you find any please report them in #bug-reports in [discord](https://discord.gg/3CC6kVcdQH).


## General Info:

### Incompatibilities:
There may be some issues with messages getting cancelled when interacting with the CogWorks chat messages, if a plugin that alters chat messages is also installed.
An example of this would be any of the no chart report plugins.<br>
If you are the author of one of these plugins or have any idea on how to resolve this issue please contact tye though [discord](https://discord.gg/3CC6kVcdQH).

### Download:
The latest public version of the plugin will always be available on [Modrinth](https://modrinth.com/plugin/file-manager). Or you can join on the [discord](https://discord.gg/3CC6kVcdQH) to see if there are any snapshot builds you can mess around with!

### Commands:
- /plugin - The base command for installing or removing plugins from the server.
- /file - Opens a GUI for navigating around the file system, alongside editing, creating, & deleting files.

### Features:
- Install jar files from the web using "/plugin install \<URL>"
- Installing plugins from Modrinth using "/plugin browse" or "/plugin search \<query>".
- Will automatically install the dependencies of a plugin when downloading from Modrinth.
- Will attempt to meet unmet dependencies on start up if CogWorks detects any. Disable this in the config if you have a slow connection or limited bandwidth.
- Deleting plugins using the "/plugin remove \<plugin name>".
- When deleting plugins their will be a prompt on whether to delete the plugins config folder & a prompt on whether to delete plugins that depend on the plugin that is being deleted to function.
- Access a GUI file explorer with "/file".
- Unique way of interacting with the plugin by being able to just type responses in chat. No commands required!
- Supports permissions.

### Permissions:
- cogworks.plugin.ins.gen - Able to use the "/plugin install \<URL>" command to install a plugin from any URL.
- cogworks.plugin.ins.modrinth - Able to use the "/plugin search \<query>" & "/plugin browse" commands to install plugins from Modrinth.
- cogworks.plugin.reload - Able to use the "/plugin reload" command which makes CogWorks "rescan" the "./plugins" folder for any changes.
- cogworks.plugin.rm - Able to use the "/plugin remove \<plugin name>" command to remove plugins & their config folders.
- cogworks.file.nav - Able to use the "/file" command to view files & folders, & navigate though folders.
- cogworks.file.read - Able to read the content of files when using the "/file" command.
- cogworks.file.edit - Able to edit the content of files when using the "/file" command.
- cogworks.file.mk - Able to create new files when using the "/file" command.
- cogworks.file.rm - Able to delete files when using the "/file" command.

### Future Plans:
- Bug fixes.
- Attempts at back-porting to older mc versions.
- Supporting downloading plugins from other websites.
- Chat based file manager.
- & Much more!


## Further reading:
This will be a section containing more technical details for those who are interested, or want an introduction without having to read the code.

### On chat interaction:
When using CogWorks you will probably quickly notice that it has a rather unique way of handling player interactions with commands.  
For example, when interacting with "/plugin search <query>", CogWorks will send multiple messages to the user, the ones starting with "number:" are options that can be chosen from. To select one of these options simply send "number" in chat, no "/" or any other commands required. CogWorks will block the chat message from being sent to other users.  
In addition, with "/plugins search <query>" & "/plugin browse" the messages sent to you with underlined text will have hyperlinks to the content they are referring to. An example of this could be "[1: Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)", which will take you the main page on Modrinth for the Simple Voice Chat plugin. Another place that this occurs is when there are multiple valid version of a plugin, and this time the links provided will take you to the Modrinth page for that specific version of the plugin. The links will always take you to a relevant place to learn more information about the option you want to select.

### On "/plugin install \<URL>":
For this command the downloaded file extension will always be set to ".jar". Also be aware that you have to get the URL to the actual file download. Not just the URL to the website that you can download the file from or the URL to info page of a plugin.  
For example:  
Correct: https://cdn.modrinth.com/data/9eGKb6K1/versions/8sbc8kD8/voicechat-bukkit-2.4.27.jar  
Incorrect: https://modrinth.com/plugin/simple-voice-chat/version/bukkit-2.4.27  
Incorrect: https://modrinth.com/plugin/simple-voice-chat/  
Incorrect: https://modrinth.com

An easy way to tell if it is a file is to check if the URL ends in ".jar". However, be warned that not all plugin download links end in ".jar"!

<!--
@formatter:off
-->
### On "/plugin search \<query>":
For this command the query **can** contain spaces and will still be treated as a single valid search request. Furthermore, any chars that would make an invalid URL are automatically converted into the [percent-encoding](https://en.wikipedia.org/wiki/Percent-encoding) that URLs use.
<!--
@for/matter:on
-->

### On "/plugin browse":
For this command it will let you (as the name suggests) browse though the most popular plugins on Modrinth that are compatible with your server type & version. Using "0" & the max value as options for "scrolling" through the list of plugins, with the "0" input scrolling you up "where applicable", & vice versa for the max number.

### On "/plugin reload":
This command makes CogWorks "rescan" the "./plugins" folder for any changes made that weren't done though CogWorks.

### On "/plugin remove \<plugin name>":
This command will only execute the delete once it has no more plugins left in the delete queue (the delete queue is just a list of plugins that need user input for options regarding the delete) to evaluate.  
The command works by letting you select from any plugins in the "./plugins" folder to remove (this includes plugins that didn't load successfully).  
If the plugin you selected has a config folder ("./plugins/<plugin name>") then it will give you a prompt for deleting that also.  
Then if there are other plugins that have the plugin you are about to delete as a dependency then there will be a prompt asking if you want to just delete this plugin anyway, delete the plugins that depend on this one, or quit.  
For the option to delete the plugins that depend on this one, the plugin(s) will be added to the delete queue, so the config check & the depends on check will be executed for all of the plugins you've chosen to delete.

### On "/file":
This command will open an inventory for the player, which allows them to navigate though the files that have a path that starts with the same path as the one the server folder is in.  
For an example lets say that the server jar is located in "D:\paperSever" (this also applies to linux system, I'm just using Windows file paths as an example). The files "D:\paperSever\server.properties" & "D:\paperSever\world\playerdata" are files that a user can access since they start with the same path as the server folder. However, the files "D:/taxInformation" & "C:\Program Files" can't be accessed, since they start with a path that is different from the server folder one.  
All the files will be represented white wool, with the item lore of "File", & all the folders will be represented by yellow wool, with the lore of "Folder". However, do be aware that the item lore text can be changed in the lang files. And the names of the items are corresponding file names.  
When the user clicks on a folder, the inventory will change to display the content of the folder & the same for files. To go back, simply click on the arrow.  
For viewing files the user can hover over the paper to see the text for a segment of that line. And they can use the tipped arrows on the top to scroll up and down in the file. The oak sign will display the line number of the top row on your screen. The book and quill lets a person search for specific phrases in the file, and for the weird character that appears when entering the search phrase, don't worry about it, it's just there so the paper isn't called "paper", it will get automatically removed. And with the spectral arrow you select a line number to go too immediately.  
To edit the content of a file you'll have to viewing it, then if you want to change some text that is already written, just click on the paper with the item name of the text, and an anvil will open to let you rename the text to anything you wish. To add text onto empty lines or onto the end of a line you can click on any of the empty slots that are in the same line & the anvil menu will open again and any text you put into it will get added to the line. To remove text do the same thing you would to edit text but just delete all of it inside the anvil.  
Clicking on the green concrete will change the inventory allowing you to create either a file or folder. After that an anvil menu will open which allows you to set the name of the file. Make sure not to enter characters that aren't allowed to be in file names!  
To delete a file or folder, click on the red concrete, this will put you in "delete mode". To make this clear to the user, the files will change to orange wool & the folders will change to red wool. Then just click on a file or folder to open a delete confirm menu, then click on the green concrete to delete the file or folder. If you try and delete a folder that contains files or folders within it, there will be an extra confirmation step.

### On automatic dependency resolution:
Automatic dependency resolution (this will be referred to as ADR) involves CogWorks checking for any missing dependencies on start up and trying to resolve them by taking the missing plugin name (the plugin name is the name specified in the plugin.yml) and searching that up on Modrinth.  
It then downloads the top then plugins (into "CogWorks/.temp/ADR/<UniqueDirName>") returned by Modrinth & checks if any of the plugin names of the downloaded plugins match the plugin name of the missing dependency. If one of the plugins match it wil be moved into the "./plugins" folder. The remaining plugins will be deleted.  
If ADR completes without errors & the dependency still isn't resolved then it won't try to resolve the dependency again.

## Translators Wanted!
If you are willing to help by translating the plugin language file into any new languages (for past versions or the current version). Please join the [discord server](https://discord.gg/3CC6kVcdQH) and ping @IWantToTranslate.