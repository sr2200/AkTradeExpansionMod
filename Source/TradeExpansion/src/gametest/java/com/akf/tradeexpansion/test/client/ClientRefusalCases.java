package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.BankCardItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.ConfigSyncS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of what the trade screen says when the server refuses (checklist 2-18, 7-11, 8-7 and 35-1): the red status
 * line for each refused trade, deposit, withdrawal and exchange, with nothing changed, and the exchange fee. The
 * server's result codes are checked by the server tests; here only what the screen shows. The screen's own buttons do
 * not send most refused trades (they are grayed out), so those requests are sent as the screen sends them.
 */
final class ClientRefusalCases {
	private ClientRefusalCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String S = "screen." + NS + ".";
	/** The status line's red as drawn (MarketScreen draws COLOR_RED as this darker red). */
	private static final int STATUS_RED = 0xFFA02020;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	private static void tab(ClientGameTestContext context, String tab) {
		context.clickScreenButton(S + "tab." + tab);
		context.waitTicks(3);
		mouseAway(context);
	}

	private static String status(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(c -> ((Component) get(screen, "status")).getString());
	}

	/**
	 * Clears the status line, runs {@code action} (on the test thread) and waits for the line to say {@code key}'s text; then
	 * checks that it is drawn in full and red. Adds what is wrong to {@code problems}.
	 */
	private static void expectRefusal(ClientGameTestContext context, MarketScreen screen, String what, String key, Runnable action, List<String> problems) {
		context.runOnClient(c -> set(screen, "status", Component.empty()));
		String message = text(key);
		action.run();
		mouseAway(context);
		if (!waitClient(c -> ((Component) get(screen, "status")).getString().equals(message), context, 40)) {
			problems.add(what + ": the screen says \"" + status(context, screen) + "\", not \"" + message + "\"");
			return;
		}
		context.waitTicks(3); // the snapshot that follows
		mouseAway(context);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		boolean drawn = texts.stream().anyMatch(t -> t.text().equals(message) && t.color() == STATUS_RED);
		if (!drawn) problems.add(what + ": \"" + message + "\" is not drawn in full in red: " + strings(texts));
	}

	private static boolean waitClient(java.util.function.Predicate<net.minecraft.client.Minecraft> condition, ClientGameTestContext context, int ticks) {
		return ClientUtil.waitClient(context, condition, ticks);
	}

