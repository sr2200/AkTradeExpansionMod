package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.advancement.TradeAdvancements;
import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.FacilityLevelMath;
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
		MarkerPreview.register(); // N-5 (v0.41): outlines at building site stakes
		ConstructionEffects.register(); // N-5 (v0.44): sites under construction show it
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

	/**
	 * Development points building {@code id} costs now: its cost; N-11 (v0.86) for a completed one, raising it to the next
	 * level ({@code development.upgradeCostMultipliers}, 0 at the top level); for a destroyed one, rebuilding it at its level
	 * ({@code development.rebuildCostRatio} of the building and its raises, N-4).
	 */
	public static long cost(Market market, Identifier id) {
		FacilityDefinition def = FacilityCatalog.get(id);
		if (def == null) return 0;
		return FacilityLevelMath.scaled(def.cost(), costFactor(market.facilities().get(id)));
	}

	/** N-11: the factor of the building's cost and materials that {@link #cost} and {@link #required} apply. */
	private static double costFactor(Facility existing) {
		TradeExpansionConfig.Development c = Development.config();
		if (existing == null || existing.state() == Facility.State.CONSTRUCTING) return 1.0;
		if (existing.state() == Facility.State.DESTROYED) {
			return FacilityLevelMath.totalFactor(existing.level(), c.upgradeCostMultipliers) * Math.clamp(c.rebuildCostRatio, 0.0, 1.0);
		}
		return c.upgradeEnabled ? FacilityLevelMath.upgradeFactor(existing.level(), c.upgradeCostMultipliers) : 0;
	}

	/** N-11: the completed building can be raised to the next level now (the levels are on, below the top, no raise under way). */
	public static boolean upgradeable(Facility facility) {
		return Development.config().upgradeEnabled && facility != null && facility.complete() && facility.level() < FacilityLevelMath.MAX_LEVEL
				&& !facility.upgrading();
	}

	/** N-2 / N-11: the facility takes materials now: not built, destroyed, or completed and upgradeable. */
	public static boolean takesMaterials(Market market, Identifier id) {
		Facility existing = market.facilities().get(id);
		return existing == null || existing.state() == Facility.State.DESTROYED || upgradeable(existing);
	}

	/**
	 * N-2 (v0.42): how many of a wanted material the facility needs now: the data's count × {@code development.materialCountMultiplier}
	 * (rounded up, at least 1), or {@code development.rebuildCostRatio} of that (rounded up) when rebuilding a destroyed one.
	 */
	public static int required(Market market, Identifier id, FacilityDefinition.Wanted wanted) {
		Facility existing = market.facilities().get(id);
		double multiplier = Development.config().materialCountMultiplier;
		FacilityDefinition def = FacilityCatalog.get(id);
		if (market.hasFacility(FacilityKind.TOWN_HALL) && (def == null || def.kind() != FacilityKind.TOWN_HALL)) {
			// v0.79: the town hall; N-11: fewer with its level
			multiplier *= FacilityEffects.raise(market, FacilityKind.TOWN_HALL, 0, Math.clamp(Development.config().townHallMaterialMultiplier, 0.1, 1.0));
		}
		long full = DevelopmentMath.materialCount(wanted.count(), multiplier);
		// N-11 (v0.86): raising to the next level, or rebuilding at the level it had
		return (int) Math.min(Integer.MAX_VALUE, FacilityLevelMath.scaled(full, costFactor(existing)));
	}

	/** N-2 (v0.42): every wanted material is donated (always true when {@code development.requireMaterials} is off). */
	public static boolean materialsReady(Market market, Identifier id) {
		FacilityDefinition def = FacilityCatalog.get(id);
		if (def == null || !Development.config().requireMaterials) return true;
		java.util.Map<String, Integer> donated = market.donations(id);
		for (FacilityDefinition.Wanted w : def.wanted()) {
			if (donated.getOrDefault(w.key(), 0) < required(market, id, w)) return false;
		}
		return true;
	}

	/** Constructions under way: new buildings and (N-11) raises to the next level. */
	public static long constructing(Market market) {
		return market.facilities().values().stream().filter(f -> f.state() == Facility.State.CONSTRUCTING || f.upgrading()).count();
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
		if (!materialsReady(market, id)) return Outcome.fail(TradeResult.MATERIALS_MISSING);
		long cost = cost(market, id);
		if (Development.points(market, now) + 1e-9 < cost) return Outcome.fail(TradeResult.NOT_ENOUGH_POINTS);
		String style = style(level, market.center());
		// N-11 (v0.86): a destroyed building is rebuilt at the level it had; a tall level 3 without room for its second
		// storey is rebuilt without it
		int buildLevel = existing == null ? 1 : existing.level();
		boolean tall = existing != null && existing.tall();
		String branchLook = existing == null ? "" : existing.branch();
		StructureTemplate template = template(level.getServer(), def, style, buildLevel, tall, branchLook).orElse(null);
		if (template == null) return Outcome.fail(TradeResult.NO_TEMPLATE);

		SiteFinder.Result found = SiteFinder.find(level, market, template, def.landRows());
		if (found.site() == null && tall) {
			StructureTemplate low = template(level.getServer(), def, style, buildLevel, false, branchLook).orElse(null);
			SiteFinder.Result lower = low == null ? found : SiteFinder.find(level, market, low, def.landRows());
			if (lower.site() != null) {
				found = lower;
				existing.setTall(false);
			}
		}
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
		if (name.equals("snow")) name = "snowy"; // the villager type of snowy biomes is "snow"; its village (and our look) "snowy"
		for (String style : VILLAGE_STYLES) {
			if (style.equals(name)) return style;
		}
		return "plains";
	}

	/** {@code <ns>:facility/<style>/<structure>}, falling back to the plains look. */
	public static Optional<StructureTemplate> template(MinecraftServer server, FacilityDefinition def, String style) {
		return look(server, def, style, "");
	}

	/**
	 * N-11 (v0.86): the look of {@code level}: {@code <structure>_lv2}, {@code _lv3} or {@code _lv3_tall} (with the second
	 * storey), falling back to the plains look; a level look a data pack lacks falls back to level 1 (a missing tall look
	 * is empty, so that the caller builds without the second storey).
	 */
	public static Optional<StructureTemplate> template(MinecraftServer server, FacilityDefinition def, String style, int level, boolean tall) {
		return template(server, def, style, level, tall, "");
	}

	/**
	 * v0.87: the look of {@code level} in the direction {@code branch} (chosen at level 2): direction b has looks of its
	 * own ({@code _lv2_b}, {@code _lv3_b}, {@code _lv3_b_tall}: banners of another colour); without them, and for
	 * direction a, the looks above.
	 */
	public static Optional<StructureTemplate> template(MinecraftServer server, FacilityDefinition def, String style, int level, boolean tall,
			String branch) {
		if (level <= 1) return template(server, def, style);
		String suffix = "_lv" + Math.min(level, FacilityLevelMath.MAX_LEVEL);
		if ("b".equals(branch)) {
			Optional<StructureTemplate> own = look(server, def, style, suffix + "_b" + (tall ? "_tall" : ""));
			if (own.isPresent()) return own;
		}
		if (tall) return look(server, def, style, suffix + "_tall");
		Optional<StructureTemplate> found = look(server, def, style, suffix);
		return found.isPresent() ? found : template(server, def, style);
	}

	/** The look a building has now (its level, direction and second storey). */
	public static Optional<StructureTemplate> template(MinecraftServer server, FacilityDefinition def, Facility facility) {
		Optional<StructureTemplate> found = template(server, def, facility.style(), facility.level(), facility.tall(), facility.branch());
		return found.isPresent() ? found : template(server, def, facility.style(), facility.level(), false, facility.branch());
	}

	private static Optional<StructureTemplate> look(MinecraftServer server, FacilityDefinition def, String style, String suffix) {
		Identifier base = TradeExpansion.id(def.structure());
		var manager = server.getStructureTemplateManager();
		Optional<StructureTemplate> styled = manager.get(Identifier.fromNamespaceAndPath(base.getNamespace(), "facility/" + style + "/" + base.getPath() + suffix));
		if (styled.isPresent()) return styled;
		return manager.get(Identifier.fromNamespaceAndPath(base.getNamespace(), "facility/plains/" + base.getPath() + suffix));
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
				} else if (facility.upgrading() && now >= facility.upgradeDue()
						&& level.hasChunksAt(facility.box().minX(), facility.box().minZ(), facility.box().maxX(), facility.box().maxZ())) {
					completeUpgrade(level, market, facility); // N-11 (v0.86)
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
			if (!level.hasChunksAt(facility.box().minX(), facility.box().minZ(), facility.box().maxX(), facility.box().maxZ())) continue;
			if (facility.state() == Facility.State.CONSTRUCTING) {
				complete(level, market, facility);
				count++;
			} else if (facility.upgrading()) {
				completeUpgrade(level, market, facility); // N-11 (v0.86)
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
		// N-11 (v0.86): a rebuilt building gets the look of the level it had
		StructureTemplate template = def == null ? null : template(level.getServer(), def, facility).orElse(null);
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
		foundation(level, box, def.landRows() >= 0);
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
		TradeAdvancements.onFacilityCompleted(level.getServer(), market, MarketClock.now(level)); // R
	}

	// ---- levels (N-11, v0.86) ----

	/**
	 * N-11: starts raising the completed building {@code id} to the next level (the same checks as building it: the limit
	 * of constructions, the reputation, the materials and the points). At level 1 {@code branch} (0 = a, 1 = b) chooses
	 * the direction. Level 3 gets the second storey when the kind has one and the space above the building is free. The
	 * building keeps its effect while the work goes on ({@code development.buildDays}).
	 */
	public static Outcome upgrade(ServerPlayer player, ServerLevel level, Market market, Identifier id, int branch, long now) {
		TradeExpansionConfig.Development c = Development.config();
		FacilityDefinition def = FacilityCatalog.get(id);
		if (def == null || !c.upgradeEnabled) return Outcome.fail(TradeResult.NOT_AVAILABLE);
		Facility facility = market.facilities().get(id);
		if (facility == null || !facility.complete()) return Outcome.fail(TradeResult.NOT_BUILT);
		if (facility.level() >= FacilityLevelMath.MAX_LEVEL) return Outcome.fail(TradeResult.MAX_LEVEL);
		if (facility.upgrading()) return Outcome.fail(TradeResult.ALREADY_BUILT);
		if (facility.level() == 1 && branch != 0 && branch != 1) return Outcome.fail(TradeResult.INVALID_REQUEST);
		if (constructing(market) >= c.maxConcurrentBuilds) return Outcome.fail(TradeResult.BUILD_LIMIT);
		if (Reputation.level(market, player.getUUID(), now) < c.buildReputationLevel) return Outcome.fail(TradeResult.REPUTATION_TOO_LOW);
		if (!materialsReady(market, id)) return Outcome.fail(TradeResult.MATERIALS_MISSING);
		long cost = cost(market, id);
		if (Development.points(market, now) + 1e-9 < cost) return Outcome.fail(TradeResult.NOT_ENOUGH_POINTS);
		int next = facility.level() + 1;
		MinecraftServer server = level.getServer();
		String nextBranch = facility.level() == 1 ? (branch == 1 ? "b" : "a") : facility.branch();
		if (template(server, def, facility).isEmpty() || template(server, def, facility.style(), next, false, nextBranch).isEmpty()) {
			return Outcome.fail(TradeResult.NO_TEMPLATE);
		}
		Component reason = null;
		boolean tall = false;
		if (next == FacilityLevelMath.MAX_LEVEL) {
			StructureTemplate high = template(server, def, facility.style(), next, true, nextBranch).orElse(null);
			if (high != null) {
				tall = roomAbove(level, facility, high);
				if (!tall) reason = Component.translatable("message.ak_trade_expansion.facility.upgrade.no_room", name(facility.kind()));
			}
		}
		Development.spend(market, cost, now);
		facility.startUpgrade(branch == 1 ? "b" : "a", tall, now + Math.round(c.buildDays * PriceModel.TICKS_PER_DAY));
		TradeExpansion.LOGGER.info("{} started raising {} of market #{} to level {}{} (direction {})", player.getPlainTextName(), id, market.id(),
				next, tall ? " with a second storey" : "", facility.branch());
		return new Outcome(TradeResult.SUCCESS, cost, reason);
	}

	/** The box the building would fill with {@code template}, at its place. */
	private static BoundingBox boxOf(Facility facility, StructureTemplate template) {
		return template.getBoundingBox(new StructurePlaceSettings().setRotation(facility.rotation()), facility.origin());
	}

	/** N-11: the cells a taller look adds over the building are free (air, plants, snow: what a building may replace). */
	private static boolean roomAbove(ServerLevel level, Facility facility, StructureTemplate high) {
		BoundingBox now = facility.box();
		BoundingBox box = boxOf(facility, high);
		if (box.maxY() >= level.getMaxY()) return false;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), now.maxY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!SiteFinder.replaceable(level.getBlockState(p), false)) return false;
		}
		return true;
	}

	/** The blocks of a look at the building's place: world position → state (rotated as placed). */
	private static java.util.Map<BlockPos, BlockState> blocks(StructureTemplate template, Facility facility) {
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(facility.rotation());
		java.util.Map<BlockPos, BlockState> map = new java.util.HashMap<>();
		List<StructureTemplate.Palette> palettes = ((com.akf.tradeexpansion.mixin.StructureTemplateAccessor) template).tradeExpansion$palettes();
		if (palettes.isEmpty()) return map;
		for (StructureTemplate.StructureBlockInfo info : palettes.getFirst().blocks()) {
			BlockPos pos = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(facility.origin());
			map.put(pos, info.state().mirror(settings.getMirror()).rotate(settings.getRotation()));
		}
		return map;
	}

	/**
	 * N-11: the raise is done. Only the blocks in which the two looks differ change, so chests and what players keep in
	 * the building stay; a block a player put where the new look has another one is dropped as an item (as at the
	 * completion of a construction). The second storey replaces the roof (the old roof's blocks go without drops).
	 */
	static void completeUpgrade(ServerLevel level, Market market, Facility facility) {
		FacilityDefinition def = FacilityCatalog.get(facility.id());
		MinecraftServer server = level.getServer();
		StructureTemplate from = def == null ? null : template(server, def, facility).orElse(null);
		int next = facility.level() + 1;
		boolean tall = facility.upgradeTall();
		StructureTemplate to = def == null ? null : template(server, def, facility.style(), next, tall, facility.branch()).orElse(null);
		if (to == null && tall && def != null) {
			tall = false;
			to = template(server, def, facility.style(), next, false, facility.branch()).orElse(null);
		}
		if (from == null || to == null) {
			TradeExpansion.LOGGER.warn("Facility {} of market #{}: no template for level {}, the raise stays open", facility.id(), market.id(), next);
			return;
		}
		java.util.Map<BlockPos, BlockState> old = blocks(from, facility);
		java.util.Map<BlockPos, BlockState> target = blocks(to, facility);
		java.util.Set<BlockPos> cells = new java.util.HashSet<>(old.keySet());
		cells.addAll(target.keySet());
		List<BlockPos> changed = new java.util.ArrayList<>();
		BlockState air = Blocks.AIR.defaultBlockState();
		List<BlockPos> sorted = new java.util.ArrayList<>(cells);
		sorted.sort(java.util.Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()));
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (BlockPos pos : sorted) {
			BlockState had = old.getOrDefault(pos, air);
			BlockState want = target.getOrDefault(pos, air);
			if (had.equals(want)) continue;
			BlockState current = level.getBlockState(pos);
			if (current.equals(want)) continue;
			if (!current.equals(had) && !SiteFinder.replaceable(current, true)) level.destroyBlock(pos, true); // put there meanwhile
			level.setBlock(pos, want, flags);
			changed.add(pos);
		}
		// Shapes and neighbours as a placed template would have them (fences join, doors and lanterns check their support).
		for (BlockPos pos : changed) {
			BlockState state = level.getBlockState(pos);
			BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
			if (!shaped.equals(state)) level.setBlock(pos, shaped, flags);
		}
		for (BlockPos pos : changed) level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
		if (tall) facility.setBox(boxOf(facility, to));
		int before = facility.level();
		facility.finishUpgrade(tall);
		market.clearDonations(facility.id());
		if (facility.kind() == FacilityKind.INN) {
			// more villagers come to a bigger inn: those its new strength adds
			int base = Development.config().innVillagers;
			int more = FacilityLevelMath.count(base, FacilityEffects.strength(FacilityKind.INN, facility.level(), facility.branch(), 0))
					- FacilityLevelMath.count(base, FacilityEffects.strength(FacilityKind.INN, before, facility.branch(), 0));
			BoundingBox box = facility.box();
			if (more > 0) spawnVillagers(level, new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2), more);
		}
		if (facility.kind() == FacilityKind.QUEST_HALL) market.setLastRequestRefresh(Long.MIN_VALUE); // new slots fill at once
		TradeExpansion.LOGGER.info("Facility {} of market #{} raised to level {}{} ({} blocks changed)", facility.id(), market.id(), facility.level(),
				tall ? " with a second storey" : "", changed.size());
		changed(level, market, Component.translatable("message.ak_trade_expansion.facility.upgraded", MarketNames.name(market),
				name(facility.kind()), facility.level()).withStyle(ChatFormatting.GREEN));
		TradeAdvancements.onFacilityCompleted(server, market, MarketClock.now(level)); // R
	}

	/**
	 * Columns of the box lower than the floor get one block of the floor's own block below them; dirt under a pond (water
	 * or lava, which would run out) and under a dirt path (which turns to dirt under a block anyway).
	 */
	private static void foundation(ServerLevel level, BoundingBox box, boolean waterside) {
		int floorY = box.minY();
		net.minecraft.world.level.block.Block deck = waterside ? deck(level, box) : null;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos below = new BlockPos(x, floorY - 1, z);
				BlockState floor = level.getBlockState(new BlockPos(x, floorY, z));
				if (waterside && !floor.isAir() && SiteFinder.overWater(level, below)) {
					// v0.51 harbor: posts of the pier go down to the bottom; the deck stays above the water. The posts are
					// the style's pillar (a log, cut sandstone, a purpur pillar): whatever is not the deck.
					if (!floor.is(deck)) {
						BlockPos.MutableBlockPos p = below.mutable();
						for (int i = 0; i < 24 && p.getY() > level.getMinY() && SiteFinder.waterOrReplaceable(level.getBlockState(p)); i++, p.move(0, -1, 0)) {
							level.setBlockAndUpdate(p, floor);
						}
					}
					continue;
				}
				if (floor.isAir() || !SiteFinder.replaceable(level.getBlockState(below), false)) continue;
				boolean dirt = !floor.getFluidState().isEmpty() || floor.is(Blocks.DIRT_PATH);
				level.setBlockAndUpdate(below, dirt ? Blocks.DIRT.defaultBlockState() : floor);
			}
		}
	}

	/** The deck of a waterside building: the most common block of its floor over the water. */
	private static net.minecraft.world.level.block.Block deck(ServerLevel level, BoundingBox box) {
		java.util.Map<net.minecraft.world.level.block.Block, Integer> counts = new java.util.HashMap<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockState floor = level.getBlockState(new BlockPos(x, box.minY(), z));
				if (!floor.isAir() && SiteFinder.overWater(level, new BlockPos(x, box.minY() - 1, z))) counts.merge(floor.getBlock(), 1, Integer::sum);
			}
		}
		return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(Blocks.AIR);
	}

	/** N-4: villagers come to a completed inn (once per market, not when it is rebuilt). */
	private static void callVillagers(ServerLevel level, Facility facility) {
		BoundingBox box = facility.box();
		spawnVillagers(level, new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2), Development.config().innVillagers);
	}

	/** Spawns {@code count} plain villagers on a free spot near {@code center} (N-4 inn, K-2 fulfilled shortages). */
	public static void spawnVillagers(ServerLevel level, BlockPos center, int count) {
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
		registry.invalidateRoutes(market.dimension()); // v0.79: a station changes the routes
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

	/** N-5: a stake registers with the nearest market whose site radius (Development.siteRadius) reaches it. */
	public static void onMarkerPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Market market = Development.siteMarket(level, pos);
		if (market == null || !Development.enabled(market)) return; // the right-click on the stake says why
		if (!market.markers().contains(pos)) market.markers().add(pos.immutable());
		MarketRegistry.get(level.getServer()).setDirty();
		// Where each building would stand: only the outlines, the chat comes with a right-click on the stake.
		if (player != null) MarkerPreview.show(player, level, pos, false);
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
