# Example Mod

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.

`removeTask(entity, taskId)` removes one server task and runs its cleanup
immediately. Use it for logout, dimension changes, and server shutdown when a
later scheduler tick is not guaranteed. Available since 1.0.1.
