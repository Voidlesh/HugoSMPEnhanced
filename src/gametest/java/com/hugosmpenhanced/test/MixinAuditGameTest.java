package com.hugosmpenhanced.test;

import org.spongepowered.asm.mixin.MixinEnvironment;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Loads every class the mixins target, which applies all of them. A mixin with a wrong target or descriptor fails
 * here, not only once the game first draws what it changes (a bad descriptor in a class that is only loaded in a
 * world, like the player renderer, once crashed the 26.3 build at the first player).
 */
public class MixinAuditGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MixinEnvironment.getCurrentEnvironment().audit();
	}
}
