package com.akf.tradeexpansion.crafting;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jspecify.annotations.Nullable;

/**
 * Stonecutter recipe whose output count is {@code currency.emeraldCoinsPerBlock} (design E-5). The count is read when
 * recipes load (server start, {@code /reload}); 0 removes the recipe through the {@code emerald_coin_enabled}
 * load condition.
 */
public class EmeraldCoinRecipe extends StonecutterRecipe {
	public static final RecipeSerializer<StonecutterRecipe> SERIALIZER = new RecipeSerializer<>(
			SingleItemRecipe.<StonecutterRecipe>simpleMapCodec(EmeraldCoinRecipe::create),
			SingleItemRecipe.<StonecutterRecipe>simpleStreamCodec(EmeraldCoinRecipe::new));

	/** {@code "fabric:load_conditions": [{"condition": "ak_trade_expansion:emerald_coin_enabled"}]} */
	public static final ResourceConditionType<Enabled> ENABLED = ResourceConditionType.create(
			TradeExpansion.id("emerald_coin_enabled"), MapCodec.unit(new Enabled()));

	public EmeraldCoinRecipe(Recipe.CommonInfo info, Ingredient input, ItemStackTemplate result) {
		super(info, input, result);
	}

	private static StonecutterRecipe create(Recipe.CommonInfo info, Ingredient input, ItemStackTemplate result) {
		int count = Math.clamp(TradeExpansionConfig.get().currency.emeraldCoinsPerBlock, 1, 64);
		return new EmeraldCoinRecipe(info, input, new ItemStackTemplate(result.item(), count, result.components()));
	}

	@Override
	public RecipeSerializer<StonecutterRecipe> getSerializer() {
		return SERIALIZER;
	}

	public record Enabled() implements ResourceCondition {
		@Override
		public ResourceConditionType<?> getType() {
			return ENABLED;
		}

		@Override
		public boolean test(RegistryOps.@Nullable RegistryInfoLookup registryInfo) {
			return TradeExpansionConfig.get().currency.emeraldCoinsPerBlock > 0;
		}
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TradeExpansion.id("emerald_coin"), SERIALIZER);
		ResourceConditions.register(ENABLED);
	}
}
