package com.akf.tradeexpansion.item;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A coin. Shows its value in the tooltip; sneak + right-click breaks one coin into the next smaller tier (E-2).
 */
public class CoinItem extends Item {
	private final CoinType type;

	public CoinItem(CoinType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public CoinType type() {
		return type;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		CoinType lower = type.lower();
		if (!sneakUseExchange() || lower == null || !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack held = player.getItemInHand(hand);
			held.shrink(1);
			long[] counts = new long[CoinType.values().length];
			counts[lower.ordinal()] = type.ratioToLower();
			WalletService.giveCoins(player, counts);
		}
		return InteractionResult.SUCCESS;
	}

	/** Tooltip lines, added on the client through Fabric's ItemTooltipCallback (replaces the deprecated appendHoverText, E-1). */
	public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
		tooltip.accept(Component.translatable("tooltip.trade_expansion.coin_value", formatCopper(type.value()))
				.withStyle(ChatFormatting.GOLD));
		if (stack.getCount() > 1) {
			tooltip.accept(Component.translatable("tooltip.trade_expansion.coin_stack_value", formatCopper(type.value() * stack.getCount()))
					.withStyle(ChatFormatting.GRAY));
		}
		if (sneakUseExchange() && type.lower() != null) {
			tooltip.accept(Component.translatable("tooltip.trade_expansion.coin_break_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** {@code currency.sneakUseExchange} (synced to clients, design G). */
	private static boolean sneakUseExchange() {
		return TradeExpansionConfig.get().currency.sneakUseExchange;
	}

	/** 1000 → "1,000c" */
	public static String formatCopper(long amount) {
		return String.format("%,dc", amount);
	}
}
