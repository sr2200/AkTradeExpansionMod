package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.SiteFinder;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketAccess;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.test.AutotestReport;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Screenshots for the download page (docs/release/post-draft.md), taken by release.bat through shots.bat
 * (runClientGameTestShots, system property {@code ak.autotest.shots}). A normal world with a fixed seed: the village
 * with a generated stall (or, when none of the nearest villages has one, a stall building put in the first village),
 * two buildings of the village development put up at once, a few markets further away with guild news, and a price
 * history for the graph. The images (1920x1080) go to {@code ak.shots.dir}.
 */
final class PostScreenshots {
	/** World seed: the first villages around spawn are searched for a generated stall. */
	private static final String SEED = "20261114";
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;
	/** Facilities to put up, in the order tried; the first two that find a site are built. */
	private static final List<String> FACILITIES = List.of("fountain", "clock_tower", "park", "festival", "watchtower", "tavern");

	private PostScreenshots() {
	}

	private record Village(BlockPos stall, Direction facing, long market) {
	}

	static void run(ClientGameTestContext context) {
		Path dir = Path.of(System.getProperty("ak.shots.dir", AutotestReport.directory().resolve("post-screenshots").toString()));
		List<String> failed = new ArrayList<>();
		// Before the world: the integrated server takes its view distance from here when it starts.
		context.runOnClient(client -> {
			client.options.renderDistance().set(8);
			client.options.bobView().set(false);
		});
		TestSingleplayerContext world = context.worldBuilder().adjustSettings(PostScreenshots::normalWorld).create();
		try {
			context.getInput().resizeWindow(WIDTH, HEIGHT);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
				// No hotbar or chat in the pictures (screens are still drawn).
				if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			Village village = world.getServer().computeOnServer(PostScreenshots::prepareVillage);
			AutotestReport.LOGGER.info("SHOTS village stall at {} facing {}, market #{}", village.stall(), village.facing(), village.market());

			shot(failed, "stall", () -> stallShot(context, world, village, dir));
			List<BoundingBox> buildings = new ArrayList<>();
			shot(failed, "facility", () -> {
				buildings.addAll(world.getServer().computeOnServer(server -> buildFacilities(server, village)));
				facilityShot(context, world, village, buildings, dir);
			});
			shot(failed, "trade", () -> tradeShot(context, world, village, dir));
			shot(failed, "board", () -> boardShot(context, world, village, dir));
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
			world.close();
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.options.renderDistance().set(5);
				client.resizeGui();
			});
		}
		check(failed.isEmpty(), "screenshots not taken: %s (see the log)", failed);
	}

	private interface Shot {
		void take() throws Exception;
	}

	private static void shot(List<String> failed, String name, Shot shot) {
		try {
			shot.take();
		} catch (Throwable t) {
			AutotestReport.LOGGER.error("Screenshot {} failed", name, t);
			failed.add(name);
		}
	}

	private static void normalWorld(WorldCreationUiState creator) {
		var preset = creator.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
		creator.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
		creator.setSeed(SEED);
		creator.setGenerateStructures(true);
	}

	// ---- the world

	/** Finds a village with a generated stall (or puts a stall building in the first one), its market and other markets. */
	private static Village prepareVillage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		var villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureTags.VILLAGE);
		BlockPos spawn = level.getRespawnData().pos();
		List<BlockPos> found = new ArrayList<>();
		BlockPos stall = null;
		// The nearest village from a few points around spawn: the first with a stall wins.
		int[][] from = {{0, 0}, {800, 0}, {0, 800}, {-800, 0}, {0, -800}, {800, 800}, {-800, -800}};
		for (int[] f : from) {
			var near = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, spawn.offset(f[0], 0, f[1]), 60, false);
			if (near == null || found.contains(near.getFirst())) continue;
			BlockPos center = near.getFirst();
			found.add(center);
			stall = findStall(level, center);
			if (stall != null) break;
		}
		check(!found.isEmpty(), "no village found around spawn");
		if (stall == null) stall = putStallBuilding(level, found.getFirst());
		MarketStallBlockEntity entity = (MarketStallBlockEntity) level.getBlockEntity(stall);
		Market market = entity.resolveMarket(level).market();
		check(market != null, "the stall at %s has no market", stall);
		entity.ensureTrader(level, market.id());
		Direction facing = level.getBlockState(stall).getValue(HorizontalDirectionalBlock.FACING);
		otherMarkets(level, market);
		priceHistory(level, market);
		ServerPlayer player = player(server);
		WalletService.give(player, 12_480);
		return new Village(stall.immutable(), facing, market.id());
	}

	/** A stall of a generated village building within 96 blocks of the village center (chunks generated here). */
	private static BlockPos findStall(ServerLevel level, BlockPos center) {
		BlockPos best = null;
		for (int cx = (center.getX() >> 4) - 6; cx <= (center.getX() >> 4) + 6; cx++) {
			for (int cz = (center.getZ() >> 4) - 6; cz <= (center.getZ() >> 4) + 6; cz++) {
				for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof MarketStallBlockEntity && (best == null || be.getBlockPos().distSqr(center) < best.distSqr(center))) {
						best = be.getBlockPos();
					}
				}
			}
		}
		return best;
	}

	/** Puts the plains stall building (with its trader) on the ground next to the village center. */
	private static BlockPos putStallBuilding(ServerLevel level, BlockPos center) {
		StructureTemplate template = level.getServer().getStructureTemplateManager()
				.get(Identifier.fromNamespaceAndPath("ak_trade_expansion", "village/plains/market_stall")).orElseThrow();
		BlockPos ground = ground(level.getServer(), center.getX() + 12, center.getZ());
		var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
		template.placeInWorld(level, ground.below(), ground.below(), settings, level.getRandom(), 3);
		for (BlockPos p : BlockPos.betweenClosed(ground.below(), ground.offset(template.getSize().getX(), template.getSize().getY(), template.getSize().getZ()))) {
			if (level.getBlockState(p).is(ModBlocks.MARKET_STALL)) return p.immutable();
		}
		throw new AssertionError("the stall building put at " + ground + " has no stall");
	}

	/** Three markets 200–300 blocks away (stalls on the ground) and guild news from two of them. */
	private static void otherMarkets(ServerLevel level, Market home) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		int[][] offsets = {{230, 70}, {-160, 230}, {110, -280}};
		List<Market> others = new ArrayList<>();
		for (int[] o : offsets) {
			BlockPos pos = ground(level.getServer(), home.center().getX() + o[0], home.center().getZ() + o[1]);
			level.setBlockAndUpdate(pos, ModBlocks.MARKET_STALL.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
				Market m = stall.resolveMarket(level).market();
				if (m != null && m != home) others.add(m);
			}
		}
		long now = MarketClock.now(level);
		for (Market m : others.subList(0, Math.min(2, others.size()))) {
			MarketUpkeep.touch(level, m);
			RivalService.startNow(level, registry, m, MarketUpkeep.touch(level, m));
		}
		// Some trading in the other markets, so the board shows deals.
		Random random = new Random(7);
		for (Market m : others) {
			m.goods().forEach((id, state) -> {
				var params = m.params(id);
				if (params != null) state.setPressure(params.clampPressure(state.pressure() + (random.nextDouble() - 0.5) * 40));
			});
			m.refresh(now);
		}
		registry.setDirty();
	}

	/** Ten days of price history (a random walk) for every good of the market: the graph has something to show. */
	private static void priceHistory(ServerLevel level, Market market) {
		long now = MarketUpkeep.touch(level, market);
		Random random = new Random(11);
		for (var entry : market.goods().entrySet()) {
			GoodState state = entry.getValue();
			var params = market.params(entry.getKey());
			if (params == null) continue;
			List<Double> history = state.history();
			history.clear();
			double x = (random.nextDouble() - 0.5) * 20;
			for (int day = 0; day < 10; day++) {
				x = params.clampPressure(Mth.clamp(x + (random.nextDouble() - 0.5) * 18, -40, 40));
				history.add(x);
			}
			state.setPressure(x);
		}
		market.refresh(now);
		MarketRegistry.get(level.getServer()).setDirty();
	}

	/** Puts up two facilities of {@link #FACILITIES} at once (as finished buildings). @return their boxes */
	private static List<BoundingBox> buildFacilities(MinecraftServer server, Village village) {
		ServerLevel level = server.overworld();
		Market market = MarketRegistry.get(server).get(village.market());
		long now = MarketClock.now(level);
		List<BoundingBox> boxes = new ArrayList<>();
		for (String name : FACILITIES) {
			if (boxes.size() >= 2) break;
			Identifier id = Identifier.fromNamespaceAndPath("ak_trade_expansion", name);
			FacilityDefinition def = FacilityCatalog.get(id);
			if (def == null) continue;
			String style = FacilityBuilder.style(level, market.center());
			StructureTemplate template = FacilityBuilder.template(server, def, style).orElse(null);
			if (template == null) continue;
			SiteFinder.Site site = SiteFinder.find(level, market, template, def.landRows()).site();
			if (site == null) {
				AutotestReport.LOGGER.info("SHOTS no site for {}", id);
				continue;
			}
			Facility facility = new Facility(id, def.kind());
			facility.start(site.origin(), site.rotation(), site.front(), site.box(), style, now, now);
			market.facilities().put(id, facility);
			FacilityBuilder.completeNow(level, market);
			boxes.add(site.box());
		}
		MarketRegistry.get(server).setDirty();
		check(!boxes.isEmpty(), "no facility found a site around the market");
		return boxes;
	}

	// ---- the shots

	private static void stallShot(ClientGameTestContext context, TestSingleplayerContext world, Village village, Path dir) {
		BlockPos s = village.stall();
		Direction f = village.facing();
		// The trader stands in front of the stall: look at the trader with the stall behind it.
		Vec3 target = new Vec3(s.getX() + 0.5 + f.getStepX() * 0.6, s.getY() + 1.0, s.getZ() + 0.5 + f.getStepZ() * 0.6);
		double baseYaw = Math.toDegrees(Math.atan2(f.getStepZ(), f.getStepX()));
		Vec3 eye = world.getServer().computeOnServer(server -> findView(server.overworld(), target, baseYaw, new double[] {7, 8, 6, 9, 5},
				new double[] {2.0, 3.0, 1.2}));
		view(context, world, eye, target);
		save(context, dir, "stall");
	}

	private static void facilityShot(ClientGameTestContext context, TestSingleplayerContext world, Village village, List<BoundingBox> boxes, Path dir) {
		BoundingBox all = boxes.getFirst();
		for (BoundingBox b : boxes) all = BoundingBox.encapsulatingBoxes(List.of(all, b)).orElse(all);
		BoundingBox union = all;
		Vec3 target = new Vec3((union.minX() + union.maxX() + 1) / 2.0, union.minY() + union.getYSpan() * 0.35, (union.minZ() + union.maxZ() + 1) / 2.0);
		BlockPos center = world.getServer().computeOnServer(server -> MarketRegistry.get(server).get(village.market()).center());
		// From the market side (the buildings face the center), far enough to see them whole.
		double baseYaw = Math.toDegrees(Math.atan2(center.getZ() + 0.5 - target.z, center.getX() + 0.5 - target.x));
		double size = Math.max(union.getXSpan(), union.getZSpan());
		double[] distances = {size * 0.9 + 6, size * 1.1 + 8, size * 0.7 + 4, size * 1.4 + 10};
		double[] heights = {union.getYSpan() * 0.5 + 4, union.getYSpan() + 6, 3};
		Vec3 eye = world.getServer().computeOnServer(server -> findView(server.overworld(), target, baseYaw, distances, heights));
		view(context, world, eye, target);
		save(context, dir, "facility");
	}

	private static void tradeShot(ClientGameTestContext context, TestSingleplayerContext world, Village village, Path dir) {
		MarketScreen screen = openMarket(context, world, village);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(3);
		List<MarketSnapshotS2C.GoodView> goods = visibleGoods(context, screen);
		check(goods.size() >= 3, "the buy tab lists only %d goods", goods.size());
		// Hover the most expensive good without a row tooltip (the graph is then drawn right of the mouse; cheap goods
		// draw a flat line), scrolled to the third row of the list.
		int hover = 2;
		double best = -1;
		for (int i = 2; i < goods.size(); i++) {
			MarketSnapshotS2C.GoodView v = goods.get(i);
			boolean tooltip = context.computeOnClient(client -> !((List<?>) call(screen, "rowTooltip", v)).isEmpty());
			if (!tooltip && v.params().basePrice() > best) {
				best = v.params().basePrice();
				hover = i;
			}
		}
		int rows = context.computeOnClient(client -> (Integer) get(screen, "rows"));
		int scroll = Math.max(0, Math.min(hover - 2, goods.size() - rows));
		int row = hover - scroll;
		// The hovered good is also the selected one (quantity 8: the panel shows a total).
		select(context, screen, goods.get(hover).goods());
		context.runOnClient(client -> {
			set(screen, "quantity", 8);
			set(screen, "scroll", scroll);
			call(screen, "updateButtons");
		});
		int rowY = context.computeOnClient(client -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + row * constant(MarketScreen.class, "ROW_H");
		hoverAt(context, listX(context, screen) + 50, rowY + constant(MarketScreen.class, "ROW_H") / 2.0);
		context.waitTicks(5);
		save(context, dir, "trade");
		mouseAway(context);
		context.setScreen(() -> null);
	}

	private static void boardShot(ClientGameTestContext context, TestSingleplayerContext world, Village village, Path dir) {
		openMarket(context, world, village);
		context.clickScreenButton("screen.ak_trade_expansion.tab.board");
		context.waitTicks(20);
		mouseAway(context);
		save(context, dir, "board");
		context.setScreen(() -> null);
	}

	/**
	 * Moves the mouse to a point in GUI pixels and corrects it with the position the game reads back (at 1920x1080 the
	 * window coordinates need not match the GUI scale exactly).
	 */
	private static void hoverAt(ClientGameTestContext context, double guiX, double guiY) {
		moveTo(context, guiX, guiY);
		for (int i = 0; i < 3; i++) {
			double[] now = context.computeOnClient(client -> new double[] {
					client.mouseHandler.getScaledXPos(client.getWindow()), client.mouseHandler.getScaledYPos(client.getWindow()),
					client.mouseHandler.xpos(), client.mouseHandler.ypos(),
					(double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
					(double) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()});
			double dx = guiX - now[0];
			double dy = guiY - now[1];
			if (Math.abs(dx) < 1 && Math.abs(dy) < 1) return;
			context.getInput().setCursorPos(now[2] + dx * now[4], now[3] + dy * now[5]);
			context.waitTicks(1);
		}
	}

	/** Opens the market screen of the village stall, standing behind its trader. */
	private static MarketScreen openMarket(ClientGameTestContext context, TestSingleplayerContext world, Village village) {
		BlockPos s = village.stall();
		Direction f = village.facing();
		world.getServer().runCommand("gamemode survival @a");
		Vec3 at = new Vec3(s.getX() + 0.5 + f.getStepX() * 3, s.getY(), s.getZ() + 0.5 + f.getStepZ() * 3);
		float yaw = f.getOpposite().toYRot();
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 10", at.x, at.y, at.z, yaw));
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			Market market = MarketRegistry.get(server).get(village.market());
			MarketAccess.open(player, level, market, (MarketStallBlockEntity) level.getBlockEntity(s));
		});
		context.waitForScreen(MarketScreen.class);
		context.waitTicks(10);
		return screen(context);
	}

	/**
	 * The first eye position (around {@code baseYaw}, the direction from the target towards the camera, in degrees)
	 * that is in the air and sees the target without a block in between.
	 */
	private static Vec3 findView(ServerLevel level, Vec3 target, double baseYaw, double[] distances, double[] heights) {
		double[] turns = {0, 20, -20, 40, -40, 65, -65, 90, -90};
		for (double h : heights) {
			for (double d : distances) {
				for (double turn : turns) {
					double a = Math.toRadians(baseYaw + turn);
					Vec3 eye = new Vec3(target.x + Math.cos(a) * d, target.y + h, target.z + Math.sin(a) * d);
					if (clear(level, eye, target)) return eye;
				}
			}
		}
		double a = Math.toRadians(baseYaw);
		AutotestReport.LOGGER.warn("SHOTS no clear view of {}: taking it from above", target);
		return new Vec3(target.x + Math.cos(a) * distances[0], target.y + heights[0] + 6, target.z + Math.sin(a) * distances[0]);
	}

	private static boolean clear(ServerLevel level, Vec3 eye, Vec3 target) {
		BlockPos feet = BlockPos.containing(eye.x, eye.y - 1.62, eye.z);
		for (BlockPos p : List.of(feet, feet.above(), BlockPos.containing(eye))) {
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) return false;
		}
		// Stop just before the target: the target itself may be inside a block (the building).
		Vec3 end = target.add(eye.subtract(target).normalize().scale(1.5));
		var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Spectator at {@code eye} looking at {@code target}, the HUD hidden, chunks drawn. */
	private static void view(ClientGameTestContext context, TestSingleplayerContext world, Vec3 eye, Vec3 target) {
		world.getServer().runCommand("gamemode spectator @a");
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
		context.waitTicks(10);
		try {
			world.getConnection().waitForChunksRender(2400);
		} catch (AssertionError e) {
			// Far chunks still generating: take the picture with what is drawn by now.
			AutotestReport.LOGGER.warn("SHOTS chunks not all drawn: {}", e.getMessage());
		}
		context.waitTicks(40);
	}

	/** Takes a screenshot and copies it to {@code dir/<name>.png}. */
	private static void save(ClientGameTestContext context, Path dir, String name) {
		context.runOnClient(client -> {
			client.gui.toastManager().clear();
			client.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(1);
		Path taken = context.takeScreenshot("post_" + name);
		try {
			Files.createDirectories(dir);
			Files.copy(taken, dir.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy the screenshot " + taken, e);
		}
		AutotestReport.LOGGER.info("SHOTS saved {}", dir.resolve(name + ".png"));
	}
}
