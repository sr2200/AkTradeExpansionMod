package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketAccess;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Facilities of the village development (design N-4 / N-5): starting a construction (development points, site, scaffolding
 * and the construction sign), completing it after {@code development.buildDays} once the site is loaded, the effects that
 * end when the building's sign is broken, the building site stakes and the optional protection.
 */
public final class FacilityBuilder {
	/** Every this many ticks the server completes due buildings and checks the signs of completed ones. */
	private static final int TICK_INTERVAL = 40;
	/** Players within this distance of the market center hear about completed / destroyed buildings. */
	private static final double NOTIFY_RADIUS = 128;
	private static final String[] VILLAGE_STYLES = {"plains", "desert", "savanna", "snowy", "taiga"};

	private FacilityBuilder() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_INTERVAL == 0) tick(server);
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player.isCreative() || !(level instanceof ServerLevel serverLevel) || !isProtected(serverLevel, pos)) return true;
			player.sendOverlayMessage(Component.translatable("message.ak_trade_expansion.facility.protected").withStyle(ChatFormatting.RED));
			return false;
		});
	}

	public static Component name(FacilityKind kind) {
		return Component.translatable("facility.ak_trade_expansion." + kind.getSerializedName());
	}

	// ---- build ----

	/** Result of the BUILD action: the code, the development points spent and, for a blocked stake, why. */
	public record Outcome(TradeResult result, long cost, Component reason) {
		static Outcome fail(TradeResult result) {
			return new Outcome(result, 0, null);
		}
	}

	/** Development points building {@code id} costs now (half when rebuilding a destroyed one, N-4). */
	public static long cost(Market market, Identifier id) {
		FacilityDefinition def = FacilityCatalog.get(id);
		if (def == null) return 0;
		Facility existing = market.facilities().get(id);
		boolean rebuild = existing != null && existing.state() == Facility.State.DESTROYED;
		return DevelopmentMath.cost(def.cost(), rebuild, Development.config().rebuildCostRatio);
	}

	public static long constructing(Market market) {
		return market.facilities().values().stream().filter(f -> f.state() == Facility.State.CONSTRUCTING).count();
	}

	/** N-5: checks, finds a site, spends the points and puts up the construction site. */
	public static Outcome build(ServerPlayer player, ServerLevel level, Market market, Identifier id, long now) {
		TradeExpansionConfig.Development c = Development.config();
		FacilityDefinition def = FacilityCatalog.get(id);
		if (def == null) return Outcome.fail(TradeResult.NOT_AVAILABLE);
		Facility existing = market.facilities().get(id);
		if (existing != null && existing.state() != Facility.State.DESTROYED) return Outcome.fail(TradeResult.ALREADY_BUILT);
		if (constructing(market) >= c.maxConcurrentBuilds) return Outcome.fail(TradeResult.BUILD_LIMIT);
		if (Reputation.level(market, player.getUUID(), now) < c.buildReputationLevel) return Outcome.fail(TradeResult.REPUTATION_TOO_LOW);
		long cost = cost(market, id);
		if (Development.points(market, now) + 1e-9 < cost) return Outcome.fail(TradeResult.NOT_ENOUGH_POINTS);
		String style = style(level, market.center());
		StructureTemplate template = template(level.getServer(), def, style).orElse(null);
		if (template == null) return Outcome.fail(TradeResult.NO_TEMPLATE);

		SiteFinder.Result found = SiteFinder.find(level, market, template);
		if (found.site() == null) {
			return new Outcome(found.markerUsed() ? TradeResult.MARKER_BLOCKED : TradeResult.NO_SITE, 0, found.reason());
		}
		SiteFinder.Site site = found.site();
		Development.spend(market, cost, now);
		Facility facility = existing != null ? existing : new Facility(id, def.kind());
		long due = now + Math.round(c.buildDays * PriceModel.TICKS_PER_DAY);
		facility.start(site.origin(), site.rotation(), site.front(), site.box(), style, now, due);
		market.facilities().put(id, facility);
		if (site.marker() != null) {
			market.markers().remove(site.marker());
			level.removeBlock(site.marker(), false); // the stake is used up
		}
		putUpSite(level, facility);
		TradeExpansion.LOGGER.info("{} started {} at market #{} ({} at {}, style {})", player.getPlainTextName(), id, market.id(),
				site.rotation(), site.origin().toShortString(), style);
		return new Outcome(TradeResult.SUCCESS, cost, null);
	}

	/** Scaffolding at the four corners and the construction sign at the front (only where nothing stands). */
	private static void putUpSite(ServerLevel level, Facility facility) {
		BoundingBox box = facility.box();
		int y = facility.front().getY();
		for (int x : new int[] {box.minX(), box.maxX()}) {
			for (int z : new int[] {box.minZ(), box.maxZ()}) {
				for (int dy = 0; dy < 3; dy++) {
					BlockPos p = new BlockPos(x, y + dy, z);
					if (SiteFinder.replaceable(level.getBlockState(p), false)) level.setBlockAndUpdate(p, Blocks.SCAFFOLDING.defaultBlockState());
				}
			}
		}
		BlockPos front = facility.front();
		if (SiteFinder.replaceable(level.getBlockState(front), true)) {
			level.setBlockAndUpdate(front, ModBlocks.FACILITY_SIGN.defaultBlockState()
					.setValue(FacilitySignBlock.KIND, facility.kind())
					.setValue(FacilitySignBlock.BUILT, false)
					.setValue(FacilitySignBlock.FACING, SiteFinder.facing(facility.rotation())));
		}
	}

	/** Building style of a place: the village type of its biome (jungle, swamp and others: plains), Nether, End. */
	public static String style(ServerLevel level, BlockPos center) {
		if (level.dimension().equals(Level.NETHER)) return "nether";
		if (level.dimension().equals(Level.END)) return "end";
		Holder<Biome> biome = level.getBiome(center);
		ResourceKey<VillagerType> type = biome.unwrapKey().map(VillagerType.BY_BIOME::get).orElse(null);
		String name = type == null ? "plains" : type.identifier().getPath();
		for (String style : VILLAGE_STYLES) {
			if (style.equals(name)) return style;
		}
		return "plains";
	}

	/** {@code <ns>:facility/<style>/<structure>}, falling back to the plains look. */
	public static Optional<StructureTemplate> template(MinecraftServer server, FacilityDefinition def, String style) {
		Identifier base = TradeExpansion.id(def.structure());
		var manager = server.getStructureTemplateManager();
		Optional<StructureTemplate> styled = manager.get(Identifier.fromNamespaceAndPath(base.getNamespace(), "facility/" + style + "/" + base.getPath()));
		if (styled.isPresent()) return styled;
		return manager.get(Identifier.fromNamespaceAndPath(base.getNamespace(), "facility/plains/" + base.getPath()));
	}

	// ---- completion ----

	private static void tick(MinecraftServer server) {
		MarketRegistry registry = MarketRegistry.get(server);
		for (Market market : registry.all()) {
			if (market.facilities().isEmpty()) continue;
			ServerLevel level = server.getLevel(market.dimension());
			if (level == null) continue;
			long now = MarketClock.now(level);
			for (Facility facility : List.copyOf(market.facilities().values())) {
				if (facility.state() == Facility.State.CONSTRUCTING && now >= facility.due()
						&& level.hasChunksAt(facility.box().minX(), facility.box().minZ(), facility.box().maxX(), facility.box().maxZ())) {
					complete(level, market, facility);
				} else if (facility.complete() && facility.core() != null && level.isLoaded(facility.core())
						&& !level.getBlockState(facility.core()).is(ModBlocks.FACILITY_SIGN)) {
					destroyed(level, market, facility); // removed without a block update we saw (e.g. by another mod)
				}
			}
		}
	}

	/** Completes every construction of the market now ({@code /aktradeex development complete}). @return how many */
	public static int completeNow(ServerLevel level, Market market) {
		int count = 0;
		for (Facility facility : List.copyOf(market.facilities().values())) {
			if (facility.state() == Facility.State.CONSTRUCTING && level.hasChunksAt(facility.box().minX(), facility.box().minZ(),
					facility.box().maxX(), facility.box().maxZ())) {
				complete(level, market, facility);
				count++;
			}
		}
		return count;
	}

	/**
	 * Clears the site (scaffolding and the construction sign go; anything else built there meanwhile is dropped as items),
	 * places the building, fills the foundation and turns the effect on.
	 */
	static void complete(ServerLevel level, Market market, Facility facility) {
		FacilityDefinition def = FacilityCatalog.get(facility.id());
		StructureTemplate template = def == null ? null : template(level.getServer(), def, facility.style()).orElse(null);
		if (template == null) {
			TradeExpansion.LOGGER.warn("Facility {} of market #{}: no template, construction stays open", facility.id(), market.id());
			return;
		}
		BoundingBox box = facility.box();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.SCAFFOLDING) || state.is(ModBlocks.FACILITY_SIGN) && !state.getValue(FacilitySignBlock.BUILT)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			} else if (!SiteFinder.replaceable(state, true)) {
				level.destroyBlock(p, true);
			}
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(facility.rotation());
		template.placeInWorld(level, facility.origin(), facility.origin(), settings, RandomSource.create(market.id() ^ facility.started()), Block.UPDATE_ALL);
		foundation(level, box);
		BlockPos core = template.filterBlocks(facility.origin(), settings, ModBlocks.FACILITY_SIGN).stream()
				.map(StructureTemplate.StructureBlockInfo::pos).findFirst().orElse(null);
		facility.complete(core);
		market.clearDonations(facility.id());
		if (facility.kind() == FacilityKind.INN && !facility.villagersCalled()) {
			callVillagers(level, facility);
			facility.setVillagersCalled();
		}
		if (facility.kind() == FacilityKind.QUEST_HALL) market.setLastRequestRefresh(Long.MIN_VALUE); // new slots fill at once
		TradeExpansion.LOGGER.info("Facility {} of market #{} completed at {}", facility.id(), market.id(), box);
		changed(level, market, Component.translatable("message.ak_trade_expansion.facility.completed", MarketNames.name(market),
				name(facility.kind())).withStyle(ChatFormatting.GREEN));
	}

	/** Columns of the box lower than the floor get one block of the floor's own block below them. */
	private static void foundation(ServerLevel level, BoundingBox box) {
		int floorY = box.minY();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos below = new BlockPos(x, floorY - 1, z);
				BlockState floor = level.getBlockState(new BlockPos(x, floorY, z));
				if (!floor.isAir() && SiteFinder.replaceable(level.getBlockState(below), false)) level.setBlockAndUpdate(below, floor);
			}
		}
	}

	/** N-4: villagers come to a completed inn (once per market, not when it is rebuilt). */
	private static void callVillagers(ServerLevel level, Facility facility) {
		int count = Development.config().innVillagers;
		BoundingBox box = facility.box();
		BlockPos center = new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2);
		BlockPos spot = center;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 1, 3))) {
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
				spot = p.immutable();
				break;
			}
		}
		for (int i = 0; i < count; i++) EntityTypes.VILLAGER.spawn(level, spot, EntitySpawnReason.MOB_SUMMONED);
	}

	/** N-4: the sign of a completed building is gone: the effect ends (it can be rebuilt). */
	static void destroyed(ServerLevel level, Market market, Facility facility) {
		if (!facility.complete()) return;
		facility.destroy();
		TradeExpansion.LOGGER.info("Facility {} of market #{} destroyed", facility.id(), market.id());
		changed(level, market, Component.translatable("message.ak_trade_expansion.facility.destroyed", MarketNames.name(market),
				name(facility.kind())).withStyle(ChatFormatting.YELLOW));
	}

	/** Level bonus (N-3), saving and the screens of everyone looking at the market; nearby players are told. */
	private static void changed(ServerLevel level, Market market, Component message) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		int resized = MarketFactory.resize(level, market, registry.linkedTo(level.dimension()));
		registry.setDirty();
		for (ServerPlayer player : level.players()) {
			if (market.horizontalDistanceTo(player.blockPosition()) > NOTIFY_RADIUS) continue;
			player.sendSystemMessage(message);
			if (resized != 0) {
				player.sendSystemMessage(Component.translatable(resized > 0 ? "message.ak_trade_expansion.market_grew" : "message.ak_trade_expansion.market_shrank",
						market.id(), MarketStallBlockEntity.levelName(market.level())).withStyle(resized > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			}
		}
		TradeService.broadcast(level.getServer(), registry, Set.of(market.id()), MarketClock.now(level));
	}

	// ---- block hooks ----

	/** The market and facility whose sign (construction sign or core) is at {@code pos}, or {@code null}. */
	private record Found(Market market, Facility facility) {
	}

	private static Found facilityWithSign(ServerLevel level, BlockPos pos) {
		for (Market market : MarketRegistry.get(level.getServer()).inDimension(level.dimension())) {
			for (Facility facility : market.facilities().values()) {
				if (pos.equals(facility.core()) || facility.state() == Facility.State.CONSTRUCTING && pos.equals(facility.front())) {
					return new Found(market, facility);
				}
			}
		}
		return null;
	}

	public static void onSignRemoved(ServerLevel level, BlockPos pos) {
		Found found = facilityWithSign(level, pos);
		if (found != null && found.facility().complete() && pos.equals(found.facility().core())) destroyed(level, found.market(), found.facility());
	}

	/** Construction sign: what is being built and how long it still takes. Building sign: the market screen on its tab. */
	public static void useSign(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		Found found = facilityWithSign(level, pos);
		if (found == null) {
			player.sendOverlayMessage(Component.translatable("message.ak_trade_expansion.facility.no_market").withStyle(ChatFormatting.GRAY));
			return;
		}
		Market market = found.market();
		Facility facility = found.facility();
		if (facility.state() == Facility.State.CONSTRUCTING) {
			long left = Math.max(0, facility.due() - MarketClock.now(level));
			long minutes = (long) Math.ceil(left / (double) PriceModel.TICKS_PER_DAY * 20);
			player.sendOverlayMessage(Component.translatable("message.ak_trade_expansion.facility.under_construction", name(facility.kind()), minutes));
			return;
		}
		MarketMenu.OpenData data = new MarketMenu.OpenData(market.id(), pos.immutable(), -1, facility.kind().tab());
		MarketAccess.open(player, level, market, new ExtendedMenuProvider<>() {
			@Override
			public MarketMenu.OpenData getScreenOpeningData(ServerPlayer p) {
				return data;
			}

			@Override
			public Component getDisplayName() {
				return MarketNames.title(market);
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new MarketMenu(containerId, inventory, data);
			}
		});
	}

	/** N-5: a stake registers with the nearest market within {@code development.siteRadius}. */
	public static void onMarkerPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Market market = MarketRegistry.get(level.getServer()).nearestWithin(level.dimension(), pos, Development.config().siteRadius);
		if (market == null || !Development.enabled(market)) {
			if (player != null) player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.facility.marker_no_market",
					Development.config().siteRadius).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!market.markers().contains(pos)) market.markers().add(pos.immutable());
		MarketRegistry.get(level.getServer()).setDirty();
		if (player != null) {
			Direction facing = level.getBlockState(pos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
			player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.facility.marker_placed", MarketNames.name(market),
					Component.translatable("direction.ak_trade_expansion." + facing.getSerializedName().charAt(0))).withStyle(ChatFormatting.GREEN));
		}
	}

	public static void onMarkerRemoved(ServerLevel level, BlockPos pos) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		for (Market market : registry.inDimension(level.dimension())) {
			if (market.markers().remove(pos)) registry.setDirty();
		}
	}

	// ---- protection (development.protectFacilities) ----

	public static boolean protectionOn() {
		return Development.config().protectFacilities;
	}

	/** Inside a completed building while {@code development.protectFacilities} is on. */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		if (!Development.config().protectFacilities) return false;
		for (Market market : MarketRegistry.get(level.getServer()).inDimension(level.dimension())) {
			for (Facility facility : market.facilities().values()) {
				if (facility.complete() && facility.box().isInside(pos)) return true;
			}
		}
		return false;
	}
}
