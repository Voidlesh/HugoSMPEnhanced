package com.hugosmpenhanced.test;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.config.NametagStyleScreen;
import com.hugosmpenhanced.client.config.SecondNametagScreen;
import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.network.chat.Component;

/**
 * Renders the nametag previews and the in-world nametag for every style and saves screenshots, so
 * the effects can be checked by eye after a change: {@code ./gradlew runClientGameTest}, then look
 * in the run directory's {@code screenshots} folder. Animated styles are captured over several
 * frames.
 */
@SuppressWarnings("UnstableApiUsage")
public class NametagScreenshotGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		configureSecondNametag(context, false);

		// Every effect on its own in the style screen, a few frames apart.
		for (NametagEffect effect : NametagEffect.values()) {
			useEffects(context, EnumSet.of(effect));
			context.setScreen(() -> NametagStyleScreen.build(null));
			for (int frame = 0; frame < framesFor(effect); frame++) {
				context.waitTicks(frame == 0 ? 2 : ticksBetweenFrames(effect));
				context.takeScreenshot("style-" + effect.name().toLowerCase() + "-" + frame);
			}
		}

		// A few combinations that stack every layer type.
		useEffects(context, EnumSet.of(NametagEffect.RAINBOW, NametagEffect.SHIMMER, NametagEffect.WAVING));
		capture(context, "combo-rainbow-shimmer-waving", 4);
		useEffects(context, EnumSet.of(NametagEffect.FIRE, NametagEffect.GLOWING, NametagEffect.BOUNCING));
		capture(context, "combo-fire-glow-bounce", 4);
		useEffects(context, EnumSet.of(NametagEffect.METALLIC, NametagEffect.OUTLINE, NametagEffect.SWINGING));
		capture(context, "combo-metallic-outline-swing", 4);

		// The second nametag editor, with the second line above and below.
		configureSecondNametag(context, true);
		useEffects(context, EnumSet.of(NametagEffect.GRADIENT));
		context.setScreen(() -> SecondNametagScreen.build(null));
		context.waitTicks(3);
		context.takeScreenshot("second-above");
		context.runOnClient(client -> ModConfigManager.CONFIG.secondNametagPosition = SecondNametagPosition.BELOW);
		context.setScreen(() -> SecondNametagScreen.build(null));
		context.waitTicks(3);
		context.takeScreenshot("second-below");

		// The real nametag above the player, in third person.
		context.setScreen(() -> null);
		// The menu blur makes every loading-screen frame slow under software rendering, and a flat
		// world loads in time on slow machines; the nametag is all this part needs.
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.runOnClient(client -> ModConfigManager.CONFIG.secondNametagPosition = SecondNametagPosition.ABOVE);
			useEffects(context, EnumSet.of(NametagEffect.RAINBOW, NametagEffect.SHIMMER));
			context.waitTicks(20);
			context.takeScreenshot("world-rainbow-shimmer");
			useEffects(context, EnumSet.of(NametagEffect.METALLIC, NametagEffect.GLOWING));
			context.waitTicks(10);
			context.takeScreenshot("world-metallic-glow");
			useEffects(context, EnumSet.of(NametagEffect.FIRE, NametagEffect.OUTLINE, NametagEffect.BOUNCING));
			context.waitTicks(10);
			context.takeScreenshot("world-fire-outline-bounce");

			// Close-ups: a narrow field of view magnifies the nametag without moving the camera.
			context.runOnClient(client -> client.options.fov().set(30));
			for (Set<NametagEffect> effects : List.of(
				EnumSet.of(NametagEffect.OUTLINE),
				EnumSet.of(NametagEffect.OUTLINE, NametagEffect.RAINBOW),
				EnumSet.of(NametagEffect.GLOWING),
				EnumSet.of(NametagEffect.RAINBOW))) {
				useEffects(context, effects);
				context.waitTicks(5);
				context.takeScreenshot("closeup-" + effects.stream().map(e -> e.name().toLowerCase()).reduce((a, b) -> a + "+" + b).orElse(""));
			}
			context.runOnClient(client -> client.options.fov().set(70));

			// Chat, where the name is overlaid on the line vanilla already drew.
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(client -> client.gui.getChat().addMessage(
				Component.literal("<" + client.player.getGameProfile().name() + "> Outline im Chat")));
			for (Set<NametagEffect> effects : List.of(EnumSet.of(NametagEffect.OUTLINE), EnumSet.of(NametagEffect.RAINBOW))) {
				useEffects(context, effects);
				context.waitTicks(3);
				context.takeScreenshot("chat-" + effects.iterator().next().name().toLowerCase());
			}
		}
	}

	private static int framesFor(NametagEffect effect) {
		return switch (effect) {
			case OUTLINE -> 1;
			// Short events: Glitch bursts last a few frames, the sweeps a fraction of their period.
			case GLITCH -> 40;
			case SHIMMER, METALLIC -> 14;
			default -> 4;
		};
	}

	private static int ticksBetweenFrames(NametagEffect effect) {
		return switch (effect) {
			case GLITCH -> 1;
			case SHIMMER, METALLIC -> 2;
			default -> 4;
		};
	}

	private static void capture(ClientGameTestContext context, String name, int frames) {
		context.setScreen(() -> NametagStyleScreen.build(null));
		for (int frame = 0; frame < frames; frame++) {
			context.waitTicks(frame == 0 ? 2 : 4);
			context.takeScreenshot(name + "-" + frame);
		}
	}

	private static void useEffects(ClientGameTestContext context, Set<NametagEffect> effects) {
		context.runOnClient(client -> ModConfigManager.CONFIG.nametagEffects = new LinkedHashSet<>(effects));
	}

	private static void configureSecondNametag(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.secondNametagEnabled = enabled;
			ModConfigManager.CONFIG.secondNametagText = "&#FFD166&lHugo&r&7SMP &b✦ &fEnhanced";
			ModConfigManager.CONFIG.secondNametagScale = 0.8;
			ModConfigManager.CONFIG.secondNametagBackground = true;
			ModConfigManager.CONFIG.secondNametagPosition = SecondNametagPosition.ABOVE;
		});
	}
}
