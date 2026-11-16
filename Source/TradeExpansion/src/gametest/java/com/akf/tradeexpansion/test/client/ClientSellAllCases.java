package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketAccess;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of "まとめて売る" that sell_all_box left out (checklist 18-2 to 18-5): a tool the market buys excluded
 * from the start, the sale itself (the message, what stays), "40個（64個中）" and "買取停止", and another player's sale
 * while the box is open ("価格が変わった" and the new total). Each case empties the main inventory of everything but
 * money first (so only its own goods are in the box) and puts the inventory back at the end.
 */
final class ClientSellAllCases {
	private ClientSellAllCases() {
	}

	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_RED = 0xFFFF5555;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** Calls an accessor of a record object (the screen's private records). */
	private static Object field(Object record, String accessor) {
		try {
			Method m = record.getClass().getDeclaredMethod(accessor);
			m.setAccessible(true);
			return m.invoke(record);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read " + record.getClass().getSimpleName() + "." + accessor, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Object> lines(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(c -> new ArrayList<>((List<Object>) get(screen, "sellAllLines")));
	}

	private static Object line(List<Object> lines, Identifier good) {
		return lines.stream().filter(l -> ((GoodView) field(l, "view")).goods().equals(good)).findFirst()
				.orElseThrow(() -> new AssertionError(good + " is not in \"sell all\""));
	}

	private static boolean excluded(Object line) {
		return (Boolean) field(line, "excluded");
	}

	private static long total(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(c -> (Long) call(screen, "sellAllTotal"));
	}

	/** Top of the line's row on the screen, scrolling the box so it shows. */
	private static int showLine(ClientGameTestContext context, MarketScreen screen, int index) {
		int rows = context.computeOnClient(c -> (Integer) get(screen, "sellAllRows"));
		int scroll = Math.max(0, index - rows + 1);
		context.runOnClient(c -> set(screen, "sellAllScroll", scroll));
		context.waitTicks(1);
		int top = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "SELL_ALL_Y") + 16;
		return top + (index - scroll) * constant(MarketScreen.class, "ROW_H");
	}

	private static void clickLine(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		List<Object> lines = lines(context, screen);
		int rowY = showLine(context, screen, lines.indexOf(line(lines, good)));
		int x = context.computeOnClient(c -> leftPos(screen)) + constant(MarketScreen.class, "SELL_ALL_X");
		clickAt(context, x + 30, rowY + 8);
		mouseAway(context);
	}

	/** Texts of the line's row (the mouse away). */
	private static List<TextRecorder.Text> lineTexts(ClientGameTestContext context, MarketScreen screen, Identifier good) {
		List<Object> lines = lines(context, screen);
		int rowY = showLine(context, screen, lines.indexOf(line(lines, good)));
		mouseAway(context);
		return rowTexts(TextRecorder.capture(context), rowY);
	}

	/** Opens the sell tab and its "sell all" box. */
	private static MarketScreen openBox(Session s) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		context.waitTicks(2);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.runOnClient(c -> set(screen, "payByCard", false));
		context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
		context.clickScreenButton("screen.ak_trade_expansion.sell_all");
		context.waitTicks(2);
		check(context.computeOnClient(c -> (Boolean) get(screen, "sellAllOpen")), "the \"sell all\" box did not open");
		return screen;
	}

