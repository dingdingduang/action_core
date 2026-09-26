package com.whatever.actioncore;

import com.whatever.actioncore.event.ActionCoreTickEventMethods;
import net.fabricmc.api.ModInitializer;

public class ActionCore implements ModInitializer {
    public static final String MODID = "actioncore";

    @Override
    public void onInitialize() {
        ActionCoreTickEventMethods.registerEvents();
    }
}
