package com.akf.tradeexpansion.core;

import java.util.List;

/**
 * Suggested base price for a good added from the config screen (design G, D-1). Minecraft-independent.
 * <p>
 * From a recipe: the materials' prices (per item) over the items it makes, ×{@link #CRAFTED_BONUS} when two or more
 * kinds of materials are combined (goods design 1-1: a crafted good pays for the work; a material under
 * {@link #MINOR_SHARE} of the cost, such as the sticks of a tool, does not count as a kind). Without a usable recipe:
 * a rough guess from the item's traits.
 */
public final class PriceEstimate {
	/** Combined goods: about 1.25 × the materials (goods design 1-1). */
	public static final double CRAFTED_BONUS = 1.25;
	/** A material below this share of the cost is not counted as a kind of its own. */
	public static final double MINOR_SHARE = 0.1;
	/** Price multipliers by rarity (common, uncommon, rare, epic). */
	private static final double[] RARITY = {1, 3, 8, 20};

	private PriceEstimate() {
	}

	/**
	 * Price per item made by a recipe.
	 *
	 * @param kindCosts the cost of each kind of material for one use of the recipe (price × how many it takes)
	 * @param made      items one use makes
	 * @return the price per item, or -1 if there is nothing to go by
	 */
	public static double fromRecipe(List<Double> kindCosts, int made) {
		if (kindCosts.isEmpty() || made <= 0) return -1;
		double total = 0;
		for (double c : kindCosts) {
			if (!(c >= 0)) return -1;
			total += c;
		}
		if (total <= 0) return -1;
		int kinds = 0;
		for (double c : kindCosts) {
			if (c >= total * MINOR_SHARE) kinds++;
		}
		return total * (kinds >= 2 ? CRAFTED_BONUS : 1.0) / made;
	}

	/**
	 * A rough price from what the item is like: stack size (unstackable items are worth more), food, durability and
	 * rarity.
	 *
	 * @param maxStack   the most per stack
	 * @param rarity     0 common, 1 uncommon, 2 rare, 3 epic
	 * @param nutrition  hunger it fills (0: not food)
	 * @param saturation saturation it gives
	 * @param maxDamage  durability (0: none)
	 */
	public static double fromTraits(int maxStack, int rarity, int nutrition, float saturation, int maxDamage) {
		double price = maxStack <= 1 ? 40 : maxStack <= 16 ? 10 : 4;
		if (nutrition > 0) price = Math.max(price, nutrition * 2.5 + saturation * 1.5);
		if (maxDamage > 0) price += maxDamage * 0.25;
		return price * RARITY[Math.clamp(rarity, 0, RARITY.length - 1)];
	}

	/** Rounds for the price box: whole coppers from 10c, tenths below, never under 0.01. */
	public static double round(double price) {
		double rounded = price >= 10 ? Math.round(price) : Math.round(price * 10) / 10.0;
		return Math.max(0.01, rounded);
	}
}
