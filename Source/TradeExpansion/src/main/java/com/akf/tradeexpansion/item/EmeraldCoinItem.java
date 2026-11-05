package com.akf.tradeexpansion.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Emerald coin: a token, not money (design E-5). Deliberately not a {@link CoinItem}, so the wallet ignores it;
 * village markets buy it as goods at a market price.
 */
public class EmeraldCoinItem extends Item {
	public EmeraldCoinItem(Properties properties) {
		super(properties);
	}

	/** Tooltip lines, added on the client through Fabric's ItemTooltipCallback (replaces the deprecated appendHoverText, E-1). */
	public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.emerald_coin").withStyle(ChatFormatting.GREEN));
	}
}
