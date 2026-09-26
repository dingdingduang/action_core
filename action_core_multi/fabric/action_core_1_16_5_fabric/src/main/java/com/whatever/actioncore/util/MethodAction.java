package com.whatever.actioncore.util;

import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface MethodAction {
    void executeAction(LivingEntity entity);
}
