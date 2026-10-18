package com.hugosmpenhanced.client.feature.auction;

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
			if (!ServerCheck.isHugoSmp()) {
				return;
			}

			int count = stack.getCount();
			for (int i = 0; i < lines.size(); i++) {
				Optional<AuctionLore.Price> price = AuctionLore.parse(lines.get(i).getString());
				if (price.isPresent()) {
					boolean alreadyPerItem = price.get().perItem();
					double perItem = alreadyPerItem ? price.get().amount() : price.get().amount() / Math.max(1, count);
					if (ModConfigManager.CONFIG.auctionPriceMemoryEnabled) {
						AuctionPriceMemory.record(stack, perItem, AuctionMarket.current());
					}
					if (ModConfigManager.CONFIG.pricePerItemEnabled && count > 1 && !alreadyPerItem) {
						lines.add(i + 1, pricePerItemLine(perItem));
					}
					return;
				}
			}
		});
	}

	private static MutableComponent pricePerItemLine(double perItem) {
		return Component.literal("Pro Item: ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(AuctionSearchScreen.formatPrice(perItem))
						.withStyle(ChatFormatting.GOLD));
	}
}
