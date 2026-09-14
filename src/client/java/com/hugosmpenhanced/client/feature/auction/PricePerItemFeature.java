package com.hugosmpenhanced.client.feature.auction;

import java.util.Locale;
import java.util.Optional;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class PricePerItemFeature {
	private PricePerItemFeature() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!ModConfigManager.CONFIG.pricePerItemEnabled || !ServerCheck.isHugoSmp()) {
				return;
			}

			int count = stack.getCount();
			if (count <= 1) {
				return;
			}

			for (int i = 0; i < lines.size(); i++) {
				Optional<Double> price = AuctionLore.parsePrice(lines.get(i).getString());
				if (price.isPresent()) {
					double perItem = price.get() / count;
					lines.add(i + 1, pricePerItemLine(perItem));
					return;
				}
			}
		});
	}

	private static MutableComponent pricePerItemLine(double perItem) {
		return Component.literal("Pro Item: ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(String.format(Locale.US, "$%,.2f", perItem))
						.withStyle(ChatFormatting.GOLD));
	}
}
