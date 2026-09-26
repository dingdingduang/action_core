# actioncore for Minecraft 1.21.11 (fabric)

Version-local port from the supplied 26.1.2 source. Targets Java 21.
See the Aegis multiversion validation notes for current build/runtime results.

`removeTask(entity, taskId)` removes one server task and runs its cleanup
immediately. Use it for logout, dimension changes, and server shutdown when a
later scheduler tick is not guaranteed. Available since 1.0.1.
