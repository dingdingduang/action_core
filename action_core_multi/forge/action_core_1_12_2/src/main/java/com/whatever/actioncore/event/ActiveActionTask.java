package com.whatever.actioncore.event;

import com.whatever.actioncore.util.MethodAction;
import net.minecraft.entity.EntityLivingBase;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ActiveActionTask {
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

    public ActiveActionTask(int period, MethodAction action, MethodAction cleanAction) {
        this(period, 0, INFINITE_REPETITIONS, action, cleanAction);
    }

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

    void tick(EntityLivingBase entity) {
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

    void clean(EntityLivingBase entity) {
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

    public int getRemainingRuns() {
        return remainingRuns;
    }
}
