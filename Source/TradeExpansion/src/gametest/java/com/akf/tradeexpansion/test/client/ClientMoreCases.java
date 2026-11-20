package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of what the trade screen shows after things the server tests check only as numbers: a good bought
 * until it sells out and sold until the market stops buying (2-11, 2-12), the debug tooltip of consumables and after
 * a warehouse (22-27, 30-7, 32-2), and the money after dying with a wallet (7-6, 7-7).
 */
final class ClientMoreCases {
	private ClientMoreCases() {
	}

	private static final String S = "screen.ak_trade_expansion.";
	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_GOLD = 0xFFFFD700;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	private static MarketScreen reopen(Session s, String tab) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		s.context.clickScreenButton(S + "tab." + tab);
		s.context.waitTicks(3);
		mouseAway(s.context);
		return screen;
	}

	private static GoodState state(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		return context.computeOnClient(c -> (GoodState) call(screen, "currentState", view(context, screen, good)));
	}

	/** Sends the trade request the screen sends for {@code qty} of {@code good} (the total the screen previews) and waits for the answer. */
	private static void trade(Session s, MarketScreen screen, Identifier good, boolean buy, int qty) {
		ClientGameTestContext context = s.context;
		GoodView view = view(context, screen, good);
		long marketId = context.computeOnClient(c -> screen.getMenu().marketId());
		long total = context.computeOnClient(c -> (Long) call(screen, "previewTotal", view, qty));
		var before = snapshot(context, screen);
		long money = serverMoney(s);
		context.runOnClient(c -> ClientPlayNetworking.send(new TradeRequestC2S(marketId, good, buy, qty, total, false, false)));
		for (int i = 0; i < 40 && serverMoney(s) == money; i++) context.waitTick();
		check(serverMoney(s) == money + (buy ? -total : total), "%s %d of %s for the shown %dc: the money went %d -> %d", buy ? "buying" : "selling", qty, good,
				total, money, serverMoney(s));
		newSnapshot(context, screen, before);
		context.waitTicks(2);
		mouseAway(context);
	}

	/** The row of {@code good}: its texts, and the price it shows (in gold). */
	private static List<TextRecorder.Text> row(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		int rowY = showRow(context, screen, good);
		mouseAway(context);
		return rowTexts(TextRecorder.capture(context), rowY);
	}

	private static void unavailable(ClientGameTestContext context, MarketScreen screen, Identifier good, String label, String reasonKey, String minutesKey) {
		int rowY = showRow(context, screen, good);
		mouseAway(context);
		List<TextRecorder.Text> texts = rowTexts(TextRecorder.capture(context), rowY);
		check(texts.stream().anyMatch(t -> t.text().equals(label) && t.color() == COLOR_RED), "the row of %s shows no red \"%s\": %s", good, label, strings(texts));
		moveTo(context, listX(context, screen) + 40, rowY + 8);
		context.waitTicks(2);
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		check(tip.stream().anyMatch(t -> t.text().equals(text(reasonKey))), "the tooltip has no \"%s\": %s", text(reasonKey), strings(tip));
		check(tip.stream().anyMatch(t -> t.text().startsWith(prefix(minutesKey))), "the tooltip has no \"%s…\": %s", prefix(minutesKey), strings(tip));
		mouseAway(context);
	}

	/** Units after which the price has moved by a whole coin or more (V moves by exp(n / τ)). */
	private static int step(GoodView v, long price) {
		return (int) Math.ceil(v.params().elasticity() * Math.log((price + 2.0) / price)) + 1;
	}

	// ---- 2-11 / 2-12

	/**
	 * 2-11: buying the same good again and again raises the price on its row each time; when the stock is gone the row
	 * says "売り切れ" in red and its tooltip the time until it comes in again. 2-12: selling lowers the sell price and
	 * pays; at the stock limit the row says "買取停止" with the time until the market buys again. The trades are the
	 * requests the screen's buttons send (with the total the screen shows).
	 */
	static void soldOutAndFull(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		List<ItemStack> inventory = ClientBuySellCases.saveInventory(s);
		BlockPos stall = s.stall;
		Map<Identifier, double[]> saved = new HashMap<>();
		try {
			s.world.getServer().runOnServer(server -> {
				if (WalletService.balance(player(server)) < 200_000) WalletService.give(player(server), 200_000);
			});
			MarketScreen screen = reopen(s, "buy");
			GoodView buy = visibleGoods(context, screen).stream().filter(v -> v.unit() == 1 && v.patch().isEmpty() && !v.special() && v.stock() >= 1)
					.max(java.util.Comparator.comparingDouble(v -> v.params().basePrice() / v.params().elasticity()))
					.orElseThrow(() -> new AssertionError("no good to buy"));
			long p0 = PriceModel.buyPrice(buy.params(), state(context, screen, buy.goods()).pressure());
			int n = step(buy, p0);
			check(n <= 32, "%s needs %d units for its price to move a coin", buy.goods(), n);
			// Two purchases empty the stock.
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				GoodState st = market.good(buy.goods());
				saved.put(buy.goods(), new double[] {st.pressure(), st.stock()});
				st.setStock(2 * n);
				MarketRegistry.get(server).setDirty();
			});
			screen = reopen(s, "buy");
			String shown0 = CoinItem.formatCopper(PriceModel.buyPrice(buy.params(), state(context, screen, buy.goods()).pressure()));
			List<TextRecorder.Text> r0 = row(context, screen, buy.goods());
			check(r0.stream().anyMatch(t -> t.text().equals(shown0) && t.color() == COLOR_GOLD), "the row of %s does not show %s: %s", buy.goods(), shown0, strings(r0));
			trade(s, screen, buy.goods(), true, n);
			long p1 = PriceModel.buyPrice(view(context, screen, buy.goods()).params(), state(context, screen, buy.goods()).pressure());
			String shown1 = CoinItem.formatCopper(p1);
			List<TextRecorder.Text> r1 = row(context, screen, buy.goods());
			check(p1 > p0 && r1.stream().anyMatch(t -> t.text().equals(shown1)), "after buying %d the row shows %s (was %s, %dc now): %s", n, strings(r1), shown0, p1,
					strings(r1));
			screenshot(context, result, "2-11_price_rose");
			int rest = (int) Math.floor(state(context, screen, buy.goods()).stock());
			check(rest >= 1, "no stock left to buy");
			trade(s, screen, buy.goods(), true, rest);
			unavailable(context, screen, buy.goods(), text(S + "sold_out"), S + "sold_out.reason", S + "restock_in");
			screenshot(context, result, "2-11_sold_out");

			// 2-12
			try {
				MarketScreen probe = reopen(s, "sell");
				GoodView sell = visibleGoods(context, probe).stream().filter(v -> v.unit() == 1 && v.patch().isEmpty() && !v.special()
						&& !v.goods().equals(buy.goods()) && new ItemStack(itemOf(v)).getMaxStackSize() >= 16 && PriceModel.sellCapacity(v.params(), state(context, probe, v.goods())) >= 1)
						.max(java.util.Comparator.comparingDouble(v -> v.params().basePrice() / v.params().elasticity()))
						.orElseThrow(() -> new AssertionError("no good to sell"));
				long q0 = PriceModel.sellPrice(sell.params(), state(context, probe, sell.goods()).pressure());
				int m = Math.min(step(sell, Math.max(1, q0)), 32);
				// Room for two sales: the stock 2m below the limit.
				s.world.getServer().runOnServer(server -> {
					Market market = market(server, stall);
					GoodState st = market.good(sell.goods());
					saved.put(sell.goods(), new double[] {st.pressure(), st.stock()});
					GoodParams params = com.akf.tradeexpansion.market.Reputation.params(market, player(server).getUUID(), sell.goods(), MarketClock.now(server.overworld()));
					st.setStock(Math.max(0, params.maxStock() - 2 * m));
					giveItems(player(server), item(sell.goods()), 2 * m + 2);
					MarketRegistry.get(server).setDirty();
				});
				MarketScreen sellScreen = reopen(s, "sell");
				GoodState st0 = state(context, sellScreen, sell.goods());
				long q1 = PriceModel.sellPrice(view(context, sellScreen, sell.goods()).params(), st0.pressure());
				int room = PriceModel.sellCapacity(view(context, sellScreen, sell.goods()).params(), st0);
				check(room >= 2 && room <= 2 * m + 1, "room for %d of %s, not %d", room, sell.goods(), 2 * m);
				int first = Math.min(m, room - 1);
				trade(s, sellScreen, sell.goods(), false, first);
				long q2 = PriceModel.sellPrice(view(context, sellScreen, sell.goods()).params(), state(context, sellScreen, sell.goods()).pressure());
				String shownSell = CoinItem.formatCopper(q2);
				List<TextRecorder.Text> rs = row(context, sellScreen, sell.goods());
				check(q2 < q1 && rs.stream().anyMatch(t -> t.text().equals(shownSell)), "after selling %d the row shows %s (%dc before, %dc now)", first, strings(rs), q1, q2);
				screenshot(context, also, "2-12_price_fell");
				int left = PriceModel.sellCapacity(view(context, sellScreen, sell.goods()).params(), state(context, sellScreen, sell.goods()));
				check(left >= 1, "no room left to sell");
				trade(s, sellScreen, sell.goods(), false, left);
				unavailable(context, sellScreen, sell.goods(), text(S + "not_buying"), S + "not_buying.reason", S + "buying_in");
				screenshot(context, also, "2-12_not_buying");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				saved.forEach((id, v) -> {
					GoodState st = market.good(id);
					if (st == null) return;
					st.setPressure(v[0]);
					st.setStock(v[1]);
				});
				MarketRegistry.get(server).setDirty();
			});
			ClientBuySellCases.restoreInventory(s, inventory);
		}
	}

	// ---- 22-27 / 30-7 / 32-2 the debug tooltip

	private static final List<String> CONSUMABLES = List.of("crimson_stem", "warped_stem", "honey_bottle", "sugar_cane", "arrow", "brown_egg", "blue_egg");

	private static void debugTooltip(ClientGameTestContext context, boolean on) {
		context.runOnClient(c -> {
			TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
			live.client.showDebugTooltip = on;
			TradeExpansionConfig.set(live);
		});
		context.waitTicks(1);
	}

	/** The tooltip lines of a row of the buy tab (the price graph's texts are in it too). */
	private static List<String> rowTooltip(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		int rowY = showRow(context, screen, good);
		moveTo(context, listX(context, screen) + 60, rowY + 8);
		context.waitTicks(2);
		List<String> tip = strings(tooltip(TextRecorder.capture(context)));
		mouseAway(context);
		return tip;
	}

	/**
	 * 30-7 / 32-2: with the debug tooltip on, crimson and warped stems, honey bottles, sugar cane, arrows and the brown
	 * and blue eggs say their price gap halves in about 20 minutes (one day, recovery.consumableHalfLifeDays) where an
	 * ordinary good says 40 (two days). 22-27: after a warehouse is completed the stock line shows a higher limit
	 * (development.warehouseStockMultiplier). The goods the market lacks are added on the server for the case.
	 */
	static void debugStorageAndConsumables(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		BlockPos core = stall.above(6);
		List<Identifier> added = new ArrayList<>();
		Map<Identifier, double[]> saved = new HashMap<>();
		Identifier[] ordinary = new Identifier[1];
		try {
			debugTooltip(context, true);
			TradeExpansionConfig config = TradeExpansionConfig.get();
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				long now = MarketClock.now(server.overworld());
				List<Identifier> wanted = new ArrayList<>(CONSUMABLES.stream().map(ClientTownCases::id).toList());
				// An ordinary good with the usual half-life (none of its own), bought by the market.
				ordinary[0] = market.regularGoods().stream().filter(g -> {
					GoodsDefinition d = GoodsCatalog.get(g);
					return d != null && d.buy() && d.unit() == 1 && d.components().isEmpty() && !d.consumable(config) && d.halfLifeDays().isEmpty();
				}).findFirst().orElseThrow(() -> new AssertionError("the market has no ordinary good"));
				wanted.add(ordinary[0]);
				for (Identifier id : wanted) {
					if (market.good(id) == null) {
						market.addGood(id, new GoodState(1.0, 0, 0, now));
						added.add(id);
					} else {
						GoodState st = market.good(id);
						saved.put(id, new double[] {st.pressure(), st.stock()});
					}
					// Dearer than normal (the recovery line shows) with stock to buy.
					GoodState st = market.good(id);
					st.setPressure(market.params(id).minPressure() * 0.5);
					st.setStock(Math.max(2, Math.floor(market.params(id).targetStock() / 2)));
				}
				MarketRegistry.get(server).setDirty();
			});
			MarketScreen screen = reopen(s, "buy");
			String usual = text(S + "debug.price_recovery", (int) Math.ceil(config.recovery.halfLifeDays * 20));
			List<String> tip = rowTooltip(context, screen, ordinary[0]);
			check(tip.contains(usual), "the ordinary good %s does not say \"%s\": %s", ordinary[0], usual, tip);
			try {
				String fast = text(S + "debug.price_recovery", (int) Math.ceil(config.recovery.consumableHalfLifeDays * 20));
				check(!fast.equals(usual), "consumables recover as fast as other goods in this config");
				for (String goods : CONSUMABLES) {
					Identifier id = ClientTownCases.id(goods);
					List<String> lines = rowTooltip(context, screen, id);
					check(lines.contains(fast), "%s does not say \"%s\": %s", goods, fast, lines);
					if (goods.equals("brown_egg")) {
						showRow(context, screen, id);
						moveTo(context, listX(context, screen) + 60, showRow(context, screen, id) + 8);
						context.waitTicks(2);
						screenshot(context, also, "30-7_consumable_debug_tooltip");
						mouseAway(context);
					}
				}
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}

			// 22-27: the warehouse.
			String stockPrefix = prefix(S + "debug.stock");
			String before = tip.stream().filter(t -> t.startsWith(stockPrefix)).findFirst()
					.orElseThrow(() -> new AssertionError("no stock line: " + tip));
			long max0 = limit(before);
			s.world.getServer().runOnServer(server -> ClientTownCases.facility(server, stall, "warehouse", FacilityKind.WAREHOUSE, ClientTownCases.State.COMPLETE, core));
			screen = reopen(s, "buy");
			List<String> tip2 = rowTooltip(context, screen, ordinary[0]);
			String after = tip2.stream().filter(t -> t.startsWith(stockPrefix)).findFirst()
					.orElseThrow(() -> new AssertionError("no stock line after the warehouse: " + tip2));
			long max1 = limit(after);
			double k = TradeExpansionConfig.get().development.warehouseStockMultiplier;
			check(max1 > max0 && Math.abs(max1 - max0 * k) <= Math.max(2, max0 * k * 0.02), "the stock limit went %d -> %d with a warehouse (×%.2f): \"%s\" -> \"%s\"",
					max0, max1, k, before, after);
			moveTo(context, listX(context, screen) + 60, showRow(context, screen, ordinary[0]) + 8);
			context.waitTicks(2);
			screenshot(context, result, "22-27_warehouse_stock_limit");
		} finally {
			context.setScreen(() -> null);
			debugTooltip(context, false);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.facilities().remove(ClientTownCases.id("warehouse"));
				server.overworld().setBlock(core, Blocks.AIR.defaultBlockState(), 2);
				for (Identifier id : added) market.removeGood(id);
				saved.forEach((id, v) -> {
					GoodState st = market.good(id);
					if (st == null) return;
					st.setPressure(v[0]);
					st.setStock(v[1]);
				});
				MarketRegistry.get(server).setDirty();
			});
			context.waitTicks(2);
		}
	}

	/** The limit (last number) of the stock line "在庫 a ／ 目標 b ／ 上限 c". */
	private static long limit(String line) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d[\\d,]*)\\D*$").matcher(line);
		check(m.find(), "no number at the end of \"%s\"", line);
		return Long.parseLong(m.group(1).replace(",", ""));
	}

	// ---- 7-6 / 7-7 dying with a wallet

	private static long serverBank(Session s) {
		return s.world.getServer().computeOnServer(server -> BankService.accounts(player(server)).balance(player(server).getUUID()));
	}

	/** The cash shown under "現金" on the trade screen. */
	private static String cashShown(ClientGameTestContext context) {
		mouseAway(context);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		String label = text(S + "cash_label");
		TextRecorder.Text cash = texts.stream().filter(t -> t.text().equals(label)).findFirst()
				.orElseThrow(() -> new AssertionError("no \"" + label + "\" on the screen: " + strings(texts)));
		return texts.stream().filter(t -> t.x0() == cash.x0() && t.y0() >= cash.y0() + 8 && t.y0() <= cash.y0() + 12).map(TextRecorder.Text::text).findFirst()
				.orElseThrow(() -> new AssertionError("no amount under \"" + label + "\": " + strings(texts)));
	}

	private static String bankShown(Session s) {
		reopen(s, "bank");
		String all = String.join("", strings(TextRecorder.capture(s.context)));
		s.context.setScreen(() -> null);
		return all;
	}

	/**
	 * 7-6: the player holds only a wallet with 3,000c and has 2,000c more in the bank; the trade screen shows 3,000c of
	 * cash. After dying the wallet lies on the ground (not in the inventory) and, respawned, the screen shows 0c of cash.
	 * 7-7: the bank tab shows the same balance before and after dying.
	 */
	static void diedWithWallet(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		List<ItemStack> inventory = ClientBuySellCases.saveInventory(s);
		BlockPos stall = s.stall;
		double[] at = s.world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			return new double[] {p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()};
		});
		long bank0 = s.world.getServer().computeOnServer(server -> BankService.accounts(player(server)).balance(player(server).getUUID()));
		// Every market has a bank tab (an earlier case may have put the settings back to the file's).
		TradeExpansionConfig config0 = s.world.getServer().computeOnServer(server -> ConfigManager.copy(TradeExpansionConfig.get()));
		applyTestConfig(s.world.getServer());
		try {
			s.world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				p.getInventory().clearContent();
				p.getInventory().setItem(0, new ItemStack(ModItems.WALLET));
				p.getInventory().setSelectedSlot(4);
				WalletService.give(p, 3_000);
				BankService.accounts(p).add(p.getUUID(), 2_000);
				BankService.sendAccount(p);
			});
			context.waitTicks(3);
			check(serverMoney(s) == 3_000, "the wallet holds %dc, not 3,000c", serverMoney(s));
			ClientChecklistTests.openStall(s);
			check(cashShown(context).equals(CoinItem.formatCopper(3_000)), "the screen shows %s of cash, not 3,000c", cashShown(context));
			screenshot(context, result, "7-6_cash_before_dying");
			// The balance as the server has it once the screen is open (interest is paid up to now when the account is touched).
			String tab = bankShown(s).replaceAll("\\s", "");
			long bank = serverBank(s);
			check(bank >= bank0 + 2_000, "the bank holds %dc, not at least %dc", bank, bank0 + 2_000);
			String balanceLine = text(S + "bank.balance", CoinItem.formatCopper(bank)).replaceAll("\\s", "");
			check(tab.contains(balanceLine), "the bank tab does not say \"%s\" before dying: %s", balanceLine, tab);

			// Die.
			context.setScreen(() -> null);
			context.waitTicks(2);
			s.world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			String dropped = s.world.getServer().<String, RuntimeException>computeOnServer(server -> {
				ServerPlayer p = player(server);
				ServerLevel level = server.overworld();
				List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[1], at[2], at[0], at[1], at[2]).inflate(8),
						e -> e.getItem().is(ModItems.WALLET));
				if (items.isEmpty()) return "no wallet was dropped";
				if (com.akf.tradeexpansion.item.WalletItem.balance(items.getFirst().getItem()) != 3_000) return "the dropped wallet holds " + com.akf.tradeexpansion.item.WalletItem.balance(items.getFirst().getItem()) + "c";
				return null;
			});
			check(dropped == null, "%s", dropped);
			context.runOnClient(c -> c.player.respawn());
			check(waitClient(context, c -> c.player != null && c.player.isAlive() && !(c.gui.screen() instanceof DeathScreen), 200), "the player did not respawn");
			context.setScreen(() -> null);
			// Back to the stall; the dropped wallet is taken away first (it would be picked up again).
			s.world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(16), e -> true).forEach(net.minecraft.world.entity.Entity::discard);
				player(server).teleportTo(level, at[0], at[1], at[2], java.util.Set.of(), (float) at[3], (float) at[4], true);
			});
			context.waitTicks(5);
			s.world.getConnection().waitForChunksRender();
			check(serverMoney(s) == 0, "%dc of cash after dying", serverMoney(s));
			check(s.world.getServer().computeOnServer(server -> count(player(server), ModItems.WALLET)) == 0, "the wallet is still in the inventory after dying");
			ClientChecklistTests.openStall(s);
			check(cashShown(context).equals(CoinItem.formatCopper(0)), "after dying the screen shows %s of cash, not 0c", cashShown(context));
			screenshot(context, result, "7-6_cash_after_dying");
			try {
				String after = bankShown(s).replaceAll("\\s", "");
				check(serverBank(s) == bank, "the bank holds %dc after dying, %dc before", serverBank(s), bank);
				check(after.contains(balanceLine), "the bank tab does not say \"%s\" after dying: %s", balanceLine, after);
				reopen(s, "bank");
				screenshot(context, also, "7-7_bank_after_dying");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				long now = BankService.accounts(p).balance(p.getUUID());
				BankService.accounts(p).add(p.getUUID(), bank0 - now);
				BankService.sendAccount(p);
				p.teleportTo(server.overworld(), at[0], at[1], at[2], java.util.Set.of(), (float) at[3], (float) at[4], true);
				ConfigManager.apply(config0);
			});
			ClientBuySellCases.restoreInventory(s, inventory);
		}
	}
}
