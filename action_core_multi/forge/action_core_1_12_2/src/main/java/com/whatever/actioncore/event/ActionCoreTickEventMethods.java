package com.whatever.actioncore.event;

import com.whatever.actioncore.ActionCore;
import com.whatever.actioncore.util.MethodAction;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ActionCore.MODID)
public final class ActionCoreTickEventMethods {
    public static final int INFINITE_REPETITIONS = ActiveActionTask.INFINITE_REPETITIONS;

    private static final ConcurrentHashMap<EntityLivingBase, ConcurrentHashMap<String, ActiveActionTask>> ACTIVE_TASKS =
            new ConcurrentHashMap<>();

    private ActionCoreTickEventMethods() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RunAction();
        }
    }

    public static void RunAction() {
        ACTIVE_TASKS.forEach((entity, entityTasks) -> {
            if (!entity.isEntityAlive()) {
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

    public static void execute(EntityLivingBase entity, MethodAction action) {
        if (entity == null || action == null) {
            return;
        }

        action.executeAction(entity);
    }

    public static void loopAction(
            EntityLivingBase entity,
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
            EntityLivingBase entity,
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
            EntityLivingBase entity,
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
            EntityLivingBase entity,
            String taskID,
            int delayTicks,
            MethodAction action
    ) {
        loopAction(entity, taskID, 1, 1, Math.max(0, delayTicks), action, null, true);
    }

    public static boolean isTaskActive(EntityLivingBase entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        return task != null && !task.isDone();
    }

    public static int getRemainingRuns(EntityLivingBase entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task == null || task.isDone()) {
            return 0;
        }

        return task.getRemainingRuns();
    }

    public static void cancelAllTasks(EntityLivingBase entity) {
        if (entity == null) {
            return;
        }

        ConcurrentHashMap<String, ActiveActionTask> entityTasks = ACTIVE_TASKS.get(entity);
        if (entityTasks == null) {
            return;
        }

        entityTasks.forEach((taskID, task) -> task.markDone());
    }

    public static void setMethodActionTimer(
            EntityLivingBase entity,
            String taskID,
            int tickPeriod,
            MethodAction action
    ) {
        putTask(entity, taskID, new ActiveActionTask(tickPeriod, action, null), true);
    }

    public static void setMethodActionTimerWithCleanAction(
            EntityLivingBase entity,
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

    public static void setTaskActionDone(EntityLivingBase entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task != null) {
            task.markDone();
        }
    }

    public static void setTaskActionDone(
            EntityLivingBase entity,
            String taskID,
            MethodAction cleanAction
    ) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task != null) {
            task.markDone(cleanAction);
        }
    }

    /**
     * Removes one task and runs its cleanup immediately on the calling server
     * thread. Use when an owner leaves its level or the server is stopping and
     * another scheduler tick is not guaranteed. Unrelated tasks are not advanced.
     */
    public static void removeTask(EntityLivingBase entity, String taskID) {
        if (entity == null || taskID == null) {
            return;
        }
        ConcurrentHashMap<String, ActiveActionTask> tasks = ACTIVE_TASKS.get(entity);
        if (tasks == null) {
            return;
        }
        ActiveActionTask task = tasks.remove(taskID);
        try {
            if (task != null) {
                task.markDone();
                task.clean(entity);
            }
        } finally {
            removeEntityIfEmpty(entity, tasks);
        }
    }

    private static void putTask(
            EntityLivingBase entity,
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
                tasks.put(taskID, task);
            } else {
                tasks.putIfAbsent(taskID, task);
            }
            return tasks;
        });
    }

    private static ActiveActionTask getTask(EntityLivingBase entity, String taskID) {
        if (entity == null || taskID == null) {
            return null;
        }

        ConcurrentHashMap<String, ActiveActionTask> entityTasks = ACTIVE_TASKS.get(entity);
        return entityTasks == null ? null : entityTasks.get(taskID);
    }

    private static void removeDeadEntity(
            EntityLivingBase entity,
            ConcurrentHashMap<String, ActiveActionTask> entityTasks
    ) {
        if (!ACTIVE_TASKS.remove(entity, entityTasks)) {
            return;
        }

        entityTasks.forEach((taskID, task) -> task.clean(entity));
        entityTasks.clear();
    }

    private static void removeEntityIfEmpty(
            EntityLivingBase entity,
            ConcurrentHashMap<String, ActiveActionTask> entityTasks
    ) {
        ACTIVE_TASKS.computeIfPresent(entity, (ignored, currentTasks) ->
                currentTasks == entityTasks && currentTasks.isEmpty() ? null : currentTasks
        );
    }
}