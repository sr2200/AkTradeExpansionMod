package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.config.ConfigScreen;
import com.akf.tradeexpansion.client.config.GoodsManagerScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.GoodsOverride;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the settings screen (YACL) and the dedicated server (checklist chapters 1, 13 and 14). Kept apart
 * from {@link ClientChecklistTests}: the run without YACL (checklist 1-1) never loads this class.
 */
final class ClientConfigCases {
	private ClientConfigCases() {
	}

	private static final String KEY = "config.ak_trade_expansion.";
	private static final String GOODS_KEY = KEY + "goods_manager.";

	// ---- 1-3 the layout of every tab

	/** Window sizes and GUI scales (0 = auto) the settings screens are checked at. */
	private static final int[][] SIZES = {{854, 480, 0}, {1920, 1080, 0}, {1920, 1080, 2}};

	/**
	 * 1-3: the texts of every tab of the settings screen (opened from the title screen) do not overlap, leave the
	 * screen or show a translation key (Japanese and English, at {@link #SIZES}). The goods manager button is grayed
	 * out there: the screen needs the items' data, loaded only with a world (it crashed the game).
	 */
	static void layout(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		YACLScreen title = openConfig(context);
		check(!((ButtonOption) option(title, GOODS_KEY + "open")).available(), "the goods manager button can be pressed on the title screen");
		eachLanguageAndSize(context, (at, problems) -> configTabs(context, result, at, problems));
	}

	/** 1-3 (partly): the goods manager screen in its three modes, in a world, as {@link #layout}. */
	static void goodsManagerLayout(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		eachLanguageAndSize(context, (at, problems) -> goodsManagerModes(context, result, at, problems));
	}

