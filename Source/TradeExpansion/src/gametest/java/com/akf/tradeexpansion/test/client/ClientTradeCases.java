package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketLayout;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/** Client cases of the trade screen and the stall (checklist chapters 2, 3 and 4). */
final class ClientTradeCases {
	private ClientTradeCases() {
	}

	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_ORANGE = 0xFFFFAA33;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** The part of a translation before its first %s ("入荷まで約"). */
	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	// ---- 2-2 the placement hint

	/**
	 * 2-2: while a stall is in the hand, the action bar says every second what placing it would do: join the market
	 * next to it, found a new market (another village 400 blocks away) or why it cannot go there (no village). The
	 * stall is then placed in the new village: the board lists a second market for the layout check.
	 */
	static void placementHint(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		Vec3 home = s.world.getServer().computeOnServer(server -> player(server).position());
		s.world.getServer().runOnServer(server -> holdStall(player(server)));
		try {
			context.getInput().lookAt(s.stall.east(2).below());
			Component join = hint(context);
			check(key(join).equals("message.ak_trade_expansion.where.join"), "next to the market the hint says \"%s\" (%s)", join.getString(), key(join));
			Component again = hint(context);
			check(key(again).equals(key(join)), "the hint did not come again within a second");
			screenshot(context, result, "2-2_hint_join");

			// 400 blocks east: no village, more than distance.minNewMarketDistance (256) from the market.
			BlockPos far = s.world.getServer().computeOnServer(server -> ground(server, (int) home.x + 400, (int) home.z));
			teleport(s, far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			context.getInput().lookAt(far.north(2).below());
			Component refused = hint(context);
			check(!key(refused).startsWith("message.ak_trade_expansion.where."), "without a village the hint says \"%s\" (%s)", refused.getString(), key(refused));
			check(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(refused.getStyle().getColor()), "the refusal \"%s\" is not red", refused.getString());
			screenshot(context, result, "2-2_hint_refused");

			s.world.getServer().runOnServer(server -> village(server.overworld(), far));
			context.waitTicks(20);
			Component created = hint(context);
			check(key(created).equals("message.ak_trade_expansion.where.new"), "in the new village the hint says \"%s\" (%s)", created.getString(), key(created));
			screenshot(context, result, "2-2_hint_new");

			// Found the second market there with the use key.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			int markets = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).all().size());
			check(markets >= 2, "placing the stall in the second village founded no market (%d markets)", markets);
		} finally {
			teleport(s, home.x, home.y, home.z);
			s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(8, ItemStack.EMPTY));
			context.waitTicks(2);
		}
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "(literal)";
	}

	/** Clears the action bar and waits (at most 1.5 seconds) for the next hint. */
	private static Component hint(ClientGameTestContext context) {
		context.runOnClient(client -> set(client.gui.hud, "overlayMessageString", null));
		Component[] got = new Component[1];
		boolean shown = waitClient(context, client -> (got[0] = (Component) get(client.gui.hud, Hud.class, "overlayMessageString")) != null, 30);
		check(shown, "no hint on the action bar within 1.5 seconds");
		return got[0];
	}

	// ---- 2-13 the sell slot

