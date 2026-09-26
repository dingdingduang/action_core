package com.whatever.actioncore;

import com.whatever.actioncore.event.ActionCoreTickEventMethods;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class ActionCore implements ModInitializer {
    public static final String MODID = "actioncore";

    @Override
    public void onInitialize() {
        ActionCoreTickEventMethods.registerEvents();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
