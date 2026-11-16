package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.CoinMath;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the buy and sell tabs that the first cases left out (checklist 2-9, 2-10, 2-14, 5-2, 5-7, 14-2,
 * 16-3 and 23-10): "最大" and the change, Shift, today's specials and the emerald coin, another player's trade, the
 * reputation's prices and a price that changed under the open screen.
 */
final class ClientBuySellCases {
	private ClientBuySellCases() {
	}

	private static final int COLOR_SPECIAL = 0xFF8FE3FF;
	private static final int COLOR_SHIFT = 0xFF2050A0;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	private static void buyTab(ClientGameTestContext context) {
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
	}

	/** The dearest good of the buy tab with at least {@code stock} in stock (unit 1, no components, not special). */
	private static GoodView dearest(ClientGameTestContext context, MarketScreen screen, int stock, long maxPrice) {
		return visibleGoods(context, screen).stream()
				.filter(v -> v.unit() == 1 && v.patch().isEmpty() && !v.special() && v.stock() >= stock
						&& PriceModel.buyPrice(v.params(), v.pressure()) <= maxPrice)
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with " + stock + " in stock for at most " + maxPrice + "c"));
	}

	/** The player's cash: loose coins (in copper) and the balances of the wallets. */
	private static long[] cash(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			long coins = 0;
			long[] counts = WalletService.coinCounts(player);
			for (CoinType t : CoinType.values()) coins += counts[t.ordinal()] * t.value();
			long wallets = WalletService.wallets(player).stream().mapToLong(WalletItem::balance).sum();
			return new long[] {coins, wallets, WalletService.wallets(player).size()};
		});
	}

	/** Takes every coin and wallet; then gives {@code coins} copper as loose coins and, if {@code wallet}, one empty wallet. */
	private static void setCash(Session s, long coins, boolean wallet) {
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			WalletService.collectAll(player);
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
				if (inventory.getItem(slot).getItem() instanceof WalletItem) inventory.setItem(slot, ItemStack.EMPTY);
			}
			if (wallet) inventory.add(new ItemStack(ModItems.WALLET));
			WalletService.giveCoins(player, CoinMath.decompose(coins, CoinType.valueTable()));
		});
		s.context.waitTicks(3);
	}

	/** Clicks the confirm button of the buy tab and waits until the server took the money. */
	private static long confirmBuy(Session s) {
		ClientGameTestContext context = s.context;
		long money = serverMoney(s);
		clickWidget(context, text("screen.ak_trade_expansion.confirm.buy"));
		long after = money;
		for (int i = 0; i < 40 && after == money; i++) {
			context.waitTick();
			after = serverMoney(s);
		}
		check(after < money, "the buy button took no money");
		context.waitTicks(3);
		return money - after;
	}

	/** A copy of the player's inventory (coins, wallets and what the case buys would fill the hotbar for later cases). */
	static List<ItemStack> saveInventory(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			Inventory inventory = player(server).getInventory();
			List<ItemStack> copy = new java.util.ArrayList<>();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) copy.add(inventory.getItem(slot).copy());
			return copy;
		});
	}

	static void restoreInventory(Session s, List<ItemStack> saved) {
		s.world.getServer().runOnServer(server -> {
			Inventory inventory = player(server).getInventory();
			for (int slot = 0; slot < saved.size(); slot++) inventory.setItem(slot, saved.get(slot).copy());
			inventory.setChanged();
		});
		s.context.waitTicks(3);
	}

	// ---- 2-9 / 2-10 "最大" and the change

	/**
	 * 2-9 / 2-10: with an empty wallet and loose coins worth 5.5 of a good, "最大" sets the most the coins pay for (one
	 * more would cost more than the coins); buying them takes every coin and puts the change into the wallet. Without a
	 * wallet the change comes back as coins.
	 */
	static void maxAndChange(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		MarketScreen probe = reopen(s);
		buyTab(context);
		GoodView good = dearest(context, probe, 12, 5_000);
		long price = PriceModel.buyPrice(good.params(), good.pressure());
		long funds = (long) Math.ceil(price * 5.5) + 7; // not a sum of whole coins of the price: there is change
		List<ItemStack> saved = saveInventory(s);
		try {
			buyMaxAndChange(s, result, good, price, funds);
		} finally {
			context.setScreen(() -> null);
			restoreInventory(s, saved);
		}
	}

	private static void buyMaxAndChange(Session s, CaseResult.Builder result, GoodView good, long price, long funds) {
		ClientGameTestContext context = s.context;
		setCash(s, funds, true);
		MarketScreen screen = reopen(s);
		buyTab(context);
		context.runOnClient(client -> set(screen, "payByCard", false));
		select(context, screen, good.goods());
		clickWidget(context, text("screen.ak_trade_expansion.max"));
		mouseAway(context);
		int quantity = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
		GoodView now = view(context, screen, good.goods());
		long total = context.computeOnClient(client -> (Long) call(screen, "previewTotal", now, quantity));
		long oneMore = context.computeOnClient(client -> (Long) call(screen, "previewTotal", now, quantity + 1));
		check(quantity >= 5, "\"最大\" with %dc (a price of %dc) set %d", funds, price, quantity);
		check(total <= funds && oneMore > funds, "\"最大\" set %d (%dc), but %d cost %dc with %dc in coins", quantity, total, quantity + 1, oneMore, funds);
		String totalLine = text("screen.ak_trade_expansion.total", CoinItem.formatCopper(total));
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		check(texts.stream().anyMatch(t -> t.text().equals(totalLine)), "the panel does not say \"%s\": %s", totalLine, strings(texts));
		screenshot(context, result, "2-9_max");

		int items = serverCount(s, good.goods());
		long paid = confirmBuy(s);
		check(serverCount(s, good.goods()) - items == quantity, "buying %d gave %d items", quantity, serverCount(s, good.goods()) - items);
		check(paid == total, "buying %d took %dc, the screen said %dc", quantity, paid, total);
		long[] after = cash(s);
		check(after[0] == 0, "%dc of loose coins are left after paying with the coins (the change goes into the wallet)", after[0]);
		check(after[1] == funds - total, "the wallet holds %dc, not the change %dc", after[1], funds - total);

		// Without a wallet: the change comes back as coins.
		long funds2 = (long) Math.ceil(price * 1.5) + 3;
		setCash(s, funds2, false);
		MarketScreen again = reopen(s);
		buyTab(context);
		context.runOnClient(client -> set(again, "payByCard", false));
		select(context, again, good.goods());
		mouseAway(context);
		GoodView v2 = view(context, again, good.goods());
		long total2 = context.computeOnClient(client -> (Long) call(again, "previewTotal", v2, 1));
		long paid2 = confirmBuy(s);
		check(paid2 == total2, "buying 1 took %dc, the screen said %dc", paid2, total2);
		long[] after2 = cash(s);
		check(after2[2] == 0 && after2[0] == funds2 - total2, "without a wallet the change is %dc in coins (%d wallets), not %dc", after2[0], after2[2], funds2 - total2);
	}

	// ---- 2-14 Shift

	/**
	 * 2-14: with Shift held, the quantity of the panel turns blue and shows the most that can be bought (more than the
	 * 64 of one trade); the confirm button pressed with Shift buys that many.
	 */
	static void shiftConfirm(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		check(TradeExpansionConfig.get().trade.allowShiftAll, "trade.allowShiftAll is off");
		MarketScreen probe = reopen(s);
		buyTab(context);
		GoodView good = visibleGoods(context, probe).stream()
				.filter(v -> v.unit() == 1 && v.patch().isEmpty() && !v.special() && new ItemStack(itemOf(v)).getMaxStackSize() == 64
						&& PriceModel.buyPrice(v.params(), v.pressure()) <= 200)
				.min(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no cheap good that stacks to 64"));
		BlockPos stall = s.stall;
		List<ItemStack> saved = saveInventory(s);
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 1_000_000));
		double oldStock = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			double stock = market.good(good.goods()).stock();
			market.good(good.goods()).setStock(150);
			return stock;
		});
		try {
			MarketScreen screen = reopen(s);
			buyTab(context);
			context.runOnClient(client -> set(screen, "payByCard", false));
			select(context, screen, good.goods());
			mouseAway(context);
			int all = context.computeOnClient(client -> (Integer) call(screen, "maxQuantity", true));
			check(all > TradeExpansionConfig.get().trade.maxPerTransaction, "the most that can be bought is %d, not more than one trade (free space?)", all);
			int items = serverCount(s, good.goods());
			context.getInput().holdShift();
			try {
				context.waitTicks(3);
				List<TextRecorder.Text> texts = TextRecorder.capture(context);
				check(texts.stream().anyMatch(t -> t.text().equals(String.valueOf(all)) && t.color() == COLOR_SHIFT),
						"with Shift the quantity does not show %d in blue: %s", all, strings(texts));
				screenshot(context, result, "2-14_shift");
				confirmBuy(s);
			} finally {
				context.getInput().releaseShift();
			}
			int got = serverCount(s, good.goods()) - items;
			check(got == all, "the confirm button with Shift bought %d, the screen said %d", got, all);
			int quantity = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
			check(quantity == 1, "Shift changed the quantity of the next trade to %d", quantity);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> market(server, stall).good(good.goods()).setStock(Math.max(oldStock, 10)));
			restoreInventory(s, saved);
		}
	}

	// ---- 5-7 / 5-2 today's specials and the emerald coin

	/**
	 * 5-7: today's special has a ★ before its name in light blue, and its tooltip says so. 5-2: the emerald coin is on
	 * the sell tab at a price per 10 ("4c/10"), and its quantity counts tens ("1 (10)").
	 */
	static void specialsAndEmeralds(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		// A short good is orange instead: the special must not be short, and must be in stock.
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			for (Identifier id : market.specials()) {
				market.shortages().remove(id);
				if (market.good(id) != null && market.good(id).stock() < 2) market.good(id).setStock(market.params(id).targetStock());
			}
		});
		MarketScreen screen = reopen(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		GoodView special = snapshot.goods().stream().filter(GoodView::special).findFirst()
				.orElseThrow(() -> new AssertionError("the market has no special today"));
		context.clickScreenButton(special.buy() ? "screen.ak_trade_expansion.tab.buy" : "screen.ak_trade_expansion.tab.sell");
		context.waitTicks(2);
		mouseAway(context);
		int rowY = showRow(context, screen, special.goods());
		List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
		String name = "★" + special.displayStack().getHoverName().getString();
		check(row.stream().anyMatch(t -> t.text().startsWith("★") && t.color() == COLOR_SPECIAL
						&& (t.text().equals(name) || name.startsWith(t.text().replace("…", "")))),
				"the row of today's special %s shows no light blue \"%s\": %s", special.goods(), name, strings(row));
		moveTo(context, listX(context, screen) + 40, rowY + 8);
		context.waitTicks(2);
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		String line = text("screen.ak_trade_expansion.special.tooltip");
		check(tip.stream().anyMatch(t -> t.text().equals(line)), "the tooltip of the special has no \"%s\": %s", line, strings(tip));
		screenshot(context, result, "5-7_special");
		mouseAway(context);

		try {
			context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
			context.waitTicks(2);
			Identifier coin = Identifier.fromNamespaceAndPath("ak_trade_expansion", "emerald_coin");
			GoodView emerald = visibleGoods(context, screen).stream().filter(v -> v.goods().equals(coin)).findFirst()
					.orElseThrow(() -> new AssertionError("the sell tab has no emerald coin"));
			check(emerald.unit() == 10, "the emerald coin is sold %d at a time", emerald.unit());
			int coinY = showRow(context, screen, coin);
			List<TextRecorder.Text> coinRow = rowTexts(TextRecorder.capture(context), coinY);
			String price = CoinItem.formatCopper(PriceModel.sellPrice(emerald.params(), emerald.pressure())) + "/10";
			check(coinRow.stream().anyMatch(t -> t.text().equals(price)), "the emerald coin's row shows no \"%s\": %s", price, strings(coinRow));
			select(context, screen, coin);
			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			check(texts.stream().anyMatch(t -> t.text().equals("1 (10)")), "the quantity of the emerald coin does not say \"1 (10)\": %s", strings(texts));
			screenshot(context, also, "5-2_emerald_coin");
		} catch (AssertionError e) {
			throw new ClientChecklistTests.AlsoFailed(e);
		}
	}

	// ---- 14-2 another player buys

	/**
	 * 14-2: while the screen is open, a second player on the server (with its own trade screen of the same stall)
	 * buys 10 of a good: the row's price and stock change on the open screen at once.
	 */
	static void otherPlayerBuys(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		MarketScreen screen = reopen(s);
		buyTab(context);
		GoodView good = dearest(context, screen, 30, Long.MAX_VALUE);
		int rowY = showRow(context, screen, good.goods());
		mouseAway(context);
		GoodState before = context.computeOnClient(client -> (GoodState) call(screen, "currentState", view(context, screen, good.goods())));
		String stockBefore = (int) Math.floor(before.stock()) + "/" + (int) good.params().targetStock();
		List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
		check(row.stream().anyMatch(t -> t.text().equals(stockBefore)), "the row shows no stock \"%s\": %s", stockBefore, strings(row));
		MarketSnapshotS2C old = snapshot(context, screen);

		BlockPos stall = s.stall;
		ServerPlayer[] other = new ServerPlayer[1];
		try {
			String outcome = s.world.getServer().computeOnServer(server -> {
				other[0] = secondPlayer(server, stall);
				ServerPlayer p = other[0];
				ServerLevel level = server.overworld();
				Market market = market(server, stall);
				com.akf.tradeexpansion.market.MarketAccess.open(p, level, market, (MarketStallBlockEntity) level.getBlockEntity(stall));
				WalletService.give(p, 10_000_000);
				long now = MarketClock.now(level);
				GoodParams params = Reputation.params(market, p.getUUID(), good.goods(), now);
				long expected = PriceModel.sumBuy(params, market.good(good.goods()).pressure(), 10, 1, TradeExpansionConfig.get().trade.bulkDiscount());
				long money = WalletService.balance(p);
				TradeService.handle(p, new TradeRequestC2S(market.id(), good.goods(), true, 10, expected, false, false));
				return WalletService.balance(p) < money ? null : "the second player's trade did not go through";
			});
			check(outcome == null, "%s", outcome);
			newSnapshot(context, screen, old);
			context.waitTicks(2);
			mouseAway(context);
			GoodView after = view(context, screen, good.goods());
			GoodState state = context.computeOnClient(client -> (GoodState) call(screen, "currentState", after));
			check(state.stock() <= before.stock() - 9.5, "the open screen shows a stock of %.1f, was %.1f before the other player bought 10", state.stock(), before.stock());
			long priceBefore = PriceModel.buyPrice(good.params(), before.pressure());
			long priceAfter = PriceModel.buyPrice(after.params(), state.pressure());
			check(priceAfter > priceBefore, "the open screen shows %dc, was %dc before the other player bought 10", priceAfter, priceBefore);
			rowY = showRow(context, screen, good.goods());
			row = rowTexts(TextRecorder.capture(context), rowY);
			String stockAfter = (int) Math.floor(state.stock()) + "/" + (int) after.params().targetStock();
			String shown = CoinItem.formatCopper(priceAfter);
			check(row.stream().anyMatch(t -> t.text().equals(stockAfter)), "the row does not show the new stock \"%s\": %s", stockAfter, strings(row));
			check(row.stream().anyMatch(t -> t.text().equals(shown)), "the row does not show the new price \"%s\": %s", shown, strings(row));
			screenshot(context, result, "14-2_other_player_bought");
		} finally {
			s.world.getServer().runOnServer(server -> {
				if (other[0] != null) {
					other[0].closeContainer();
					server.getPlayerList().remove(other[0]);
				}
			});
			context.waitTicks(2);
		}
	}

	/** A second (survival) player next to the stall, with a connection that only keeps what the server sends. */
	private static ServerPlayer secondPlayer(MinecraftServer server, BlockPos stall) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), "other");
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerLevel level = server.overworld();
		ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = stall.west(2);
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
		return player;
	}

	// ---- 16-3 the reputation's prices

	/**
	 * 16-3: with 410 reputation points (400 decays below the threshold within ticks) the title says "評判：お得意様", the buy price in the list is lower and the sell
	 * price higher than before, and buying at the shown total goes through (no "価格が変動しました").
	 */
	static void regularCustomer(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		UUID id = s.world.getServer().computeOnServer(server -> player(server).getUUID());
		Market.Standing old = s.world.getServer().computeOnServer(server -> market(server, stall).standing(id));
		List<ItemStack> saved = saveInventory(s);
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			market.setStanding(new Market.Standing(id, 0, MarketClock.now(server.overworld())));
			WalletService.give(player(server), 1_000_000);
		});
		try {
			MarketScreen screen = reopen(s);
			buyTab(context);
			GoodView good = visibleGoods(context, screen).stream()
					.filter(v -> v.buy() && v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && v.stock() >= 3)
					.max(Comparator.comparingDouble(v -> v.params().basePrice()))
					.orElseThrow(() -> new AssertionError("no good to buy and sell"));
			long buyBefore = PriceModel.buyPrice(good.params(), good.pressure());
			long sellBefore = PriceModel.sellPrice(good.params(), good.pressure());

			s.world.getServer().runCommand("execute as @p at @p run aktradeex reputation set 410");
			context.waitTicks(3);
			MarketScreen regular = reopen(s);
			buyTab(context);
			mouseAway(context);
			MarketSnapshotS2C snapshot = snapshot(context, regular);
			check(snapshot.reputation().level() == 3, "410 points are level %d, not 3", snapshot.reputation().level());
			String label = text("screen.ak_trade_expansion.reputation.label", Component.translatable("screen.ak_trade_expansion.reputation.level.3"));
			check(label.equals("評判：お得意様") || !ClientChecklistTests.LANGUAGE.equals("ja_jp"), "level 3 is called \"%s\"", label);
			int top = context.computeOnClient(client -> topPos(regular));
			List<TextRecorder.Text> titleRow = TextRecorder.capture(context).stream().filter(t -> t.y0() >= top + 2 && t.y0() < top + 14).toList();
			check(titleRow.stream().anyMatch(t -> t.text().equals(label)), "the title row does not say \"%s\": %s", label, strings(titleRow));

			GoodView now = view(context, regular, good.goods());
			long buyAfter = PriceModel.buyPrice(now.params(), now.pressure());
			check(buyAfter < buyBefore, "the buy price of %s went %dc -> %dc with the reputation", good.goods(), buyBefore, buyAfter);
			int rowY = showRow(context, regular, good.goods());
			List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
			check(row.stream().anyMatch(t -> t.text().equals(CoinItem.formatCopper(buyAfter))), "the row does not show the lower price %s: %s",
					CoinItem.formatCopper(buyAfter), strings(row));
			screenshot(context, result, "16-3_regular_customer");
			context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
			context.waitTicks(2);
			GoodView sellView = view(context, regular, good.goods());
			long sellAfter = PriceModel.sellPrice(sellView.params(), sellView.pressure());
			check(sellAfter > sellBefore, "the sell price of %s went %dc -> %dc with the reputation", good.goods(), sellBefore, sellAfter);

			// Buying at the shown total: no "price changed".
			buyTab(context);
			context.runOnClient(client -> set(regular, "payByCard", false));
			select(context, regular, good.goods());
			mouseAway(context);
			GoodView v = view(context, regular, good.goods());
			long total = context.computeOnClient(client -> (Long) call(regular, "previewTotal", v, 1));
			long paid = confirmBuy(s);
			check(paid == total, "buying 1 took %dc, the screen said %dc", paid, total);
			String bought = text("screen.ak_trade_expansion.bought", v.displayStack().getHoverName(), 1, CoinItem.formatCopper(total));
			Component status = context.computeOnClient(client -> (Component) get(regular, "status"));
			check(status.getString().equals(bought), "after buying the screen says \"%s\", not \"%s\"", status.getString(), bought);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.setStanding(old != null ? old : new Market.Standing(id, 0, MarketClock.now(server.overworld())));
				MarketRegistry.get(server).setDirty();
			});
			restoreInventory(s, saved);
		}
	}

	// ---- 23-10 the price changed under the open screen

	/**
	 * 23-10: the price of the selected good changes on the server without a new snapshot (as at the change of day,
	 * when a rival guild or a trend moves it); buying at the old total shows "価格が変動しました。…" in red, nothing is
	 * bought, and the screen gets the latest price.
	 */
	static void priceChanged(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		// Only when short of money: every 1,000,000c more is another stack of coins in the hotbar, which later cases need.
		s.world.getServer().runOnServer(server -> {
			if (WalletService.balance(player(server)) < 200_000) WalletService.give(player(server), 1_000_000);
		});
		MarketScreen screen = reopen(s);
		buyTab(context);
		GoodView good = dearest(context, screen, 3, Long.MAX_VALUE);
		context.runOnClient(client -> set(screen, "payByCard", false));
		select(context, screen, good.goods());
		mouseAway(context);
		long shownBefore = PriceModel.buyPrice(good.params(), good.pressure());
		BlockPos stall = s.stall;
		Identifier id = good.goods();
		double oldPressure = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			GoodState state = market.good(id);
			double p = state.pressure();
			// Dearer by far more than trade.priceTolerance.
			GoodParams params = Reputation.params(market, player(server).getUUID(), id, MarketClock.now(server.overworld()));
			double q = p;
			for (int i = 0; i < 1000 && PriceModel.buyPrice(params, q) < shownBefore * 1.2 + 2; i++) q = params.clampPressure(q - 1);
			state.setPressure(q);
			return p;
		});
		try {
			MarketSnapshotS2C old = snapshot(context, screen);
			long money = serverMoney(s);
			int items = serverCount(s, id);
			clickWidget(context, text("screen.ak_trade_expansion.confirm.buy"));
			String message = text("trade.ak_trade_expansion.result.price_changed");
			boolean said = waitClient(context, client -> ((Component) get(screen, "status")).getString().equals(message), 40);
			check(said, "the screen says \"%s\", not \"%s\"", context.computeOnClient(client -> ((Component) get(screen, "status")).getString()), message);
			newSnapshot(context, screen, old);
			context.waitTicks(2);
			mouseAway(context);
			check(serverMoney(s) == money && serverCount(s, id) == items, "the refused trade moved money or items");
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			String head = message.substring(0, Math.min(8, message.length()));
			check(texts.stream().anyMatch(t -> t.text().startsWith(head) && t.color() == 0xFFA02020), "the screen shows no red \"%s…\": %s", head, strings(texts));
			GoodView latest = view(context, screen, id);
			long shownAfter = PriceModel.buyPrice(latest.params(), ((GoodState) context.computeOnClient(client -> call(screen, "currentState", latest))).pressure());
			long serverPrice = s.world.getServer().computeOnServer(server -> {
				Market market = market(server, stall);
				return PriceModel.buyPrice(Reputation.params(market, player(server).getUUID(), id, MarketClock.now(server.overworld())), market.good(id).pressure());
			});
			check(shownAfter > shownBefore && Math.abs(shownAfter - serverPrice) <= 1, "after the refusal the screen shows %dc (was %dc, the server's price %dc)",
					shownAfter, shownBefore, serverPrice);
			screenshot(context, result, "23-10_price_changed");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> market(server, stall).good(id).setPressure(oldPressure));
		}
	}
}
