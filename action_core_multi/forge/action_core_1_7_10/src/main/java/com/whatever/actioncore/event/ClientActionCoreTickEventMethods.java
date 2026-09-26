package com.whatever.actioncore.event;

import com.whatever.actioncore.ActionCore;
import com.whatever.actioncore.util.MethodAction;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientActionCoreTickEventMethods {
    public static final int INFINITE_REPETITIONS = ActiveActionTask.INFINITE_REPETITIONS;

    private static final ConcurrentHashMap<EntityLivingBase, ConcurrentHashMap<String, ActiveActionTask>>
            ACTIVE_TASKS = new ConcurrentHashMap<>();

    public ClientActionCoreTickEventMethods() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getMinecraft().isGamePaused()) {
            RunAction();
        }
    }

    @SubscribeEvent
    public void onLoggingOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearAllTasks();
    }

    public static void RunAction() {
        ACTIVE_TASKS.forEach((entity, entityTasks) -> {
            if (!entity.isEntityAlive() || entity.isDead) {
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

    /** Executes a presentation action immediately on the calling client thread. */
    public static void execute(EntityLivingBase entity, MethodAction action) {
        if (entity != null && action != null) {
            action.executeAction(entity);
        }
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
        return task == null || task.isDone() ? 0 : task.getRemainingRuns();
    }

    public static void cancelAllTasks(EntityLivingBase entity) {
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
        if (ACTIVE_TASKS.remove(entity, entityTasks)) {
            entityTasks.forEach((taskID, task) -> task.clean(entity));
            entityTasks.clear();
        }
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
