package com.akf.tradeexpansion.api;

import com.akf.tradeexpansion.trade.ItemValuations;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Prices items that a goods' {@code components} cannot match exactly (design S-7, v0.93), e.g. plants that each carry
 * their own genome. Another mod registers one valuation per goods; the goods itself is defined in data as usual
 * (sell only, and with {@code "auto_add": []} to be dealt in at every market).
 * <p>
 * Register in common code ({@code ModInitializer}): the client previews prices with the same answer (A-4), and a
 * different answer on the server makes the sale fail with "price changed". A stack that exactly matches another goods
 * the market deals in is sold as that goods instead and never reaches the valuation.
 */
@FunctionalInterface
public interface ItemValuation {
	/** Highest ratio; larger answers are cut to this. */
	double MAX_RATIO = 100.0;

	/**
	 * The price ratio of one item of {@code stack} when sold as this goods: the sell price of the item is multiplied by
	 * it (at least 1c). Return 0 or less when the stack does not count as this goods. Called on the server and the
	 * client, for each stack in the inventory, so keep it quick and free of side effects.
	 */
	double ratio(ItemStack stack);

	/** Registers (or replaces) the valuation of {@code goods}. */
	static void register(Identifier goods, ItemValuation valuation) {
		ItemValuations.register(goods, valuation);
	}
}