	/** 2-13: an item put into the sell slot selects its good on the sell tab with its count; closing gives it back. */
	static void sellSlot(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientChecklistTests.openStall(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		GoodView good = snapshot.goods().stream()
				// One the player has none of: the slot to move is then the 10 given now (earlier cases bought some goods).
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && new ItemStack(itemOf(v)).getMaxStackSize() >= 10
						&& context.computeOnClient(client -> client.player.getInventory().countItem(itemOf(v))) == 0
						&& PriceModel.sellCapacity(v.params(), new GoodState(1, v.pressure(), v.stock(), 0)) >= 10)
				.findFirst().orElseThrow(() -> new AssertionError("no good the market buys 10 of"));
		Item item = itemOf(good);
		context.setScreen(() -> null);
		int before = s.world.getServer().computeOnServer(server -> {
			giveItems(player(server), item, 10);
			return count(player(server), item);
		});
		screen = ClientChecklistTests.openStall(s);
		check("BUY".equals(context.computeOnClient(client -> get(client.gui.screen(), "tab").toString())), "the screen did not open on the buy tab");
		putInSellSlot(context, screen, item);
		context.waitTicks(3);
		MarketScreen open = screen;
		String tab = context.computeOnClient(client -> get(open, "tab").toString());
		Object selected = context.computeOnClient(client -> get(open, "selected"));
		int quantity = context.computeOnClient(client -> (Integer) get(open, "quantity"));
		int inSlot = context.computeOnClient(client -> open.getMenu().sellSlotItem().getCount());
		check(inSlot == 10, "the sell slot holds %d items, not 10", inSlot);
		check("SELL".equals(tab), "the item in the sell slot left the screen on the %s tab", tab);
		check(good.goods().equals(selected), "the sell tab selected %s instead of %s", selected, good.goods());
		check(quantity == 10, "the quantity is %d, not the slot's 10", quantity);
		screenshot(context, result, "2-13_sell_slot");
		pressEscape(context);
		check(waitClient(context, client -> client.gui.screen() == null, 20), "Escape did not close the trade screen");
		context.waitTicks(3);
		int after = s.world.getServer().computeOnServer(server -> count(player(server), item));
		check(after == before, "closing the screen gave back %d of the %d items", after - (before - 10), 10);
	}

	// ---- 2-16 the screen closes

