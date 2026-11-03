package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.entity.TraderEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Trader NPC (design A-5) and its spawn egg. */
public final class ModEntities {
	private static final ResourceKey<EntityType<?>> TRADER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TradeExpansion.id("trader"));

	public static final EntityType<TraderEntity> TRADER = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRADER_KEY,
			EntityType.Builder.of(TraderEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build(TRADER_KEY));

	public static final SpawnEggItem TRADER_SPAWN_EGG = registerSpawnEgg();

	private ModEntities() {
	}

	private static SpawnEggItem registerSpawnEgg() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradeExpansion.id("trader_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(TRADER)));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createAttributes());
	}
}
