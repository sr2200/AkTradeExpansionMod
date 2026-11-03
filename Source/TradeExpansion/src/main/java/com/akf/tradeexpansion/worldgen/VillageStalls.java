package com.akf.tradeexpansion.worldgen;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.mixin.StructureTemplatePoolAccessor;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.ArrayList;
import java.util.List;

/**
 * Stalls in generated villages (design stage 9, C-1 "自然生成"): adds a small stall building to the house pool of every
 * vanilla village type, and protects the stalls it places ({@code worldgen.protectNaturalStalls}).
 * <p>
 * The building ({@code data/trade_expansion/structure/village/<type>/market_stall.nbt}) connects to a street like a
 * vanilla house; its stall has origin {@code natural} and gets its market on first use.
 */
public final class VillageStalls {
	private static final String[] TYPES = {"plains", "desert", "savanna", "snowy", "taiga"};
	/** Weight in the house pool; vanilla small houses use 1–3, so roughly one stall per village. */
	private static final int WEIGHT = 2;

	private VillageStalls() {
	}

	public static void init() {
		// Pools are read when chunks generate; the server's registries exist from SERVER_STARTING on.
		ServerLifecycleEvents.SERVER_STARTING.register(VillageStalls::addToPools);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!TradeExpansionConfig.get().worldgen.protectNaturalStalls || player.isCreative()) return true;
			if (blockEntity instanceof MarketStallBlockEntity stall && stall.origin() == MarketOrigin.NATURAL) {
				player.sendOverlayMessage(Component.translatable("message.trade_expansion.stall_protected").withStyle(ChatFormatting.RED));
				return false;
			}
			return true;
		});
	}

	private static void addToPools(MinecraftServer server) {
		if (!TradeExpansionConfig.get().worldgen.spawnStallsInVillages) return;
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processors = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
		for (String type : TYPES) {
			StructureTemplatePool pool = pools.getValue(Identifier.withDefaultNamespace("village/" + type + "/houses"));
			if (pool == null) continue;
			String location = TradeExpansion.id("village/" + type + "/market_stall").toString();
			StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
			// Integrated servers start again for every world: add the building only once per pool.
			boolean present = accessor.tradeExpansion$rawTemplates().stream()
					.anyMatch(p -> p.getFirst() instanceof SinglePoolElement e && e.getTemplateLocation().toString().equals(location));
			if (present) continue;

			Holder<StructureProcessorList> processor = processors.get(ResourceKey.create(Registries.PROCESSOR_LIST,
					Identifier.withDefaultNamespace(type.equals("plains") ? "mossify_10_percent" : "empty"))).orElseThrow();
			StructurePoolElement element = StructurePoolElement.legacy(location, processor).apply(StructureTemplatePool.Projection.RIGID);
			List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(accessor.tradeExpansion$rawTemplates());
			raw.add(Pair.of(element, WEIGHT));
			accessor.tradeExpansion$setRawTemplates(raw);
			for (int i = 0; i < WEIGHT; i++) accessor.tradeExpansion$templates().add(element);
		}
		TradeExpansion.LOGGER.info("Added market stall buildings to village house pools");
	}
}