	/** Money, the items of the goods and every good's stock and pressure of the market: what a refusal must not change. */
	private static String fingerprint(Session s, List<Identifier> goods) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			StringBuilder b = new StringBuilder("money " + WalletService.balance(player) + " bank " + BankService.accounts(player).balance(player.getUUID()));
			Market market = market(server, stall);
			for (Identifier id : goods) {
				GoodState state = market.good(id);
				b.append(' ').append(id).append(" held ").append(count(player, item(id))).append(" stock ").append((long) Math.floor(state.stock()));
			}
			return b.toString();
		});
	}

	/** Takes every coin and the wallets' money (kept to be given back); then gives {@code keep}. Returns what was taken. */
	private static long takeMoney(Session s, long keep) {
		long taken = s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			long all = WalletService.balance(player);
			long change = WalletService.take(player, all);
			WalletService.give(player, change);
			WalletService.give(player, keep);
			return all - change;
		});
		s.context.waitTicks(3);
		return taken;
	}

	private static void giveMoney(Session s, long amount) {
		if (amount > 0) s.world.getServer().runOnServer(server -> WalletService.give(player(server), amount));
		s.context.waitTicks(3);
	}

	private static TradeRequestC2S request(ClientGameTestContext context, MarketScreen screen, GoodView view, boolean buy, boolean card) {
		long marketId = context.computeOnClient(c -> screen.getMenu().marketId());
		long total = context.computeOnClient(c -> (Long) call(screen, "previewTotal", view, 1));
		return new TradeRequestC2S(marketId, view.goods(), buy, 1, total, card, false);
	}

	private static void send(ClientGameTestContext context, TradeRequestC2S request) {
		context.runOnClient(c -> ClientPlayNetworking.send(request));
	}

	// ---- 2-18 refused trades

	/**
	 * 2-18: a refused buy or sell shows its reason in red on the status line and changes nothing: not enough money,
	 * sold out, the market cannot take more, no items to sell, card payment without a card, creative mode.
	 */
	static void trades(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		MarketScreen screen = reopen(s);
		tab(context, "buy");
		List<GoodView> buyable = visibleGoods(context, screen).stream()
				.filter(v -> v.unit() == 1 && v.patch().isEmpty() && !v.special() && v.stock() >= 2 && PriceModel.buyPrice(v.params(), v.pressure()) >= 5)
				.sorted(Comparator.comparingDouble(v -> v.params().basePrice())).toList();
		check(buyable.size() >= 2, "fewer than 2 goods to buy (%d)", buyable.size());
		GoodView dear = buyable.getLast();
		GoodView soldOut = buyable.getFirst();
		tab(context, "sell");
		List<GoodView> sellable = visibleGoods(context, screen).stream().filter(v -> v.unit() == 1 && v.patch().isEmpty()).toList();
		int heldNone = -1;
		for (int i = 0; i < sellable.size(); i++) {
			GoodView v = sellable.get(i);
			if (serverCount(s, v.goods()) == 0 && !v.goods().equals(dear.goods()) && !v.goods().equals(soldOut.goods())) {
				heldNone = i;
				break;
			}
		}
		check(heldNone >= 0, "every good of the sell tab is carried");
		GoodView notHeld = sellable.get(heldNone);
		GoodView full = sellable.stream().filter(v -> !v.goods().equals(notHeld.goods()) && !v.goods().equals(dear.goods()) && !v.goods().equals(soldOut.goods()))
				.findFirst().orElseThrow(() -> new AssertionError("no third good to sell"));
		List<Identifier> goods = List.of(dear.goods(), soldOut.goods(), notHeld.goods(), full.goods());

		double[] oldStock = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			return new double[] {market.good(soldOut.goods()).stock(), market.good(full.goods()).stock()};
		});
		List<ItemStack> cards = new ArrayList<>();
		long[] taken = {0};
		List<String> problems = new ArrayList<>();
		try {
			// Not enough money.
			tab(context, "buy");
			taken[0] = takeMoney(s, 1);
			String before = fingerprint(s, goods);
			GoodView dearNow = view(context, screen, dear.goods());
			TradeRequestC2S funds = request(context, screen, dearNow, true, false);
			expectRefusal(context, screen, "not enough money", "trade." + NS + ".result.insufficient_funds", () -> send(context, funds), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a buy without the money changed " + before + " -> " + fingerprint(s, goods));
			screenshot(context, result, "2-18_insufficient_funds");
			giveMoney(s, taken[0]);
			taken[0] = 0;

			// Sold out.
			s.world.getServer().runOnServer(server -> market(server, stall).good(soldOut.goods()).setStock(0));
			before = fingerprint(s, goods);
			TradeRequestC2S stock = request(context, screen, view(context, screen, soldOut.goods()), true, false);
			expectRefusal(context, screen, "sold out", "trade." + NS + ".result.insufficient_stock", () -> send(context, stock), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a sold-out buy changed " + before + " -> " + fingerprint(s, goods));

			// Card payment without a card (the player may carry one from the bank cases).
			s.world.getServer().runOnServer(server -> {
				Inventory inventory = player(server).getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					if (inventory.getItem(slot).getItem() instanceof BankCardItem) {
						cards.add(inventory.getItem(slot).copy());
						inventory.setItem(slot, ItemStack.EMPTY);
					}
				}
			});
			context.waitTicks(3);
			before = fingerprint(s, goods);
			TradeRequestC2S card = request(context, screen, view(context, screen, dear.goods()), true, true);
			expectRefusal(context, screen, "no card", "trade." + NS + ".result.card_invalid", () -> send(context, card), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a card payment without a card changed " + before + " -> " + fingerprint(s, goods));

			// Creative mode.
			s.world.getServer().runOnServer(server -> player(server).setGameMode(GameType.CREATIVE));
			context.waitTicks(3);
			before = fingerprint(s, goods);
			TradeRequestC2S creative = request(context, screen, view(context, screen, dear.goods()), true, false);
			expectRefusal(context, screen, "creative", "trade." + NS + ".result.creative_not_allowed", () -> send(context, creative), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a buy in creative mode changed " + before + " -> " + fingerprint(s, goods));
			s.world.getServer().runOnServer(server -> player(server).setGameMode(GameType.SURVIVAL));
			context.waitTicks(3);

			// No items to sell.
			tab(context, "sell");
			before = fingerprint(s, goods);
			TradeRequestC2S items = request(context, screen, view(context, screen, notHeld.goods()), false, false);
			expectRefusal(context, screen, "no items", "trade." + NS + ".result.insufficient_items", () -> send(context, items), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a sale without the items changed " + before + " -> " + fingerprint(s, goods));

			// The market cannot take more.
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				var params = Reputation.params(market, player(server).getUUID(), full.goods(), MarketClock.now(server.overworld()));
				market.good(full.goods()).setStock(params.maxStock());
				giveItems(player(server), item(full.goods()), 1);
			});
			context.waitTicks(3);
			before = fingerprint(s, goods);
			TradeRequestC2S stockFull = request(context, screen, view(context, screen, full.goods()), false, false);
			expectRefusal(context, screen, "stock full", "trade." + NS + ".result.stock_full", () -> send(context, stockFull), problems);
			if (!fingerprint(s, goods).equals(before)) problems.add("a sale to a full market changed " + before + " -> " + fingerprint(s, goods));
			screenshot(context, result, "2-18_stock_full");
			check(problems.isEmpty(), "%s", problems);
		} finally {
			context.setScreen(() -> null);
			if (taken[0] > 0) {
				takeMoney(s, 0); // the 1c given
				giveMoney(s, taken[0]);
			}
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				for (ItemStack c : cards) player.getInventory().add(c);
				var it = item(full.goods());
				for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
					if (player.getInventory().getItem(slot).is(it)) {
						player.getInventory().getItem(slot).shrink(1);
						break;
					}
				}
				Market market = market(server, stall);
				market.good(soldOut.goods()).setStock(oldStock[0]);
				market.good(full.goods()).setStock(oldStock[1]);
			});
		}
	}

	// ---- 7-11 refused deposits and withdrawals

	@SuppressWarnings("unchecked")
	private static Button bankButton(ClientGameTestContext context, MarketScreen screen, int index) {
		return context.computeOnClient(c -> ((List<Button>) get(screen, "bankWidgets")).get(index));
	}

	private static long bankBalance(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			return BankService.accounts(player).balance(player.getUUID());
		});
	}

	private static void addBank(Session s, long amount) {
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BankService.accounts(player).add(player.getUUID(), amount);
		});
	}

	/**
	 * 7-11: withdrawing more than the balance ("10,000c" with 50c in the account) and depositing more than the cash
	 * ("10,000c" with 500c) say "お金が足りません" in red; neither the balance nor the cash changes.
	 */
	static void bank(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		long bank0 = bankBalance(s);
		long[] taken = {0};
		List<String> problems = new ArrayList<>();
		try {
			addBank(s, 50 - bank0);
			taken[0] = takeMoney(s, 500);
			MarketScreen screen = reopen(s);
			tab(context, "bank");
			Supplier<String> state = () -> "bank " + bankBalance(s) + " cash " + serverMoney(s);
			String before = state.get();
			Button withdraw = bankButton(context, screen, 5); // withdraw 10,000c
			check(withdraw.getMessage().getString().equals(CoinItemFormat.coins(10_000)), "the button 5 of the bank tab says \"%s\"", withdraw.getMessage().getString());
			expectRefusal(context, screen, "withdraw", "bank." + NS + ".result.insufficient_funds", () -> clickWidget(context, withdraw), problems);
			if (!state.get().equals(before)) problems.add("withdrawing more than the balance changed " + before + " -> " + state.get());
			screenshot(context, result, "7-11_withdraw_too_much");

			Button deposit = bankButton(context, screen, 4); // deposit 10,000c
			expectRefusal(context, screen, "deposit", "bank." + NS + ".result.insufficient_funds", () -> clickWidget(context, deposit), problems);
			if (!state.get().equals(before)) problems.add("depositing more than the cash changed " + before + " -> " + state.get());
			screenshot(context, result, "7-11_deposit_too_much");
			check(problems.isEmpty(), "%s", problems);
		} finally {
			context.setScreen(() -> null);
			addBank(s, bank0 - bankBalance(s));
			takeMoney(s, 0); // the 500c given
			giveMoney(s, taken[0]);
		}
	}

	// ---- 8-7 refused exchanges and the fee

	private static long[] coins(ClientGameTestContext context) {
		return context.computeOnClient(c -> WalletService.coinCounts(c.player));
	}

	/**
	 * 8-7: "↓1" of a tier with no coins and "↑1" without enough coins of the tier below say "所持金が足りません" in red and
	 * change no coins. The help line shows {@code currency.exchangeFee} ("手数料 10%" with 0.1), and breaking one iron
	 * coin then gives floor(ratio × 0.9) copper.
	 */
	static void exchange(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		TradeExpansionConfig old = s.world.getServer().computeOnServer(server -> ConfigManager.copy(TradeExpansionConfig.get()));
		CoinType[] types = CoinType.values();
		// The highest tier: nobody carries it in the test world; the tier below it is kept below one coin's worth.
		int top = types.length - 1;
		long[] stash = {0};
		List<String> problems = new ArrayList<>();
		try {
			stash[0] = takeMoney(s, 0);
			s.world.getServer().runOnServer(server -> WalletService.giveCoins(player(server), new long[] {5, 2, 0, 0, 0}));
			context.waitTicks(3);
			MarketScreen screen = reopen(s);
			MarketScreen first = screen;
			tab(context, "exchange");
			long[] before = coins(context);
			check(before[top] == 0 && before[top - 1] == 0, "the player has coins of the top tiers: %s", java.util.Arrays.toString(before));
			// Row t (1..) has ↓1 ↓10 ↑1 ↑10 at index (t - 1) * 4.
			@SuppressWarnings("unchecked")
			List<Button> buttons = context.computeOnClient(c -> (List<Button>) get(first, "exchangeWidgets"));
			Button downTop = buttons.get((top - 1) * 4);
			Button upTop = buttons.get((top - 1) * 4 + 2);
			check(downTop.getMessage().getString().equals("↓1") && upTop.getMessage().getString().equals("↑1"), "the buttons of the last row say \"%s\" and \"%s\"",
					downTop.getMessage().getString(), upTop.getMessage().getString());
			check(downTop.visible && downTop.active && upTop.visible && upTop.active, "the buttons of the last row are not shown or not active");
			String key = "trade." + NS + ".result.insufficient_funds";
			expectRefusal(context, screen, "break without coins", key, () -> clickWidget(context, downTop), problems);
			if (!java.util.Arrays.equals(coins(context), before)) problems.add("breaking a coin not carried changed the coins " + java.util.Arrays.toString(before)
					+ " -> " + java.util.Arrays.toString(coins(context)));
			expectRefusal(context, screen, "make without coins", key, () -> clickWidget(context, upTop), problems);
			if (!java.util.Arrays.equals(coins(context), before)) problems.add("making a coin without enough below changed the coins " + java.util.Arrays.toString(before)
					+ " -> " + java.util.Arrays.toString(coins(context)));
			screenshot(context, result, "8-7_exchange_refused");

			// The fee.
			s.world.getServer().runOnServer(server -> {
				TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
				config.currency.exchangeFee = 0.1;
				ConfigManager.apply(config);
			});
			context.waitTicks(3);
			MarketScreen second = reopen(s);
			tab(context, "exchange");
			String help = text(S + "exchange.help", 10);
			List<String> texts = strings(TextRecorder.capture(context));
			if (!texts.contains(help)) problems.add("the exchange tab does not say \"" + help + "\": " + texts);
			long[] start = coins(context);
			@SuppressWarnings("unchecked")
			List<Button> row = context.computeOnClient(c -> (List<Button>) get(second, "exchangeWidgets"));
			clickWidget(context, row.getFirst()); // ↓1 of iron
			mouseAway(context);
			long ratio = types[1].value() / types[0].value();
			long expected = (long) Math.floor(ratio * 0.9 + 1e-9);
			if (!waitClient(c -> WalletService.coinCounts(c.player)[1] == start[1] - 1, context, 40)) {
				problems.add("\"↓1\" did not break an iron coin with the fee 10%");
			} else {
				context.waitTicks(2);
				long gained = coins(context)[0] - start[0];
				if (gained != expected) problems.add("breaking an iron coin with the fee 10% gave " + gained + " copper, not " + expected);
			}
			screenshot(context, result, "8-7_exchange_fee");
			check(problems.isEmpty(), "%s", problems);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> ConfigManager.apply(old));
			takeMoney(s, 0); // the coins given
			giveMoney(s, stash[0]);
		}
	}

	/**
	 * 35-1 (part): the fee the exchange tab shows is the one the server sends ({@code ConfigSyncS2C}), not the player's own
	 * setting. The server and the client of the test share one config in memory, so the server's file cannot differ from
	 * the player's; instead, with 0% in the settings, the server sends the sync of a server with 30%.
	 */
	static void serverFee(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		TradeExpansionConfig old = s.world.getServer().computeOnServer(server -> ConfigManager.copy(TradeExpansionConfig.get()));
		List<String> problems = new ArrayList<>();
		try {
			s.world.getServer().runOnServer(server -> {
				TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
				config.currency.exchangeFee = 0.0;
				ConfigManager.apply(config);
			});
			context.waitTicks(3);
			reopen(s);
			tab(context, "exchange");
			String own = text(S + "exchange.help", 0);
			List<String> texts = strings(TextRecorder.capture(context));
			if (!texts.contains(own)) problems.add("with the fee 0% the exchange tab does not say \"" + own + "\": " + texts);
			context.setScreen(() -> null);

			s.world.getServer().runOnServer(server -> {
				ConfigSyncS2C sync = ConfigSyncS2C.fromServer(server);
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player(server), new ConfigSyncS2C(sync.ratios(),
						sync.sneakUseExchange(), sync.halfLifeDays(), sync.restockMultiplier(), sync.clock(), sync.maxPerTransaction(),
						sync.maxDistance(), sync.walletCapacity(), sync.bankMinMarketLevel(), sync.cardFee(), sync.cardTransactionFee(),
						sync.allowShiftAll(), sync.interestRatePerDay(), sync.interestMaxBalance(), 0.3, sync.clockOffset()));
			});
			if (!waitClient(c -> Math.abs(TradeExpansionConfig.get().currency.exchangeFee - 0.3) < 1e-9, context, 40)) {
				problems.add("the client did not take the server's fee 30% (it has " + TradeExpansionConfig.get().currency.exchangeFee + ")");
			}
			reopen(s);
			tab(context, "exchange");
			String server = text(S + "exchange.help", 30);
			texts = strings(TextRecorder.capture(context));
			if (!texts.contains(server)) problems.add("after the server's sync with 30% the exchange tab does not say \"" + server + "\": " + texts);
			screenshot(context, result, "35-1_server_exchange_fee");
			check(problems.isEmpty(), "%s", problems);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> ConfigManager.apply(old));
			context.waitTicks(3);
		}
	}

	/** Coin amounts as the screen writes them. */
	private static final class CoinItemFormat {
		static String coins(long amount) {
			return com.akf.tradeexpansion.item.CoinItem.formatCopper(amount);
		}
	}
}
