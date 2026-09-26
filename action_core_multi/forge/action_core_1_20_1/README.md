# action_core
Perform logic like wc3 editor action.

`ActionCoreTickEventMethods` schedules authoritative logical-server work.
`ClientActionCoreTickEventMethods` provides the same named-task pattern on the
physical client for presentation-only work such as predicted visual movement.

`removeTask(entity, taskId)` removes one server task and runs its cleanup
immediately on the calling server thread, without advancing unrelated tasks.
Use it for logout, dimension changes, and server shutdown when a later scheduler
tick is not guaranteed. Available since 1.0.1.