	private static void eachLanguageAndSize(ClientGameTestContext context, java.util.function.BiConsumer<String, List<String>> check) {
		List<String> problems = new ArrayList<>();
		try {
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size);
					check.accept(String.format("%s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]), problems);
				}
			}
		} finally {
			resize(context, SIZES[0]);
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	private static void resize(ClientGameTestContext context, int[] size) {
		context.getInput().resizeWindow(size[0], size[1]);
		context.runOnClient(client -> {
			client.options.guiScale().set(size[2]);
			client.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void configTabs(ClientGameTestContext context, CaseResult.Builder result, String at, List<String> problems) {
		context.setScreen(() -> ConfigScreen.create(new TitleScreen()));
		context.waitTicks(5);
		int tabs = context.computeOnClient(client -> tabBar(client.gui.screen()).getTabs().size());
		for (int i = 0; i < tabs; i++) {
			int index = i;
			context.runOnClient(client -> tabBar(client.gui.screen()).selectTab(index, false));
			String title = context.computeOnClient(client -> tabBar(client.gui.screen()).getTabs().get(index).getTabTitle().getString());
			// YACL's tab bar scrolls sideways: its tabs beyond the screen and its arrows are YACL's own layout, not checked.
			int tabsBottom = context.computeOnClient(client -> ((YACLScreen) client.gui.screen()).tabArea.top());
			checkScreen(context, result, String.format("config %s %02d %s", at, i + 1, title), t -> t.y0() >= tabsBottom, null, problems);
		}
	}

	/** The goods manager screen (opened on its own with a copy of the settings: nothing is saved) in its three modes. */
	private static void goodsManagerModes(ClientGameTestContext context, CaseResult.Builder result, String at, List<String> problems) {
		TradeExpansionConfig copy = ConfigManager.copy(TradeExpansionConfig.get());
		// The HUD under the screen (the hotbar's item counts) is not part of the screen's layout: its texts, recorded with
		// no screen open, are left out.
		context.setScreen(() -> null);
		mouseAway(context);
		List<TextRecorder.Text> hud = TextRecorder.capture(context);
		java.util.function.Predicate<TextRecorder.Text> ownText = t -> hud.stream().noneMatch(h -> h.text().equals(t.text())
				&& h.x0() == t.x0() && h.y0() == t.y0());
		context.setScreen(() -> new GoodsManagerScreen(null, copy, () -> {
		}));
		context.waitTicks(3);
		for (String mode : List.of("goods", "consumable_add", "consumable_remove")) {
			// The screen opens in the first mode, whose button is grayed out.
			if (!mode.equals("goods")) context.clickScreenButton(GOODS_KEY + "mode." + mode);
			context.waitTicks(2);
			LayoutCheck.Panel panel = context.computeOnClient(client -> {
				Screen screen = client.gui.screen();
				int left = (Integer) call(screen, "left");
				int top = (Integer) call(screen, "top");
				return new LayoutCheck.Panel(left, top, left + constant(GoodsManagerScreen.class, "PANEL_W"), top + constant(GoodsManagerScreen.class, "PANEL_H"));
			});
			checkScreen(context, result, "goods manager " + at + " " + mode, ownText, panel, problems);
		}
		context.setScreen(() -> null);
	}

	private static void checkScreen(ClientGameTestContext context, CaseResult.Builder result, String where,
			java.util.function.Predicate<TextRecorder.Text> checked, LayoutCheck.Panel panel, List<String> problems) {
		mouseAway(context);
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(3);
		List<TextRecorder.Text> texts = TextRecorder.capture(context).stream().filter(checked).toList();
		List<String> found = context.computeOnClient(client -> LayoutCheck.check(where, texts,
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), panel));
		if (!found.isEmpty()) screenshot(context, result, "layout_" + where);
		problems.addAll(found);
	}

	static TabNavigationBar tabBar(Screen screen) {
		return screen.children().stream().filter(c -> c instanceof TabNavigationBar).map(c -> (TabNavigationBar) c).findFirst()
				.orElseThrow(() -> new AssertionError("the config screen has no tab bar"));
	}

	// ---- 13-1 saving in singleplayer

	/**
	 * 13-1: in singleplayer, a value changed on the settings screen and saved applies at once: trade.maxPerTransaction
	 * 64 → 48, and the "64" quantity button of the trade screen then sets 48.
	 */
	static void saveApplies(Session s, CaseResult.Builder result) throws IOException {
		ClientGameTestContext context = s.context;
		byte[] file = Files.readAllBytes(ConfigManager.path());
		try {
			int before = TradeExpansionConfig.get().trade.maxPerTransaction;
			int value = before == 48 ? 40 : 48;
			YACLScreen screen = openConfig(context);
			@SuppressWarnings("unchecked")
			Option<Integer> option = (Option<Integer>) option(screen, KEY + "trade.maxPerTransaction");
			check(option.available(), "trade.maxPerTransaction is grayed out in singleplayer");
			context.runOnClient(client -> option.requestSet(value));
			context.waitTicks(2);
			save(context, screen);
			check(waitClient(context, client -> TradeExpansionConfig.get().trade.maxPerTransaction == value, 40),
					"after saving, trade.maxPerTransaction is %d, not %d", TradeExpansionConfig.get().trade.maxPerTransaction, value);
			check(ConfigManager.read(ConfigManager.path()).trade.maxPerTransaction == value, "the config file does not have the new value");
			context.setScreen(() -> null);
			applyTestConfig(s.world.getServer()); // the reload read the file again

			MarketScreen market = ClientChecklistTests.openStall(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
			context.waitTicks(2);
			var good = visibleGoods(context, market).stream().filter(v -> v.stock() >= 64).findFirst()
					.orElseThrow(() -> new AssertionError("no good with stock for 64"));
			select(context, market, good.goods());
			clickWidget(context, "64");
			mouseAway(context);
			int quantity = context.computeOnClient(client -> (Integer) get(market, "quantity"));
			check(quantity == value, "with trade.maxPerTransaction %d the \"64\" button set %d", value, quantity);
			screenshot(context, result, "13-1_max_per_transaction_48");
		} finally {
			restore(s, file);
		}
	}

	// ---- 33-3 the settings of the specials

	/**
	 * 33-3 (partly): the "市場" tab has "掘り出し物を市場の規模に合わせる" and "高い掘り出し物の出にくさ"; changed and
	 * saved, the values apply at once and are in the file.
	 */
	static void specialSettings(Session s, CaseResult.Builder result) throws IOException {
		ClientGameTestContext context = s.context;
		byte[] file = Files.readAllBytes(ConfigManager.path());
		try {
			boolean grade = TradeExpansionConfig.get().market.specialUseGrade;
			double exponent = TradeExpansionConfig.get().market.specialPriceExponent == 1.5 ? 1.2 : 1.5;
			YACLScreen screen = openConfig(context);
			String market = Component.translatable(KEY + "category.market").getString();
			for (String key : List.of(KEY + "market.specialUseGrade", KEY + "market.specialPriceExponent")) {
				String category = categoryOf(screen, key);
				check(category.equals(market), "%s is on the tab \"%s\", not \"%s\"", Component.translatable(key).getString(), category, market);
			}
			@SuppressWarnings("unchecked")
			Option<Boolean> useGrade = (Option<Boolean>) option(screen, KEY + "market.specialUseGrade");
			@SuppressWarnings("unchecked")
			Option<Double> priceExponent = (Option<Double>) option(screen, KEY + "market.specialPriceExponent");
			// Show the tab, for a person to look at.
			int index = context.computeOnClient(client -> {
				List<String> titles = tabBar(client.gui.screen()).getTabs().stream().map(t -> t.getTabTitle().getString()).toList();
				return titles.indexOf(market);
			});
			check(index >= 0, "no tab \"%s\" on the config screen", market);
			context.runOnClient(client -> tabBar(client.gui.screen()).selectTab(index, false));
		context.waitTicks(3);
			mouseAway(context);
			screenshot(context, result, "33-3_market_tab");
			context.runOnClient(client -> {
				useGrade.requestSet(!grade);
				priceExponent.requestSet(exponent);
			});
			context.waitTicks(2);
			save(context, screen);
			check(waitClient(context, client -> TradeExpansionConfig.get().market.specialUseGrade == !grade
					&& TradeExpansionConfig.get().market.specialPriceExponent == exponent, 40),
					"after saving, specialUseGrade %s / specialPriceExponent %s (expected %s / %s)", TradeExpansionConfig.get().market.specialUseGrade,
					TradeExpansionConfig.get().market.specialPriceExponent, !grade, exponent);
			TradeExpansionConfig saved = ConfigManager.read(ConfigManager.path());
			check(saved.market.specialUseGrade == !grade && saved.market.specialPriceExponent == exponent, "the config file does not have the new values");
		} finally {
			restore(s, file);
		}
	}

	private static String categoryOf(YACLScreen screen, String key) {
		for (ConfigCategory category : screen.config.categories()) {
			for (OptionGroup group : category.groups()) {
				for (Option<?> option : group.options()) {
					if (option.name().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) return category.name().getString();
				}
			}
		}
		throw new AssertionError("no option " + key + " on the config screen");
	}

	// ---- 13-6 / 13-8 / 13-9 the goods manager

	/**
	 * 13-6 / 13-8 / 13-9 on the goods manager screen, with the mouse and the keyboard: a new good is added with an icon
	 * and a base price, a good already in the catalog is refused, one good is added to the consumables and one
	 * removed; "完了" writes {@code goods} and {@code consumables} to the file, and {@code /aktradeex consumable list}
	 * shows the change.
	 */
	static void goodsManager(Session s, CaseResult.Builder result) throws IOException {
		ClientGameTestContext context = s.context;
		byte[] file = Files.readAllBytes(ConfigManager.path());
		TradeExpansionConfig config = TradeExpansionConfig.get();
		Item newItem = List.of("minecraft:dead_bush", "minecraft:fern", "minecraft:poppy", "minecraft:allium", "minecraft:cornflower").stream()
				.map(id -> BuiltInRegistries.ITEM.getValue(Identifier.parse(id)))
				.filter(item -> GoodsCatalog.all().values().stream().noneMatch(d -> d.item().equals(BuiltInRegistries.ITEM.getKey(item))))
				.findFirst().orElseThrow(() -> new AssertionError("every candidate item is already a good"));
		Map.Entry<Identifier, GoodsDefinition> notConsumable = GoodsCatalog.all().entrySet().stream()
				.filter(e -> !GoodsCatalog.fromConfig(e.getKey()) && !e.getValue().consumable(config)).findFirst()
				.orElseThrow(() -> new AssertionError("no good that is not a consumable"));
		Map.Entry<Identifier, GoodsDefinition> consumable = GoodsCatalog.all().entrySet().stream()
				.filter(e -> !GoodsCatalog.fromConfig(e.getKey()) && e.getValue().consumable(config)).findFirst()
				.orElseThrow(() -> new AssertionError("no consumable good"));
		Item addItem = BuiltInRegistries.ITEM.getValue(notConsumable.getValue().item());
		Item removeItem = BuiltInRegistries.ITEM.getValue(consumable.getValue().item());
		String newGoodsId = "ak_trade_expansion:" + BuiltInRegistries.ITEM.getKey(newItem).getPath();
		try {
			YACLScreen yacl = openConfig(context);
			ButtonOption open = (ButtonOption) option(yacl, GOODS_KEY + "open");
			check(open.available(), "the goods manager button is grayed out in singleplayer");
			context.runOnClient(client -> open.action().accept(yacl, open));
			context.waitForScreen(GoodsManagerScreen.class);
		context.waitTicks(3);
			GoodsManagerScreen screen = (GoodsManagerScreen) context.computeOnClient(client -> client.gui.screen());

			// 13-6: search, pick the icon, type the price, "追加".
			pick(context, screen, newItem);
			clickWidget(context, (EditBox) get(screen, "price"));
			context.getInput().typeChars("123");
			context.clickScreenButton(GOODS_KEY + "add");
			context.waitTicks(2);
			check(GOODS_KEY.concat("added").equals(statusKey(context, screen)), "adding %s says \"%s\"", newItem, status(context, screen));
			check(entries(context, screen).contains(newGoodsId), "%s is not listed after \"追加\": %s", newGoodsId, entries(context, screen));
			screenshot(context, result, "13-6_goods_added");

			// 13-9: a good of the catalog is refused.
			Item catalogItem = BuiltInRegistries.ITEM.getValue(notConsumable.getValue().item());
			List<String> listed = entries(context, screen);
			pick(context, screen, catalogItem);
			context.clickScreenButton(GOODS_KEY + "add");
			context.waitTicks(2);
			check(GOODS_KEY.concat("already_goods").equals(statusKey(context, screen)), "adding the good %s says \"%s\"", catalogItem, status(context, screen));
			check(entries(context, screen).equals(listed), "the list changed after the refusal: %s", entries(context, screen));
			screenshot(context, result, "13-9_already_goods");

			// 13-8: add one consumable, remove another.
			context.clickScreenButton(GOODS_KEY + "mode.consumable_add");
			context.waitTicks(2);
			pick(context, screen, addItem);
			context.clickScreenButton(GOODS_KEY + "add");
			context.waitTicks(2);
			check(entries(context, screen).contains(BuiltInRegistries.ITEM.getKey(addItem).toString()), "%s is not listed as added consumable", addItem);
			// One more, then its icon in the list and "削除" take it out again.
			String extra = "minecraft:stick";
			pick(context, screen, BuiltInRegistries.ITEM.getValue(Identifier.parse(extra)));
			context.clickScreenButton(GOODS_KEY + "add");
			context.waitTicks(2);
			List<String> withExtra = entries(context, screen);
			int at = withExtra.indexOf(extra);
			check(at >= 0 && at < 27, "%s is listed at %d: %s", extra, at, withExtra);
			int[] corner = context.computeOnClient(client -> new int[] {(Integer) call(screen, "left"), (Integer) call(screen, "top")});
			clickAt(context, corner[0] + 176 + (at % 9) * 18 + 8, corner[1] + 114 + (at / 9) * 18 + 8);
			mouseAway(context);
			context.clickScreenButton(GOODS_KEY + "remove");
			context.waitTicks(2);
			check(!entries(context, screen).contains(extra), "\"削除\" left %s in the list: %s", extra, entries(context, screen));
			context.clickScreenButton(GOODS_KEY + "mode.consumable_remove");
			context.waitTicks(2);
			pick(context, screen, removeItem);
			context.clickScreenButton(GOODS_KEY + "add");
			context.waitTicks(2);
			check(entries(context, screen).contains(BuiltInRegistries.ITEM.getKey(removeItem).toString()), "%s is not listed as removed consumable", removeItem);
			screenshot(context, result, "13-8_consumable_remove");

			context.clickScreenButton("gui.done");
			check(waitClient(context, client -> client.gui.screen() instanceof YACLScreen, 20), "\"完了\" did not go back to the settings screen");
			TradeExpansionConfig saved = ConfigManager.read(ConfigManager.path());
			GoodsOverride added = saved.goods.get(newGoodsId);
			check(added != null, "the file has no goods.%s: %s", newGoodsId, saved.goods.keySet());
			check(added.item().map(BuiltInRegistries.ITEM.getKey(newItem).toString()::equals).orElse(false), "goods.%s has item %s", newGoodsId, added.item());
			check(added.basePrice().map(p -> Math.abs(p - 123) < 1e-9).orElse(false), "goods.%s has base_price %s", newGoodsId, added.basePrice());
			check(saved.consumables.add.contains(BuiltInRegistries.ITEM.getKey(addItem).toString()), "consumables.add is %s", saved.consumables.add);
			check(saved.consumables.remove.contains(BuiltInRegistries.ITEM.getKey(removeItem).toString()), "consumables.remove is %s", saved.consumables.remove);
			check(!saved.consumables.add.contains("minecraft:stick"), "the deleted entry is in consumables.add: %s", saved.consumables.add);

			// The reload on the server applies the file: the command lists the change.
			String addPath = notConsumable.getValue().item().getPath();
			String removePath = consumable.getValue().item().getPath();
			boolean listedNow = false;
			List<String> goods = List.of();
			for (int i = 0; i < 40 && !listedNow; i++) {
				context.waitTick();
				goods = consumableList(s);
				listedNow = goods.contains(addPath) && !goods.contains(removePath);
			}
			check(listedNow, "/aktradeex consumable list shows %s (want %s in it and %s not)", goods, addPath, removePath);
			check(GoodsCatalog.all().containsKey(Identifier.parse(newGoodsId)), "the catalog has no %s after \"完了\"", newGoodsId);
		} finally {
			restore(s, file);
		}
	}

	/** Types the item's ID into the search box and clicks its icon in the grid. */
	private static void pick(ClientGameTestContext context, GoodsManagerScreen screen, Item item) {
		EditBox search = (EditBox) get(screen, "search");
		clickWidget(context, search);
		context.runOnClient(client -> search.setValue(""));
		context.getInput().typeChars(BuiltInRegistries.ITEM.getKey(item).toString());
		context.waitTicks(2);
		@SuppressWarnings("unchecked")
		int index = context.computeOnClient(client -> ((List<Item>) get(screen, "filtered")).indexOf(item));
		check(index >= 0 && index < 63, "the search for %s puts it at %d", item, index);
		int[] at = context.computeOnClient(client -> new int[] {(Integer) call(screen, "left"), (Integer) call(screen, "top")});
		clickAt(context, at[0] + (index % 9) * 18 + 8, at[1] + 44 + (index / 9) * 18 + 8);
		mouseAway(context);
		Object picked = context.computeOnClient(client -> get(screen, "picked"));
		check(picked == item, "clicking the icon of %s picked %s", item, picked);
	}

	@SuppressWarnings("unchecked")
	private static List<String> entries(ClientGameTestContext context, GoodsManagerScreen screen) {
		return context.computeOnClient(client -> ((List<Map.Entry<String, Item>>) call(screen, "entries")).stream().map(Map.Entry::getKey).toList());
	}

	private static String statusKey(ClientGameTestContext context, GoodsManagerScreen screen) {
		return context.computeOnClient(client -> get(screen, "status") instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : "");
	}

	private static String status(ClientGameTestContext context, GoodsManagerScreen screen) {
		return context.computeOnClient(client -> ((Component) get(screen, "status")).getString());
	}

	/** The goods (item paths) the first line of {@code /aktradeex consumable list} names. */
	private static List<String> consumableList(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			List<Component> out = new ArrayList<>();
			CommandSource collector = new CommandSource() {
				@Override
				public void sendSystemMessage(Component message) {
					out.add(message);
				}

				@Override
				public boolean acceptsSuccess() {
					return true;
				}

				@Override
				public boolean acceptsFailure() {
					return true;
				}

				@Override
				public boolean shouldInformAdmins() {
					return false;
				}
			};
			CommandSourceStack source = server.createCommandSourceStack().withSource(collector);
			server.getCommands().performPrefixedCommand(source, "aktradeex consumable list");
			check(!out.isEmpty() && out.getFirst().getContents() instanceof TranslatableContents, "no output of the command: %s", out);
			Object[] args = ((TranslatableContents) out.getFirst().getContents()).getArgs();
			String list = args[args.length - 1] instanceof Component c ? c.getString() : String.valueOf(args[args.length - 1]);
			return List.of(list.split(",\\s*"));
		});
	}

	// ---- 13-2 / 14-1 the dedicated server

	/**
	 * 14-1 / 13-2 with a dedicated server (started in this process): one player connects, places a stall in a village
	 * and opens the trade screen. On the settings screen the server options are grayed out, a display (client) option
	 * can be changed, and saving writes only that.
	 */
	static void dedicatedServer(Session s, CaseResult.Builder result, CaseResult.Builder settings) throws IOException {
		ClientGameTestContext context = s.context;
		byte[] file = Files.readAllBytes(ConfigManager.path());
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			int players = server.computeOnServer(srv -> srv.getPlayerList().getPlayerCount());
			check(players == 1, "the dedicated server has %d players", players);
			BlockPos stall = villageAndStall(server);
			context.waitTicks(5);
			context.getInput().lookAt(stall.below());
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			check(server.computeOnServer(srv -> srv.overworld().getBlockState(stall).is(ModBlocks.MARKET_STALL)), "the stall was not placed on the dedicated server");
			context.getInput().lookAt(stall);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MarketScreen.class);
			MarketScreen market = screen(context);
			check(waitClient(context, client -> get(market, "snapshot") != null, 40), "the trade screen got no snapshot from the dedicated server");
			mouseAway(context);
			screenshot(context, result, "14-1_dedicated_trade_screen");
			context.setScreen(() -> null);

			// 13-2: the settings screen while connected.
			try {
				YACLScreen yacl = openConfig(context);
				Option<?> serverOption = option(yacl, KEY + "trade.maxPerTransaction");
				Option<?> goodsButton = option(yacl, GOODS_KEY + "open");
				@SuppressWarnings("unchecked")
				Option<Boolean> clientOption = (Option<Boolean>) option(yacl, KEY + "client.showDebugTooltip");
				check(!serverOption.available(), "trade.maxPerTransaction can be changed while connected to a server");
				check(!goodsButton.available(), "the goods manager opens while connected to a server");
				check(clientOption.available(), "the display option client.showDebugTooltip is grayed out while connected");
				int before = TradeExpansionConfig.get().trade.maxPerTransaction;
				boolean value = !TradeExpansionConfig.get().client.showDebugTooltip;
				context.runOnClient(client -> clientOption.requestSet(value));
				context.waitTicks(2);
				screenshot(context, settings, "13-2_settings_on_server");
				save(context, yacl);
				context.waitTicks(3);
				check(TradeExpansionConfig.get().client.showDebugTooltip == value, "the display option did not apply after saving");
				check(ConfigManager.read(ConfigManager.path()).client.showDebugTooltip == value, "the display option is not in the file");
				check(TradeExpansionConfig.get().trade.maxPerTransaction == before, "saving changed trade.maxPerTransaction");
				context.setScreen(() -> null);
			} catch (Throwable t) {
				throw new SettingsFailed(t);
			}
		} finally {
			Files.write(ConfigManager.path(), file);
			ConfigManager.load();
		}
	}

