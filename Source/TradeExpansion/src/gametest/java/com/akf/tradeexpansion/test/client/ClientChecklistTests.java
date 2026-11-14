package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.config.ConfigScreen;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Client game test (runClientGameTest, entrypoint "fabric-client-gametest"): a real client window. Fabric stops at the
 * first failing test, so this one test runs every case itself, records each result (build/autotest/client-results.json)
 * and fails at the end if any case failed. Screenshots go to build/autotest/screenshots for a person to look at.
 * System property {@code ak.autotest.only}: as for the server cases.
 */
public class ClientChecklistTests implements FabricClientGameTest {
	@FunctionalInterface
	interface Body {
		void run(ClientGameTestContext context, CaseResult.Builder result) throws Exception;
	}

	record ClientCase(String name, List<String> checklist, String partial, Body body) {
	}

	private static List<ClientCase> cases() {
		List<ClientCase> cases = new ArrayList<>();
		cases.add(new ClientCase("startup_with_modmenu_yacl", List.of("1-2"), "Mod Menu・YACL ありで起動してタイトル画面が出ることのみ（Mod 一覧のアイコンは見ていない）",
				ClientChecklistTests::startup));
		cases.add(new ClientCase("config_screen_tabs", List.of("1-3"), "設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認）",
				ClientChecklistTests::configScreen));
		cases.add(new ClientCase("stall_place_and_open", List.of("2-8"), null, ClientChecklistTests::stallScreen));
		return cases;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		List<CaseResult> results = new ArrayList<>();
		for (ClientCase c : filter(cases())) {
			CaseResult.Builder result = new CaseResult.Builder(c.name(), c.checklist(), c.partial());
			try {
				c.body().run(context, result);
				results.add(result.pass());
			} catch (Throwable t) {
				AutotestReport.LOGGER.error("Case {} failed", c.name(), t);
				results.add(result.fail(t));
			}
			backToTitle(context);
		}
		AutotestReport.write("client", results);
		long failed = results.stream().filter(r -> !r.ok()).count();
		if (failed > 0) throw new AssertionError(failed + " of " + results.size() + " client checklist cases failed (see build/autotest/client-results.json)");
	}

	private static List<ClientCase> filter(List<ClientCase> cases) {
		String only = System.getProperty("ak.autotest.only");
		if (only == null || only.isBlank()) return cases;
		List<String> words = List.of(only.split("[,+]"));
		return cases.stream().filter(c -> words.stream().map(String::trim).filter(w -> !w.isEmpty())
				.anyMatch(w -> c.name().contains(w) || c.checklist().contains(w))).toList();
	}

	/** Fabric requires every test to end on the title screen with no world open. */
	private static void backToTitle(ClientGameTestContext context) {
		boolean inWorld = context.computeOnClient(client -> client.level != null);
		if (!inWorld && !(context.computeOnClient(client -> client.gui.screen()) instanceof TitleScreen)) {
			context.setScreen(() -> new TitleScreen());
		}
	}

	// ---- cases

	private static void startup(ClientGameTestContext context, CaseResult.Builder result) {
		check(FabricLoader.getInstance().isModLoaded("modmenu"), "Mod Menu is not loaded");
		check(FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3"), "YACL is not loaded");
		context.waitForScreen(TitleScreen.class);
		screenshot(context, result, "1-2_title");
	}

	private static final List<String> CONFIG_TABS = List.of("currency", "price", "goods", "recovery", "distance", "market", "wallet",
			"bank", "trade", "other", "village", "reputation", "development", "staff", "client");

	private static void configScreen(ClientGameTestContext context, CaseResult.Builder result) {
		context.setScreen(() -> ConfigScreen.create(new TitleScreen()));
		context.waitTicks(5);
		Screen screen = context.computeOnClient(client -> client.gui.screen());
		check(screen != null && !(screen instanceof TitleScreen), "the config screen did not open (%s)", screen);
		// YACL's tabs are a TabNavigationBar, not buttons: select each tab through it.
		List<String> titles = context.computeOnClient(client -> tabBar(client.gui.screen()).getTabs().stream().map(t -> t.getTabTitle().getString()).toList());
		List<String> expected = CONFIG_TABS.stream().map(t -> net.minecraft.network.chat.Component.translatable("config.ak_trade_expansion.category." + t).getString()).toList();
		List<String> missing = expected.stream().filter(t -> !titles.contains(t)).toList();
		check(missing.isEmpty(), "config tabs not found: %s (tabs: %s)", missing, titles);
		for (int i = 0; i < titles.size(); i++) {
			int index = i;
			context.runOnClient(client -> tabBar(client.gui.screen()).selectTab(index, false));
			context.waitTicks(3);
			screenshot(context, result, String.format("1-3_config_%02d", i + 1));
		}
	}

	private static net.minecraft.client.gui.components.tabs.TabNavigationBar tabBar(Screen screen) {
		return screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
				.map(c -> (net.minecraft.client.gui.components.tabs.TabNavigationBar) c).findFirst()
				.orElseThrow(() -> new AssertionError("the config screen has no tab bar"));
	}

	private static void stallScreen(ClientGameTestContext context, CaseResult.Builder result) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			// A small village in front of the player (C-1: 3 beds and 3 villagers) and a crafted stall in the hand.
			BlockPos target = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				BlockPos stall = feet.north(2);
				for (int i = 0; i < 3; i++) bed(level, feet.offset(-6 + 3 * i, 0, 5));
				for (int i = 0; i < 3; i++) EntityTypes.VILLAGER.spawn(level, feet.offset(-6 + 3 * i, 0, 9), EntitySpawnReason.COMMAND);
				player.getInventory().setSelectedSlot(8);
				player.getInventory().setItem(8, new ItemStack(ModBlocks.MARKET_STALL.asItem()));
				return stall;
			});
			context.waitTicks(5);
			// Place it with the use key, aiming at the ground in front.
			context.getInput().lookAt(target.below());
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			boolean placed = world.getServer().computeOnServer(server -> server.overworld().getBlockState(target).is(ModBlocks.MARKET_STALL));
			check(placed, "the stall was not placed with the use key at %s", target);
			String name = world.getServer().computeOnServer(server -> {
				Market market = MarketRegistry.get(server).nearestWithin(server.overworld().dimension(), target, 64);
				return market == null ? null : MarketNames.name(market).getString();
			});
			check(name != null, "placing the stall founded no market");
			// Open it with the use key.
			context.getInput().lookAt(target);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MarketScreen.class);
			context.waitTicks(10);
			String title = context.computeOnClient(client -> client.gui.screen().getTitle().getString());
			check(title.contains(name), "the screen title \"%s\" does not show the market name \"%s\"", title, name);
			screenshot(context, result, "2-8_trade_screen");
			context.setScreen(() -> null);
		}
	}

	// ---- helpers

	private static void bed(ServerLevel level, BlockPos head) {
		BlockState state = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		level.setBlock(head, state, 3);
		level.setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 3);
	}

	private static void check(boolean condition, String message, Object... args) {
		if (!condition) throw new AssertionError(args.length == 0 ? message : String.format(message, args));
	}

	/** Takes a screenshot and copies it to build/autotest/screenshots/&lt;name&gt;.png. */
	private static void screenshot(ClientGameTestContext context, CaseResult.Builder result, String name) {
		Path taken = context.takeScreenshot(name);
		Path target = AutotestReport.directory().resolve("screenshots").resolve(name + ".png");
		try {
			Files.createDirectories(target.getParent());
			Files.copy(taken, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy the screenshot " + taken, e);
		}
		result.screenshot("screenshots/" + name + ".png");
	}
}
