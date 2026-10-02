package com.backrooms.client;

import net.fabricmc.api.ClientModInitializer;

public class BackroomsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCovers.init();
	}
}