	/** 2-16: the screen closes when the player walks away from the stall, and when the stall is broken. Runs last. */
	static void closesWhenAway(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Vec3 home = s.world.getServer().computeOnServer(server -> player(server).position());
		double max = com.akf.tradeexpansion.config.TradeExpansionConfig.get().trade.maxDistance;
		ClientChecklistTests.openStall(s);
		try {
			teleport(s, home.x + max + 4, home.y, home.z);
			check(waitClient(context, client -> !(client.gui.screen() instanceof MarketScreen), 40),
					"the trade screen stayed open %.0f blocks from the stall", max + 4);
		} finally {
			teleport(s, home.x, home.y, home.z);
		}
		ClientChecklistTests.openStall(s);
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> server.overworld().setBlock(stall, Blocks.AIR.defaultBlockState(), 3));
		check(waitClient(context, client -> !(client.gui.screen() instanceof MarketScreen), 40), "the trade screen stayed open after the stall was broken");
	}

	// ---- 3-1 the change since opening

	/** 3-1: a good bought while the screen is open shows ▲ in its row (its price went up since the screen opened). */
	static void priceArrows(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 1_000_000));
		context.waitTicks(3);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		int rows = context.computeOnClient(client -> (Integer) get(screen, "rows"));
		List<GoodView> goods = visibleGoods(context, screen);
		GoodView good = goods.subList(0, Math.min(rows, goods.size())).stream()
				.filter(v -> v.unit() == 1 && v.stock() >= 65 && v.patch().isEmpty())
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with stock for 64 in the first rows"));
		int rowY = showRow(context, screen, good.goods());
		List<TextRecorder.Text> first = rowTexts(TextRecorder.capture(context), rowY);
		check(first.stream().noneMatch(t -> t.text().equals("▲") || t.text().equals("▼")), "the row shows %s before any trade", strings(first));
		buyWithButtons(s, screen, good.goods(), "64");
		rowY = showRow(context, screen, good.goods());
		mouseAway(context);
		List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
		check(row.stream().anyMatch(t -> t.text().equals("▲") && t.color() == COLOR_RED), "no red ▲ in the row of %s after buying 64 (row: %s)",
				good.goods(), strings(row));
		screenshot(context, result, "3-1_price_up");
	}

	// ---- 3-4 the money

	/** 3-4: "現金" and "銀行" right of the player inventory; the mouse over them shows the coins and the bank balance. */
	static void balance(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 12_345));
		context.waitTicks(3);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		check(waitClient(context, client -> get(screen, "account") != null, 40), "the screen got no bank account");
		mouseAway(context);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		int inventoryRight = context.computeOnClient(client -> leftPos(screen) + ((MarketLayout) get(screen, "layout")).inventoryX() + 9 * 18);
		String cashLabel = text("screen.ak_trade_expansion.cash_label");
		String bankLabel = text("screen.ak_trade_expansion.bank_label");
		TextRecorder.Text cash = texts.stream().filter(t -> t.text().equals(cashLabel)).findFirst()
				.orElseThrow(() -> new AssertionError("no \"" + cashLabel + "\" on the screen: " + strings(texts)));
		check(texts.stream().anyMatch(t -> t.text().equals(bankLabel)), "no \"%s\" on the screen", bankLabel);
		check(cash.x0() >= inventoryRight, "\"%s\" is at x %d, not right of the inventory (%d)", cashLabel, cash.x0(), inventoryRight);
		moveTo(context, cash.x0() + 3, cash.y0() + 3);
		context.waitTicks(2);
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		AccountS2C account = context.computeOnClient(client -> (AccountS2C) get(screen, "account"));
		String bankLine = text("screen.ak_trade_expansion.balance.bank", CoinItem.formatCopper(account.bankBalance()));
		check(tip.stream().anyMatch(t -> t.text().equals(bankLine)), "the tooltip has no \"%s\": %s", bankLine, strings(tip));
		check(tip.stream().anyMatch(t -> t.text().contains(" ×")), "the tooltip shows no coins: %s", strings(tip));
		screenshot(context, result, "3-4_balance_tooltip");
		mouseAway(context);
	}

	// ---- 3-7 / 3-8 / 3-9 rows of goods that are short, sold out or not bought

	/**
	 * 3-7 / 3-8 / 3-9: one good is made short (K), one sold out, one full (the market does not buy it). Their rows: an
	 * orange name; "売り切れ" / "買取停止" in red with the reason and the minutes in the tooltip; the panel of the full good
	 * says so, and its item in the sell slot gets a red frame.
	 */
	static void rowStates(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientChecklistTests.openStall(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		List<GoodView> goods = snapshot.goods().stream().filter(v -> !v.special() && v.patch().isEmpty() && v.unit() == 1).toList();
		GoodView shortGood = goods.stream().filter(v -> v.buy() && v.stock() >= 2 && !v.shortage()).findFirst()
				.orElseThrow(() -> new AssertionError("no good to make short"));
		GoodView soldOut = goods.stream().filter(v -> v.buy() && v != shortGood).findFirst()
				.orElseThrow(() -> new AssertionError("no good to sell out"));
		GoodView full = goods.stream().filter(v -> v.sell() && v != shortGood && v != soldOut && new ItemStack(itemOf(v)).getMaxStackSize() >= 4).findFirst()
				.orElseThrow(() -> new AssertionError("no good to fill up"));
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			market.shortages().put(shortGood.goods(), 1.5);
			market.good(soldOut.goods()).setStock(0);
			market.good(full.goods()).setStock(market.params(full.goods()).maxStock());
			giveItems(player(server), itemOf(full), 4);
		});
		try {
			screen = ClientChecklistTests.openStall(s);
			MarketScreen open = screen;
			context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
			mouseAway(context);

			// 3-7: the short good's name is orange.
			int rowY = showRow(context, open, shortGood.goods());
			List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
			check(row.stream().anyMatch(t -> t.color() == COLOR_ORANGE && t.x0() < listX(context, open) + 100),
					"the name of the short good %s is not orange (row: %s)", shortGood.goods(), strings(row));
			screenshot(context, result, "3-7_shortage_orange");

			// 3-8: sold out.
			rowY = showRow(context, open, soldOut.goods());
			row = rowTexts(TextRecorder.capture(context), rowY);
			String soldOutText = text("screen.ak_trade_expansion.sold_out");
			check(row.stream().anyMatch(t -> t.text().equals(soldOutText) && t.color() == COLOR_RED), "the sold-out row shows no red \"%s\": %s", soldOutText, strings(row));
			check(row.stream().anyMatch(t -> t.color() == COLOR_GRAY), "the sold-out row has no gray name: %s", strings(row));
			unavailableTooltip(context, open, rowY, "screen.ak_trade_expansion.sold_out.reason", "screen.ak_trade_expansion.restock_in");
			screenshot(context, result, "3-8_sold_out_tooltip");

			// 3-8: not bought (sell tab).
			context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
			mouseAway(context);
			rowY = showRow(context, open, full.goods());
			row = rowTexts(TextRecorder.capture(context), rowY);
			String notBuying = text("screen.ak_trade_expansion.not_buying");
			check(row.stream().anyMatch(t -> t.text().equals(notBuying) && t.color() == COLOR_RED), "the full good's row shows no red \"%s\": %s", notBuying, strings(row));
			unavailableTooltip(context, open, rowY, "screen.ak_trade_expansion.not_buying.reason", "screen.ak_trade_expansion.buying_in");
			screenshot(context, result, "3-8_not_buying_tooltip");

			// 3-9: the panel of the selected full good, and its item in the sell slot.
			select(context, open, full.goods());
			mouseAway(context);
			int panelX = context.computeOnClient(client -> leftPos(open) + (Integer) get(open, "panelX"));
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			String buyingIn = prefix("screen.ak_trade_expansion.buying_in");
			check(texts.stream().anyMatch(t -> t.text().equals(notBuying) && t.x0() >= panelX), "the panel shows no \"%s\": %s", notBuying, strings(texts));
			check(texts.stream().anyMatch(t -> t.text().startsWith(buyingIn) && t.x0() >= panelX), "the panel shows no \"%s…\": %s", buyingIn, strings(texts));
			putInSellSlot(context, open, itemOf(full));
			context.waitTicks(2);
			boolean frame = context.computeOnClient(client -> (Boolean) call(open, "sellSlotNotBuying"));
			check(frame, "the sell slot with a good the market does not buy has no red frame");
			screenshot(context, result, "3-9_not_buying_sell_slot");
		} finally {
			context.setScreen(() -> null);
			context.waitTicks(2);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.shortages().remove(shortGood.goods());
				market.good(soldOut.goods()).setStock(market.params(soldOut.goods()).targetStock());
				market.good(full.goods()).setStock(market.params(full.goods()).targetStock());
			});
		}
	}

	/** Hovers the name of a row and checks the tooltip: the reason and "…まで約N分". */
	private static void unavailableTooltip(ClientGameTestContext context, MarketScreen screen, int rowY, String reasonKey, String minutesKey) {
		moveTo(context, listX(context, screen) + 40, rowY + 8);
		context.waitTicks(2);
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		String reason = text(reasonKey);
		String minutes = prefix(minutesKey);
		check(tip.stream().anyMatch(t -> t.text().equals(reason)), "the tooltip has no \"%s\": %s", reason, strings(tip));
		check(tip.stream().anyMatch(t -> t.text().startsWith(minutes)), "the tooltip has no \"%s…\": %s", minutes, strings(tip));
	}

	// ---- 3-11 "max" with just enough money

	/**
	 * 3-11: with exactly the price of 20 (bulk discount included) in cash, "最大" sets 20, and buying them leaves
	 * nothing.
	 */
	static void maxWithExactMoney(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		int n = 20;
		GoodView good = visibleGoods(context, screen).stream()
				.filter(v -> v.unit() == 1 && v.stock() >= n + 2 && v.patch().isEmpty())
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with stock for " + n));
		select(context, screen, good.goods());
		long funds = context.computeOnClient(client -> (Long) call(screen, "previewTotal", view(context, screen, good.goods()), n));
		long plain = context.computeOnClient(client -> {
			GoodView v = view(context, screen, good.goods());
			GoodState state = (GoodState) call(screen, "currentState", v);
			return PriceModel.sumBuy(v.params(), state.pressure(), n);
		});
		check(funds < plain, "20 of %s cost %dc with the bulk discount and %dc without: no discount to check", good.goods(), funds, plain);
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			WalletService.take(player, WalletService.balance(player));
			WalletService.give(player, funds);
		});
		check(waitClient(context, client -> WalletService.balance(client.player) == funds, 40), "the client does not see the new cash of %dc", funds);
		context.runOnClient(client -> set(screen, "payByCard", false));
		context.clickScreenButton("screen.ak_trade_expansion.max");
		mouseAway(context);
		int quantity = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
		check(quantity == n, "\"最大\" with %dc (the discounted price of %d) set %d", funds, n, quantity);
		screenshot(context, result, "3-11_max_exact_money");
		int items = serverCount(s, good.goods());
		context.clickScreenButton("screen.ak_trade_expansion.confirm.buy");
		check(waitClient(context, client -> WalletService.balance(client.player) == 0, 40), "buying the %d did not take all the %dc", n, funds);
		context.waitTicks(3);
		check(serverMoney(s) == 0, "%dc are left on the server", serverMoney(s));
		check(serverCount(s, good.goods()) - items == n, "buying %d gave %d items", n, serverCount(s, good.goods()) - items);
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 1_000_000));
	}

	// ---- 4-3 the world opened again

	/** 4-3: after a trade, the world is closed and opened again: the good's price and stock are the same. */
	static void reopenWorld(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		GoodView good = visibleGoods(context, screen).stream()
				.filter(v -> v.unit() == 1 && v.stock() >= 70 && v.patch().isEmpty())
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with stock for 64"));
		buyWithButtons(s, screen, good.goods(), "64");
		long shownBefore = shownPrice(context, screen, good.goods());
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		double[] before = s.world.getServer().computeOnServer(server -> state(market(server, stall), good.goods()));

		TestWorldSave save = s.world.getWorldSave();
		s.world.close();
		s.world = save.open();
		s.world.getConnection().waitForChunksRender();
		applyTestConfig(s.world.getServer());
		context.waitTicks(5);

		double[] after = s.world.getServer().computeOnServer(server -> state(market(server, stall), good.goods()));
		check(Math.abs(after[0] - before[0]) <= Math.max(0.01, Math.abs(before[0]) * 0.01), "the pressure of %s went %.4f -> %.4f", good.goods(), before[0], after[0]);
		check(Math.abs(after[1] - before[1]) <= 1, "the stock of %s went %.2f -> %.2f", good.goods(), before[1], after[1]);
		check(Math.abs(after[2] - before[2]) <= 1, "the price of %s went %.0fc -> %.0fc", good.goods(), before[2], after[2]);
		MarketScreen reopened = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		long shownAfter = shownPrice(context, reopened, good.goods());
		check(Math.abs(shownAfter - shownBefore) <= 1, "the screen showed %dc before and %dc after opening the world again", shownBefore, shownAfter);
		screenshot(context, result, "4-3_reopened");
	}

	/** Pressure, stock and buy price of a good on the server. */
	private static double[] state(Market market, Identifier good) {
		GoodState state = market.good(good);
		GoodParams params = market.params(good);
		return new double[] {state.pressure(), state.stock(), PriceModel.buyPrice(params, state.pressure())};
	}

	private static long shownPrice(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		return context.computeOnClient(client -> {
			GoodView v = view(context, screen, good);
			GoodState state = (GoodState) call(screen, "currentState", v);
			return PriceModel.buyPrice(v.params(), state.pressure());
		});
	}

}
