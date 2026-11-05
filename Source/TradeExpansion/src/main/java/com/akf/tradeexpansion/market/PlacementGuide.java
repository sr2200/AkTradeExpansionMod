package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helps players see where a stall can go (design C-1, v0.19): {@code /aktradeex where} (village range, nearest market,
 * beds / villagers, placement result), {@code /aktradeex where show} (ranges drawn with particles for a while), and an
 * action bar hint while holding a stall ({@code trade.showPlacementHint}).
 */
public final class PlacementGuide {
	private static final int HINT_INTERVAL = 20;
	private static final int DRAW_INTERVAL = 10;
	private static final int SHOW_TICKS = 30 * 20;
	/** Only particles this close to the player are sent (keeps the packet count small). */
	private static final double DRAW_RANGE = 64;
	private static final int MAX_MARKETS = 5;

	private static final DustParticleOptions VILLAGE = new DustParticleOptions(0x55FF55, 1.5f);
	private static final DustParticleOptions MERGE = new DustParticleOptions(0xFFD700, 1.5f);
	private static final DustParticleOptions TOO_CLOSE = new DustParticleOptions(0xFF5555, 1.5f);
	private static final DustParticleOptions COUNT = new DustParticleOptions(0x55AAFF, 1.0f);

	/** A range display started by {@code /aktradeex where show}. */
	private record Display(ResourceKey<Level> dimension, long until, @Nullable BoundingBox village, List<BlockPos> markets, BlockPos origin) {
	}

	private static final Map<UUID, Display> DISPLAYS = new HashMap<>();