	/** 13-2 failed (recorded on its own result) while 14-1 passed. */
	static final class SettingsFailed extends ClientChecklistTests.AlsoFailed {
		SettingsFailed(Throwable cause) {
			super(cause);
		}
	}

	// ---- helpers

	private static YACLScreen openConfig(ClientGameTestContext context) {
		context.setScreen(() -> ConfigScreen.create(null));
		context.waitTicks(5);
		Screen screen = context.computeOnClient(client -> client.gui.screen());
		check(screen instanceof YACLScreen, "the config screen did not open (%s)", screen);
		return (YACLScreen) screen;
	}

	private static Option<?> option(YACLScreen screen, String key) {
		for (ConfigCategory category : screen.config.categories()) {
			for (OptionGroup group : category.groups()) {
				for (Option<?> option : group.options()) {
					if (option.name().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) return option;
				}
			}
		}
		throw new AssertionError("no option " + key + " on the config screen");
	}

	/** The save button of YACL ("変更を保存"), or the same action when its text is not found. */
	private static void save(ClientGameTestContext context, YACLScreen screen) {
		if (!context.tryClickScreenButton("yacl.gui.save")) {
			AutotestReport.LOGGER.info("No YACL save button found by its key: saving through YACLScreen.finishOrSave");
			context.runOnClient(client -> screen.finishOrSave());
		}
		context.waitTicks(3);
	}

	/** Writes the config file back and reloads it (in singleplayer the integrated server reloads it too). */
	private static void restore(Session s, byte[] file) throws IOException {
		Path path = ConfigManager.path();
		Files.write(path, file);
		s.context.setScreen(() -> null);
		if (s.world != null) {
			s.world.getServer().runOnServer(ServerConfig::reload);
			s.context.waitTicks(3);
			applyTestConfig(s.world.getServer());
		} else {
			ConfigManager.load();
		}
	}
}
