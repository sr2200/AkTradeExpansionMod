package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.item.BankCardItem;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.EmeraldCoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltips of the mod's items (design E-1), added through Fabric's ItemTooltipCallback instead of the deprecated
 * {@code Item.appendHoverText}. Lines go right below the item name.
 */
public final class ItemTooltips {
	private ItemTooltips() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			List<Component> extra = new ArrayList<>();
			switch (stack.getItem()) {
				case CoinItem coin -> coin.addTooltip(stack, extra::add);
				case EmeraldCoinItem coin -> coin.addTooltip(stack, extra::add);
				case WalletItem wallet -> wallet.addTooltip(stack, extra::add);
				case BankCardItem card -> card.addTooltip(stack, extra::add);
				default -> {
					return;
				}
			}
			lines.addAll(Math.min(1, lines.size()), extra);
		});
	}
}
