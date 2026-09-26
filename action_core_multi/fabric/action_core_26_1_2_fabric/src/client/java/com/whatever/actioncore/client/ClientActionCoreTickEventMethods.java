package com.whatever.actioncore.client;

import com.whatever.actioncore.event.ActiveActionTask;
import com.whatever.actioncore.util.MethodAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class ClientActionCoreTickEventMethods {
    public static final int INFINITE_REPETITIONS = ActiveActionTask.INFINITE_REPETITIONS;

    private static final ConcurrentHashMap<LivingEntity, ConcurrentHashMap<String, ActiveActionTask>>
            ACTIVE_TASKS = new ConcurrentHashMap<>();

    private ClientActionCoreTickEventMethods() {
    }

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!client.isPaused()) {
                RunAction();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearAllTasks();
        });
    }

    public static void RunAction() {
        ACTIVE_TASKS.forEach((entity, entityTasks) -> {
            if (!entity.isAlive() || entity.isRemoved()) {
                removeDeadEntity(entity, entityTasks);
                return;
            }

            entityTasks.forEach((taskID, task) -> {
                if (task.isDone()) {
                    if (entityTasks.remove(taskID, task)) {
                        task.clean(entity);
                    }
                    return;
                }
                task.tick(entity);
            });

            removeEntityIfEmpty(entity, entityTasks);
        });
    }

    public static boolean isMethodActionTimerActive() {
        return !ACTIVE_TASKS.isEmpty();
    }

    public static void execute(LivingEntity entity, MethodAction action) {
        if (entity != null && action != null) {
            action.executeAction(entity);
        }
    }

    public static void loopAction(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            MethodAction action
    ) {
        loopAction(
                entity,
                taskID,
                tickPeriod,
                INFINITE_REPETITIONS,
                tickPeriod,
                action,
                null,
                true
        );
    }

    public static void loopAction(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            int repetitions,
            MethodAction action
    ) {
        loopAction(
                entity,
                taskID,
                tickPeriod,
                repetitions,
                tickPeriod,
                action,
                null,
                true
        );
    }

    public static void loopAction(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            int repetitions,
            int initialDelayTicks,
            MethodAction action,
            MethodAction cleanAction,
            boolean shouldOverwrite
    ) {
        if (repetitions == 0) {
            return;
        }
        putTask(
                entity,
                taskID,
                new ActiveActionTask(
                        tickPeriod,
                        initialDelayTicks,
                        repetitions,
                        action,
                        cleanAction
                ),
                shouldOverwrite
        );
    }

    public static void runLater(
            LivingEntity entity,
            String taskID,
            int delayTicks,
            MethodAction action
    ) {
        loopAction(entity, taskID, 1, 1, Math.max(0, delayTicks), action, null, true);
    }

    public static boolean isTaskActive(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        return task != null && !task.isDone();
    }

    public static int getRemainingRuns(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        return task == null || task.isDone() ? 0 : task.getRemainingRuns();
    }

    public static void cancelAllTasks(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        ConcurrentHashMap<String, ActiveActionTask> entityTasks = ACTIVE_TASKS.get(entity);
        if (entityTasks != null) {
            entityTasks.forEach((taskID, task) -> task.markDone());
        }
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.forEach((entity, entityTasks) -> {
            if (ACTIVE_TASKS.remove(entity, entityTasks)) {
                entityTasks.forEach((taskID, task) -> task.clean(entity));
                entityTasks.clear();
            }
        });
    }

    public static void setMethodActionTimer(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            MethodAction action
    ) {
        putTask(entity, taskID, new ActiveActionTask(tickPeriod, action, null), true);
    }

    public static void setMethodActionTimerWithCleanAction(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            MethodAction action,
            MethodAction cleanAction,
            boolean shouldOverwrite
    ) {
        putTask(
                entity,
                taskID,
                new ActiveActionTask(tickPeriod, action, cleanAction),
                shouldOverwrite
        );
    }

    public static void setTaskActionDone(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task != null) {
            task.markDone();
        }
    }

    public static void setTaskActionDone(
            LivingEntity entity,
            String taskID,
            MethodAction cleanAction
    ) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task != null) {
            task.markDone(cleanAction);
        }
    }

    private static void putTask(
            LivingEntity entity,
            String taskID,
            ActiveActionTask task,
            boolean shouldOverwrite
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(taskID, "taskID");
        ACTIVE_TASKS.compute(entity, (ignored, existingTasks) -> {
            ConcurrentHashMap<String, ActiveActionTask> tasks = existingTasks;
            if (tasks == null) {
                tasks = new ConcurrentHashMap<>();
            }
            if (shouldOverwrite) {
                ActiveActionTask replaced = tasks.put(taskID, task);
                if (replaced != null) {
                    replaced.clean(entity);
                }
            } else {
                tasks.putIfAbsent(taskID, task);
            }
            return tasks;
        });
    }

    private static ActiveActionTask getTask(LivingEntity entity, String taskID) {
        if (entity == null || taskID == null) {
            return null;
        }
        ConcurrentHashMap<String, ActiveActionTask> entityTasks = ACTIVE_TASKS.get(entity);
        return entityTasks == null ? null : entityTasks.get(taskID);
    }

    private static void removeDeadEntity(
            LivingEntity entity,
            ConcurrentHashMap<String, ActiveActionTask> entityTasks
    ) {
        if (ACTIVE_TASKS.remove(entity, entityTasks)) {
            entityTasks.forEach((taskID, task) -> task.clean(entity));
            entityTasks.clear();
        }
    }

    private static void removeEntityIfEmpty(
            LivingEntity entity,
            ConcurrentHashMap<String, ActiveActionTask> entityTasks
    ) {
        ACTIVE_TASKS.computeIfPresent(entity, (ignored, currentTasks) ->
                currentTasks == entityTasks && currentTasks.isEmpty() ? null : currentTasks
        );
    }
}