	private PlacementGuide() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PlacementGuide::tick);
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % HINT_INTERVAL == 0 && TradeExpansionConfig.get().trade.showPlacementHint) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getItemInHand(InteractionHand.MAIN_HAND).is(ModBlocks.MARKET_STALL.asItem())
						|| player.getItemInHand(InteractionHand.OFF_HAND).is(ModBlocks.MARKET_STALL.asItem())) {
					player.sendOverlayMessage(result(player.level(), targetPos(player)));
				}
			}
		}
		if (tick % DRAW_INTERVAL == 0 && !DISPLAYS.isEmpty()) {
			DISPLAYS.entrySet().removeIf(e -> {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				Display display = e.getValue();
				if (player == null || tick > display.until() || !player.level().dimension().equals(display.dimension())) return true;
				draw(player, display);
				return false;
			});
		}
	}

	/** Where a stall in the player's hand would be placed: against the looked-at block, else where they stand. */
	private static BlockPos targetPos(ServerPlayer player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return block.getBlockPos().relative(block.getDirection());
		}
		return player.blockPosition();
	}

	/** "Joins market X" / "founds a new market" / the reason it is refused (C-1). */
	public static Component result(ServerLevel level, BlockPos pos) {
		MarketRegistry.Preview preview = MarketRegistry.get(level.getServer()).preview(level, pos, MarketOrigin.CRAFTED);
		return switch (preview.kind()) {
			case JOIN -> Component.translatable("message.ak_trade_expansion.where.join", MarketNames.name(preview.market())).withStyle(ChatFormatting.GREEN);
			case NEW -> Component.translatable("message.ak_trade_expansion.where.new").withStyle(ChatFormatting.GREEN);
			case REFUSED -> preview.reason().copy().withStyle(ChatFormatting.RED);
		};
	}

	/** {@code /aktradeex where}: what is around the player's position. */
	public static List<Component> describe(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		TradeExpansionConfig config = TradeExpansionConfig.get();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.ak_trade_expansion.where.header", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD));
		BoundingBox village = SettlementRule.villageAt(level, pos);
		lines.add(village == null
				? Component.translatable("message.ak_trade_expansion.where.no_village")
				: Component.translatable("message.ak_trade_expansion.where.village", village.minX(), village.maxX(), village.minZ(), village.maxZ()));
		Market nearest = MarketRegistry.get(level.getServer()).nearestWithin(level.dimension(), pos, Double.MAX_VALUE);
		lines.add(nearest == null
				? Component.translatable("message.ak_trade_expansion.where.no_market")
				: Component.translatable("message.ak_trade_expansion.where.market", MarketNames.title(nearest), (int) nearest.horizontalDistanceTo(pos),
				(int) config.distance.mergeRadius, (int) config.distance.minNewMarketDistance));
		long villagers = MarketFactory.countVillagers(level, pos);
		lines.add(MarketFactory.usesVillagers(level)
				? Component.translatable("message.ak_trade_expansion.where.count_villagers", config.market.levelRadius, villagers)
				: Component.translatable("message.ak_trade_expansion.where.count", config.market.levelRadius, MarketFactory.countBeds(level, pos), villagers));
		// D-4: the size a market here would have now (beds and villagers, the lower level wins).
		lines.add(Component.translatable("message.ak_trade_expansion.where.level",
				com.akf.tradeexpansion.block.MarketStallBlockEntity.levelName(MarketFactory.measureLevel(level, pos))));
		lines.add(Component.translatable("message.ak_trade_expansion.where.result", result(level, pos)));
		lines.add(Component.translatable("message.ak_trade_expansion.where.show_hint").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** {@code /aktradeex where show}: draws the ranges for 30 seconds. */
	public static void show(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		TradeExpansionConfig config = TradeExpansionConfig.get();
		double reach = config.distance.minNewMarketDistance + DRAW_RANGE;
		List<BlockPos> markets = MarketRegistry.get(level.getServer()).inDimension(level.dimension()).stream()
				.filter(m -> m.horizontalDistanceTo(pos) <= reach)
				.sorted(java.util.Comparator.comparingDouble(m -> m.horizontalDistanceTo(pos)))
				.limit(MAX_MARKETS)
				.map(Market::center)
				.toList();
		DISPLAYS.put(player.getUUID(), new Display(level.dimension(), level.getServer().getTickCount() + SHOW_TICKS,
				SettlementRule.villageAt(level, pos), markets, pos.immutable()));
	}

	public static void hide(ServerPlayer player) {
		DISPLAYS.remove(player.getUUID());
	}

	private static void draw(ServerPlayer player, Display display) {
		ServerLevel level = player.level();
		TradeExpansionConfig config = TradeExpansionConfig.get();
		BoundingBox v = display.village();
		if (v != null) {
			for (int x = v.minX(); x <= v.maxX() + 1; x++) {
				dot(level, player, x, v.minZ(), VILLAGE);
				dot(level, player, x, v.maxZ() + 1, VILLAGE);
			}
			for (int z = v.minZ(); z <= v.maxZ() + 1; z++) {
				dot(level, player, v.minX(), z, VILLAGE);
				dot(level, player, v.maxX() + 1, z, VILLAGE);
			}
		}
		for (BlockPos center : display.markets()) {
			circle(level, player, center, config.distance.mergeRadius, MERGE);
			circle(level, player, center, config.distance.minNewMarketDistance, TOO_CLOSE);
		}
		circle(level, player, display.origin(), config.market.levelRadius, COUNT);
	}

	private static void circle(ServerLevel level, ServerPlayer player, BlockPos center, double radius, DustParticleOptions color) {
		int steps = Math.max(16, (int) Math.ceil(2 * Math.PI * radius));
		for (int i = 0; i < steps; i++) {
			double angle = 2 * Math.PI * i / steps;
			dot(level, player, center.getX() + 0.5 + radius * Math.cos(angle), center.getZ() + 0.5 + radius * Math.sin(angle), color);
		}
	}

	private static void dot(ServerLevel level, ServerPlayer player, double x, double z, DustParticleOptions color) {
		double dx = x - player.getX();
		double dz = z - player.getZ();
		if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) return;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		level.sendParticles(player, color, true, true, x, y + 0.3, z, 1, 0, 0, 0, 0);
	}
}
