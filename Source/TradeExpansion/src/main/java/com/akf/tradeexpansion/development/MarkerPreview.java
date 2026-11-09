package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * N-5 (v0.41): when a building site stake is placed or right-clicked, the outline of every building not yet built is
 * drawn with particles for 30 seconds at the place it would stand (green: it can be built there now, red: it cannot), and
 * the chat says which can and why the others cannot. Buildings of the same size share one outline.
 */
public final class MarkerPreview {
	private static final int DRAW_INTERVAL = 10;
	private static final int SHOW_TICKS = 30 * 20;
	/** Only particles this close to the player are sent. */
	private static final double DRAW_RANGE = 64;
	private static final DustParticleOptions OK = new DustParticleOptions(0x55FF55, 1.2f);
	private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xFF5555, 1.2f);

	private record Outline(BoundingBox box, boolean ok) {
	}

	private record Display(ResourceKey<Level> dimension, long until, List<Outline> outlines) {
	}

	private static final Map<UUID, Display> DISPLAYS = new HashMap<>();

	private MarkerPreview() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MarkerPreview::tick);
	}

	private static void tick(MinecraftServer server) {
		if (DISPLAYS.isEmpty() || server.getTickCount() % DRAW_INTERVAL != 0) return;
		long tick = server.getTickCount();
		DISPLAYS.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Display display = e.getValue();
			if (player == null || tick > display.until() || !player.level().dimension().equals(display.dimension())) return true;
			for (Outline outline : display.outlines()) draw(player, outline.box(), outline.ok() ? OK : BLOCKED);
			return false;
		});
	}

	/** Shows the outlines of the stake at {@code pos} to {@code player} and lists the buildings in the chat. */
	public static void show(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Market market = MarketRegistry.get(level.getServer()).nearestWithin(level.dimension(), pos, Development.config().siteRadius);
		if (market == null || !Development.enabled(market)) {
			player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.facility.marker_no_market",
					Development.config().siteRadius).withStyle(ChatFormatting.YELLOW));
			return;
		}
		Direction facing = level.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING);
		String style = FacilityBuilder.style(level, market.center());
		Map<BoundingBox, Boolean> boxes = new LinkedHashMap<>();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.ak_trade_expansion.facility.preview.header", MarketNames.name(market),
				Component.translatable("direction.ak_trade_expansion." + facing.getSerializedName().charAt(0))).withStyle(ChatFormatting.GOLD));
		for (Map.Entry<Identifier, FacilityDefinition> e : FacilityCatalog.all().entrySet()) {
			Facility existing = market.facilities().get(e.getKey());
			if (existing != null && existing.state() != Facility.State.DESTROYED) continue; // built or under construction
			StructureTemplate template = FacilityBuilder.template(level.getServer(), e.getValue(), style).orElse(null);
			if (template == null) continue;
			SiteFinder.Preview preview = SiteFinder.preview(level, market, template, pos, facing, e.getValue().landRows());
			boolean ok = preview.reason() == null;
			boxes.merge(preview.box(), ok, Boolean::logicalOr);
			Component name = FacilityBuilder.name(e.getValue().kind());
			String size = template.getSize().getX() + "×" + template.getSize().getZ();
			lines.add(ok
					? Component.translatable("message.ak_trade_expansion.facility.preview.ok", name, size).withStyle(ChatFormatting.GREEN)
					: Component.translatable("message.ak_trade_expansion.facility.preview.blocked", name, size, preview.reason()).withStyle(ChatFormatting.RED));
		}
		if (boxes.isEmpty()) lines.add(Component.translatable("message.ak_trade_expansion.facility.preview.none").withStyle(ChatFormatting.GRAY));
		// Building uses the oldest stake of the market: say so when this one is not it.
		BlockPos first = market.markers().stream().filter(p -> level.isLoaded(p) && level.getBlockState(p).is(com.akf.tradeexpansion.registry.ModBlocks.CONSTRUCTION_MARKER))
				.findFirst().orElse(null);
		if (first != null && !first.equals(pos)) {
			lines.add(Component.translatable("message.ak_trade_expansion.facility.preview.not_first", first.getX(), first.getY(), first.getZ())
					.withStyle(ChatFormatting.YELLOW));
		}
		lines.forEach(player::sendSystemMessage);
		List<Outline> outlines = new ArrayList<>();
		boxes.forEach((box, ok) -> outlines.add(new Outline(box, ok)));
		DISPLAYS.put(player.getUUID(), new Display(level.dimension(), level.getServer().getTickCount() + SHOW_TICKS, outlines));
	}

	/** The rectangle at floor level and the four corner posts up to the roof. */
	private static void draw(ServerPlayer player, BoundingBox box, DustParticleOptions color) {
		double x0 = box.minX(), x1 = box.maxX() + 1, z0 = box.minZ(), z1 = box.maxZ() + 1;
		double y0 = box.minY() + 0.1, y1 = box.maxY() + 1;
		for (double x = x0; x <= x1 + 1e-6; x += 0.5) {
			dot(player, x, y0, z0, color);
			dot(player, x, y0, z1, color);
		}
		for (double z = z0 + 0.5; z < z1 - 1e-6; z += 0.5) {
			dot(player, x0, y0, z, color);
			dot(player, x1, y0, z, color);
		}
		for (double y = y0 + 0.5; y <= y1 + 1e-6; y += 0.5) {
			dot(player, x0, y, z0, color);
			dot(player, x1, y, z0, color);
			dot(player, x0, y, z1, color);
			dot(player, x1, y, z1, color);
		}
	}

	private static void dot(ServerPlayer player, double x, double y, double z, DustParticleOptions color) {
		double dx = x - player.getX();
		double dz = z - player.getZ();
		if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) return;
		player.level().sendParticles(player, color, true, true, x, y, z, 1, 0, 0, 0, 0);
	}
}
