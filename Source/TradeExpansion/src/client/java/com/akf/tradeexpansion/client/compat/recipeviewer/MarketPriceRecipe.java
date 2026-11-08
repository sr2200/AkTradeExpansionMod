package com.akf.tradeexpansion.client.compat.recipeviewer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** I (v0.47): one "nearby markets" page: the item it is about. Shared by the JEI and REI pages. */
public record MarketPriceRecipe(Identifier item) {
	/** The page for an item, or {@code null} when the id is not a known item. */
	public static @Nullable MarketPriceRecipe of(Identifier item) {
		Item value = BuiltInRegistries.ITEM.getValue(item);
		return value == null || value == Items.AIR ? null : new MarketPriceRecipe(item);
	}

	public ItemStack stack() {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(item));
	}
}
