package com.whatever.actioncore.event;

import com.whatever.actioncore.util.MethodAction;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the behavior and timing state for one active entity/task pair.
 *
 * <p>A task has three timing knobs: an initial delay before the first run, the
 * period between runs, and how many runs it should perform before finishing on
 * its own. The historical behavior — fire immediately, then every period, for
 * ever — is what the three-argument constructor still produces.</p>
 */
public final class ActiveActionTask {
    /** Repetition count meaning "keep running until the caller stops the task". */
    public static final int INFINITE_REPETITIONS = -1;

    private final MethodAction action;
    private final int period;
    private final int initialDelayTicks;
    private final AtomicBoolean cleanupStarted = new AtomicBoolean(false);

    private volatile MethodAction cleanAction;
    private volatile boolean done;
    private volatile int remainingRuns;
    private long elapsedTicks;
    private long lastTriggeredCycle = -1L;

    /**
     * Fires immediately on the first tick, then once every {@code period} ticks,
     * for as long as the task is registered.
     */
    public ActiveActionTask(int period, MethodAction action, MethodAction cleanAction) {
        this(period, 0, INFINITE_REPETITIONS, action, cleanAction);
    }

    /**
     * @param period            ticks between runs; values below 1 are treated as 1
     * @param initialDelayTicks ticks to wait before the first run. 0 fires on the
     *                          next tick; passing {@code period} produces an
     *                          evenly spaced loop with no run at time zero.
     * @param repetitions       how many times to run before finishing, or
     *                          {@link #INFINITE_REPETITIONS}
     */
    public ActiveActionTask(int period, int initialDelayTicks, int repetitions,
                            MethodAction action, MethodAction cleanAction) {
        this.period = Math.max(1, period);
        this.initialDelayTicks = Math.max(0, initialDelayTicks);
        this.remainingRuns = repetitions < 0 ? INFINITE_REPETITIONS : repetitions;
        this.action = action;
        this.cleanAction = cleanAction;
        if (this.remainingRuns == 0) {
            this.done = true;
        }
    }

    /**
     * Advances the task by one tick, running the action when a period boundary is
     * crossed. A task with a finite repetition count marks itself done after its
     * last run, so the registry cleans it up without the caller intervening.
     */
    void tick(LivingEntity entity) {
        if (done) {
            return;
        }

        if (elapsedTicks < initialDelayTicks) {
            elapsedTicks++;
            return;
        }

        long currentCycle = (elapsedTicks - initialDelayTicks) / period;
        if (currentCycle != lastTriggeredCycle) {
            lastTriggeredCycle = currentCycle;
            if (action != null) {
                action.executeAction(entity);
            }
            if (remainingRuns > 0 && --remainingRuns == 0) {
                done = true;
            }
        }

        if (elapsedTicks < Long.MAX_VALUE) {
            elapsedTicks++;
        }
    }

    void markDone() {
        done = true;
    }

    void markDone(MethodAction cleanAction) {
        this.cleanAction = cleanAction;
        done = true;
    }

    boolean isDone() {
        return done;
    }

    /**
     * Ensures cleanup is executed at most once, even if removal paths race.
     */
    void clean(LivingEntity entity) {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }

        MethodAction actionToRun = cleanAction;
        if (actionToRun != null) {
            actionToRun.executeAction(entity);
        }
    }

    public int getPeriod() {
        return period;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public int getInitialDelayTicks() {
        return initialDelayTicks;
    }

    /** Runs left before the task finishes, or {@link #INFINITE_REPETITIONS}. */
    public int getRemainingRuns() {
        return remainingRuns;
    }
}
