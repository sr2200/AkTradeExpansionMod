package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.config.ConfigScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client game test (runClientGameTest, entrypoint "fabric-client-gametest"): a real client window, in Japanese
 * (screens are checked as Japanese players see them). Fabric stops at the first failing test, so this one test runs
 * every case itself, records each result (build/autotest/client-results.json) and fails at the end if any case failed.
 * Cases that need a world share one singleplayer world with a small village and a stall placed with the use key.
 * Screenshots go to build/autotest/screenshots for a person to look at.
 * System property {@code ak.autotest.only}: as for the server cases.
 */
public class ClientChecklistTests implements FabricClientGameTest {
	/** The language the screens are checked in; English is checked once more for the layout only. */
	static final String LANGUAGE = "ja_jp";

	@FunctionalInterface
	interface Body {
		void run(Session session, CaseResult.Builder result) throws Exception;
	}

	/**
	 * One case. {@code alsoChecklist} / {@code alsoPartial}: items the case covers only partly while it covers
	 * {@code checklist} fully (recorded as a second result with the same outcome).
	 */
	record ClientCase(String name, List<String> checklist, String partial, boolean world, Body body, List<String> alsoChecklist, String alsoPartial) {
		ClientCase(String name, List<String> checklist, String partial, boolean world, Body body) {
			this(name, checklist, partial, world, body, List.of(), null);
		}
	}

	/** The test context and, while world cases run, the shared world and the stall in it. */
	static final class Session {
		final ClientGameTestContext context;
		TestSingleplayerContext world;
		BlockPos stall;

		Session(ClientGameTestContext context) {
			this.context = context;
		}
	}

	private static List<ClientCase> cases() {
		List<ClientCase> cases = new ArrayList<>();
		cases.add(new ClientCase("startup_with_modmenu_yacl", List.of("1-2"), "Mod Menu・YACL ありで起動してタイトル画面が出ることのみ（Mod 一覧のアイコンは見ていない）",
				false, ClientChecklistTests::startup));
		cases.add(new ClientCase("config_screen_tabs", List.of("1-3"), "設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認）",
				false, ClientChecklistTests::configScreen));
		cases.add(new ClientCase("stall_place_and_open", List.of("2-8"), null, true, ClientChecklistTests::stallScreen));
		cases.add(new ClientCase("buy_with_screen_buttons", List.of("3-10"), null, true, ClientChecklistTests::buyWithButtons,
				List.of("2-9", "2-10"), "交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない）"));
		cases.add(new ClientCase("layout_market_screen_ja", List.of("3-6", "19-1", "19-2", "19-3", "22-1", "26-1"),
				"交易画面の各タブの文字が重ならず、画面と枠からはみ出さないこと（日本語、854×480 と 1920×1080、GUIの大きさ 1〜4）。一覧の行数・スクロールバー・「まとめて売る」の枠は見ていない",
				true, (s, r) -> layout(s, r, LANGUAGE)));
		cases.add(new ClientCase("layout_market_screen_en", List.of("26-1"), "英語でも交易画面の各タブの文字が重ならず、はみ出さないこと",
				true, (s, r) -> layout(s, r, "en_us")));
		cases.add(new ClientCase("clock_advance_updates_open_screen", List.of("13-10"),
				"開いたままの交易画面に新しい時計が届き、依頼の残り時間の表示がすぐ減ること（建設・雇い人の残り時間は見ていない）", true, ClientChecklistTests::clockAdvance));
		return cases;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		setLanguage(context, LANGUAGE);
		Session session = new Session(context);
		List<CaseResult> results = new ArrayList<>();
		for (ClientCase c : filter(cases())) {
			CaseResult.Builder result = new CaseResult.Builder(c.name(), c.checklist(), c.partial());
			CaseResult.Builder also = new CaseResult.Builder(c.name(), c.alsoChecklist(), c.alsoPartial());
			try {
				if (c.world() && session.world == null) openWorld(session);
				if (!c.world() && session.world != null) closeWorld(session);
				c.body().run(session, result);
				results.add(result.pass());
				if (!c.alsoChecklist().isEmpty()) results.add(also.pass());
			} catch (Throwable t) {
				AutotestReport.LOGGER.error("Case {} failed", c.name(), t);
				results.add(result.fail(t));
				if (!c.alsoChecklist().isEmpty()) results.add(also.fail(t));
			}
			if (session.world != null) {
				context.setScreen(() -> null);
			} else {
				backToTitle(context);
			}
		}
		if (session.world != null) closeWorld(session);
		backToTitle(context);
		AutotestReport.write("client", results);
		long failed = results.stream().filter(r -> !r.ok()).count();
		if (failed > 0) throw new AssertionError(failed + " of " + results.size() + " client checklist results failed (see build/autotest/client-results.json)");
	}

