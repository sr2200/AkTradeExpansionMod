package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.RequestView;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.UUID;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the bank tab (when it shows, its buttons and lines) and the requests tab (the list, accepting,
 * delivering, a 3-star request without the reputation) — checklist chapters 5, 7, 9, 16 and 22.
 */
final class ClientBankQuestCases {
	private ClientBankQuestCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String S = "screen." + NS + ".";
	private static final Identifier BANK = Identifier.fromNamespaceAndPath(NS, "bank");
	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_GOLD = 0xFFFFD700;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	private static String squeeze(String s) {
		return s.replaceAll("\\s", "").replace("…", "");
	}

	private static String coins(long amount) {
		return CoinItem.formatCopper(amount);
	}

	private static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	private static boolean tabShown(ClientGameTestContext context, String tab) {
		String key = S + "tab." + tab;
		return context.computeOnClient(c -> c.gui.screen().children().stream().anyMatch(ch -> ch instanceof AbstractWidget w && w.visible
				&& w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)));
	}

	private static AccountS2C account(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(c -> (AccountS2C) get(screen, "account"));
	}

	private static long bankBalance(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			return BankService.accounts(player).balance(player.getUUID());
		});
	}

	/** Waits (at most 2 seconds) until the server's bank balance is not {@code old} any more. */
	private static long newBalance(Session s, long old) {
		for (int i = 0; i < 40 && bankBalance(s) == old; i++) s.context.waitTick();
		s.context.waitTicks(3); // the account packet
		return bankBalance(s);
	}

	@SuppressWarnings("unchecked")
	private static Button bankButton(ClientGameTestContext context, MarketScreen screen, int index) {
		return context.computeOnClient(c -> ((List<Button>) get(screen, "bankWidgets")).get(index));
	}

	/** All texts of the screen in drawing order, joined without spaces (mouse away). */
	private static String all(ClientGameTestContext context) {
		mouseAway(context);
		return squeeze(String.join("", strings(TextRecorder.capture(context))));
	}

	private static String rate() {
		return String.valueOf(Math.round(TradeExpansionConfig.get().bank.interestRatePerDay * 100_000) / 1000.0).replaceAll("\\.?0+$", "");
	}

	// ---- 5-4 / 22-30 when the bank tab shows

	/**
	 * 5-4: with {@code bank.minMarketLevel} 3 (the default) the bank tab shows at a market of level 3 and not at level
	 * 2. 22-30: at level 1 with a bank building it shows too, and a deposit there registers the account: the interest
	 * line says "（200,000c まで・○○の銀行に登録）".
	 */
	static void bankTabByLevel(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		BlockPos stall = s.stall;
		int[] old = s.world.getServer().computeOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			int minLevel = config.bank.minMarketLevel;
			config.bank.minMarketLevel = 3;
			ConfigManager.apply(config);
			return new int[] {minLevel, market(server, stall).level()};
		});
		try {
			setLevel(s, 2);
			reopen(s);
			check(!tabShown(context, "bank"), "the bank tab shows at a market of level 2 (bank.minMarketLevel 3)");
			screenshot(context, result, "5-4_level2_no_bank");
			setLevel(s, 3);
			reopen(s);
			check(tabShown(context, "bank"), "no bank tab at a market of level 3");
			screenshot(context, result, "5-4_level3_bank");

			try {
				setLevel(s, 1);
				s.world.getServer().runOnServer(server -> {
					Market market = market(server, stall);
					long now = MarketClock.now(server.overworld());
					Facility bank = new Facility(BANK, FacilityKind.BANK);
					bank.start(stall, Rotation.NONE, stall, new BoundingBox(stall), "plains", now, now);
					// A completed building counts as destroyed without its sign at the core: one in the air above the stall.
					BlockPos core = stall.above(4);
					server.overworld().setBlock(core, com.akf.tradeexpansion.registry.ModBlocks.FACILITY_SIGN.defaultBlockState(), 2);
					bank.complete(core);
					market.facilities().put(BANK, bank);
					MarketRegistry.get(server).setDirty();
				});
				s.world.getServer().runOnServer(server -> com.akf.tradeexpansion.currency.WalletService.give(player(server), 5_000));
				MarketScreen screen = reopen(s);
				check(tabShown(context, "bank"), "no bank tab at a market of level 1 with a bank building");
				context.clickScreenButton(S + "tab.bank");
				context.waitTicks(3);
				long before = bankBalance(s);
				clickWidget(context, bankButton(context, screen, 2)); // deposit 1,000c
				long after = newBalance(s, before);
				check(after - before == 1_000, "depositing at the bank building added %dc", after - before);
				AccountS2C account = account(context, screen);
				check(account != null && !account.bankName().isEmpty(), "the account is not registered at the bank building (%s)", account);
				String line = squeeze(text(S + "bank.interest.registered", rate(), coins(account.interestCap()), account.bankName()));
				String shown = all(context);
				check(shown.contains(line), "the interest line does not say \"%s\": %s", line, shown);
				check(account.interestCap() > TradeExpansionConfig.get().bank.interestMaxBalance, "the interest cap %d is not raised by the bank building",
						account.interestCap());
				screenshot(context, also, "22-30_bank_building");
				clickWidget(context, bankButton(context, screen, 7)); // withdraw everything
				newBalance(s, after);
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.facilities().remove(BANK);
				server.overworld().setBlock(stall.above(4), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				market.setLevel(old[1]);
				TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
				config.bank.minMarketLevel = old[0];
				ConfigManager.apply(config);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	private static void setLevel(Session s, int level) {
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			market(server, stall).setLevel(level);
			MarketRegistry.get(server).setDirty();
		});
	}

	// ---- 7-7 / 16-13 the bank tab

	/**
	 * 7-7: the bank tab's buttons ("1,000c" deposit, "100c" and "全額" withdraw) move the balance and the lines "銀行残高"
	 * / "現金" follow. 16-13: the interest line "利息：1日 0.03%（100,000c まで）".
	 */
	static void bankTab(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		s.world.getServer().runOnServer(server -> com.akf.tradeexpansion.currency.WalletService.give(player(server), 5_000));
		MarketScreen screen = reopen(s);
		context.clickScreenButton(S + "tab.bank");
		context.waitTicks(3);
		long bank0 = bankBalance(s);
		long cash0 = serverMoney(s);
		String shown = all(context);
		for (String line : List.of(text(S + "bank.balance", coins(bank0)), text(S + "bank.cash", coins(cash0)), text(S + "bank.deposit"),
				text(S + "bank.withdraw"))) {
			check(shown.contains(squeeze(line)), "the bank tab does not say \"%s\": %s", line, shown);
		}
		screenshot(context, result, "7-7_bank_tab");

		clickWidget(context, bankButton(context, screen, 2)); // deposit 1,000c
		long bank1 = newBalance(s, bank0);
		check(bank1 - bank0 == 1_000 && cash0 - serverMoney(s) == 1_000, "\"1,000c\" deposited %dc and took %dc", bank1 - bank0, cash0 - serverMoney(s));
		check(all(context).contains(squeeze(text(S + "bank.balance", coins(bank1)))), "the balance line does not show %s", coins(bank1));
		clickWidget(context, bankButton(context, screen, 1)); // withdraw 100c
		long bank2 = newBalance(s, bank1);
		check(bank1 - bank2 == 100, "\"100c\" withdrew %dc", bank1 - bank2);
		clickWidget(context, bankButton(context, screen, 7)); // withdraw everything
		long bank3 = newBalance(s, bank2);
		check(bank3 == 0, "\"%s\" left %dc in the bank", text(S + "bank.all"), bank3);
		check(serverMoney(s) == cash0 + bank0, "the cash is %d after everything came back (%d expected)", serverMoney(s), cash0 + bank0);
		check(all(context).contains(squeeze(text(S + "bank.balance", coins(0)))), "the balance line does not show 0c");
		screenshot(context, result, "7-7_bank_after");

		try {
			AccountS2C account = account(context, screen);
			long cap = account == null ? TradeExpansionConfig.get().bank.interestMaxBalance : account.interestCap();
			String key = account != null && !account.bankName().isEmpty() ? "bank.interest.registered" : "bank.interest";
			String line = key.endsWith("registered") ? text(S + key, rate(), coins(cap), account.bankName()) : text(S + key, rate(), coins(cap));
			check(all(context).contains(squeeze(line)), "the interest line does not say \"%s\"", line);
		} catch (Throwable t) {
			throw new ClientChecklistTests.AlsoFailed(t);
		}
	}

	// ---- 7-8 / 7-9 the card on the screen

	/** Gives the player a valid bank card (as "カードを発行" does, without the fee). */
	static void giveCard(Session s) {
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			int number = BankService.accounts(player).nextCardNumber(player.getUUID());
			net.minecraft.world.item.ItemStack card = new net.minecraft.world.item.ItemStack(com.akf.tradeexpansion.registry.ModItems.BANK_CARD);
			card.set(com.akf.tradeexpansion.registry.ModDataComponents.CARD_OWNER,
					new com.akf.tradeexpansion.registry.ModDataComponents.CardOwner(player.getUUID(), player.getPlainTextName(), number));
			player.getInventory().add(card);
		});
		s.context.waitTicks(2);
	}

	/** Takes every bank card from the player (the later cases pay with cash). */
	static void takeCards(Session s) {
		s.world.getServer().runOnServer(server -> {
			var inventory = player(server).getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).getItem() instanceof com.akf.tradeexpansion.item.BankCardItem) inventory.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
		});
		s.context.waitTicks(2);
	}

	private static boolean carriesCard(ClientGameTestContext context) {
		return context.computeOnClient(c -> {
			for (net.minecraft.world.item.ItemStack stack : c.player.getInventory()) {
				if (stack.getItem() instanceof com.akf.tradeexpansion.item.BankCardItem) return true;
			}
			return false;
		});
	}

	/**
	 * 7-8: the bank tab's "カードを発行（N）" takes the fee from the account and gives a card; without a card the trade tabs
	 * have no payment button. 7-9: with the card the buy tab shows "支払い：カード" and buying takes the bank balance,
	 * not the cash; selling pays into the account. Pressed, the button says "支払い：現金" and buying takes the cash.
	 */
	static void cardButtons(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		takeCards(s);
		long fee = TradeExpansionConfig.get().bank.cardFee;
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BankService.accounts(player).add(player.getUUID(), fee + 20_000);
			com.akf.tradeexpansion.currency.WalletService.give(player, 20_000);
		});
		try {
			MarketScreen screen = reopen(s);
			context.clickScreenButton(S + "tab.buy");
			context.waitTicks(2);
			check(!field(context, screen, "paymentButton").visible, "the payment button shows without a card");

			context.clickScreenButton(S + "tab.bank");
			context.waitTicks(3);
			@SuppressWarnings("unchecked")
			Button issue = context.computeOnClient(c -> ((List<Button>) get(screen, "bankWidgets")).getLast());
			check(issue.visible && issue.getMessage().getString().equals(text(S + "bank.issue_card", coins(fee))), "the issue button says \"%s\" (visible %s), not \"%s\"",
					issue.getMessage().getString(), issue.visible, text(S + "bank.issue_card", coins(fee)));
			long bank0 = bankBalance(s);
			long cash0 = serverMoney(s);
			clickWidget(context, issue);
			mouseAway(context);
			long bank1 = newBalance(s, bank0);
			check(bank0 - bank1 == fee, "issuing the card took %dc from the account (the fee is %dc)", bank0 - bank1, fee);
			check(serverMoney(s) == cash0, "issuing the card took cash");
			check(waitClient(context, c -> account(context, screen) != null && account(context, screen).validCard(), 40) && carriesCard(context),
					"no valid card after \"%s\"", text(S + "bank.issue_card", coins(fee)));
			screenshot(context, result, "7-8_card_issued");

			// 7-9: pay by card.
			context.clickScreenButton(S + "tab.buy");
			context.waitTicks(2);
			Button pay = field(context, screen, "paymentButton");
			check(pay.visible && pay.getMessage().getString().equals(text(S + "pay.card")), "the payment button says \"%s\" (visible %s), not \"%s\"",
					pay.getMessage().getString(), pay.visible, text(S + "pay.card"));
			MarketSnapshotS2C.GoodView good = visibleGoods(context, screen).stream()
					.filter(v -> v.buy() && v.sell() && v.unit() == 1 && v.stock() >= 4 && v.patch().isEmpty())
					.min(java.util.Comparator.comparingDouble(v -> v.params().basePrice()))
					.orElseThrow(() -> new AssertionError("no good to buy and sell"));
			screenshot(context, result, "7-9_pay_by_card");
			long[] moved = trade(s, screen, good.goods(), "confirm.buy");
			check(moved[0] < 0 && moved[1] == 0, "buying by card moved the bank by %dc and the cash by %dc", moved[0], moved[1]);
			context.clickScreenButton(S + "tab.sell");
			context.waitTicks(2);
			moved = trade(s, screen, good.goods(), "confirm.sell");
			check(moved[0] > 0 && moved[1] == 0, "selling with the card moved the bank by %dc and the cash by %dc", moved[0], moved[1]);

			// Cash.
			context.clickScreenButton(S + "tab.buy");
			context.waitTicks(2);
			clickWidget(context, pay);
			mouseAway(context);
			check(pay.getMessage().getString().equals(text(S + "pay.cash")), "after a click the payment button says \"%s\", not \"%s\"",
					pay.getMessage().getString(), text(S + "pay.cash"));
			moved = trade(s, screen, good.goods(), "confirm.buy");
			check(moved[0] == 0 && moved[1] < 0, "buying with cash moved the bank by %dc and the cash by %dc", moved[0], moved[1]);
			screenshot(context, result, "7-9_pay_by_cash");
		} finally {
			context.setScreen(() -> null);
			takeCards(s);
		}
	}

	/** One of the good with the confirm button of the current tab; returns how much the bank balance and the cash moved. */
	private static long[] trade(Session s, MarketScreen screen, Identifier good, String confirm) {
		ClientGameTestContext context = s.context;
		select(context, screen, good);
		mouseAway(context);
		long bank = bankBalance(s);
		long cash = serverMoney(s);
		context.clickScreenButton(S + confirm);
		for (int i = 0; i < 40 && bankBalance(s) == bank && serverMoney(s) == cash; i++) context.waitTick();
		context.waitTicks(3);
		return new long[] {bankBalance(s) - bank, serverMoney(s) - cash};
	}

	// ---- 9-2 / 16-5 the requests

	/** A plain request for {@code good} put first in the market's list (reward 500c, 3 days, the given stars). */
	private static int addRequest(Session s, int difficulty) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			Identifier good = market.regularGoods().stream().filter(g -> {
				GoodsDefinition def = GoodsCatalog.get(g);
				return def != null && def.sell() && def.unit() == 1 && def.components().isEmpty() && !market.isSpecial(g)
						&& !new net.minecraft.world.item.ItemStack(item(g)).isDamageableItem();
			}).findFirst().orElseThrow(() -> new AssertionError("no good for a request"));
			long now = MarketClock.now(server.overworld());
			Request r = new Request(market.takeRequestId(), good, 4, 500, now + 3 * PriceModel.TICKS_PER_DAY, difficulty);
			market.requests().addFirst(r);
			MarketRegistry.get(server).setDirty();
			return r.id();
		});
	}

	private static RequestView request(ClientGameTestContext context, MarketScreen screen, int id) {
		return snapshot(context, screen).requests().stream().filter(r -> r.request().id() == id).findFirst()
				.orElseThrow(() -> new AssertionError("the request #" + id + " is not listed"));
	}

	/** Shows the request's row and returns its top in GUI pixels. */
	private static int showRequest(ClientGameTestContext context, MarketScreen screen, int id) {
		List<RequestView> list = snapshot(context, screen).requests();
		int index = -1;
		for (int i = 0; i < list.size(); i++) if (list.get(i).request().id() == id) index = i;
		check(index >= 0, "the request #%d is not listed", id);
		int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
		int scroll = Math.max(0, Math.min(index, list.size() - rows));
		context.runOnClient(c -> set(screen, "scroll", scroll));
		context.waitTicks(2);
		return context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + (index - scroll) * constant(MarketScreen.class, "ROW_H");
	}

	private static List<TextRecorder.Text> requestRow(ClientGameTestContext context, MarketScreen screen, int id) {
		int y = showRequest(context, screen, id);
		mouseAway(context);
		return rowTexts(TextRecorder.capture(context), y);
	}

	private static void selectRequest(ClientGameTestContext context, MarketScreen screen, int id) {
		int y = showRequest(context, screen, id);
		clickAt(context, listX(context, screen) + 60, y + 8);
		mouseAway(context);
		int selected = context.computeOnClient(c -> (Integer) get(screen, "selectedRequest"));
		check(selected == id, "a click on the row selected #%d, not #%d", selected, id);
	}

	private static String panel(ClientGameTestContext context, MarketScreen screen) {
		mouseAway(context);
		int px = context.computeOnClient(c -> leftPos(screen) + (Integer) get(screen, "panelX"));
		return squeeze(String.join("", strings(TextRecorder.capture(context).stream().filter(t -> t.x0() >= px).toList())));
	}

	private static Button field(ClientGameTestContext context, MarketScreen screen, String name) {
		return context.computeOnClient(c -> (Button) get(screen, name));
	}

	/**
	 * 9-2: a request's row shows the good and its number, the stars and the reward; selected, the panel shows the reward,
	 * the time left and how many are carried; "納品する" with the goods takes them and pays the reward. 16-5: "依頼を受ける"
	 * makes it "受付済み" (not pressable) and puts ✔ in the row.
	 */
	static void requests(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		int id = addRequest(s, 1);
		MarketScreen screen = reopen(s);
		context.clickScreenButton(S + "tab.quest");
		context.waitTicks(2);
		MarketScreen first = screen;
		RequestView r = request(context, screen, id);
		String name = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(r.item())).getHoverName().getString();
		String label = name + " ×" + r.request().quantity();
		List<String> row = strings(requestRow(context, screen, id));
		check(row.stream().anyMatch(t -> t.length() >= 2 && label.startsWith(t.replace("…", ""))), "the row does not say \"%s\": %s", label, row);
		check(row.contains("★"), "the row has no ★: %s", row);
		check(row.contains(coins(r.request().reward())), "the row has no reward %s: %s", coins(r.request().reward()), row);
		selectRequest(context, screen, id);
		// Earlier cases may have left some of the good in the inventory.
		int held0 = serverCount(s, r.request().goods());
		String p = panel(context, screen);
		for (String line : List.of(text(S + "quest.reward", coins(r.request().reward())), prefix(S + "quest.time_left"), text(S + "quest.held", held0))) {
			check(p.contains(squeeze(line)), "the panel does not say \"%s\": %s", line, p);
		}
		check(field(context, screen, "deliverButton").active == held0 >= r.request().quantity(), "\"%s\" can%s be pressed with %d of %d",
				text(S + "quest.deliver"), held0 >= r.request().quantity() ? "not" : "", held0, r.request().quantity());
		screenshot(context, result, "9-2_request_selected");

		// 16-5: accept.
		try {
			Button accept = field(context, screen, "acceptButton");
			check(accept.visible && accept.active && accept.getMessage().getString().equals(text(S + "quest.accept")), "no \"%s\" button",
					text(S + "quest.accept"));
			clickWidget(context, accept);
			mouseAway(context);
			check(waitClient(context, c -> {
				MarketSnapshotS2C now = (MarketSnapshotS2C) get(first, "snapshot");
				return now.requests().stream().anyMatch(x -> x.request().id() == id && x.accepted());
			}, 60), "the request did not become accepted");
			context.waitTicks(2);
			check(!accept.active && accept.getMessage().getString().equals(text(S + "quest.accepted")), "the button says \"%s\" (active %s), not \"%s\"",
					accept.getMessage().getString(), accept.active, text(S + "quest.accepted"));
			check(strings(requestRow(context, screen, id)).contains("✔"), "the accepted request's row has no ✔");
			screenshot(context, also, "16-5_accepted");
		} catch (Throwable t) {
			throw new ClientChecklistTests.AlsoFailed(t);
		}

		// Deliver.
		Identifier good = r.request().goods();
		int quantity = r.request().quantity();
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> giveItems(player(server), item(good), quantity));
		context.waitTicks(3);
		screen = reopen(s);
		context.clickScreenButton(S + "tab.quest");
		context.waitTicks(2);
		long reward = request(context, screen, id).request().reward();
		selectRequest(context, screen, id);
		check(panel(context, screen).contains(squeeze(text(S + "quest.held", held0 + quantity))), "the panel does not say \"%s\"",
				text(S + "quest.held", held0 + quantity));
		Button deliver = field(context, screen, "deliverButton");
		check(deliver.active, "\"%s\" cannot be pressed with the goods", text(S + "quest.deliver"));
		double rep0 = snapshot(context, screen).reputation().points();
		int difficulty = request(context, screen, id).request().difficulty();
		long before = serverMoney(s);
		clickWidget(context, deliver);
		mouseAway(context);
		for (int i = 0; i < 40 && serverMoney(s) == before; i++) context.waitTick();
		check(serverMoney(s) - before == reward, "delivering paid %dc, the reward is %dc", serverMoney(s) - before, reward);
		check(serverCount(s, good) == held0, "%d of the goods are carried after delivering (%d before)", serverCount(s, good), held0);
		context.waitTicks(3);
		MarketScreen open = screen;
		check(snapshot(context, open).requests().stream().noneMatch(x -> x.request().id() == id), "the delivered request is still listed");
		check(all(context).contains(squeeze(text(S + "quest.delivered", coins(reward)))), "the status line does not say \"%s\"",
				text(S + "quest.delivered", coins(reward)));
		screenshot(context, result, "9-2_delivered");

		// 16-5: the accepted request gave acceptedQuestMultiplier times the usual points, and the title's reputation
		// tooltip shows them.
		try {
			var rep = snapshot(context, open).reputation();
			var config = com.akf.tradeexpansion.config.TradeExpansionConfig.get().reputation;
			double expected = config.questPoints.get(Math.clamp(difficulty - 1, 0, config.questPoints.size() - 1)) * config.acceptedQuestMultiplier;
			check(config.acceptedQuestMultiplier > 1, "reputation.acceptedQuestMultiplier is %.2f", config.acceptedQuestMultiplier);
			check(Math.abs(rep.points() - rep0 - expected) < 1, "delivering the accepted request gave %.2f reputation points, not %.2f", rep.points() - rep0, expected);
			String repLabel = prefix(S + "reputation.label");
			mouseAway(context);
			int top = context.computeOnClient(c -> topPos(open));
			TextRecorder.Text repText = TextRecorder.capture(context).stream().filter(t -> t.text().startsWith(repLabel) && t.y0() >= top && t.y0() < top + 14)
					.findFirst().orElseThrow(() -> new AssertionError("no \"" + repLabel + "…\" in the title row"));
			moveTo(context, (repText.x0() + repText.x1()) / 2.0, (repText.y0() + repText.y1()) / 2.0);
			context.waitTicks(2);
			List<String> tip = strings(tooltip(TextRecorder.capture(context)));
			String line = rep.next() < 0 ? text(S + "reputation.points_max", (long) Math.floor(rep.points()))
					: text(S + "reputation.points", (long) Math.floor(rep.points()), rep.next());
			check(tip.contains(line), "the reputation tooltip does not say \"%s\" after delivering: %s", line, tip);
			screenshot(context, also, "16-5_reputation_after_delivery");
			mouseAway(context);
		} catch (Throwable t) {
			throw new ClientChecklistTests.AlsoFailed(t);
		}
	}

	// ---- 16-4 / 16-7 a 3-star request and the reputation

	private static void setPoints(Session s, double points) {
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			UUID id = player(server).getUUID();
			market.setStanding(new Market.Standing(id, points, MarketClock.now(server.overworld())));
			MarketRegistry.get(server).setDirty();
		});
	}

	private static long serverReward(Session s, int id) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			Request r = market.requests().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
			return Reputation.reward(market, player(server).getUUID(), r, MarketClock.now(server.overworld()));
		});
	}

	/**
	 * 16-4: below "お得意様" a 3-star request's stars are gray, the panel says "評判「お得意様」から納品可" and "納品する"
	 * cannot be pressed even with the goods; at "お得意様" the stars are gold and it can be delivered. 16-7: the row's
	 * reward is the request's reward with the level's bonus, more at level 3 than at level 0.
	 */
	static void lockedRequest(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Market.Standing old = s.world.getServer().computeOnServer(server -> market(server, s.stall).standing(player(server).getUUID()));
		int id = addRequest(s, 3);
		try {
			setPoints(s, 0);
			Identifier good = s.world.getServer().computeOnServer(server -> market(server, s.stall).requests().stream()
					.filter(x -> x.id() == id).findFirst().orElseThrow().goods());
			s.world.getServer().runOnServer(server -> giveItems(player(server), item(good), 4));
			MarketScreen screen = reopen(s);
			context.clickScreenButton(S + "tab.quest");
			context.waitTicks(2);
			RequestView r = request(context, screen, id);
			check(r.locked(), "the 3-star request is not locked at reputation 0");
			List<TextRecorder.Text> row = requestRow(context, screen, id);
			TextRecorder.Text stars = row.stream().filter(t -> t.text().equals("★★★")).findFirst()
					.orElseThrow(() -> new AssertionError("the row has no ★★★: " + strings(row)));
			check(stars.color() == COLOR_GRAY, "the stars of a locked request are %08X, not gray", stars.color());
			long reward0 = r.request().reward();
			check(reward0 == serverReward(s, id), "the row's reward %d is not the reward at level 0 (%d)", reward0, serverReward(s, id));
			check(strings(row).contains(coins(reward0)), "the row has no reward %s", coins(reward0));
			selectRequest(context, screen, id);
			int needed = snapshot(context, screen).reputation().hardQuestLevel();
			String locked = squeeze(text(S + "quest.locked", Component.translatable(S + "reputation.level." + needed)));
			String p = panel(context, screen);
			check(p.contains(locked), "the panel does not say \"%s\": %s", locked, p);
			check(!field(context, screen, "deliverButton").active, "\"%s\" can be pressed for a locked request", text(S + "quest.deliver"));
			screenshot(context, result, "16-4_locked");

			// "お得意様": gold stars, a higher reward, and it can be delivered.
			int threshold = TradeExpansionConfig.get().reputation.levelThresholds.get(needed - 1);
			setPoints(s, threshold + 50);
			screen = reopen(s);
			context.clickScreenButton(S + "tab.quest");
			context.waitTicks(2);
			RequestView open = request(context, screen, id);
			check(!open.locked(), "the 3-star request is still locked at level %d", needed);
			List<TextRecorder.Text> row3 = requestRow(context, screen, id);
			TextRecorder.Text gold = row3.stream().filter(t -> t.text().equals("★★★")).findFirst().orElseThrow();
			check(gold.color() == COLOR_GOLD, "the stars of an open request are %08X, not gold", gold.color());
			selectRequest(context, screen, id);
			Button deliver = field(context, screen, "deliverButton");
			check(deliver.active, "\"%s\" cannot be pressed at level %d with the goods", text(S + "quest.deliver"), needed);
			try {
				long reward3 = open.request().reward();
				check(reward3 == serverReward(s, id) && reward3 > reward0, "the reward at level %d is %d (server %d, level 0: %d)", needed, reward3,
						serverReward(s, id), reward0);
				check(strings(row3).contains(coins(reward3)), "the row has no reward %s at level %d", coins(reward3), needed);
				screenshot(context, also, "16-7_reward_level3");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
			long before = serverMoney(s);
			clickWidget(context, deliver);
			mouseAway(context);
			for (int i = 0; i < 40 && serverMoney(s) == before; i++) context.waitTick();
			check(serverMoney(s) > before, "delivering at level %d paid nothing", needed);
		} finally {
			context.setScreen(() -> null);
			BlockPos stall = s.stall;
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.requests().removeIf(x -> x.id() == id);
				UUID uuid = player(server).getUUID();
				market.setStanding(old != null ? old : new Market.Standing(uuid, 0, MarketClock.now(server.overworld())));
				MarketRegistry.get(server).setDirty();
			});
		}
	}
	// ---- 21 transport requests

	private static Market other(net.minecraft.server.MinecraftServer server, Market home) {
		return MarketRegistry.get(server).all().stream().filter(m -> m.id() != home.id() && m.dimension().equals(home.dimension()))
				.min(java.util.Comparator.comparingDouble(m -> m.center().distSqr(home.center())))
				.orElseThrow(() -> new AssertionError("no other market (the case placement_hint founds one: run it too)"));
	}

	/** A transport request to the nearest other market put first in the list; {@code carrier}: someone has taken it. */
	static int addTransport(Session s, UUID carrier) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			Market to = other(server, market);
			Identifier good = market.regularGoods().stream().filter(g -> GoodsCatalog.get(g) != null && !market.isSpecial(g)).findFirst().orElseThrow();
			long now = MarketClock.now(server.overworld());
			Request r = new Request(market.takeRequestId(), good, 8, 800, now + 3 * PriceModel.TICKS_PER_DAY, 1,
					carrier == null ? List.of() : List.of(carrier), java.util.Optional.of(new Request.Transport(to.id(), 200)));
			market.requests().addFirst(r);
			MarketRegistry.get(server).setDirty();
			return r.id();
		});
	}

	private static int crates(ClientGameTestContext context, long marketId) {
		return context.computeOnClient(c -> com.akf.tradeexpansion.quest.Cargo.slotsFor(c.player, marketId).size());
	}

	/**
	 * 21-1: a transport request's row says "品 ×数 → 届け先"; selected, the panel shows the destination, where it is, the
	 * reward, the deposit and the time left. 21-2: "荷箱を受け取る" takes the deposit and gives the crate; the button
	 * becomes "受け取り済み". 21-7: at the market that gave it there is no "荷箱を届ける". 21-5: at the destination
	 * "荷箱を届ける（1）" takes the crate and pays the reward and the deposit; the request is gone at the first market.
	 */
	static void transport(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		s.world.getServer().runOnServer(server -> com.akf.tradeexpansion.currency.WalletService.give(player(server), 5_000));
		int id = addTransport(s, null);
		long[] ids = s.world.getServer().computeOnServer(server -> {
			Market home = market(server, s.stall);
			return new long[] {home.id(), other(server, home).id()};
		});
		MarketScreen screen = reopen(s);
		context.clickScreenButton(S + "tab.quest");
		context.waitTicks(2);
		RequestView r = request(context, screen, id);
		MarketSnapshotS2C.TransportView t = r.transport().orElseThrow(() -> new AssertionError("the request has no transport part"));
		String name = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(r.item())).getHoverName().getString();
		String label = name + " ×" + r.request().quantity() + " → " + t.destinationName().getString();
		List<String> row = strings(requestRow(context, screen, id));
		check(row.stream().anyMatch(x -> x.length() >= 4 && label.startsWith(x.replace("…", ""))), "the row does not say \"%s\": %s", label, row);
		check(row.contains(coins(r.request().reward())), "the row has no reward %s: %s", coins(r.request().reward()), row);
		selectRequest(context, screen, id);
		String p = panel(context, screen);
		for (String line : List.of("→" + t.destinationName().getString(), text(S + "quest.cargo.where", "", t.distance()),
				text(S + "quest.reward", coins(r.request().reward())), text(S + "quest.cargo.deposit", coins(t.deposit())), prefix(S + "quest.time_left"))) {
			check(p.contains(squeeze(line)), "the panel does not say \"%s\": %s", line, p);
		}
		screenshot(context, result, "21-1_transport_selected");

		// Take the crate.
		try {
			Button take = field(context, screen, "acceptButton");
			check(take.visible && take.active && take.getMessage().getString().equals(text(S + "quest.cargo.take")), "no \"%s\" button (%s)",
					text(S + "quest.cargo.take"), take.getMessage().getString());
			long before = serverMoney(s);
			clickWidget(context, take);
			mouseAway(context);
			check(waitClient(context, c -> com.akf.tradeexpansion.quest.Cargo.slotsFor(c.player, ids[1]).size() == 1, 60), "no cargo crate for the destination arrived");
			context.waitTicks(3);
			check(before - serverMoney(s) == t.deposit(), "taking the crate took %dc, the deposit is %dc", before - serverMoney(s), t.deposit());
			check(!take.active && take.getMessage().getString().equals(text(S + "quest.cargo.took")), "the button says \"%s\" (active %s), not \"%s\"",
					take.getMessage().getString(), take.active, text(S + "quest.cargo.took"));
			screenshot(context, also, "21-2_crate_taken");
		} catch (Throwable e) {
			throw new AssertionError("taking the crate: " + e.getMessage(), e);
		}
		check(!field(context, screen, "cargoButton").visible, "\"%s\" shows at the market that gave the request", text(S + "quest.cargo.deliver", 1));

		// The destination.
		Vec3Pos home = homePos(s);
		try {
			MarketScreen there = openStallOf(s, ids[1]);
			context.clickScreenButton(S + "tab.quest");
			context.waitTicks(3);
			Button deliver = field(context, there, "cargoButton");
			check(deliver.visible && deliver.getMessage().getString().equals(text(S + "quest.cargo.deliver", 1)), "the destination shows no \"%s\" (%s)",
					text(S + "quest.cargo.deliver", 1), deliver.getMessage().getString());
			screenshot(context, result, "21-5_deliver_button");
			long money = serverMoney(s);
			clickWidget(context, deliver);
			mouseAway(context);
			for (int i = 0; i < 40 && serverMoney(s) == money; i++) context.waitTick();
			long paid = serverMoney(s) - money;
			check(paid > t.deposit(), "delivering paid %dc (the deposit is %dc)", paid, t.deposit());
			check(crates(context, ids[1]) == 0, "the crate is still carried");
		} finally {
			context.setScreen(() -> null);
			teleport(s, home.x(), home.y(), home.z());
		}
		screen = reopen(s);
		context.clickScreenButton(S + "tab.quest");
		context.waitTicks(2);
		check(snapshot(context, screen).requests().stream().noneMatch(x -> x.request().id() == id), "the delivered transport request is still listed");
	}

	/** 21-4: a transport request someone else took is gray "運送中" in the list and cannot be taken. */
	static void transportTaken(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		int id = addTransport(s, UUID.randomUUID());
		try {
			MarketScreen screen = reopen(s);
			context.clickScreenButton(S + "tab.quest");
			context.waitTicks(2);
			List<TextRecorder.Text> row = requestRow(context, screen, id);
			String carrying = text(S + "quest.cargo.carrying");
			TextRecorder.Text shown = row.stream().filter(x -> x.text().equals(carrying)).findFirst()
					.orElseThrow(() -> new AssertionError("the row does not say \"" + carrying + "\": " + strings(row)));
			check(shown.color() == COLOR_GRAY, "\"%s\" is %08X, not gray", carrying, shown.color());
			selectRequest(context, screen, id);
			check(!field(context, screen, "acceptButton").visible, "the crate someone else took can still be taken");
			check(panel(context, screen).contains(squeeze(carrying)), "the panel does not say \"%s\"", carrying);
			screenshot(context, result, "21-4_taken_by_other");
		} finally {
			context.setScreen(() -> null);
			BlockPos stall = s.stall;
			s.world.getServer().runOnServer(server -> {
				market(server, stall).requests().removeIf(x -> x.id() == id);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	private record Vec3Pos(double x, double y, double z) {
	}

	private static Vec3Pos homePos(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			return new Vec3Pos(player.getX(), player.getY(), player.getZ());
		});
	}

	/** Teleports to a stall of another market and opens it with the use key. */
	static MarketScreen openStallOf(Session s, long marketId) {
		ClientGameTestContext context = s.context;
		BlockPos stall = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).get(marketId).stalls().iterator().next());
		context.setScreen(() -> null);
		teleport(s, stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		context.getInput().lookAt(stall);
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(MarketScreen.class);
		context.waitTicks(10);
		MarketScreen there = screen(context);
		long opened = context.computeOnClient(c -> (Long) call(there, "marketId"));
		check(opened == marketId, "the stall opened market #%d, not #%d", opened, marketId);
		return there;
	}
}
