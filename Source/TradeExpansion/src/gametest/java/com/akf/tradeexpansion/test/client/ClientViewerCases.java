package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.PriceLookupCache;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.GoodsOverride;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases with a recipe viewer installed (checklist 25-2〜25-8 and 30-1): the "市場の相場" pages of JEI
 * (runClientGameTestJei) and REI (runClientGameTestRei). What differs between the viewers is behind {@link Viewer}
 * ({@link JeiViewer}, {@link ReiViewer}: only the class of the viewer that runs is loaded). The cases go in order in
 * one world: the keys, the page (six other markets 100–350 blocks away carry the good of the page), then the goods
 * taken away and added again.
 */
final class ClientViewerCases {
	private static final String NS = "ak_trade_expansion";
	static final String OUR_CATEGORY = NS + ":market_prices";
	/** COLOR_SHORTAGE of MarketPricePanel. */
	private static final int SHORTAGE_COLOR = 0xFFB04A00;

	private ClientViewerCases() {
	}

	/** What the cases need of a recipe viewer. */
	interface Viewer {
		/** "JEI" / "REI", for the messages and screenshots. */
		String name();

		/** The viewer has started (its runtime and our plugin are there). */
		boolean ready();

		/** The ids of the categories the viewer has for the item: its recipes (R) or its uses (U), in the order shown. */
		List<String> categories(ItemStack stack, boolean recipes);

		/** The item is a catalyst / workstation of the "市場の相場" category. */
		boolean ourStation(ItemStack stack);

		/** The screen is one of the viewer's recipe screens. */
		boolean isViewerScreen(Screen screen);

		/** Opens the "市場の相場" page of the item. */
		void showPage(ItemStack stack);

		/** The ids of the categories the open recipe screen has (its tabs). */
		List<String> shownCategories(Screen screen);

		/** The centre of the tab of a category on the open recipe screen (GUI pixels), or {@code null}. */
		double[] tab(Screen screen, String category);
	}