	/**
	 * Closes the menu on the server too (a test's setScreen(null) does not tell the server, which then keeps items
	 * given meanwhile from reaching the client), and clears the main inventory but for coins, wallets and cards.
	 */
	private static void emptyMainInventory(Session s) {
		s.context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.closeContainer();
			Inventory inventory = player.getInventory();
			for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.isEmpty() || stack.getItem() instanceof CoinItem || stack.getItem() instanceof WalletItem || stack.is(ModItems.BANK_CARD)) continue;
				inventory.setItem(slot, ItemStack.EMPTY);
			}
			if (WalletService.balance(player) < 100_000) WalletService.give(player, 1_000_000);
		});
		s.context.waitTicks(3);
	}

	private static int freeSlot(ServerPlayer player, int from, int to) {
		Inventory inventory = player.getInventory();
		for (int i = from; i < to; i++) if (inventory.getItem(i).isEmpty()) return i;
		throw new AssertionError("no free inventory slot between " + from + " and " + to);
	}

	private static void give(ServerPlayer player, Identifier good, int count, boolean hotbar) {
		ItemStack stack = new ItemStack(item(good), count);
		player.getInventory().setItem(hotbar ? freeSlot(player, 0, 9) : freeSlot(player, 9, Inventory.INVENTORY_SIZE), stack);
	}

	private record SellAllConfig(List<String> exclude, List<String> include) {
	}

	private static SellAllConfig saveConfig(ClientGameTestContext context) {
		return context.computeOnClient(c -> new SellAllConfig(List.copyOf(TradeExpansionConfig.get().client.sellAllExclude),
				List.copyOf(TradeExpansionConfig.get().client.sellAllInclude)));
	}

	private static void restoreConfig(ClientGameTestContext context, SellAllConfig saved) {
		context.runOnClient(c -> {
			TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
			live.client.sellAllExclude = new ArrayList<>(saved.exclude());
			live.client.sellAllInclude = new ArrayList<>(saved.include());
			TradeExpansionConfig.set(live);
			TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
			file.client = live.client;
			ConfigManager.write(ConfigManager.path(), file);
		});
	}

	/** Goods of the snapshot the market buys in single items, without components, held by nobody's choice yet. */
	private static List<GoodView> plainGoods(MarketSnapshotS2C snapshot, int room) {
		return snapshot.goods().stream()
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && !v.displayStack().isDamageableItem()
						&& v.displayStack().getMaxStackSize() >= 64 && PriceModel.sellCapacity(v.params(), v.state()) >= room)
				.sorted(Comparator.comparingDouble((GoodView v) -> v.params().basePrice()).reversed())
				.toList();
	}

	/** Clicks "売る" and waits for the status line of its result. */
	private static String confirm(ClientGameTestContext context, MarketScreen screen) {
		Component before = context.computeOnClient(c -> (Component) get(screen, "status"));
		clickWidget(context, text("screen.ak_trade_expansion.sell_all.confirm"));
		check(waitClient(context, c -> get(screen, "status") != before && !(Boolean) get(screen, "sellAllPending"), 40), "\"売る\" got no answer");
		context.waitTicks(2);
		return context.computeOnClient(c -> ((Component) get(screen, "status")).getString());
	}

	// ---- 18-2 / 18-3 a tool and the sale

	/**
	 * 18-2 / 18-3: the market buys a tool (one is added when it buys none): it is in the box excluded from the start,
	 * "除外" in gray, and a click includes it and another excludes it again. Another good excluded by a click, one held
	 * outside and inside the hotbar: "売る" says "まとめて 4 個を売りました：合計 X" with the total the box showed, that much
	 * money comes in, the box closes, and the excluded good, the tool and the hotbar items are still there.
	 */
	static void toolsAndSale(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<ItemStack> saved = ClientBuySellCases.saveInventory(s);
		SellAllConfig config = saveConfig(context);
		Identifier[] added = new Identifier[1];
		try {
			emptyMainInventory(s);
			// A tool the market buys.
			Identifier tool = s.world.getServer().computeOnServer(server -> {
				Market market = market(server, stall);
				for (Identifier g : market.regularGoods()) {
					GoodsDefinition def = GoodsCatalog.get(g);
					if (def != null && def.sell() && def.unit() == 1 && def.components().isEmpty() && new ItemStack(item(g)).isDamageableItem()) return g;
				}
				Identifier sword = Identifier.fromNamespaceAndPath("ak_trade_expansion", "iron_sword");
				check(GoodsCatalog.get(sword) != null, "no good %s", sword);
				market.addGood(sword, new GoodState(1.0, 0, 0, MarketUpkeep.touch(server.overworld(), market)));
				MarketRegistry.get(server).setDirty();
				added[0] = sword;
				return sword;
			});
			context.runOnClient(c -> {
				TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
				String toolItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item(tool)).toString();
				live.client.sellAllExclude.remove(toolItem);
				live.client.sellAllInclude.remove(toolItem);
				TradeExpansionConfig.set(live);
			});
			MarketScreen first = openBox(s);
			List<GoodView> plain = plainGoods(snapshot(context, first), 8);
			check(plain.size() >= 2, "fewer than 2 goods the market buys: %s", plain.stream().map(GoodView::goods).toList());
			GoodView sold = plain.get(0);
			GoodView kept = plain.get(1);
			context.runOnClient(c -> {
				TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
				for (GoodView v : List.of(sold, kept)) {
					live.client.sellAllExclude.remove(v.item().toString());
					live.client.sellAllInclude.remove(v.item().toString());
				}
				TradeExpansionConfig.set(live);
			});
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				give(player, sold.goods(), 4, false);
				give(player, sold.goods(), 3, true);
				give(player, kept.goods(), 4, false);
				give(player, tool, 1, false);
			});
			context.waitTicks(3);

			MarketScreen screen = openBox(s);
			List<Object> lines = lines(context, screen);
			check(lines.size() == 3, "the box has %d lines, not 3 (2 goods and the tool): %s", lines.size(),
					lines.stream().map(l -> ((GoodView) field(l, "view")).goods()).toList());
			check(excluded(line(lines, tool)), "the tool %s is not excluded from the start", tool);
			check(!excluded(line(lines, sold.goods())) && !excluded(line(lines, kept.goods())), "a good is excluded from the start");
			check((Integer) field(line(lines, sold.goods()), "units") == 4, "the box sells %s of %s, not the 4 outside the hotbar",
					field(line(lines, sold.goods()), "units"), sold.goods());
			String exclusion = text("screen.ak_trade_expansion.sell_all.excluded");
			List<TextRecorder.Text> toolRow = lineTexts(context, screen, tool);
			check(toolRow.stream().anyMatch(t -> t.text().equals(exclusion) && t.color() == COLOR_GRAY), "the tool's row has no gray \"%s\": %s", exclusion, strings(toolRow));
			String hint = text("screen.ak_trade_expansion.sell_all.hint");
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(hint)), "the box has no \"%s\"", hint);
			screenshot(context, result, "18-2_tool_excluded");

			// The tool: a click includes it (its price is in the total), another excludes it again.
			long withoutTool = total(context, screen);
			clickLine(context, screen, tool);
			Object toolLine = line(lines(context, screen), tool);
			check(!excluded(toolLine), "a click did not include the tool");
			check(total(context, screen) == withoutTool + (Long) field(toolLine, "total"), "including the tool took the total %d -> %d",
					withoutTool, total(context, screen));
			List<TextRecorder.Text> included = lineTexts(context, screen, tool);
			String toolPrice = CoinItem.formatCopper((Long) field(toolLine, "total"));
			check(included.stream().anyMatch(t -> t.text().equals(toolPrice)), "the included tool's row does not show %s: %s", toolPrice, strings(included));
			clickLine(context, screen, tool);
			check(excluded(line(lines(context, screen), tool)), "a second click did not exclude the tool again");
			check(total(context, screen) == withoutTool, "excluding the tool again did not put the total back");

			// Exclude the kept good; sell the rest.
			clickLine(context, screen, kept.goods());
			check(excluded(line(lines(context, screen), kept.goods())), "a click did not exclude %s", kept.goods());
			long shown = total(context, screen);
			check(shown == (Long) field(line(lines(context, screen), sold.goods()), "total"), "the total is not the one good left");
			String totalText = text("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper(shown), 1);
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(totalText)), "the box does not say \"%s\"", totalText);
			long money = serverMoney(s);
			int soldBefore = serverCount(s, sold.goods()), keptBefore = serverCount(s, kept.goods()), toolBefore = serverCount(s, tool);
			String status = confirm(context, screen);
			String done = text("screen.ak_trade_expansion.sell_all.done", 4, CoinItem.formatCopper(shown));
			check(status.equals(done), "after \"売る\" the screen says \"%s\", not \"%s\"", status, done);
			check(!context.computeOnClient(c -> (Boolean) get(screen, "sellAllOpen")), "the box is still open after selling");
			long got = serverMoney(s) - money;
			check(got == shown, "selling all paid %dc, the box said %dc", got, shown);
			check(serverCount(s, sold.goods()) == soldBefore - 4, "%d of %s are left, not the %d in the hotbar", serverCount(s, sold.goods()), sold.goods(), soldBefore - 4);
			check(serverCount(s, kept.goods()) == keptBefore, "%d of the excluded %s are left, not %d", serverCount(s, kept.goods()), kept.goods(), keptBefore);
			check(serverCount(s, tool) == toolBefore, "the excluded tool was sold");
			mouseAway(context);
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(done)), "the screen does not show \"%s\"", done);
			screenshot(context, result, "18-3_sold_all");
		} finally {
			context.setScreen(() -> null);
			restoreConfig(context, config);
			s.world.getServer().runOnServer(server -> {
				player(server).closeContainer();
				if (added[0] != null) {
					market(server, stall).removeGood(added[0]);
					MarketRegistry.get(server).setDirty();
				}
			});
			ClientBuySellCases.restoreInventory(s, saved);
		}
	}

	// ---- 18-4 / 18-5 the stock limit and another player's sale

	/**
	 * 18-4 / 18-5: one good with room for 40 (64 held) and one at its stock limit. The box says "40個（64個中）" and
	 * "買取停止" in red (left out of the count). With the box open, a second player sells a third good until its price
	 * moved: "売る" says "価格が変動しました…", nothing is sold, the box stays open with the new total, and "売る" again
	 * sells at that total.
	 */
	static void limitsAndOtherPlayer(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		List<ItemStack> saved = ClientBuySellCases.saveInventory(s);
		SellAllConfig config = saveConfig(context);
		List<Runnable> restore = new ArrayList<>();
		ServerPlayer[] other = new ServerPlayer[1];
		try {
			emptyMainInventory(s);
			MarketScreen first = openBox(s);
			List<GoodView> plain = plainGoods(snapshot(context, first), 4);
			// Room for 40 is made by setting the stock: a good with a limit above that.
			GoodView partial = plain.stream().filter(v -> v.params().maxStock() >= 48).findFirst()
					.orElseThrow(() -> new AssertionError("no good with a stock limit of 48 or more: " + plain.stream().map(GoodView::goods).toList()));
			// The other player sells this one: the most room.
			GoodView moving = plain.stream().filter(v -> v != partial).max(Comparator.comparingInt(v -> PriceModel.sellCapacity(v.params(), v.state())))
					.orElseThrow(() -> new AssertionError("no second good the market buys"));
			GoodView full = plain.stream().filter(v -> v != partial && v != moving).findFirst()
					.orElseThrow(() -> new AssertionError("no third good the market buys"));
			context.runOnClient(c -> {
				TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
				for (GoodView v : List.of(partial, full, moving)) {
					live.client.sellAllExclude.remove(v.item().toString());
					live.client.sellAllInclude.remove(v.item().toString());
				}
				TradeExpansionConfig.set(live);
			});
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				Market market = market(server, stall);
				MarketUpkeep.touch(server.overworld(), market);
				for (GoodView v : List.of(partial, full, moving)) {
					GoodState state = market.good(v.goods());
					double stock = state.stock(), pressure = state.pressure();
					restore.add(() -> {
						state.setStock(stock);
						state.setPressure(pressure);
					});
				}
				// Room for 40.5 (consumption over the next seconds keeps it at 40); none at all.
				market.good(partial.goods()).setStock(market.params(partial.goods()).maxStock() - 40.5);
				market.good(full.goods()).setStock(market.params(full.goods()).maxStock());
				give(player, partial.goods(), 64, false);
				give(player, full.goods(), 4, false);
				give(player, moving.goods(), 4, false);
			});
			context.waitTicks(3);

			MarketScreen screen = openBox(s);
			Object p = line(lines(context, screen), partial.goods());
			int units = (Integer) field(p, "units");
			check(units >= 40 && units < 64 && (Integer) field(p, "heldUnits") == 64, "%s sells %d of %s (room for 40)", partial.goods(), units, field(p, "heldUnits"));
			String partly = text("screen.ak_trade_expansion.sell_all.partial", units, 64);
			List<TextRecorder.Text> partialRow = lineTexts(context, screen, partial.goods());
			check(partialRow.stream().anyMatch(t -> t.text().equals(partly)), "the row of %s does not say \"%s\": %s", partial.goods(), partly, strings(partialRow));
			Object f = line(lines(context, screen), full.goods());
			check((Integer) field(f, "units") == 0 && (Long) field(f, "total") == 0, "the good at its limit sells %s", field(f, "units"));
			String notBuying = text("screen.ak_trade_expansion.not_buying");
			List<TextRecorder.Text> fullRow = lineTexts(context, screen, full.goods());
			check(fullRow.stream().anyMatch(t -> t.text().equals(notBuying) && t.color() == COLOR_RED), "the row of %s has no red \"%s\": %s", full.goods(), notBuying, strings(fullRow));
			long shown = total(context, screen);
			String totalText = text("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper(shown), 2);
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(totalText)), "the box does not say \"%s\" (the good at its limit not counted)", totalText);
			screenshot(context, result, "18-4_partial_and_not_buying");

			// 18-5: a second player sells the third good until the box's total is out of date.
			Identifier id = moving.goods();
			long movingShown = (Long) field(line(lines(context, screen), id), "total");
			MarketSnapshotS2C old = snapshot(context, screen);
			String outcome = s.world.getServer().computeOnServer(server -> {
				other[0] = ClientBuySellCases.secondPlayer(server, stall);
				ServerPlayer o = other[0];
				ServerLevel level = server.overworld();
				Market market = market(server, stall);
				MarketAccess.open(o, level, market, (MarketStallBlockEntity) level.getBlockEntity(stall));
				for (int i = 0; i < 40; i++) {
					long now = MarketClock.now(level);
					GoodParams mine = Reputation.params(market, player(server).getUUID(), id, now);
					if (Math.abs(PriceModel.sumSell(mine, market.good(id).pressure(), 4) - movingShown) >= 2) return null;
					GoodParams theirs = Reputation.params(market, o.getUUID(), id, now);
					int batch = Math.min(8, PriceModel.sellCapacity(theirs, market.good(id)) - 4); // leave room for the player's 4
					if (batch <= 0) return "no room left to move the price of " + id;
					o.getInventory().add(new ItemStack(item(id), batch));
					long expected = PriceModel.sumSell(theirs, market.good(id).pressure(), batch);
					TradeService.handle(o, new TradeRequestC2S(market.id(), id, false, batch, expected, false, false));
				}
				return "the second player's sales did not change the price of " + id;
			});
			check(outcome == null, "%s", outcome);
			// The new prices reach the open box: its total changes by itself.
			newSnapshot(context, screen, old);
			context.waitTicks(2);
			long movingLive = (Long) field(line(lines(context, screen), id), "total");
			check(movingLive != movingShown, "after the other player's sales the open box still shows %dc for %s", movingShown, id);
			long liveTotal = total(context, screen);
			mouseAway(context);
			String liveText = text("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper(liveTotal), 2);
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(liveText)), "the open box does not show the new \"%s\"", liveText);
			screenshot(context, result, "18-5_box_updated");

			// The price moves again before the new prices arrive (changed on the server without a snapshot, as in a
			// race with the other player): "売る" is refused with "価格が変動しました…" and the box gets the latest total.
			double oldPressure = s.world.getServer().computeOnServer(server -> {
				Market market = market(server, stall);
				GoodState state = market.good(id);
				double p0 = state.pressure();
				GoodParams mine = Reputation.params(market, player(server).getUUID(), id, MarketClock.now(server.overworld()));
				for (int dir : new int[] {1, -1}) {
					double q = p0;
					for (int i = 0; i < 1000 && Math.abs(PriceModel.sumSell(mine, q, 4) - movingLive) < 4; i++) q = mine.clampPressure(q + dir);
					if (Math.abs(PriceModel.sumSell(mine, q, 4) - movingLive) >= 4) {
						state.setPressure(q);
						return p0;
					}
				}
				throw new AssertionError("the price of " + id + " cannot be moved by 4c either way");
			});
			MarketSnapshotS2C before = snapshot(context, screen);
			long money = serverMoney(s);
			int movingHeld = serverCount(s, id), partialHeld = serverCount(s, partial.goods());
			String status = confirm(context, screen);
			String changed = text("trade.ak_trade_expansion.result.price_changed");
			check(status.equals(changed), "with an old total \"売る\" says \"%s\", not \"%s\"", status, changed);
			check(serverMoney(s) == money && serverCount(s, id) == movingHeld && serverCount(s, partial.goods()) == partialHeld, "the refused sale moved money or items");
			newSnapshot(context, screen, before);
			context.waitTicks(2);
			check(context.computeOnClient(c -> (Boolean) get(screen, "sellAllOpen")), "the box closed after the refusal");
			long movingNow = (Long) field(line(lines(context, screen), id), "total");
			check(movingNow != movingLive, "the box still shows %dc for %s after the refusal", movingLive, id);
			long newTotal = total(context, screen);
			mouseAway(context);
			String newText = text("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper(newTotal), 2);
			check(TextRecorder.capture(context).stream().anyMatch(t -> t.text().equals(newText)), "the box does not show the new \"%s\"", newText);
			screenshot(context, result, "18-5_price_changed");
			s.world.getServer().runOnServer(server -> market(server, stall).good(id).setPressure(oldPressure));
			// The new total sells (the box opened again: the pressure was put back).
			MarketScreen again = openBox(s);
			newTotal = total(context, again);
			int sellUnits = (Integer) field(line(lines(context, again), partial.goods()), "units") + 4;
			String done = confirm(context, again);
			String expected = text("screen.ak_trade_expansion.sell_all.done", sellUnits, CoinItem.formatCopper(newTotal));
			check(done.equals(expected), "with the new total \"売る\" says \"%s\", not \"%s\"", done, expected);
			check(serverMoney(s) - money == newTotal, "selling paid %dc, the box said %dc", serverMoney(s) - money, newTotal);
		} finally {
			context.setScreen(() -> null);
			restoreConfig(context, config);
			s.world.getServer().runOnServer(server -> {
				player(server).closeContainer();
				if (other[0] != null) {
					other[0].closeContainer();
					server.getPlayerList().remove(other[0]);
				}
				restore.forEach(Runnable::run);
				MarketRegistry.get(server).setDirty();
			});
			ClientBuySellCases.restoreInventory(s, saved);
		}
	}
}
