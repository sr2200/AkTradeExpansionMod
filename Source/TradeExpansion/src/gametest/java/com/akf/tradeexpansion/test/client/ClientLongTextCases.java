package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.WalletScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client case of the longest texts the screens show: market names of the name tag's limit
 * ({@link MarketNames#MAX_LENGTH} characters, wide Japanese and wide Latin letters), cash over a million coppers,
 * a bank balance of hundreds of millions, high development points and a wallet near the largest capacity.
 */
final class ClientLongTextCases {
	private ClientLongTextCases() {
	}

	/** Window sizes and GUI scales (0 = auto). */
	private static final int[][] SIZES = {{854, 480, 0}, {854, 480, 1}, {1920, 1080, 2}, {1920, 1080, 0}};

	private static final long CASH = 1_234_567;
	private static final long BANK = 987_654_321;
	private static final double POINTS = 1_234_567;
	private static final long WALLET = 999_999_999;

	/** A name of {@code MAX_LENGTH} characters built from {@code part}. */
	private static String longName(String part) {
		StringBuilder b = new StringBuilder();
		while (b.codePointCount(0, b.length()) < MarketNames.MAX_LENGTH) b.append(part);
		return b.substring(0, b.offsetByCodePoints(0, MarketNames.MAX_LENGTH));
	}

	/** Japanese with a wide-kanji name, English with a name of W (the widest Latin letter). */
	private static final Map<String, String> NAMES = Map.of(
			ClientChecklistTests.LANGUAGE, longName("黒鷲交易所の巨大市場"),
			"en_us", longName("W"));

	/**
	 * Every tab of the trade screen (the board with a market selected, the development tab with a building selected)
	 * and the wallet screen, with the long names on every market, at several sizes in Japanese and English: no texts on
	 * top of each other, none outside the screen or the panel, and the numbers are shown whole.
	 */
	static void longTexts(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		Map<Long, String> names = s.world.getServer().computeOnServer(server -> {
			Map<Long, String> m = new HashMap<>();
			MarketRegistry.get(server).all().forEach(market -> m.put(market.id(), market.name()));
			return m;
		});
		double development0 = s.world.getServer().computeOnServer(server -> market(server, s.stall).development());
		int level0 = s.world.getServer().computeOnServer(server -> market(server, s.stall).level());
		long bank0 = s.world.getServer().computeOnServer(server -> BankService.accounts(player(server)).balance(player(server).getUUID()));
		List<ItemStack> inventory0 = s.world.getServer().computeOnServer(server -> {
			List<ItemStack> items = new ArrayList<>();
			var inv = player(server).getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) items.add(inv.getItem(i).copy());
			return items;
		});
		TradeExpansionConfig config0 = s.world.getServer().computeOnServer(server -> ConfigManager.copy(TradeExpansionConfig.get()));
		List<String> problems = new ArrayList<>();
		boolean[] seen = new boolean[3]; // board market, building, wallet
		try {
			s.world.getServer().runOnServer(server -> {
				TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
				config.wallet.capacity = 1_000_000_000L;
				ConfigManager.apply(config);
				ServerPlayer player = player(server);
				var inv = player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).getItem() instanceof CoinItem || inv.getItem(i).getItem() instanceof WalletItem) inv.setItem(i, ItemStack.EMPTY);
				}
				ItemStack wallet = new ItemStack(ModItems.WALLET);
				WalletItem.setBalance(wallet, WALLET);
				inv.setItem(8, wallet);
				WalletService.giveCoins(player, com.akf.tradeexpansion.core.CoinMath.decompose(CASH, CoinType.valueTable()));
				BankService.accounts(player).set(player.getUUID(), BANK);
				Market market = market(server, s.stall);
				market.setDevelopment(POINTS, MarketClock.now(server.overworld()));
				MarketRegistry.get(server).setDirty();
			});
			ClientBankQuestCases.giveCard(s);
			long cash = serverMoney(s);
			check(cash == CASH + WALLET, "the player carries %dc, not %dc", cash, CASH + WALLET);

			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				String name = NAMES.get(language);
				s.world.getServer().runOnServer(server -> {
					MarketRegistry registry = MarketRegistry.get(server);
					registry.all().forEach(market -> MarketNames.rename(market, name, null));
					registry.setDirty();
				});
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					context.setScreen(() -> null);
					resize(context, size[0], size[1], size[2]);
					String at = String.format("long %s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					MarketScreen screen = ClientChecklistTests.openStall(s);
					context.waitTicks(5);
					boolean first = size == SIZES[0];
					String prefix = name.substring(0, name.offsetByCodePoints(0, 3));
					List<String> tabs = context.computeOnClient(client -> ClientChecklistTests.visibleTabs(client.gui.screen()));
					check(tabs.size() >= 5, "%s: only the tabs %s", at, tabs);
					for (String tab : tabs) {
						String key = tab.substring(tab.lastIndexOf('.') + 1);
						context.clickScreenButton(tab);
						context.waitTicks(3);
						String where = at + " " + key;
						ClientChecklistTests.checkLayout(context, result, where, first, problems, null);
						List<String> shown = strings(TextRecorder.capture(context));
						check(shown.stream().anyMatch(t -> t.contains(prefix)), "%s: the market name \"%s…\" is not shown: %s", where, prefix, shown);
						switch (key) {
							case "buy", "sell" -> check(shown.stream().anyMatch(t -> t.contains(CoinItem.formatCopper(cash))),
									"%s: the cash %s is not shown whole: %s", where, CoinItem.formatCopper(cash), shown);
							case "bank" -> {
								// The interest adds to the balance while the case runs: the balance the screen was sent.
								long sent = context.computeOnClient(client -> {
									Object account = get(screen, "account");
									return account == null ? -1L : (Long) call(account, "bankBalance");
								});
								check(sent >= BANK, "%s: the screen was sent the bank balance %d", where, sent);
								String bank = CoinItem.formatCopper(sent);
								check(shown.stream().anyMatch(t -> t.contains(bank)), "%s: the bank balance %s is not shown whole: %s", where, bank, shown);
							}
							case "town" -> {
								String points = String.format(Locale.ROOT, "%,d", (long) POINTS);
								check(shown.stream().anyMatch(t -> t.contains(points)), "%s: the points %s are not shown whole: %s", where, points, shown);
								MarketSnapshotS2C now = snapshot(context, screen);
								if (!now.development().facilities().isEmpty()) {
									setOnScreen(context, screen, "selectedFacility", now.development().facilities().getLast().id());
									ClientChecklistTests.checkLayout(context, result, where + "(building)", first, problems, null);
									setOnScreen(context, screen, "selectedFacility", null);
									seen[1] = true;
								}
							}
							case "board" -> {
								BoardS2C board = context.computeOnClient(client -> (BoardS2C) get(screen, "board"));
								if (board != null && !board.markets().isEmpty()) {
									setOnScreen(context, screen, "boardMarket", board.markets().getFirst().id());
									ClientChecklistTests.checkLayout(context, result, where + "(market)", first, problems, null);
									setOnScreen(context, screen, "boardMarket", -1L);
									seen[0] = true;
								}
							}
							default -> {
							}
						}
					}
					context.setScreen(() -> null);

					// The wallet screen of a wallet near the largest capacity.
					context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
					float[] rot = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});
					context.getInput().lookAt(rot[0], -90);
					context.waitTicks(2);
					context.getInput().pressKey(options -> options.keyUse);
					if (waitClient(context, c -> c.gui.screen() instanceof WalletScreen, 40)) {
						context.waitTicks(3);
						String where = at + " wallet";
						mouseAway(context);
						List<TextRecorder.Text> texts = TextRecorder.capture(context);
						String balance = CoinItem.formatCopper(WALLET);
						check(strings(texts).stream().anyMatch(t -> t.contains(balance)), "%s: the balance %s is not shown whole: %s", where, balance, strings(texts));
						List<String> found = context.computeOnClient(c -> LayoutCheck.check(where, texts,
								c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight(), null));
						if (first || !found.isEmpty()) screenshot(context, result, "layout_" + where);
						problems.addAll(found);
						seen[2] = true;
					} else {
						check(false, "%s: the wallet did not open its screen", at);
					}
					context.setScreen(() -> null);
					context.getInput().lookAt(rot[0], rot[1]);
					context.waitTicks(2);
				}
			}
		} finally {
			context.setScreen(() -> null);
			resize(context, 854, 480, 0);
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
			s.world.getServer().runOnServer(server -> {
				ConfigManager.apply(config0);
				MarketRegistry registry = MarketRegistry.get(server);
				registry.all().forEach(market -> market.setName(names.get(market.id())));
				Market market = market(server, s.stall);
				market.setDevelopment(development0, MarketClock.now(server.overworld()));
				market.setLevel(level0);
				registry.setDirty();
				ServerPlayer player = player(server);
				BankService.accounts(player).set(player.getUUID(), bank0);
				var inv = player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, inventory0.get(i));
			});
			context.waitTicks(3);
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
		check(seen[0], "the board listed no market to select");
		check(seen[1], "the development tab listed no building");
		check(seen[2], "the wallet screen was not checked");
	}

	private static void resize(ClientGameTestContext context, int width, int height, int guiScale) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(client -> {
			client.options.guiScale().set(guiScale);
			client.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void setOnScreen(ClientGameTestContext context, MarketScreen screen, String field, Object value) {
		context.runOnClient(client -> {
			set(screen, field, value);
			set(screen, "scroll", 0);
			call(screen, "updateButtons");
		});
		context.waitTicks(2);
	}
}
