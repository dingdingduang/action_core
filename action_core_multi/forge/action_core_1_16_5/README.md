# ActionCore

Entity task scheduling for Minecraft.

`removeTask(entity, taskId)` removes one server task and runs its cleanup
immediately on the calling server thread, without advancing unrelated tasks.
Use it for logout, dimension changes, and server shutdown when a later scheduler
tick is not guaranteed. Available since 1.0.1.
