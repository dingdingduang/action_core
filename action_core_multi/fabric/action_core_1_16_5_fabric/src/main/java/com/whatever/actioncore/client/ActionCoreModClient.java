package com.whatever.actioncore.client;

import com.whatever.actioncore.event.ClientActionCoreTickEventMethods;
import net.fabricmc.api.ClientModInitializer;

/** Installs client scheduling only on the physical client. */
public final class ActionCoreModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientActionCoreTickEventMethods.registerEvents();
    }
}
