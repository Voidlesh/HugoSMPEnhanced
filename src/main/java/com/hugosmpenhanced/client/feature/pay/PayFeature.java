package com.hugosmpenhanced.client.feature.pay;

import com.hugosmpenhanced.client.ConfigCommand;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

/** Payment log, {@code /hse zahlungen} and the /pay warning (the warning itself lives in mixins). */
public final class PayFeature {
	private PayFeature() {
	}

	public static void register() {
		PayLog.register();
		ConfigCommand.addSubcommand(root -> root.then(ClientCommands.literal("zahlungen")
			.executes(context -> ConfigCommand.open(PayLogScreen::new))));
	}
}
