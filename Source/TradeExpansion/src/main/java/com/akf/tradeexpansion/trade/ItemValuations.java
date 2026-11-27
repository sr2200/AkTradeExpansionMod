package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.api.ItemValuation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** The valuations other mods registered (design S-7): goods id to {@link ItemValuation}. Shared by server and client. */
public final class ItemValuations {
	private static final Map<Identifier, ItemValuation> BY_GOODS = new ConcurrentHashMap<>();

	private ItemValuations() {
	}

	public static void register(Identifier goods, ItemValuation valuation) {
		Objects.requireNonNull(goods, "goods");
		Objects.requireNonNull(valuation, "valuation");
		if (BY_GOODS.put(goods, valuation) != null) TradeExpansion.LOGGER.warn("Item valuation of {} replaced", goods);
	}

	/** Removes the valuation of {@code goods} (the in-game tests put back what they registered). */
	public static void unregister(Identifier goods) {
		BY_GOODS.remove(goods);
	}

	/** The valuation of {@code goods}, or {@code null}. */
	public static ItemValuation get(Identifier goods) {
		return goods == null ? null : BY_GOODS.get(goods);
	}

	public static boolean has(Identifier goods) {
		return goods != null && BY_GOODS.containsKey(goods);
	}

	/** The ratio of one item of {@code stack} (cut to {@link ItemValuation#MAX_RATIO}), or -1 if it does not count. */
	public static double ratio(ItemValuation valuation, ItemStack stack) {
		if (valuation == null || stack.isEmpty()) return -1;
		double r;
		try {
			r = valuation.ratio(stack);
		} catch (RuntimeException e) {
			return -1; // a broken valuation only stops that item from selling
		}
		if (!(r > 0) || Double.isNaN(r)) return -1;
		return Math.min(ItemValuation.MAX_RATIO, r);
	}
}