	/** A field of the object or of one of its superclasses (the viewers' screens keep what the cases read in fields). */
	static Object field(Object target, String name) {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				java.lang.reflect.Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				// the superclass
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot read " + c.getSimpleName() + "." + name, e);
			}
		}
		throw new AssertionError("no field " + name + " in " + target.getClass().getName());
	}

	private static Viewer viewer;

	/** The viewer of this run: JEI or REI (whichever is loaded). */
	static Viewer viewer() {
		if (viewer == null) {
			viewer = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei") ? new JeiViewer() : new ReiViewer();
		}
		return viewer;
	}

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static boolean hasPage(ItemStack stack, boolean recipes) {
		return viewer().categories(stack, recipes).contains(OUR_CATEGORY);
	}

	/** The viewer and the tradable items from the server are there (they arrive after joining). */
	static void waitReady(Session s) {
		ClientGameTestContext context = s.context;
		check(waitClient(context, c -> viewer().ready() && !PriceLookupCache.tradable().isEmpty(), 600),
				"%s did not start or the tradable items did not arrive (ready %s, %d items)", viewer().name(),
				context.computeOnClient(c -> viewer().ready()), context.computeOnClient(c -> PriceLookupCache.tradable().size()));
	}

	// ---- 25-2 / 25-6 / 25-7 the keys

	/** Goods (one item each, unit 1, no components) that the stall's market sells and buys. */
	private static List<Identifier> plainGoods(Session s) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			List<Identifier> out = new ArrayList<>();
			market.goods().forEach((id, state) -> {
				GoodsDefinition def = GoodsCatalog.get(id);
				if (def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && def.autoAdd().isEmpty() && single(def.item())
						&& !market.isSpecial(id)) out.add(id);
			});
			out.sort(Comparator.comparing(Identifier::toString));
			return out;
		});
	}

	/** Only one goods definition is about the item. */
	private static boolean single(Identifier item) {
		return GoodsCatalog.all().values().stream().filter(d -> d.item().equals(item)).count() == 1;
	}

	private static Identifier goodsOf(Identifier item) {
		return GoodsCatalog.all().entrySet().stream().filter(e -> e.getValue().item().equals(item)).map(java.util.Map.Entry::getKey).findFirst()
				.orElseThrow(() -> new AssertionError("no good of " + item));
	}

	/**
	 * Lists the good in the market with 20 in stock. A good it does not have takes the place of its last regular good
	 * (one more would be shelved again: a market lists as many goods as its level allows).
	 */
	private static void putGood(Market market, Identifier good, long now) {
		if (market.good(good) == null) {
			List<Identifier> regular = market.regularGoods();
			if (!regular.isEmpty()) market.removeGood(regular.getLast());
			market.addGood(good, new GoodState(1.0, 0, 20, now));
		}
		market.good(good).setStock(20);
	}

	/** Opens the survival inventory with the item in slot 10 (the second row) and the mouse on it. */
	private static void hoverInInventory(Session s, ItemStack stack) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		ItemStack copy = stack.copy();
		s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(10, copy));
		check(waitClient(context, c -> ItemStack.isSameItem(c.player.getInventory().getItem(10), copy), 40), "the item did not reach the client");
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(3);
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) context.computeOnClient(c -> c.gui.screen());
		double[] at = context.computeOnClient(c -> {
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == 10) return new double[] {leftPos(screen) + slot.x + 8, topPos(screen) + slot.y + 8};
			}
			return null;
		});
		check(at != null, "no slot 10 in the inventory screen");
		moveTo(context, at[0], at[1]);
		context.waitTicks(2);
	}

	/** Presses R (recipes) or U (uses) over the item in the inventory; returns the texts of the screen that came up. */
	private static List<TextRecorder.Text> pressOver(Session s, ItemStack stack, boolean recipes) {
		ClientGameTestContext context = s.context;
		hoverInInventory(s, stack);
		context.getInput().pressKey(recipes ? InputConstants.KEY_R : InputConstants.KEY_U);
		waitClient(context, c -> viewer().isViewerScreen(c.gui.screen()), 60);
		context.waitTicks(10);
		return TextRecorder.capture(context);
	}

	private static boolean showsTitle(List<TextRecorder.Text> texts) {
		String title = text("recipe_viewer." + NS + ".title");
		return texts.stream().anyMatch(t -> t.text().equals(title));
	}

	/** The market's good with the highest buy price (a cheap one rounds the reputation discount away). */
	private static Identifier priciestGood(Session s) {
		BlockPos stall = s.stall;
		List<Identifier> goods = plainGoods(s);
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			return goods.stream().max(Comparator.comparingLong(g -> PriceModel.buyPrice(market.params(g), market.good(g).pressure())))
					.orElseThrow(() -> new AssertionError("the market has no plain good"));
		});
	}

	/** Clicks the tab of the "市場の相場" category on the open recipe screen and returns the texts drawn then. */
	private static List<TextRecorder.Text> clickOurTab(ClientGameTestContext context, String what) {
		String v = viewer().name();
		List<String> shown = context.computeOnClient(c -> viewer().shownCategories(c.gui.screen()));
		check(shown.contains(OUR_CATEGORY), "%s: the recipe screen of %s has no market page: %s", v, what, shown);
		double[] at = context.computeOnClient(c -> viewer().tab(c.gui.screen(), OUR_CATEGORY));
		check(at != null, "%s: the recipe screen of %s has no tab of the market page (categories %s)", v, what, shown);
		clickAt(context, at[0], at[1]);
		context.waitTicks(5);
		mouseAway(context);
		return TextRecorder.capture(context);
	}

	/**
	 * 25-2 (JEI) / 25-7 (REI): iron ingots have the page in their recipes and uses; R and U over the stall market's
	 * priciest good open the recipe screen with the "市場の相場" tab, and its tab shows the page with the stall's market
	 * in it; bedrock (not traded) has no page. 25-6: the stall is the category's catalyst, and (JEI) U over it opens a
	 * recipe screen with the "市場の相場" tab.
	 */
	static void keys(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		waitReady(s);
		String v = viewer().name();
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		check(context.computeOnClient(c -> hasPage(iron, true)), "%s: iron ingots have no market page in their recipes: %s", v, context.computeOnClient(c -> viewer().categories(iron, true)));
		check(context.computeOnClient(c -> hasPage(iron, false)), "%s: iron ingots have no market page in their uses: %s", v, context.computeOnClient(c -> viewer().categories(iron, false)));
		ItemStack bedrock = new ItemStack(Items.BEDROCK);
		check(context.computeOnClient(c -> !hasPage(bedrock, true) && !hasPage(bedrock, false)), "%s: bedrock has a market page", v);

		String marketTitle = s.world.getServer().computeOnServer(server -> MarketNames.title(market(server, s.stall)).getString());
		ItemStack stack = new ItemStack(item(priciestGood(s)));
		for (boolean recipes : new boolean[] {true, false}) {
			String key = recipes ? "R" : "U";
			pressOver(s, stack, recipes);
			check(context.computeOnClient(c -> viewer().isViewerScreen(c.gui.screen())), "%s: %s over %s did not open the recipe screen (%s)", v, key,
					stack.getItem(), context.computeOnClient(c -> c.gui.screen()));
			List<TextRecorder.Text> texts = clickOurTab(context, stack.getItem() + " (" + key + ")");
			check(showsTitle(texts), "%s: the market tab of %s (%s) does not show \"%s\": %s", v, stack.getItem(), key, text("recipe_viewer." + NS + ".title"), strings(texts));
			check(waitPage(context, t -> rowTitle(t, marketTitle) != null), "%s: the page of %s (%s) does not list the stall's market \"%s\": %s", v,
					stack.getItem(), key, marketTitle, strings(TextRecorder.capture(context)));
			screenshot(context, result, "25-2_" + v + "_" + key);
		}
		pressOver(s, bedrock, true);
		boolean bedrockPage = context.computeOnClient(c -> viewer().isViewerScreen(c.gui.screen()) && viewer().shownCategories(c.gui.screen()).contains(OUR_CATEGORY));
		check(!bedrockPage, "%s: R over bedrock shows the market page", v);
		context.setScreen(() -> null);

		ItemStack stall = new ItemStack(ModBlocks.MARKET_STALL.asItem());
		check(context.computeOnClient(c -> viewer().ourStation(stall)), "%s: the stall is not the market page's catalyst", v);
		// REI lists a workstation's categories under its uses only with fixed displays (ours are made per item): the
		// checklist asks U over the stall of JEI only (25-6).
		if (!(viewer() instanceof JeiViewer)) return;
		pressOver(s, stall, false);
		check(context.computeOnClient(c -> viewer().isViewerScreen(c.gui.screen())), "%s: U over the stall did not open the recipe screen", v);
		List<TextRecorder.Text> texts = clickOurTab(context, "the stall (U)");
		check(showsTitle(texts), "%s: the market tab of the stall (U) does not show \"%s\": %s", v, text("recipe_viewer." + NS + ".title"), strings(texts));
		screenshot(context, also, "25-6_" + v + "_stall_uses");
		context.setScreen(() -> null);
	}

	// ---- 25-3 / 25-4 / 25-5 the page

	/** Waits (about 10 seconds at most) until the page drawn matches. */
	private static boolean waitPage(ClientGameTestContext context, java.util.function.Predicate<List<TextRecorder.Text>> test) {
		for (int i = 0; i < 50; i++) {
			if (test.test(TextRecorder.capture(context))) return true;
			context.waitTicks(4);
		}
		return false;
	}

	/** The text drawn for a market's name on the page (cut with "…" when long), or {@code null}. */
	private static TextRecorder.Text rowTitle(List<TextRecorder.Text> texts, String title) {
		for (TextRecorder.Text t : texts) {
			String shown = t.text();
			if (shown.equals(title)) return t;
			if (shown.endsWith("…") && shown.length() > 3 && title.startsWith(shown.substring(0, shown.length() - 1))) return t;
		}
		return null;
	}

	/** The texts in the row of a market's name (the name line and the price line below it). */
	private static List<String> row(List<TextRecorder.Text> texts, TextRecorder.Text title) {
		int x0 = title.x0() - 4;
		int x1 = title.x0() + 175;
		return texts.stream().filter(t -> t.stratum() == title.stratum() && t.y0() >= title.y0() - 1 && t.y0() <= title.y0() + 12
				&& t.x0() >= x0 && t.x0() <= x1).map(TextRecorder.Text::text).toList();
	}

	record Other(long id, String title, double distance) {
	}

	/** Six other markets around (100–350 blocks away) that carry the good, one of them short of it. */
	private static List<Other> otherMarkets(Session s, Identifier good) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			MarketRegistry registry = MarketRegistry.get(server);
			Market home = market(server, stall);
			int[][] offsets = {{100, 0}, {0, 150}, {-200, 0}, {0, -250}, {300, 0}, {0, 350}};
			long now = MarketClock.now(level);
			List<Other> others = new ArrayList<>();
			for (int[] o : offsets) {
				BlockPos pos = ground(server, home.center().getX() + o[0], home.center().getZ() + o[1]);
				Market m = registry.assignStall(level, pos, MarketOrigin.NATURAL).market();
				check(m != null && m != home, "no new market at %s", pos);
				MarketUpkeep.touch(level, m);
				putGood(m, good, now);
				others.add(new Other(m.id(), MarketNames.title(m).getString(), m.horizontalDistanceTo(player(server).blockPosition())));
			}
			registry.setDirty();
			return others;
		});
	}

	/**
	 * 25-3 (JEI) / 25-7 (REI): the page of a good the stall's market and six other markets carry. It lists the five
	 * nearest markets in order with their direction and distance, buy and sell prices and stock; the stall's prices are
	 * the trade screen's (with the reputation discount of 300 points); the market short of the good is orange; the mouse
	 * on a row shows the market's name and the shortage note. 25-4 (also): with the page open, the stall's price changes
	 * on the server (its pressure moves) and the page shows the new price within seconds.
	 */
	static void page(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		waitReady(s);
		String v = viewer().name();
		Identifier good = priciestGood(s);
		ItemStack stack = new ItemStack(item(good));
		Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		BlockPos stall = s.stall;
		List<Other> others = otherMarkets(s, good);
		Other shortOne = others.get(1);
		UUID uuid = context.computeOnClient(c -> c.player.getUUID());
		s.world.getServer().runOnServer(server -> {
			Market home = market(server, stall);
			home.setStanding(new Market.Standing(uuid, 300, MarketClock.now(server.overworld())));
			home.shortages().remove(good); // only the other market is short of it
			MarketRegistry.get(server).get(shortOne.id()).shortages().put(good, 1.5);
			MarketRegistry.get(server).setDirty();
		});
		try {
			// The trade screen's prices of the good (the reputation discount in them), and the prices without it.
			MarketScreen screen = ClientChecklistTests.openStall(s);
			MarketSnapshotS2C.GoodView view = view(context, screen, good);
			long buy = PriceModel.buyPrice(view.params(), view.pressure());
			long sell = PriceModel.sellPrice(view.params(), view.pressure());
			long plainBuy = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				return PriceModel.buyPrice(home.params(good), home.good(good).pressure());
			});
			check(buy < plainBuy, "the reputation of 300 points gives no discount on %s (%d, %d without)", good, buy, plainBuy);
			context.setScreen(() -> null);

			context.runOnClient(c -> viewer().showPage(stack));
			check(waitClient(context, c -> viewer().isViewerScreen(c.gui.screen()), 60), "%s: the page of %s did not open", v, item);
			String homeTitle = s.world.getServer().computeOnServer(server -> MarketNames.title(market(server, stall)).getString());
			check(waitPage(context, t -> rowTitle(t, homeTitle) != null && others.stream().limit(4).allMatch(o -> rowTitle(t, o.title()) != null)),
					"%s: the page does not list the five nearest markets: %s", v, strings(TextRecorder.capture(context)));
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			screenshot(context, result, "25-3_" + v + "_page");

			// The five nearest, nearest first; the sixth is not listed.
			List<String> order = new ArrayList<>();
			order.add(homeTitle);
			others.stream().sorted(Comparator.comparingDouble(Other::distance)).limit(4).forEach(o -> order.add(o.title()));
			int lastY = Integer.MIN_VALUE;
			for (String title : order) {
				TextRecorder.Text t = rowTitle(texts, title);
				check(t != null, "%s: no row of \"%s\": %s", v, title, strings(texts));
				check(t.y0() > lastY, "%s: \"%s\" is not below the nearer markets", v, title);
				lastY = t.y0();
			}
			Other farthest = others.stream().max(Comparator.comparingDouble(Other::distance)).orElseThrow();
			check(rowTitle(texts, farthest.title()) == null, "%s: the page lists a sixth market \"%s\"", v, farthest.title());

			// The stall's row: direction and distance, the trade screen's prices, the stock.
			TextRecorder.Text homeRow = rowTitle(texts, homeTitle);
			List<String> cells = row(texts, homeRow);
			String buyText = text("recipe_viewer." + NS + ".buy", CoinItem.formatCopper(buy));
			String sellText = text("recipe_viewer." + NS + ".sell", CoinItem.formatCopper(sell));
			check(cells.contains(buyText) && cells.contains(sellText), "%s: the stall's row shows %s, not \"%s\" and \"%s\" as the trade screen", v, cells, buyText, sellText);
			check(cells.stream().anyMatch(c -> c.startsWith(text("recipe_viewer." + NS + ".stock", "").trim())), "%s: the stall's row has no stock: %s", v, cells);
			String whereKey = Language.getInstance().getOrDefault("screen." + NS + ".board.where");
			String distanceWord = whereKey.substring(whereKey.lastIndexOf('%') + 2);
			for (String title : order.subList(1, order.size())) {
				List<String> other = row(texts, rowTitle(texts, title));
				check(other.stream().anyMatch(c -> c.endsWith(distanceWord)), "%s: the row of \"%s\" has no direction and distance: %s", v, title, other);
			}

			// The market short of the good: its name in orange; its tooltip says so.
			TextRecorder.Text shortTitle = rowTitle(texts, shortOne.title());
			check(shortTitle.color() == SHORTAGE_COLOR, "%s: the market short of the good is drawn in #%08X, not orange", v, shortTitle.color());
			check(rowTitle(texts, homeTitle).color() != SHORTAGE_COLOR, "%s: the stall's market (not short) is orange", v);
			moveTo(context, shortTitle.x0() + 3, shortTitle.y0() + 4);
			String shortageLine = text("recipe_viewer." + NS + ".shortage");
			// The tooltip: the market's name and the shortage line (drawn only on hover; REI draws it on the screen's layer).
			check(waitPage(context, t -> strings(t).contains(shortageLine) && t.stream().filter(x -> x.text().equals(shortOne.title())).count()
					+ (rowTitle(t, shortOne.title()).text().equals(shortOne.title()) ? 0 : 1) >= 2),
					"%s: the tooltip of the short market does not show \"%s\" and \"%s\": %s", v, shortOne.title(), shortageLine, strings(TextRecorder.capture(context)));
			screenshot(context, result, "25-3_" + v + "_shortage_tooltip");
			mouseAway(context);

			// 25-4: the stall's price moves; the open page follows within seconds.
			long newBuy = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				GoodState state = home.good(good);
				GoodParams p = Reputation.params(home, uuid, good, MarketClock.now(server.overworld()));
				double pressure = p.clampPressure(state.pressure() + 25);
				state.setPressure(pressure);
				MarketRegistry.get(server).setDirty();
				return PriceModel.buyPrice(p, pressure);
			});
			check(newBuy != buy, "moving the pressure did not change the price (%d)", buy);
			String newBuyText = text("recipe_viewer." + NS + ".buy", CoinItem.formatCopper(newBuy));
			check(waitPage(context, t -> {
				TextRecorder.Text h = rowTitle(t, homeTitle);
				return h != null && row(t, h).contains(newBuyText);
			}), "%s: the open page still shows %s (the price is now %s)", v, buyText, newBuyText);
			screenshot(context, also, "25-4_" + v + "_new_price");
			context.setScreen(() -> null);

		} finally {
			s.world.getServer().runOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				for (Other o : others) registry.remove(o.id());
				Market home = market(server, stall);
				home.setStanding(new Market.Standing(uuid, 0, MarketClock.now(server.overworld())));
				registry.setDirty();
			});
		}
	}

	/**
	 * 25-5: with no market within board.radius (16 here, the player 40 blocks away from the stall's market) the page
	 * says there is none, and its header names the radius.
	 */
	static void none(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		waitReady(s);
		String v = viewer().name();
		ItemStack stack = new ItemStack(Items.IRON_INGOT);
		BlockPos stall = s.stall;
		int oldRadius = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().board.radius);
		try {
			s.world.getServer().runOnServer(server -> TradeExpansionConfig.get().board.radius = 16);
			BlockPos away = s.world.getServer().computeOnServer(server -> ground(server, stall.getX() - 40, stall.getZ() + 40));
			teleport(s, away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			context.runOnClient(c -> viewer().showPage(stack));
			check(waitClient(context, c -> viewer().isViewerScreen(c.gui.screen()), 60), "%s: the page of iron ingots did not open", v);
			String none = text("recipe_viewer." + NS + ".none");
			check(waitPage(context, t -> t.stream().anyMatch(x -> none.startsWith(x.text()) && x.text().length() >= 6)),
					"%s: away from every market the page does not say \"%s\": %s", v, none, strings(TextRecorder.capture(context)));
			String header = text("recipe_viewer." + NS + ".header", 16);
			check(waitPage(context, t -> strings(t).contains(header)), "%s: the page's header is not \"%s\": %s", v, header, strings(TextRecorder.capture(context)));
			screenshot(context, result, "25-5_" + v + "_none");
		} finally {
			s.world.getServer().runOnServer(server -> TradeExpansionConfig.get().board.radius = oldRadius);
			context.setScreen(() -> null);
			teleport(s, stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		}
	}

	// ---- 25-8 goods taken away and added

	private static boolean waitTradable(ClientGameTestContext context, Identifier item, boolean tradable) {
		return waitClient(context, c -> PriceLookupCache.tradable().contains(item) == tradable, 100);
	}

	/**
	 * 25-8: {@code /aktradeex goods set <good> buy false} and {@code sell false} (they reload like /aktradeex reload) take
	 * the page of the item away; {@code goods reset} brings it back. An item added in the config file (the config
	 * screen's "add a good") gets a page after {@code /aktradeex reload}, and loses it when taken out again.
	 */
	static void reload(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		waitReady(s);
		String v = viewer().name();
		ItemStack stack = new ItemStack(Items.IRON_INGOT);
		Identifier good = goodsOf(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
		check(single(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), "more than one good is iron ingots");
		String id = good.getNamespace().equals(NS) ? good.getPath() : good.toString();
		try {
			s.world.getServer().runCommand("aktradeex goods set " + id + " buy false");
			s.world.getServer().runCommand("aktradeex goods set " + id + " sell false");
			check(waitTradable(context, Identifier.withDefaultNamespace("iron_ingot"), false), "iron ingots are still tradable after goods set buy/sell false");
			check(waitClient(context, c -> !hasPage(stack, true) && !hasPage(stack, false), 60), "%s: iron ingots still have a market page", v);
		} finally {
			s.world.getServer().runCommand("aktradeex goods reset " + id + " buy");
			s.world.getServer().runCommand("aktradeex goods reset " + id + " sell");
		}
		check(waitTradable(context, Identifier.withDefaultNamespace("iron_ingot"), true), "iron ingots are not tradable again after goods reset");
		check(waitClient(context, c -> hasPage(stack, true) && hasPage(stack, false), 60), "%s: iron ingots have no market page again after goods reset", v);

		// An item that is no good yet, added in the config file.
		Item added = java.util.stream.Stream.of(Items.SPONGE, Items.DRIED_KELP_BLOCK, Items.NAUTILUS_SHELL, Items.HEART_OF_THE_SEA)
				.filter(i -> GoodsCatalog.all().values().stream().noneMatch(d -> d.item().equals(BuiltInRegistries.ITEM.getKey(i)))).findFirst()
				.orElseThrow(() -> new AssertionError("every candidate item is already a good"));
		Identifier addedId = BuiltInRegistries.ITEM.getKey(added);
		ItemStack addedStack = new ItemStack(added);
		check(context.computeOnClient(c -> !hasPage(addedStack, true)), "%s: %s has a market page before it is a good", v, addedId);
		try {
			s.world.getServer().runOnServer(server -> {
				TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
				file.goods.put(addedId.toString(), new GoodsOverride(Optional.of(40.0), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(addedId.toString())));
				ConfigManager.write(ConfigManager.path(), file);
			});
			s.world.getServer().runCommand("aktradeex reload");
			check(waitTradable(context, addedId, true), "%s added in the config is not tradable after /aktradeex reload", addedId);
			check(waitClient(context, c -> hasPage(addedStack, true) && hasPage(addedStack, false), 60), "%s: the added %s has no market page", v, addedId);
			context.runOnClient(c -> viewer().showPage(addedStack));
			check(waitClient(context, c -> viewer().isViewerScreen(c.gui.screen()), 60), "%s: the page of the added %s did not open", v, addedId);
			check(waitPage(context, ClientViewerCases::showsTitle), "%s: the page of the added %s does not show its title", v, addedId);
			screenshot(context, result, "25-8_" + v + "_added_good");
			context.setScreen(() -> null);
		} finally {
			s.world.getServer().runOnServer(server -> {
				TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
				file.goods.remove(addedId.toString());
				ConfigManager.write(ConfigManager.path(), file);
			});
			s.world.getServer().runCommand("aktradeex reload");
		}
		check(waitTradable(context, addedId, false), "%s is still tradable after it was taken out of the config", addedId);
		check(waitClient(context, c -> !hasPage(addedStack, true), 60), "%s: %s keeps its market page after it was taken out", v, addedId);
	}

	// ---- 30-1 new goods in the viewer

	/**
	 * 30-1 (part): new goods of goods-design v0.7 (elytra, trident, a diamond tool and armour piece) have the page, and
	 * the page's item is a new one (no damage): screenshots of the pages for their icons.
	 */
	static void newGoods(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		waitReady(s);
		String v = viewer().name();
		for (Item item : List.of(Items.ELYTRA, Items.TRIDENT, Items.DIAMOND_PICKAXE, Items.DIAMOND_CHESTPLATE)) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			check(context.computeOnClient(c -> PriceLookupCache.tradable().contains(id)), "%s is not a tradable item", id);
			ItemStack stack = new ItemStack(item);
			check(context.computeOnClient(c -> hasPage(stack, true)), "%s: %s has no market page", v, id);
			ItemStack shown = com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe.of(id).stack();
			check(shown.getDamageValue() == 0, "%s: the page shows a damaged %s", v, id);
			context.runOnClient(c -> viewer().showPage(stack));
			check(waitClient(context, c -> viewer().isViewerScreen(c.gui.screen()), 60), "%s: the page of %s did not open", v, id);
			check(waitPage(context, ClientViewerCases::showsTitle), "%s: the page of %s does not show its title", v, id);
			screenshot(context, result, "30-1_" + v + "_" + id.getPath());
			context.setScreen(() -> null);
		}
	}
}
