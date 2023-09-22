## 0.2.1:
- Fixed capital letters getting deleted from query when using "/plugin" install.
- General improvements to "/file" command.
- Fixed broken url for plugin main page when using /plugin" install.
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
- Implemented "/plugin browse" - the command lets you browse though plugins in modrinth (might be built into "/plugin search" later).