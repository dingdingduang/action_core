package com.whatever.actioncore.event;

import com.whatever.actioncore.ActionCore;
import com.whatever.actioncore.util.MethodAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs named, periodic actions for living entities on the logical server.
 *
 * <p>The registry is safe to mutate from an action or cleanup callback while a
 * tick is being processed. Minecraft entities themselves are not thread-safe,
 * so callers should still use this API from the logical server thread.</p>
 *
 * <p>Three entry points cover most callers:</p>
 * <ul>
 *   <li>{@link #loopAction} for a repeating action, optionally with a bounded
 *       repetition count and an initial delay, which finishes and cleans itself
 *       up without the caller counting runs;</li>
 *   <li>{@link #runLater} for a single delayed action;</li>
 *   <li>{@link #execute} to run an action payload immediately, for work that has
 *       to resolve inside the caller's own event.</li>
 * </ul>
 *
 * <p>{@link #setMethodActionTimer} and
 * {@link #setMethodActionTimerWithCleanAction} keep their original behavior:
 * fire on the first tick, then every period, for ever.</p>
 */
@Mod.EventBusSubscriber(modid = ActionCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ActionCoreTickEventMethods {
    /** Repetition count meaning "keep running until the caller stops the task". */
    public static final int INFINITE_REPETITIONS = ActiveActionTask.INFINITE_REPETITIONS;

    private static final ConcurrentHashMap<LivingEntity, ConcurrentHashMap<String, ActiveActionTask>> ACTIVE_TASKS =
            new ConcurrentHashMap<>();

    private ActionCoreTickEventMethods() {
    }

    /**
     * Forge 1.20.1 server-tick integration. A server tick fires once in each
     * phase, so the phase check prevents every task from being updated twice.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RunAction();
        }
    }

    /**
     * Advances every active task by one server tick.
     */
    public static void RunAction() {
        ACTIVE_TASKS.forEach((entity, entityTasks) -> {
            if (!entity.isAlive()) {
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

    /**
     * Runs {@code action} immediately, on the calling thread, through the
     * {@link MethodAction} contract.
     *
     * <p>For work that cannot be deferred a tick — damage interception, or any
     * other payload that has to resolve inside the event that produced it — so
     * that such callers still express their behavior as an action payload rather
     * than bypassing this API entirely. Null entities and actions are ignored.</p>
     */
    public static void execute(LivingEntity entity, MethodAction action) {
        if (entity == null || action == null) {
            return;
        }

        action.executeAction(entity);
    }

    /**
     * Loops {@code action} every {@code tickPeriod} ticks, starting one full
     * period from now, until {@link #setTaskActionDone} is called.
     *
     * <p>This is the usual shape for an ongoing effect: unlike
     * {@link #setMethodActionTimer}, it does not fire an extra run at time zero,
     * so the gap between the triggering moment and the first run matches the gap
     * between every later pair of runs.</p>
     */
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

    /**
     * Loops {@code action} a bounded number of times, starting one full period
     * from now. The task finishes and is cleaned up after its last run.
     */
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

    /**
     * Full loop form.
     *
     * @param tickPeriod        ticks between runs
     * @param repetitions       how many runs to perform, or
     *                          {@link #INFINITE_REPETITIONS} to run until stopped.
     *                          Zero registers nothing.
     * @param initialDelayTicks ticks before the first run. Pass 0 to run on the
     *                          next tick, or {@code tickPeriod} for an evenly
     *                          spaced loop.
     * @param cleanAction       run once when the task finishes or is stopped, and
     *                          when its entity dies; may be null
     * @param shouldOverwrite   when false, an existing task with the same entity
     *                          and task id is left running and this call is a no-op
     */
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

    /**
     * Runs {@code action} once, {@code delayTicks} ticks from now. A delay of 0
     * runs it on the next tick; to run without waiting a tick, use
     * {@link #execute}.
     */
    public static void runLater(
            LivingEntity entity,
            String taskID,
            int delayTicks,
            MethodAction action
    ) {
        loopAction(entity, taskID, 1, 1, Math.max(0, delayTicks), action, null, true);
    }

    /**
     * Whether a task with this entity and id is registered and has not finished.
     */
    public static boolean isTaskActive(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        return task != null && !task.isDone();
    }

    /**
     * Runs left for a task, {@link #INFINITE_REPETITIONS} for an unbounded one, or
     * 0 when no such task is active.
     */
    public static int getRemainingRuns(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task == null || task.isDone()) {
            return 0;
        }

        return task.getRemainingRuns();
    }

    /**
     * Marks every task for one entity for removal and cleanup. Useful when an
     * entity leaves play in a way that is not death — a player logging out, or an
     * entity being discarded.
     */
    public static void cancelAllTasks(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        ConcurrentHashMap<String, ActiveActionTask> entityTasks = ACTIVE_TASKS.get(entity);
        if (entityTasks == null) {
            return;
        }

        entityTasks.forEach((taskID, task) -> task.markDone());
    }

    /**
     * Adds or replaces the named task and resets its timer.
     */
    public static void setMethodActionTimer(
            LivingEntity entity,
            String taskID,
            int tickPeriod,
            MethodAction action
    ) {
        putTask(entity, taskID, new ActiveActionTask(tickPeriod, action, null), true);
    }

    /**
     * Adds a named task. When {@code shouldOverwrite} is false, an existing
     * task with the same entity and task id is left unchanged.
     */
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

    /**
     * Marks a task for removal and cleanup at the start of its next update.
     */
    public static void setTaskActionDone(LivingEntity entity, String taskID) {
        ActiveActionTask task = getTask(entity, taskID);
        if (task != null) {
            task.markDone();
        }
    }

    /**
     * Replaces the task's cleanup callback, then marks it for removal.
     */
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
                tasks.put(taskID, task);
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
        if (!ACTIVE_TASKS.remove(entity, entityTasks)) {
            return;
        }

        entityTasks.forEach((taskID, task) -> task.clean(entity));
        entityTasks.clear();
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
