package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.client.FittedButton;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.NotebookScreen;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsGroups;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.entity.HiredHandEntity;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.NotebookS2C;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.notebook.LedgerService;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.staff.HiredHands;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.client.ClientStaffCases.K;
import static com.akf.tradeexpansion.test.client.ClientStaffCases.coins;
import static com.akf.tradeexpansion.test.client.ClientStaffCases.squeeze;
import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of texts the other cases left out: the shortage's "あと N 個売ると…" and the village's vigor (checklist
 * 9-5 / 9-6), the school's outlook on the trade screen and in a carrier's destinations (39-9), the price notes of the
 * merchant's notebook with two markets (40-9), and the staff screens of v0.83: "毎回選ぶ", the "おまかせ" button, the
 * notebook's staff page and the staff tab opened by talking to an employee (43-1 / 43-3 / 43-7 / 43-9).
 */
final class ClientScreenMoreCases {
	private ClientScreenMoreCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String NB = "screen." + NS + ".notebook.";
	private static final int RGB_GREEN = 0x55FF55;
	private static final int RGB_GOLD = 0xFFD700;
	private static final int RGB_RED = 0xFF5555;
	/** NotebookScreen's colors of the cheapest buy (blue) and the dearest sell (green) among the notes. */
	private static final int RGB_BEST = 0x1565C0;
	private static final int RGB_PROFIT = 0x2E7D32;
	private static final int[][] SIZES = {{854, 480, 0}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}};

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String x2(double m) {
		return String.format(java.util.Locale.ROOT, "×%.2f", m);
	}

	/** A completed building of {@code market} without its blocks; its sign at {@code core} keeps it from counting as destroyed. */
	private static void building(MinecraftServer server, Market market, FacilityKind kind, BlockPos core, List<Runnable> restore) {
		ServerLevel level = server.overworld();
		long now = MarketClock.now(level);
		Identifier id = Identifier.fromNamespaceAndPath(NS, kind.getSerializedName());
		Facility f = new Facility(id, kind);
		f.start(core, Rotation.NONE, core, new BoundingBox(core), "plains", now, now + 2 * PriceModel.TICKS_PER_DAY);
		level.setBlock(core, ModBlocks.FACILITY_SIGN.defaultBlockState().setValue(FacilitySignBlock.KIND, kind).setValue(FacilitySignBlock.BUILT, true), 2);
		f.complete(core);
		market.facilities().put(id, f);
		MarketRegistry.get(server).setDirty();
		restore.add(() -> {
			market.facilities().remove(id);
			level.setBlock(core, Blocks.AIR.defaultBlockState(), 2);
		});
	}

	private static void resize(ClientGameTestContext context, int[] size) {
		context.getInput().resizeWindow(size[0], size[1]);
		context.runOnClient(c -> {
			c.options.guiScale().set(size[2]);
			c.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void restoreWindow(ClientGameTestContext context) {
		resize(context, new int[] {854, 480, 0});
		ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
	}

	private static void runRestore(Session s, List<Runnable> restore) {
		s.world.getServer().runOnServer(server -> {
			for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
			MarketRegistry.get(server).setDirty();
		});
	}

	/** The tooltip over (x, y), every line inside the screen. */
	private static List<TextRecorder.Text> tipAt(ClientGameTestContext context, int x, int y) {
		moveTo(context, x, y);
		context.waitTicks(2);
		return ClientDisplayCases.tip(context);
	}

	/**
	 * The tooltip over (x, y) as the frame's last layer, every line inside the screen: a tooltip with more lines than
	 * the page has texts would be taken for the page by {@link ClientUtil#tooltip}.
	 */
	private static List<TextRecorder.Text> lastLayer(ClientGameTestContext context, int x, int y) {
		moveTo(context, x, y);
		context.waitTicks(2);
		List<TextRecorder.Text> all = TextRecorder.capture(context);
		int last = all.stream().mapToInt(TextRecorder.Text::stratum).max().orElse(0);
		List<TextRecorder.Text> tip = all.stream().filter(t -> t.stratum() == last).toList();
		int[] size = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight()});
		List<TextRecorder.Text> outside = tip.stream().filter(t -> t.x0() < 0 || t.y0() < 0 || t.x1() > size[0] || t.y1() > size[1]).toList();
		check(outside.isEmpty(), "tooltip lines run off the %dx%d screen: %s", size[0], size[1], outside);
		return tip;
	}

	private static TextRecorder.Text line(List<TextRecorder.Text> tip, String line, String what) {
		return tip.stream().filter(t -> t.text().equals(line)).findFirst()
				.orElseThrow(() -> new AssertionError(what + ": no \"" + line + "\" in " + strings(tip)));
	}

	/** A good of {@code market} that is bought and sold, one item, no components, not a special (the {@code skip}ped ones left out). */
	private static Identifier plainGood(Market market, List<Identifier> skip) {
		return market.regularGoods().stream().filter(g -> {
			var def = GoodsCatalog.get(g);
			return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && !market.isSpecial(g) && !skip.contains(g)
					&& !GoodsGroups.groupsOf(g).isEmpty();
		}).findFirst().orElseThrow(() -> new AssertionError("the market has no plain good"));
	}

	// ---- 9-5 / 9-6 the shortage's units left and the vigor

	/**
	 * 9-5: a shortage with 5 units already sold: its row's tooltip says "あと 11 個売ると品不足が満たされ、村が育つ" (gray)
	 * under the orange shortage line. 9-6: the development tab's header shows "元気 N" between the points and the level,
	 * green from 50, yellow from 25, red below; the mouse over the header shows the vigor, "貯まる発展度が ×0.9N" below
	 * 50, the shortages fulfilled and how many more bring a villager.
	 */
	static void shortageAndVigor(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<Runnable> restore = new ArrayList<>();
		try {
			Identifier good = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				Identifier g = plainGood(home, List.of());
				Map<Identifier, Double> shortages = new HashMap<>(home.shortages());
				Map<Identifier, Integer> sold = new HashMap<>(home.shortageSold());
				double vigor = home.vigor();
				long vigorTime = home.vigorTime();
				int fulfilled = home.shortagesFulfilled();
				restore.add(() -> {
					home.shortages().clear();
					home.shortages().putAll(shortages);
					home.shortageSold().clear();
					home.shortageSold().putAll(sold);
					home.setVigor(vigor);
					home.setVigorTime(vigorTime);
					home.setShortagesFulfilled(fulfilled);
				});
				home.shortages().put(g, 1.5);
				home.shortageSold().put(g, 5);
				MarketRegistry.get(server).setDirty();
				return g;
			});
			int required = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().demand.fulfillCount);
			MarketScreen screen = ClientDisplayCases.reopen(s);
			GoodView view = view(context, screen, good);
			check(view.shortage() && view.shortageLeft() == required - 5, "the snapshot says %d left of %s, not %d", view.shortageLeft(), good, required - 5);
			context.clickScreenButton("screen." + NS + ".tab." + (view.buy() ? "buy" : "sell"));
			context.waitTicks(2);
			int rowY = showRow(context, screen, good);
			List<TextRecorder.Text> tip = tipAt(context, listX(context, screen) + 60, rowY + 8);
			String shortLine = text("screen." + NS + ".shortage.tooltip", x2(1.5));
			String leftLine = text("screen." + NS + ".shortage.left", required - 5);
			TextRecorder.Text left = line(tip, leftLine, "9-5 the shortage row's tooltip");
			check(left.styleColor() == 0xAAAAAA, "\"%s\" is %06X, not gray", leftLine, left.styleColor());
			check(tip.indexOf(line(tip, shortLine, "9-5 the shortage row's tooltip")) < tip.indexOf(left), "the units left come before the shortage line: %s", strings(tip));
			screenshot(context, result, "9-5_shortage_left");
			mouseAway(context);

			// 9-6: the vigor at three levels.
			int every = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().demand.villagersEvery);
			for (double vigor : new double[] {80, 30, 10}) {
				s.world.getServer().runOnServer(server -> {
					Market home = market(server, stall);
					home.setVigor(vigor);
					home.setVigorTime(MarketClock.now(server.overworld()));
					home.setShortagesFulfilled(4);
					MarketRegistry.get(server).setDirty();
				});
				MarketScreen town = ClientDisplayCases.reopen(s);
				context.clickScreenButton("screen." + NS + ".tab.town");
				context.waitTicks(2);
				MarketSnapshotS2C.DevelopmentView d = snapshot(context, town).development();
				check(Math.floor(d.vigor()) == vigor, "the snapshot's vigor is %.1f, not %.0f", d.vigor(), vigor);
				int headY = context.computeOnClient(c -> topPos(town)) + constant(MarketScreen.class, "LIST_Y");
				mouseAway(context);
				List<TextRecorder.Text> header = TextRecorder.capture(context).stream().filter(t -> t.y0() >= headY && t.y0() < headY + 10).toList();
				String label = text("screen." + NS + ".town.vigor", (long) vigor);
				int rgb = vigor >= 50 ? RGB_GREEN : vigor >= 25 ? RGB_GOLD : RGB_RED;
				TextRecorder.Text shown = ClientDisplayCases.find(header, label, "9-6 the vigor in the header");
				check((shown.color() & 0xFFFFFF) == rgb, "\"%s\" is %06X, not %06X", label, shown.color() & 0xFFFFFF, rgb);
				List<TextRecorder.Text> others = header.stream().filter(t -> t != shown).toList();
				for (TextRecorder.Text o : others) {
					check(o.x1() <= shown.x0() || o.x0() >= shown.x1(), "\"%s\" runs into \"%s\" in the header", label, o.text());
				}
				List<TextRecorder.Text> headTip = lastLayer(context, (shown.x0() + shown.x1()) / 2, headY + 5);
				String all = String.join("", strings(headTip));
				for (String expected : List.of(label, text("screen." + NS + ".town.vigor.fulfilled", 4, every - 4 % every),
						text("screen." + NS + ".town.vigor.how"))) {
					check(all.contains(expected) || String.join(" ", strings(headTip)).contains(expected), "the header's tooltip (vigor %.0f) has no \"%s\": %s",
							vigor, expected, strings(headTip));
				}
				String low = text("screen." + NS + ".town.vigor.low", x2(d.vigorMultiplier()));
				boolean hasLow = all.contains(low) || String.join(" ", strings(headTip)).contains(low);
				check(hasLow == (vigor < 50 && d.vigorMultiplier() < 1 - 1e-9), "the header's tooltip (vigor %.0f, ×%.3f) %s \"%s\"", vigor, d.vigorMultiplier(),
						hasLow ? "has" : "has no", low);
				screenshot(context, also, "9-6_vigor_" + (long) vigor);
				mouseAway(context);
			}
		} finally {
			mouseAway(context);
			context.setScreen(() -> null);
			runRestore(s, restore);
		}
	}

	// ---- 39-9 the school's outlook

	/**
	 * 39-9: a school in the stall's village, a good short and a trend in fashion there: the shortage row's tooltip, the
	 * trend row's tooltip and the "流行" label's tooltip each end with "学舎の見立て：…はあと N 日で入れ替わります" (the days
	 * of the snapshot). Hiring a carrier of a good short and in fashion at the other market: the destination's tooltip has
	 * "学舎の見立て（行き先の相場）：", "・品不足：基準価格 ×1.50（あと N 日）" and the trend line. Every tooltip inside the
	 * screen, in Japanese and English at every size.
	 */
	static void schoolOutlook(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		ClientStaffMoreCases.fresh(s, c -> {
		});
		BlockPos stall = s.stall;
		List<Runnable> restore = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		try {
			Identifier[] goods = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				Market other = ClientStaffCases.other(server, home);
				building(server, home, FacilityKind.SCHOOL, stall.above(9), restore);
				Map<Identifier, Double> homeShortages = new HashMap<>(home.shortages());
				Map<Identifier, Double> homeTrends = new HashMap<>(home.trends());
				Map<Identifier, Double> otherShortages = new HashMap<>(other.shortages());
				Map<Identifier, Double> otherTrends = new HashMap<>(other.trends());
				restore.add(() -> {
					home.shortages().clear();
					home.shortages().putAll(homeShortages);
					home.trends().clear();
					home.trends().putAll(homeTrends);
					other.shortages().clear();
					other.shortages().putAll(otherShortages);
					other.trends().clear();
					other.trends().putAll(otherTrends);
				});
				Identifier shortGood = plainGood(home, List.of(ClientStaffCases.carrierGood));
				home.shortages().put(shortGood, 1.5);
				Identifier group = GoodsGroups.groupsOf(ClientStaffCases.carrierGood).stream().findFirst()
						.orElseThrow(() -> new AssertionError(ClientStaffCases.carrierGood + " is in no group"));
				home.trends().clear();
				home.trends().put(group, 1.2);
				other.shortages().put(ClientStaffCases.carrierGood, 1.5);
				other.trends().clear();
				other.trends().put(group, 1.2);
				MarketRegistry.get(server).setDirty();
				return new Identifier[] {shortGood, ClientStaffCases.carrierGood};
			});

			// The trade screen.
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size);
					String where = String.format("%s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					MarketScreen screen = ClientDisplayCases.reopen(s);
					MarketSnapshotS2C snapshot = snapshot(context, screen);
					check(snapshot.outlook().school(), "the snapshot has no school's outlook");
					String shortageDays = text("screen." + NS + ".outlook.shortage", snapshot.outlook().shortageDays());
					String trendDays = text("screen." + NS + ".outlook.trend", snapshot.outlook().trendDays());
					for (Object[] row : new Object[][] {{goods[0], shortageDays}, {goods[1], trendDays}}) {
						Identifier good = (Identifier) row[0];
						GoodView v = view(context, screen, good);
						context.clickScreenButton("screen." + NS + ".tab." + (v.buy() ? "buy" : "sell"));
						context.waitTicks(2);
						int rowY = showRow(context, screen, good);
						try {
							List<TextRecorder.Text> tip = tipAt(context, listX(context, screen) + 60, rowY + 8);
							String expected = (String) row[1];
							String joined = String.join(" ", strings(tip));
							if (!joined.contains(expected) && !String.join("", strings(tip)).contains(expected)) {
								problems.add(where + ": the tooltip of " + good + " has no \"" + expected + "\": " + strings(tip));
							}
							if (size == SIZES[0]) screenshot(context, result, "39-9_outlook_row_" + good.getPath() + "_" + language);
						} catch (AssertionError e) {
							problems.add(where + " " + good + ": " + e.getMessage());
						}
						mouseAway(context);
					}
					// The "流行" label.
					int top = context.computeOnClient(c -> topPos(screen));
					mouseAway(context);
					List<TextRecorder.Text> titleRow = TextRecorder.capture(context).stream().filter(t -> t.y0() >= top + 2 && t.y0() < top + 14).toList();
					TextRecorder.Text label = titleRow.stream().filter(t -> t.text().equals(text("screen." + NS + ".trend.label"))).findFirst().orElse(null);
					if (label == null) {
						problems.add(where + ": no \"" + text("screen." + NS + ".trend.label") + "\" in the title row: " + strings(titleRow));
					} else {
						try {
							List<TextRecorder.Text> tip = ClientDisplayCases.hover(context, label);
							String joined = String.join(" ", strings(tip));
							if (!joined.contains(trendDays) && !String.join("", strings(tip)).contains(trendDays)) {
								problems.add(where + ": the trends tooltip has no \"" + trendDays + "\": " + strings(tip));
							}
						} catch (AssertionError e) {
							problems.add(where + " trends: " + e.getMessage());
						}
						mouseAway(context);
					}
				}
			}
			restoreWindow(context);

			// A carrier's destinations.
			Object staff = ClientStaffCases.openTab(s);
			MarketScreen screen = screen(context);
			long otherId = s.world.getServer().computeOnServer(server -> ClientStaffCases.other(server, market(server, stall)).id());
			clickWidget(context, text(K + "hire.carrier"));
			check(waitClient(context, c -> get(staff, "carrierHints") != null, 100), "no recommendations of the goods arrived");
			context.waitTicks(2);
			@SuppressWarnings("unchecked")
			List<GoodView> pickable = context.computeOnClient(c -> (List<GoodView>) call(staff, "pickableGoods"));
			int index = -1;
			for (int i = 0; i < pickable.size(); i++) if (pickable.get(i).goods().equals(goods[1])) index = i + 1;
			check(index > 0, "%s is not in the goods of a carrier", goods[1]);
			ClientStaffCases.clickRow(context, screen, staff, index);
			@SuppressWarnings("unchecked")
			boolean listed = waitClient(context, c -> !((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).isEmpty(), 100);
			check(listed, "no destinations arrived");
			@SuppressWarnings("unchecked")
			List<StaffS2C.Destination> destinations = context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
			int di = -1;
			for (int i = 0; i < destinations.size(); i++) if (destinations.get(i).id() == otherId) di = i;
			check(di >= 0, "the other market is not a destination");
			StaffS2C.Destination d = destinations.get(di);
			StaffS2C.Outlook o = d.outlook().orElseThrow(() -> new AssertionError("the destination has no school's outlook"));
			check(o.shortage() == 1.5 && o.trend() > 1, "the outlook says shortage ×%.2f, trend ×%.2f", o.shortage(), o.trend());
			String tip = ClientStaffCases.rowTooltip(context, screen, staff, di);
			for (String expected : List.of(text(K + "outlook"), text(K + "outlook.shortage", x2(o.shortage()), o.shortageDays()),
					text(K + "outlook.trend_up", x2(o.trend()), o.trendDays()))) {
				check(tip.contains(squeeze(expected)), "the destination's tooltip has no \"%s\": %s", expected, tip);
			}
			int y = ClientStaffCases.showRow(context, screen, staff, di);
			moveTo(context, listX(context, screen) + 60, y + 8);
			context.waitTicks(2);
			screenshot(context, result, "39-9_outlook_destination");
			mouseAway(context);
		} finally {
			restoreWindow(context);
			mouseAway(context);
			context.setScreen(() -> null);
			runRestore(s, restore);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	// ---- 40-9 the price notes with two markets

	/** Opens the merchant's notebook (held in the last hotbar slot) with the use key, looking at the sky. */
	private static float[] openNotebook(Session s) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(8, new ItemStack(ModItems.MERCHANT_NOTEBOOK)));
		float[] rot = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
		context.getInput().lookAt(rot[0], -90);
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(NotebookScreen.class);
		context.waitTicks(3);
		return rot;
	}

	private static void closeNotebook(Session s, float[] rot) {
		s.context.setScreen(() -> null);
		if (rot != null) s.context.getInput().lookAt(rot[0], rot[1]);
		s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(8, ItemStack.EMPTY));
		s.context.waitTicks(2);
	}

	private static LayoutCheck.Panel notebookPanel(Object screen) {
		int left = (int) get(screen, "left");
		int top = (int) get(screen, "top");
		return new LayoutCheck.Panel(left, top, left + (int) get(screen, "panelW"), top + (int) get(screen, "panelH"));
	}

	private static void notebookTab(ClientGameTestContext context, String tab) {
		boolean current = context.computeOnClient(c -> String.valueOf(get(c.gui.screen(), "tab")).equalsIgnoreCase(tab));
		if (!current) clickWidget(context, text(NB + "tab." + tab));
		context.waitTicks(2);
	}

	/**
	 * 40-9: the prices of the stall's market and of the other market noted, one good cheap here and dear there: the
	 * goods with the best spread come first; choosing that good shows "いちばん安い所で買って高い所で売ると +N" in green,
	 * each market's buy and sell price with the cheapest buy blue and the dearest sell green, the day and where each
	 * market is; the mouse over a row says it was noted then, and which is the cheapest / dearest. No text on another
	 * (Japanese and English, every size).
	 */
	static void priceNotes(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<Runnable> restore = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		float[] rot = null;
		try {
			Identifier good = s.world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				Market home = market(server, stall);
				Market other = ClientStaffCases.other(server, home);
				long now = MarketUpkeep.touch(server.overworld(), other);
				Identifier g = plainGood(home, List.of());
				if (other.good(g) == null) {
					other.addGood(g, new GoodState(1.0, 0, GoodsCatalog.get(g).targetStock(), now));
					restore.add(() -> other.removeGood(g));
				}
				double homePressure = home.good(g).pressure(), otherPressure = other.good(g).pressure();
				restore.add(() -> {
					home.good(g).setPressure(homePressure);
					other.good(g).setPressure(otherPressure);
				});
				home.good(g).setPressure(home.params(g).maxPressure() * 0.9); // cheap here
				other.good(g).setPressure(other.params(g).minPressure() * 0.9); // dear there
				LedgerService.notePrices(player, home, MarketUpkeep.touch(server.overworld(), home));
				LedgerService.notePrices(player, other, MarketUpkeep.touch(server.overworld(), other));
				MarketRegistry.get(server).setDirty();
				return g;
			});
			rot = openNotebook(s);
			notebookTab(context, "prices");
			Object screen = context.computeOnClient(c -> c.gui.screen());
			NotebookS2C data = context.computeOnClient(c -> (NotebookS2C) get(screen, "data"));
			check(data.notes().size() >= 2, "the notebook has %d price notes, not the 2 markets", data.notes().size());
			List<?> rows = context.computeOnClient(c -> (List<?>) call(screen, "goodsRows"));
			// The goods with the best spread first.
			long first = (Long) get(rows.getFirst(), "spread");
			for (Object r : rows) check((Long) get(r, "spread") <= first, "the goods are not by their spread: %s first", get(rows.getFirst(), "goods"));
			int index = -1;
			for (int i = 0; i < rows.size(); i++) if (get(rows.get(i), "goods").equals(good)) index = i;
			check(index >= 0, "%s is not on the price notes", good);
			long spread = (Long) get(rows.get(index), "spread");
			check(spread > 0, "%s (cheap here, dear there) has no spread: %d", good, spread);
			int left = context.computeOnClient(c -> (Integer) get(screen, "left"));
			int top = context.computeOnClient(c -> (Integer) get(screen, "top"));
			int listTop = top + constant(NotebookScreen.class, "TOP");
			int rowH = constant(NotebookScreen.class, "ROW_H");
			int visible = context.computeOnClient(c -> (Integer) call(screen, "visibleRows", 0));
			int scrollTo = Math.max(0, index - visible + 1);
			context.runOnClient(c -> set(screen, "goodsScroll", scrollTo));
			context.waitTicks(1);
			clickAt(context, left + 40, listTop + (index - scrollTo) * rowH + 8);
			mouseAway(context);
			check(good.equals(context.computeOnClient(c -> get(screen, "selectedGoods"))), "a click on the row did not choose %s", good);

			List<?> prices = context.computeOnClient(c -> (List<?>) call(screen, "priceRows", good));
			check(prices.size() >= 2, "%s has %d prices noted", good, prices.size());
			long minBuy = Long.MAX_VALUE, maxSell = Long.MIN_VALUE;
			for (Object r : prices) {
				NotebookS2C.PriceView p = (NotebookS2C.PriceView) get(r, "price");
				if (p.buy() >= 0) minBuy = Math.min(minBuy, p.buy());
				if (p.sell() >= 0) maxSell = Math.max(maxSell, p.sell());
			}
			check(maxSell - minBuy == spread, "the spread %d is not the dearest sell %d less the cheapest buy %d", spread, maxSell, minBuy);
			int px = left + constant(NotebookScreen.class, "GOODS_LIST_W") + 6;
			List<TextRecorder.Text> page = TextRecorder.capture(context).stream().filter(t -> t.x0() >= px).toList();
			String summary = text(NB + "prices.spread", "+" + CoinItem.formatCopper(spread));
			TextRecorder.Text sum = page.stream().filter(t -> t.text().equals(summary) || summary.startsWith(t.text().replace("…", "")) && t.text().length() > 4)
					.findFirst().orElseThrow(() -> new AssertionError("the page does not say \"" + summary + "\": " + strings(page)));
			check((sum.color() & 0xFFFFFF) == RGB_PROFIT, "\"%s\" is %06X, not green", summary, sum.color() & 0xFFFFFF);
			String cheap = CoinItem.formatCopper(minBuy), dear = CoinItem.formatCopper(maxSell);
			List<TextRecorder.Text> blue = page.stream().filter(t -> (t.color() & 0xFFFFFF) == RGB_BEST).toList();
			check(blue.size() == 1 && blue.getFirst().text().equals(cheap), "the blue prices are %s, not the cheapest buy %s", strings(blue), cheap);
			List<TextRecorder.Text> green = page.stream().filter(t -> (t.color() & 0xFFFFFF) == RGB_PROFIT && t != sum).toList();
			check(green.size() == 1 && green.getFirst().text().equals(dear), "the green prices are %s, not the dearest sell %s", strings(green), dear);
			// Each market's name and "N日前  北東 340m" under it.
			for (Object r : prices) {
				NotebookS2C.NoteView note = (NotebookS2C.NoteView) get(r, "note");
				String name = note.title().getString();
				check(page.stream().anyMatch(t -> t.text().length() >= 2 && name.startsWith(t.text().replace("…", ""))), "the page has no market \"%s\": %s", name, strings(page));
			}
			check(page.stream().anyMatch(t -> t.text().matches(".+ [0-9,]+m")), "the page does not say where the markets are: %s", strings(page));
			screenshot(context, result, "40-9_price_notes");

			// The tooltips of the cheapest and the dearest market.
			int hy = listTop + 35;
			int priceRowH = constant(NotebookScreen.class, "PRICE_ROW_H");
			for (int i = 0; i < prices.size(); i++) {
				NotebookS2C.PriceView p = (NotebookS2C.PriceView) get(prices.get(i), "price");
				List<TextRecorder.Text> tip = tipAt(context, px + 20, hy + i * priceRowH + 6);
				String joined = String.join("", strings(tip));
				check(joined.contains(text(NB + "prices.tooltip.old")) || String.join(" ", strings(tip)).contains(text(NB + "prices.tooltip.old")),
						"the price row's tooltip has no \"%s\": %s", text(NB + "prices.tooltip.old"), strings(tip));
				for (Object[] mark : new Object[][] {{p.buy() == minBuy, "cheapest"}, {p.sell() == maxSell, "dearest"}}) {
					String line = text(NB + "prices.tooltip." + mark[1]);
					boolean has = joined.contains(line) || String.join(" ", strings(tip)).contains(line);
					check(has == (Boolean) mark[0], "row %d's tooltip %s \"%s\": %s", i, has ? "has" : "has no", line, strings(tip));
				}
				if (p.buy() == minBuy) screenshot(context, result, "40-9_price_notes_cheapest_tooltip");
			}
			mouseAway(context);

			// The layout of the page.
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size);
					Object now = context.computeOnClient(c -> c.gui.screen());
					check(now instanceof NotebookScreen, "the notebook closed while resizing");
					context.runOnClient(c -> set(now, "selectedGoods", good));
					context.waitTicks(2);
					mouseAway(context);
					String where = String.format("notebook prices %s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					// The hotbar's counts behind the notebook (the bottom 22 pixels, outside the page) are not the page's texts.
					int bottom = context.computeOnClient(c -> c.getWindow().getGuiScaledHeight()) - 22;
					int pageBottom = context.computeOnClient(c -> (Integer) get(now, "top") + (Integer) get(now, "panelH"));
					List<TextRecorder.Text> texts = TextRecorder.capture(context).stream().filter(t -> t.y0() < bottom || t.y1() <= pageBottom).toList();
					List<String> found = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, where, texts, notebookPanel(now)));
					if (!found.isEmpty()) screenshot(context, result, "layout_" + where);
					problems.addAll(found);
				}
			}
		} finally {
			restoreWindow(context);
			closeNotebook(s, rot);
			runRestore(s, restore);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	// ---- 43-1 / 43-3 / 43-7 / 43-9 the staff screens of v0.83

	/**
	 * A third market, a village 700 blocks south (its chunks kept loaded), dealing in {@code good}: a second destination
	 * for a carrier. Taken away with {@code restore}.
	 */
	private static long thirdMarket(Session s, BlockPos stall, Identifier good, List<Runnable> restore) {
		BlockPos feet = s.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos f = ground(server, stall.getX(), stall.getZ() + 700);
			for (int cx = (f.getX() >> 4) - 2; cx <= (f.getX() >> 4) + 2; cx++) {
				for (int cz = (f.getZ() >> 4) - 2; cz <= (f.getZ() >> 4) + 2; cz++) {
					if (level.setChunkForced(cx, cz, true)) {
						int x = cx, z = cz;
						restore.add(() -> level.setChunkForced(x, z, false));
					}
				}
			}
			village(level, f);
			village(level, f.west(12));
			return f;
		});
		s.context.waitTicks(20);
		return s.world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			MarketRegistry registry = MarketRegistry.get(server);
			BlockPos at = ground(server, feet.getX(), feet.getZ() - 2);
			Market existing = registry.all().stream().filter(m -> m.dimension().equals(level.dimension()) && m.horizontalDistanceTo(at) < 64).findFirst().orElse(null);
			Market third;
			if (existing != null) {
				third = existing;
			} else {
				level.setBlock(at, ModBlocks.MARKET_STALL.defaultBlockState(), 3);
				MarketRegistry.Assignment a = ((MarketStallBlockEntity) level.getBlockEntity(at)).assign(level, MarketOrigin.CRAFTED);
				check(a.market() != null && a.created(), "the stall 700 blocks south founded no market (%s)", a.reason() == null ? a.rejection() : a.reason().getString());
				third = a.market();
				long id = third.id();
				restore.add(() -> {
					level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
					registry.remove(id);
				});
			}
			long now = MarketUpkeep.touch(level, third);
			if (third.good(good) == null) third.addGood(good, new GoodState(1.0, 0, GoodsCatalog.get(good).targetStock(), now));
			third.good(good).setPressure(third.params(good).minPressure() * 0.5); // dear there too
			registry.setDirty();
			return third.id();
		});
	}

	/**
	 * 43-1: a carrier of a good with two destinations: the first row of the destinations is "毎回選ぶ（今なら○○）" with
	 * the tooltip "出かけるたびに、近くの4 か所の市場から…"; hired so, the list shows "毎回選ぶ（今は○○）". 43-3: the
	 * carrier's right panel has "おまかせ" (not cut), with its tooltip; pressing it makes the carrier おまかせ and the button
	 * goes. 43-7: the employee standing at the market, talked to, opens the trade screen on the staff tab with it
	 * selected. 43-9: the notebook's staff page lists it on two lines (role, goods and destination with the status; home
	 * market and purse), cut with "…" for a long market name, its tooltip with the purse and where to change it; no text
	 * on another (Japanese and English, every size).
	 */
	static void staffScreens(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		ClientStaffMoreCases.fresh(s, c -> c.showEmployees = true);
		BlockPos stall = s.stall;
		Identifier good = ClientStaffCases.carrierGood;
		List<Runnable> restore = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		float[] rot = null;
		try {
			long thirdId = thirdMarket(s, stall, good, restore);
			Object staff = ClientStaffCases.openTab(s);
			MarketScreen screen = screen(context);
			clickWidget(context, text(K + "hire.carrier"));
			check(waitClient(context, c -> get(staff, "carrierHints") != null, 100), "no recommendations of the goods arrived");
			context.waitTicks(2);
			@SuppressWarnings("unchecked")
			List<GoodView> pickable = context.computeOnClient(c -> (List<GoodView>) call(staff, "pickableGoods"));
			int index = -1;
			for (int i = 0; i < pickable.size(); i++) if (pickable.get(i).goods().equals(good)) index = i + 1;
			check(index > 0, "%s is not in the goods of a carrier", good);
			ClientStaffCases.clickRow(context, screen, staff, index);
			@SuppressWarnings("unchecked")
			boolean listed = waitClient(context, c -> ((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).size() >= 3, 100);
			@SuppressWarnings("unchecked")
			List<StaffS2C.Destination> destinations = context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
			check(listed, "fewer than 2 destinations and the row of choosing every trip: %s", destinations.stream().map(d -> d.name().getString()).toList());
			check(destinations.stream().anyMatch(d -> d.id() == thirdId), "the market 700 blocks south is not a destination");
			StaffS2C.Destination roam = destinations.getFirst();
			check(roam.id() == Employee.ROAM, "the first destination is \"%s\", not choosing every trip", roam.name().getString());
			String roamName = roam.name().getString();
			check(roamName.startsWith(ClientStaffCases.prefix("staff." + NS + ".roam.now")), "the first destination is \"%s\"", roamName);
			List<String> row = ClientStaffCases.rowStrings(context, screen, staff, 0);
			check(row.stream().anyMatch(t -> t.length() >= 3 && roamName.startsWith(t.replace("…", ""))), "the first row does not show \"%s\": %s", roamName, row);
			int roamChoices = ClientStaffCases.data(context, staff).info().roamChoices();
			String tip = ClientStaffCases.rowTooltip(context, screen, staff, 0);
			check(tip.contains(squeeze(text(K + "roam.tooltip", roamChoices))), "the first row's tooltip has no \"%s\": %s", text(K + "roam.tooltip", roamChoices), tip);
			int y = ClientStaffCases.showRow(context, screen, staff, 0);
			moveTo(context, listX(context, screen) + 60, y + 8);
			context.waitTicks(2);
			screenshot(context, result, "43-1_roam_row");
			mouseAway(context);
			// Hired choosing every trip.
			ClientStaffCases.clickRow(context, screen, staff, 0);
			check(ClientStaffCases.mode(context, staff).equals("SETUP"), "a click on the row opened %s, not the setup", ClientStaffCases.mode(context, staff));
			long fee = ClientStaffCases.data(context, staff).info().carrierFee();
			clickWidget(context, text(K + "confirm", coins(fee)));
			mouseAway(context);
			check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().anyMatch(v -> v.goods().equals(good)), 60), "the carrier is not listed");
			context.waitTicks(2);
			StaffS2C.View carrier = ClientStaffCases.data(context, staff).staff().stream().filter(v -> v.goods().equals(good)).findFirst().orElseThrow();
			check(carrier.roam(), "the carrier does not choose its destination every trip");
			int id = carrier.id();
			String label = text(K + "roam.label", carrier.destinationName());
			String list = ClientStaffCases.all(context);
			check(list.contains(squeeze(label)) || list.contains(squeeze(label).substring(0, Math.min(6, squeeze(label).length()))),
					"the tab does not show \"%s\": %s", label, list);
			screenshot(context, result, "43-1_roam_hired");

			// 43-3: the "おまかせ" button of a carrier of one good.
			ClientStaffCases.select(context, staff, id);
			Button auto = ClientStaffCases.button(staff, "autoButton");
			check(context.computeOnClient(c -> auto.visible && auto.active), "the carrier's panel has no \"%s\" to press", text(K + "to_auto"));
			int width = context.computeOnClient(c -> c.font.width(auto.getMessage()));
			check(width <= auto.getWidth() - 4 || auto instanceof FittedButton && width * FittedButton.MIN_SCALE <= auto.getWidth() - 4,
					"\"%s\" (%d px) is cut in %d px", auto.getMessage().getString(), width, auto.getWidth());
			moveTo(context, auto.getX() + auto.getWidth() / 2.0, auto.getY() + auto.getHeight() / 2.0);
			context.waitTicks(15);
			String autoTip = squeeze(String.join("", strings(tooltip(TextRecorder.capture(context)))));
			check(autoTip.contains(squeeze(text(K + "to_auto.tooltip")).substring(0, 10)), "\"%s\" has no tooltip: %s", text(K + "to_auto"), autoTip);
			screenshot(context, also, "43-3_to_auto_button");
			clickWidget(context, auto);
			mouseAway(context);
			ClientStaffCases.waitView(context, staff, id, StaffS2C.View::auto, 100, "おまかせ");
			ClientStaffCases.select(context, staff, id);
			check(!context.computeOnClient(c -> auto.visible), "\"%s\" still shows for an おまかせ carrier", text(K + "to_auto"));
			context.setScreen(() -> null);
			context.waitTicks(2);

			// 43-7: talking to the employee standing at the market.
			boolean talked = s.world.getServer().computeOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				HiredHands.update(server, registry);
				Market home = market(server, stall);
				ServerPlayer player = player(server);
				HiredHandEntity hand = server.overworld().getEntitiesOfClass(HiredHandEntity.class, new AABB(home.center()).inflate(32),
						h -> !h.isRemoved() && h.employeeId() == id).stream().findFirst().orElse(null);
				if (hand == null) return false;
				player.interactOn(hand, InteractionHand.MAIN_HAND, Vec3.ZERO);
				return true;
			});
			check(talked, "the carrier does not stand at the market");
			context.waitForScreen(MarketScreen.class);
			context.waitTicks(5);
			MarketScreen opened = screen(context);
			String openedTab = context.computeOnClient(c -> String.valueOf(get(opened, "tab")));
			check(openedTab.equals("STAFF"), "talking to the employee opened the tab %s", openedTab);
			Object staffTab = context.computeOnClient(c -> get(opened, "staffTab"));
			check(waitClient(context, c -> get(staffTab, "data") != null, 100), "the staff tab got no list");
			context.waitTicks(2);
			check(context.computeOnClient(c -> (Integer) get(staffTab, "selected")) == id, "the staff tab opened with #%s selected, not #%d",
					context.computeOnClient(c -> get(staffTab, "selected")), id);
			screenshot(context, also, "43-7_talk_opens_staff");
			context.setScreen(() -> null);
			context.waitTicks(2);

			// 43-9: the notebook's staff page, the home market with a long name.
			s.world.getServer().runOnServer(server -> {
				Market home = market(server, stall);
				String old = home.name();
				restore.add(() -> home.setName(old));
				MarketNames.rename(home, "とても長い名前の交易の盛んな港町の市場", null);
				MarketRegistry.get(server).setDirty();
			});
			rot = openNotebook(s);
			notebookTab(context, "staff");
			Object notebook = context.computeOnClient(c -> c.gui.screen());
			NotebookS2C data = context.computeOnClient(c -> (NotebookS2C) get(notebook, "data"));
			StaffS2C.View v = data.staff().stream().filter(x -> x.id() == id).findFirst()
					.orElseThrow(() -> new AssertionError("the notebook does not list the carrier: " + data.staff()));
			int left = context.computeOnClient(c -> (Integer) get(notebook, "left"));
			int top = context.computeOnClient(c -> (Integer) get(notebook, "top"));
			int listTop = top + constant(NotebookScreen.class, "TOP");
			int rowH = constant(NotebookScreen.class, "ROW_H");
			int at = data.staff().indexOf(v);
			mouseAway(context);
			List<TextRecorder.Text> rowTexts = TextRecorder.capture(context).stream().filter(t -> t.y0() >= listTop + at * rowH && t.y0() < listTop + (at + 1) * rowH).toList();
			String role = text("staff." + NS + ".role.carrier");
			check(rowTexts.stream().anyMatch(t -> t.text().startsWith(role)), "the row does not start with \"%s\": %s", role, strings(rowTexts));
			String home = "＠" + v.homeName().getString();
			TextRecorder.Text homeText = rowTexts.stream().filter(t -> t.text().startsWith("＠")).findFirst()
					.orElseThrow(() -> new AssertionError("the row has no \"＠home market\": " + strings(rowTexts)));
			check(homeText.text().equals(home) || homeText.text().endsWith("…") && home.startsWith(homeText.text().replace("…", "")),
					"the home market is \"%s\", not \"%s\" (or cut with …)", homeText.text(), home);
			String purse = CoinItem.formatCopper(v.purse());
			check(rowTexts.stream().anyMatch(t -> t.text().equals(purse)), "the row has no purse %s: %s", purse, strings(rowTexts));
			check(rowTexts.stream().map(TextRecorder.Text::y0).distinct().count() >= 2, "the row is not on two lines: %s", rowTexts);
			List<TextRecorder.Text> rowTip = lastLayer(context, left + 60, listTop + at * rowH + 8);
			String tipText = squeeze(String.join("", strings(rowTip)));
			for (String expected : List.of(text(K + "purse", purse), text(NB + "staff.hint"))) {
				check(tipText.contains(squeeze(expected)), "the notebook row's tooltip has no \"%s\": %s", expected, strings(rowTip));
			}
			screenshot(context, also, "43-9_notebook_staff");
			mouseAway(context);
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size);
					Object now = context.computeOnClient(c -> c.gui.screen());
					check(now instanceof NotebookScreen, "the notebook closed while resizing");
					mouseAway(context);
					String where = String.format("notebook staff %s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					// The hotbar's counts behind the notebook (the bottom 22 pixels, outside the page) are not the page's texts.
					int bottom = context.computeOnClient(c -> c.getWindow().getGuiScaledHeight()) - 22;
					int pageBottom = context.computeOnClient(c -> (Integer) get(now, "top") + (Integer) get(now, "panelH"));
					List<TextRecorder.Text> texts = TextRecorder.capture(context).stream().filter(t -> t.y0() < bottom || t.y1() <= pageBottom).toList();
					List<String> found = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, where, texts, notebookPanel(now)));
					if (!found.isEmpty()) screenshot(context, also, "layout_" + where);
					problems.addAll(found);
				}
			}
		} finally {
			restoreWindow(context);
			closeNotebook(s, rot);
			mouseAway(context);
			s.world.getServer().runOnServer(server -> {
				MarketRegistry.get(server).employees().removeIf(e -> e.owner().equals(player(server).getUUID()));
				HiredHands.update(server, MarketRegistry.get(server));
			});
			runRestore(s, restore);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		if (!problems.isEmpty()) {
			throw new ClientChecklistTests.AlsoFailed(new AssertionError(problems.size() + " layout problems of the notebook's staff page, e.g. "
					+ String.join(" | ", problems.subList(0, Math.min(8, problems.size())))));
		}
	}
}
