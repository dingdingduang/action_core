package com.whatever.actioncore.client;

import net.fabricmc.api.ClientModInitializer;

public class ActionCoreModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientActionCoreTickEventMethods.registerEvents();
	}
}