	private static List<ClientCase> filter(List<ClientCase> cases) {
		String only = System.getProperty("ak.autotest.only");
		if (only == null || only.isBlank()) return cases;
		List<String> words = List.of(only.split("[,+]"));
		return cases.stream().filter(c -> words.stream().map(String::trim).filter(w -> !w.isEmpty())
				.anyMatch(w -> c.name().contains(w) || c.checklist().contains(w) || c.alsoChecklist().contains(w))).toList();
	}

	/** Fabric requires every test to end on the title screen with no world open. */
	private static void backToTitle(ClientGameTestContext context) {
		boolean inWorld = context.computeOnClient(client -> client.level != null);
		if (!inWorld && !(context.computeOnClient(client -> client.gui.screen()) instanceof TitleScreen)) {
			context.setScreen(() -> new TitleScreen());
		}
	}

	static void setLanguage(ClientGameTestContext context, String code) {
		if (code.equals(context.computeOnClient(client -> client.getLanguageManager().getSelected()))) return;
		var reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(code);
			client.options.languageCode = code;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone(), 2400);
		// The loading overlay fades out after the reload and takes the input until it is gone.
		context.waitFor(client -> client.gui.overlay() == null, 2400);
		context.waitTicks(5);
	}

	// ---- the shared world

	private static void openWorld(Session s) {
		ClientGameTestContext context = s.context;
		s.world = context.worldBuilder().create();
		s.world.getConnection().waitForChunksRender();
		s.world.getServer().runCommand("gamemode survival @a");
		s.world.getServer().runOnServer(server -> {
			// Every market has a bank tab (bank.minMarketLevel 1), so the layout check sees it too.
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			config.bank.minMarketLevel = 1;
			ConfigManager.apply(config);
		});
		// A small village in front of the player (C-1: 3 beds and 3 villagers) and a crafted stall in the hand.
		s.stall = s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			BlockPos feet = player.blockPosition();
			for (int i = 0; i < 3; i++) bed(level, feet.offset(-6 + 3 * i, 0, 5));
			for (int i = 0; i < 3; i++) EntityTypes.VILLAGER.spawn(level, feet.offset(-6 + 3 * i, 0, 9), EntitySpawnReason.COMMAND);
			player.getInventory().setSelectedSlot(8);
			player.getInventory().setItem(8, new ItemStack(ModBlocks.MARKET_STALL.asItem()));
			return feet.north(2);
		});
		context.waitTicks(5);
	}

	private static void closeWorld(Session s) {
		s.context.setScreen(() -> null);
		s.world.close();
		s.world = null;
		s.stall = null;
	}

	/** Opens the stall's trade screen with the use key. */
	private static MarketScreen openStall(Session s) {
		ClientGameTestContext context = s.context;
		if (context.computeOnClient(client -> client.gui.screen()) instanceof MarketScreen screen) return screen;
		context.setScreen(() -> null);
		context.getInput().lookAt(s.stall);
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(MarketScreen.class);
		context.waitTicks(10);
		return (MarketScreen) context.computeOnClient(client -> client.gui.screen());
	}

	// ---- cases

	private static void startup(Session s, CaseResult.Builder result) {
		check(FabricLoader.getInstance().isModLoaded("modmenu"), "Mod Menu is not loaded");
		check(FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3"), "YACL is not loaded");
		s.context.waitForScreen(TitleScreen.class);
		screenshot(s.context, result, "1-2_title");
	}

	private static final List<String> CONFIG_TABS = List.of("currency", "price", "goods", "recovery", "distance", "market", "wallet",
			"bank", "trade", "other", "village", "reputation", "development", "staff", "client");

	private static void configScreen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> ConfigScreen.create(new TitleScreen()));
		context.waitTicks(5);
		Screen screen = context.computeOnClient(client -> client.gui.screen());
		check(screen != null && !(screen instanceof TitleScreen), "the config screen did not open (%s)", screen);
		// YACL's tabs are a TabNavigationBar, not buttons: select each tab through it.
		List<String> titles = context.computeOnClient(client -> tabBar(client.gui.screen()).getTabs().stream().map(t -> t.getTabTitle().getString()).toList());
		List<String> expected = CONFIG_TABS.stream().map(t -> Component.translatable("config.ak_trade_expansion.category." + t).getString()).toList();
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

	private static void stallScreen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		BlockPos target = s.stall;
		// Place it with the use key, aiming at the ground in front.
		context.getInput().lookAt(target.below());
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(5);
		boolean placed = s.world.getServer().computeOnServer(server -> server.overworld().getBlockState(target).is(ModBlocks.MARKET_STALL));
		check(placed, "the stall was not placed with the use key at %s", target);
		String name = s.world.getServer().computeOnServer(server -> {
			Market market = MarketRegistry.get(server).nearestWithin(server.overworld().dimension(), target, 64);
			return market == null ? null : MarketNames.name(market).getString();
		});
		check(name != null, "placing the stall founded no market");
		// Open it with the use key.
		openStall(s);
		String title = context.computeOnClient(client -> client.gui.screen().getTitle().getString());
		check(title.contains(name), "the screen title \"%s\" does not show the market name \"%s\"", title, name);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		screenshot(context, result, "2-8_trade_screen");
	}

	/**
	 * 2-9 / 2-10 / 3-10 with the screen's own buttons: the quantity buttons change the total, 16 and 64 show the bulk
	 * discount, and "buy" takes exactly the shown total (the server refuses any other total: trade.priceTolerance 0).
	 */
	private static void buyWithButtons(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> WalletService.give(server.getPlayerList().getPlayers().getFirst(), 1_000_000));
		context.waitTicks(3);
		MarketScreen screen = openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		// The most expensive good with stock for 1 + 16 + 64 (cheap goods hide the bulk discount in rounding).
		MarketSnapshotS2C snapshot = context.computeOnClient(client -> (MarketSnapshotS2C) get(screen, "snapshot"));
		Identifier good = snapshot.goods().stream()
				.filter(v -> v.buy() && v.unit() == 1 && v.stock() >= 82 && v.patch().isEmpty())
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with stock for 81")).goods();
		context.runOnClient(client -> {
			set(screen, "selected", good);
			call(screen, "updateButtons");
		});
		String bulkLabel = Language.getInstance().getOrDefault("screen.ak_trade_expansion.bulk_discount").split("%s")[0];
		long previous = -1;
		for (int qty : new int[] {1, 16, 64}) {
			switch (qty) {
				case 1 -> clickWidget(context, "1");
				case 16 -> {
					clickWidget(context, "8");
					for (int i = 0; i < 8; i++) clickWidget(context, "+");
				}
				default -> clickWidget(context, "64");
			}
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			int shownQty = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
			check(shownQty == qty, "the quantity buttons set %d instead of %d", shownQty, qty);
			long shown = context.computeOnClient(client -> (Long) call(screen, "previewTotal", selectedView(screen, good), qty));
			check(shown > previous, "the total did not grow with the quantity (%d -> %d at %d)", previous, shown, qty);
			previous = shown;
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			boolean discountShown = texts.stream().anyMatch(t -> t.text().startsWith(bulkLabel));
			check(discountShown == (qty >= 16), "the bulk discount line is %s at %d (texts: %s)", discountShown ? "shown" : "missing", qty,
					texts.stream().map(TextRecorder.Text::text).toList());
			if (qty == 64) screenshot(context, result, "3-10_bulk_64");
			long before = serverMoney(s);
			int itemsBefore = serverCount(s, good);
			context.clickScreenButton("screen.ak_trade_expansion.confirm.buy");
			long after = before;
			for (int i = 0; i < 40 && after == before; i++) {
				context.waitTick();
				after = serverMoney(s);
			}
			check(before - after == shown, "buying %d took %dc, the screen showed %dc", qty, before - after, shown);
			int got = serverCount(s, good) - itemsBefore;
			check(got == qty, "buying %d gave %d items", qty, got);
			context.waitTicks(3); // the new snapshot
		}
	}

	/**
	 * 13-10: {@code /aktradeex clock advance} while the trade screen is open. The client gets the new clock offset and a
	 * fresh snapshot, and the time left of the selected request shrinks by the advanced time at once.
	 */
	private static void clockAdvance(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.quest");
		context.waitTicks(2);
		MarketSnapshotS2C before = context.computeOnClient(client -> (MarketSnapshotS2C) get(screen, "snapshot"));
		// The request with the most time left (a request lasts quest.durationDays, 3 days by default).
		MarketSnapshotS2C.RequestView request = before.requests().stream()
				.max(Comparator.comparingLong(r -> r.request().deadline()))
				.orElseThrow(() -> new AssertionError("the market has no request"));
		context.runOnClient(client -> {
			set(screen, "selectedRequest", request.request().id());
			call(screen, "updateButtons");
		});
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		long left0 = timeLeft(context);
		check(left0 > 15, "the request has only %d minutes left", left0);

		s.world.getServer().runCommand("aktradeex clock advance 0.5"); // = 10 minutes of the time left
		MarketSnapshotS2C after = before;
		for (int i = 0; i < 40 && after == before; i++) {
			context.waitTick();
			after = context.computeOnClient(client -> (MarketSnapshotS2C) get(screen, "snapshot"));
		}
		check(after != before, "no new snapshot after \"clock advance\"");
		check(after.gameTime() - before.gameTime() >= PriceModel.TICKS_PER_DAY / 2, "the snapshot's market time moved by %d ticks",
				after.gameTime() - before.gameTime());
		long serverOffset = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).clockOffset());
		long clientOffset = context.computeOnClient(client -> MarketClock.offset(client.level));
		check(clientOffset == serverOffset, "the client's clock offset is %d, the server's %d", clientOffset, serverOffset);
		context.waitTicks(2);
		long left1 = timeLeft(context);
		check(left0 - left1 >= 9 && left0 - left1 <= 11, "the time left went %d -> %d minutes (expected 10 less)", left0, left1);
		screenshot(context, result, "13-10_quest_after_clock_advance");
	}

	/** Minutes of "残り約%s分" on the screen. */
	private static long timeLeft(ClientGameTestContext context) {
		String[] parts = Language.getInstance().getOrDefault("screen.ak_trade_expansion.quest.time_left").split("%s", -1);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		for (TextRecorder.Text t : texts) {
			String text = t.text();
			if (text.startsWith(parts[0]) && text.endsWith(parts[1])) {
				String number = text.substring(parts[0].length(), text.length() - parts[1].length()).replace(",", "").trim();
				if (number.matches("\\d+")) return Long.parseLong(number);
			}
		}
		throw new AssertionError("no time left on the screen (texts: " + texts.stream().map(TextRecorder.Text::text).toList() + ")");
	}

	private static MarketSnapshotS2C.GoodView selectedView(MarketScreen screen, Identifier good) {
		MarketSnapshotS2C snapshot = (MarketSnapshotS2C) get(screen, "snapshot");
		return snapshot.goods().stream().filter(v -> v.goods().equals(good)).findFirst().orElseThrow();
	}

	private static long serverMoney(Session s) {
		return s.world.getServer().computeOnServer(server -> WalletService.balance(server.getPlayerList().getPlayers().getFirst()));
	}

	private static int serverCount(Session s, Identifier good) {
		return s.world.getServer().computeOnServer(server -> {
			Item item = BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(good).item());
			int n = 0;
			for (ItemStack stack : server.getPlayerList().getPlayers().getFirst().getInventory()) if (stack.is(item)) n += stack.getCount();
			return n;
		});
	}

	/** Window sizes and GUI scales (0 = auto, the largest that fits) the layout is checked at. */
	private static final int[][] SIZES = {{854, 480, 0}, {854, 480, 1}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 0}};

	/** Every tab of the trade screen, at every size: no texts on top of each other, none outside the screen or panel. */
	private static void layout(Session s, CaseResult.Builder result, String language) {
		ClientGameTestContext context = s.context;
		setLanguage(context, language);
		openStall(s);
		List<String> problems = new ArrayList<>();
		try {
			for (int[] size : SIZES) {
				context.getInput().resizeWindow(size[0], size[1]);
				context.runOnClient(client -> {
					client.options.guiScale().set(size[2]);
					client.resizeGui();
				});
				context.waitTicks(3);
				List<String> tabs = context.computeOnClient(client -> visibleTabs(client.gui.screen()));
				check(!tabs.isEmpty(), "no tab buttons on the trade screen");
				for (String tab : tabs) {
					context.clickScreenButton(tab);
					context.getInput().setCursorPos(0, 0);
					// Toasts (advancements) slide in from outside the screen: not part of the screen's layout.
					context.runOnClient(client -> client.gui.toastManager().clear());
					context.waitTicks(4);
					String where = String.format("%s %dx%d GUI %s %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2],
							tab.substring(tab.lastIndexOf('.') + 1));
					List<TextRecorder.Text> texts = TextRecorder.capture(context);
					List<String> found = context.computeOnClient(client -> LayoutCheck.check(where, texts,
							client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), panel(client.gui.screen())));
					// Japanese at the first size: every tab, for a person to look at; otherwise the first problem of a size.
					boolean everyTab = language.equals(LANGUAGE) && size == SIZES[0];
					if (everyTab || !found.isEmpty() && problems.stream().noneMatch(p -> p.startsWith(where.substring(0, where.lastIndexOf(' '))))) {
						screenshot(context, result, "layout_" + where);
					}
					problems.addAll(found);
				}
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
			});
			setLanguage(context, LANGUAGE);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	private static List<String> visibleTabs(Screen screen) {
		List<String> keys = new ArrayList<>();
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().startsWith("screen.ak_trade_expansion.tab.")) {
				keys.add(t.getKey());
			}
		}
		return keys;
	}

	private static LayoutCheck.Panel panel(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> c)) return null;
		int left = (Integer) get(c, AbstractContainerScreen.class, "leftPos");
		int top = (Integer) get(c, AbstractContainerScreen.class, "topPos");
		int w = (Integer) get(c, AbstractContainerScreen.class, "imageWidth");
		int h = (Integer) get(c, AbstractContainerScreen.class, "imageHeight");
		return new LayoutCheck.Panel(left, top, left + w, top + h);
	}

	// ---- helpers

	/** Clicks the visible widget showing exactly {@code text} with the mouse, as a player does. */
	private static void clickWidget(ClientGameTestContext context, String text) {
		double[] at = context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.visible && w.active && w.getMessage().getString().equals(text)) {
					// GUI pixels -> window coordinates (MouseHandler.getScaledXPos the other way round).
					double sx = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
					double sy = (double) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
					return new double[] {(w.getX() + w.getWidth() / 2.0) * sx, (w.getY() + w.getHeight() / 2.0) * sy};
				}
			}
			return null;
		});
		check(at != null, "no button \"%s\" on the screen", text);
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(1); // the move is applied with the next frame
		// 26.3 reads the mouse through SDL: the left button is 1, not GLFW's 0 (0 is ignored by the widgets).
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
	}

	private static void bed(ServerLevel level, BlockPos head) {
		BlockState state = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		level.setBlock(head, state, 3);
		level.setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 3);
	}

	private static Object get(Object target, String field) {
		return get(target, target.getClass(), field);
	}

	private static Object get(Object target, Class<?> owner, String field) {
		try {
			Field f = owner.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read " + owner.getSimpleName() + "." + field, e);
		}
	}

	private static void set(Object target, String field, Object value) {
		try {
			Field f = target.getClass().getDeclaredField(field);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot set " + target.getClass().getSimpleName() + "." + field, e);
		}
	}

	private static Object call(Object target, String method, Object... args) {
		for (Method m : target.getClass().getDeclaredMethods()) {
			if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
			try {
				m.setAccessible(true);
				return m.invoke(target, args);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot call " + target.getClass().getSimpleName() + "." + method, e);
			}
		}
		throw new AssertionError("no method " + target.getClass().getSimpleName() + "." + method + " with " + args.length + " arguments");
	}

	private static void check(boolean condition, String message, Object... args) {
		if (!condition) throw new AssertionError(args.length == 0 ? message : String.format(message, args));
	}

	/** Takes a screenshot and copies it to build/autotest/screenshots/&lt;name&gt;.png. */
	static void screenshot(ClientGameTestContext context, CaseResult.Builder result, String name) {
		String file = name.replaceAll("[^A-Za-z0-9_.-]", "_");
		// Advancement toasts would cover the right side of the screen.
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(1);
		Path taken = context.takeScreenshot(file);
		Path target = AutotestReport.directory().resolve("screenshots").resolve(file + ".png");
		try {
			Files.createDirectories(target.getParent());
			Files.copy(taken, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("Could not copy the screenshot " + taken, e);
		}
		result.screenshot("screenshots/" + file + ".png");
	}
}
