package com.hugosmpenhanced.test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Prediction;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A stand-in for HugoSMP's /sell in the singleplayer test world: a 36-slot menu with the sell
 * button (an emerald) in the last slot. Clicking it pays a fixed price per item, writes the result
 * to chat and closes the menu, like the real server. Closing it by hand gives the items back.
 */
public class TestSellServer implements ModInitializer {
	public static int sales;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
			dispatcher.register(Commands.literal("sell").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SellMenu(id, inventory), Component.literal("Verkaufen")));
				return 1;
			})));
	}

	private static double price(ItemStack stack) {
		if (stack.is(Items.SUGAR_CANE)) {
			return 10;
		}
		if (stack.is(Items.MELON_SLICE)) {
			return 5;
		}
		return 1;
	}

	private static final class SellMenu extends ChestMenu {
		private final SimpleContainer goods;

		private SellMenu(int id, net.minecraft.world.entity.player.Inventory inventory) {
			this(id, inventory, new SimpleContainer(36));
		}

		private SellMenu(int id, net.minecraft.world.entity.player.Inventory inventory, SimpleContainer goods) {
			super(MenuType.GENERIC_9x4, id, inventory, goods, 4);
			this.goods = goods;
			ItemStack button = new ItemStack(Items.EMERALD);
			button.set(DataComponents.CUSTOM_NAME, Component.literal("Verkaufen"));
			goods.setItem(35, button);
		}

		@Override
		public void clicked(int slot, int button, ContainerInput type, Player player) {
			if (slot != 35) {
				super.clicked(slot, button, type, player);
				return;
			}
			int items = 0;
			double money = 0;
			for (int i = 0; i < 35; i++) {
				ItemStack stack = this.goods.getItem(i);
				items += stack.getCount();
				money += price(stack) * stack.getCount();
				this.goods.setItem(i, ItemStack.EMPTY);
			}
			if (items == 0) {
				return;
			}
			sales++;
			DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY));
			player.sendSystemMessage(Component.literal("Du hast " + format.format(items) + " Items für $" + format.format(money) + " verkauft!"));
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.level().getServer().execute(serverPlayer::closeContainer);
			}
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			for (int i = 0; i < 35; i++) {
				ItemStack stack = this.goods.removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
				}
			}
		}
	}
}
