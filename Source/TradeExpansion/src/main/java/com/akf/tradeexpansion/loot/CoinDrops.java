package com.akf.tradeexpansion.loot;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Coins from hostile mobs (design E-2, Q22): only mobs in {@code #ak_trade_expansion:drops_coins} killed by a player;
 * 0..2 copper coins (+ up to the Looting level more), and a 2.5% chance of one iron coin.
 * In the Nether there is also a 0.5% chance of one gold coin.
 */
public final class CoinDrops {
	public static final TagKey<EntityType<?>> DROPS_COINS = TagKey.create(Registries.ENTITY_TYPE, TradeExpansion.id("drops_coins"));
	private static final float IRON_CHANCE = 0.025f;
	private static final float NETHER_GOLD_CHANCE = 0.005f;

	private CoinDrops() {
	}

	public static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!TradeExpansionConfig.get().currency.mobDropCoins) return;
			if (table.unwrapKey().map(k -> !k.identifier().getPath().startsWith("entities/")).orElse(true)) return;
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			Player killer = context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
			if (entity == null || killer == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(DROPS_COINS)) return;

			RandomSource random = context.getRandom();
			int looting = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.LOOTING)
					.map(h -> EnchantmentHelper.getEnchantmentLevel(h, killer)).orElse(0);
			int copper = random.nextInt(3) + (looting > 0 ? random.nextInt(looting + 1) : 0);
			if (copper > 0) drops.add(new ItemStack(ModItems.COPPER_COIN, copper));
			if (random.nextFloat() < IRON_CHANCE) drops.add(new ItemStack(ModItems.IRON_COIN));
			if (context.getLevel().dimension() == Level.NETHER && random.nextFloat() < NETHER_GOLD_CHANCE) {
				drops.add(new ItemStack(ModItems.GOLD_COIN));
			}
		});
	}
}
