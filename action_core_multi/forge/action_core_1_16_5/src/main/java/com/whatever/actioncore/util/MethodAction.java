package com.whatever.actioncore.util;

import net.minecraft.entity.LivingEntity;

@FunctionalInterface
public interface MethodAction {
    void executeAction(LivingEntity entity);
}
