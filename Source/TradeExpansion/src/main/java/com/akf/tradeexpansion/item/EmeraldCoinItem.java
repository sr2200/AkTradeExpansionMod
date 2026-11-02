package com.akf.tradeexpansion.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Emerald coin: a token, not money (design E-5). Deliberately not a {@link CoinItem}, so the wallet ignores it;
 * village markets buy it as goods at a market price.
 */
public class EmeraldCoinItem extends Item {
	public EmeraldCoinItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.trade_expansion.emerald_coin").withStyle(ChatFormatting.GREEN));
	}
}
