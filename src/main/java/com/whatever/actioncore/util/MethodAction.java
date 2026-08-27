package com.whatever.actioncore.util;

import net.minecraft.world.entity.LivingEntity;

/**
 * An action that is executed for a living entity on the logical server.
 */
@FunctionalInterface
public interface MethodAction {
    void executeAction(LivingEntity entity);
}
