package com.akf.tradeexpansion.crafting;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.item.CoinItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.jspecify.annotations.Nullable;

/**
 * Minting (design E-2, Q20): a shaped recipe whose output is defined by copper value, so changing the coin ratios
 * changes the number of coins but not their worth. Count = value ÷ coin value (rounded down, 1..64), computed when
 * recipes load. Disabled through the {@code minting_enabled} load condition when {@code currency.allowMinting} is off.
 */
public class MintingRecipe extends ShapedRecipe {
	private final CommonInfo info;
	private final CraftingBookInfo book;
	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate coin;
	private final long value;

	public MintingRecipe(CommonInfo info, CraftingBookInfo book, ShapedRecipePattern pattern, ItemStackTemplate coin, long value) {
		super(info, book, pattern, new ItemStackTemplate(coin.item(), count(coin, value), coin.components()));
		this.info = info;
		this.book = book;
		this.pattern = pattern;
		this.coin = coin;
		this.value = value;
	}

	private static int count(ItemStackTemplate coin, long value) {
		long coinValue = coin.item().value() instanceof CoinItem c ? c.type().value() : 1;
		return (int) Math.clamp(value / coinValue, 1, 64);
	}

	private static final MapCodec<ShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(r -> ((MintingRecipe) r).info),
			CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> ((MintingRecipe) r).book),
			ShapedRecipePattern.MAP_CODEC.forGetter(r -> ((MintingRecipe) r).pattern),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> ((MintingRecipe) r).coin),
			Codec.LONG.fieldOf("value").forGetter(r -> ((MintingRecipe) r).value)
	).apply(i, MintingRecipe::new));

	/** Network form is the plain shaped recipe (clients only see recipe displays). */
	public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, ShapedRecipe.STREAM_CODEC);

	public static final ResourceConditionType<Enabled> ENABLED = ResourceConditionType.create(
			TradeExpansion.id("minting_enabled"), MapCodec.unit(new Enabled()));

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		return SERIALIZER;
	}

	public record Enabled() implements ResourceCondition {
		@Override
		public ResourceConditionType<?> getType() {
			return ENABLED;
		}

		@Override
		public boolean test(RegistryOps.@Nullable RegistryInfoLookup registryInfo) {
			return TradeExpansionConfig.get().currency.allowMinting;
		}
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TradeExpansion.id("minting"), SERIALIZER);
		ResourceConditions.register(ENABLED);
	}
}
