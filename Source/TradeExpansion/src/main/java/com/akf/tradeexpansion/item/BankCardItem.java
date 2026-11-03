package com.akf.tradeexpansion.item;

import com.akf.tradeexpansion.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Bank card (design E-8): pays from the owner's bank account at any market. Issued at a bank tab; only the
 * owner can use it, and only the most recently issued card works.
 */
public class BankCardItem extends Item {
	public BankCardItem(Properties properties) {
		super(properties);
	}

	/** Tooltip lines, added on the client through Fabric's ItemTooltipCallback (replaces the deprecated appendHoverText, E-1). */
	public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
		ModDataComponents.CardOwner owner = stack.get(ModDataComponents.CARD_OWNER);
		if (owner == null) {
			tooltip.accept(Component.translatable("tooltip.trade_expansion.card_blank").withStyle(ChatFormatting.RED));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.trade_expansion.card_owner", owner.name(), owner.number()).withStyle(ChatFormatting.AQUA));
	}
}
