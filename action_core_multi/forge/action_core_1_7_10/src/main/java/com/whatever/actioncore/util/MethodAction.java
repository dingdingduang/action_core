package com.whatever.actioncore.util;

import net.minecraft.entity.EntityLivingBase;

@FunctionalInterface
public interface MethodAction {
    void executeAction(EntityLivingBase entity);
}
