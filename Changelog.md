## 0.2.1:
- Fixed capital letters getting deleted from query when using "/plugin" install.
- General improvements to "/file" command.
- Fixed broken url for plugin main page when using "/plugin install".
- Minor QOL changes.
- Can now delete plugins that didn't load.
- Reworked internal method handling for errors.
- Checking for dependencies on startup and attempts to resolve them automatically.
- Made automatic dependency resolve occur in multiple threads.
- Fixed bug where played would still have inventory interact events canceled after closing the  "/file" gui.
- Added user editable config options.
- Added understandable error messages.
- Added check for an out-of-bounds number input when installing a plugin.
- Plugins get added/removed from pluginData on install/deletion.
- Added java docs for internal methods.
- Fixed bug in dependency resolve where if a matching plugin was found it wouldn't be deleted.
- A message is sent to ops on log on if there has been a severe error since last restart.

## 0.3:
- Converted commands and chat interacts to use separate threads due to networking.
- Changed chat manager to use custom object, rather than unchecked casting.
- Implemented "/plugin browse" - the command lets you browse though plugins in modrinth (might be built into "/plugin install" later).
- Added README to GitHub.
- User redirected to "/file chat" when trying to use the gui though a terminal.
- Fixed bug with fileReader not being closed after editing a file.
- Changed file navigator, text editor & "/plugin" to use more specific permissions.
- Implemented ability to create new files.
- Fixed weird behaviour when interacting with normal items when using the "/file" command.
- Implemented ability to delete files.
- Optimized FileGui file.
- Removed scroll limit on files.
- Implemented ability to add end of liens & new lines by clicking on the background.
- Reworked Logging system.
- Text responses are got from lang file instead of being hard coded (allows for translation).
- Lang file will automatically repair itself.
- Will install new language lang files from GitHub if new ones are published for a version.
- Before installing a plugin it will be checked if it is already installed.
- When deleting a plugin that other plugins depend on, it will prompt you if you want to delete those as well.
- Removed "showErrors" config option. A warning/error message will always display. However, you can disable the java errors with "showErrorTrace".
- If a plugin version has multiple files it will prompt you for which ones to install.
- Added "/plugin reload", which makes CogWorks rescan the "./plugins" folder for any changes.
- If a plugin doesn't have a config folder then there won't be a prompt to delete the config folder.
- When updating CogWorks it will automatically install the correct versions of the lang files. If an updated version of the lang you are using isn't yet available it will continue to use the old version and substitute any missing values with english ones.
- Split /plugin install into two commands:<br>
  /plugin install <URL> - Downloads the file from the url to the \"./plugins\" folder.
  <br>/plugin search <Plugin name> - Uses Modrinth to search the name given and returns the results, which can be chosen from to download.
- Changed permissions to reflect new command:<br>
  "cogworks.plugin.ins.gen" - lets the user use the /plugin install command.<br>
  "cogworks.plugin.ins.modrinth" - lets the user use /plugin search & /plugin browse.
- MAJOR code cleanup.
- Added catch for invalid file name when creating a file.
- Updated ADR to not retry after ADR completed & dependency couldn't be resolved.
- Rewrote plugin remove commands for better readability & maintainability.
- Plugins will be enabled immediately on installation.
- Release of version 0.3.

## 0.4:
- Multiple plugins can be removed at once by separating each plugin name by a space.
- Added backwards compatibility to version 1.17