package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.WalletScreen;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the screens as the window and the world change under them: the wallet screen from the off hand and
 * its limits (checklist 7-12), shift-click into and out of the sell slot and the inventory key (2-19), the trade
 * screen kept through window resizes (19-5), and the open screen when the market loses the selected good or is
 * deleted (2-20).
 */
final class ClientWindowCases {
	private ClientWindowCases() {
	}

	private static final String S = "screen.ak_trade_expansion.";

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static void resize(ClientGameTestContext context, int width, int height, int guiScale) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(client -> {
			client.options.guiScale().set(guiScale);
			client.resizeGui();
		});
		context.waitTicks(3);
	}

	// ---- 7-12 the wallet screen

	/** Window sizes and GUI scales (0 = auto) the small screens are checked at. */
	private static final int[][] SIZES = {{854, 480, 0}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 0}};

	/**
	 * 7-12: a wallet in the off hand (the main hand empty) opens the wallet screen with the use key; rows the balance
	 * cannot pay are gray; taking out more than the balance takes nothing; the texts do not overlap or leave the screen
	 * (Japanese and English, every size).
	 */
	static void wallet(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		closeContainer(context);
		ItemStack offhand0 = s.world.getServer().computeOnServer(server -> player(server).getOffhandItem().copy());
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().setItem(8, ItemStack.EMPTY);
			ItemStack wallet = new ItemStack(ModItems.WALLET);
			WalletItem.setBalance(wallet, 15);
			player.setItemInHand(InteractionHand.OFF_HAND, wallet);
		});
		float[] rot = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});
		List<String> problems = new ArrayList<>();
		try {
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
			context.waitTicks(2);
			check(context.computeOnClient(c -> c.player.getMainHandItem().isEmpty()), "the main hand is not empty");
			check(context.computeOnClient(c -> c.player.getOffhandItem().getItem() instanceof WalletItem), "the wallet is not in the off hand");
			context.getInput().lookAt(rot[0], -90); // the sky: no block in reach
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(WalletScreen.class);
			context.waitTicks(3);
			Object hand = context.computeOnClient(c -> get(c.gui.screen(), "hand"));
			check(hand == InteractionHand.OFF_HAND, "the wallet screen shows the wallet of the %s", hand);

			mouseAway(context);
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			String balance = text(S + "wallet.balance", CoinItem.formatCopper(15), CoinItem.formatCopper(WalletItem.capacity()));
			check(strings(texts).contains(balance), "the wallet screen does not show \"%s\": %s", balance, strings(texts));
			// 15c pays copper (1c) and iron (10c), not gold (100c).
			checkRowColor(texts, CoinType.COPPER, true);
			checkRowColor(texts, CoinType.IRON, true);
			checkRowColor(texts, CoinType.GOLD, false);
			screenshot(context, result, "7-12_wallet_offhand");

			long copper0 = copperCoins(s);
			clickRowButton(context, CoinType.COPPER, 1); // ×10
			check(waitClient(context, c -> WalletItem.balance(c.player.getOffhandItem()) == 5, 40),
					"\"×10\" of the copper row did not take 10c out of the off-hand wallet (balance %d)", offhandBalance(context));
			check(copperCoins(s) - copper0 == 10, "taking 10c out gave %d copper coins", copperCoins(s) - copper0);
			mouseAway(context);
			checkRowColor(TextRecorder.capture(context), CoinType.IRON, false); // 5c no longer pays 10c

			// More than the balance: nothing taken.
			clickRowButton(context, CoinType.COPPER, 1); // ×10 of 5c
			clickRowButton(context, CoinType.IRON, 0); // ×1 = 10c of 5c
			context.waitTicks(10);
			check(offhandBalance(context) == 5, "taking out more than the balance changed it to %d", offhandBalance(context));
			long serverBalance = s.world.getServer().computeOnServer(server -> WalletItem.balance(player(server).getOffhandItem()));
			check(serverBalance == 5, "taking out more than the balance changed the server's wallet to %d", serverBalance);
			check(copperCoins(s) - copper0 == 10, "taking out more than the balance gave coins (%d copper in all)", copperCoins(s) - copper0);
			clickRowButton(context, CoinType.COPPER, 0); // ×1: the screen still works
			check(waitClient(context, c -> WalletItem.balance(c.player.getOffhandItem()) == 4, 40), "\"×1\" after the refusals took nothing");

			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size[0], size[1], size[2]);
					String where = String.format("wallet %s %dx%d GUI %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					mouseAway(context);
					List<TextRecorder.Text> now = TextRecorder.capture(context);
					List<String> found = context.computeOnClient(c -> LayoutCheck.check(where, now,
							c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight(), null));
					if (!found.isEmpty()) screenshot(context, result, "layout_" + where);
					problems.addAll(found);
				}
			}
			check(context.computeOnClient(c -> c.gui.screen()) instanceof WalletScreen, "the wallet screen closed while resizing");
			clickWidget(context, text("gui.done"));
			check(waitClient(context, c -> c.gui.screen() == null, 20), "\"Done\" did not close the wallet screen");
		} finally {
			resize(context, 854, 480, 0);
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
			context.setScreen(() -> null);
			context.getInput().lookAt(rot[0], rot[1]);
			s.world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.OFF_HAND, offhand0));
			context.waitTicks(2);
		}
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	/**
	 * Closes the screen and the server's container too (setScreen(null) leaves the server's trade menu open, and the
	 * off hand is then not sent to the client: the menu has no off-hand slot).
	 */
	private static void closeContainer(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player.containerMenu != client.player.inventoryMenu) client.player.closeContainer();
			client.gui.setScreen(null);
		});
		context.waitTicks(2);
	}

	private static long offhandBalance(ClientGameTestContext context) {
		return context.computeOnClient(c -> WalletItem.balance(c.player.getOffhandItem()));
	}

	private static long copperCoins(Session s) {
		int n = s.world.getServer().computeOnServer(server -> count(player(server), ModItems.coin(CoinType.COPPER)));
		return n;
	}

	/** Clicks button {@code column} (×1, ×10, ×64) of the coin's row; the rows go largest coin first. */
	private static void clickRowButton(ClientGameTestContext context, CoinType coin, int column) {
		int[] screen = context.computeOnClient(c -> new int[] {c.gui.screen().width, c.gui.screen().height});
		int left = screen[0] / 2 - 100;
		int top = screen[1] / 2 - 60;
		int row = CoinType.values().length - 1 - coin.ordinal();
		clickAt(context, left + 110 + column * 31 + 14, top + 24 + row * 22 + 9);
		context.waitTicks(2);
	}

	/** The value of the coin's row is white when the balance pays one coin, gray otherwise. */
	private static void checkRowColor(List<TextRecorder.Text> texts, CoinType coin, boolean pays) {
		String label = CoinItem.formatCopper(coin.value());
		TextRecorder.Text row = texts.stream().filter(t -> t.text().equals(label)).findFirst()
				.orElseThrow(() -> new AssertionError("no row \"" + label + "\" on the wallet screen: " + strings(texts)));
		int expected = pays ? 0xFFFFFFFF : 0xFF808080;
		check(row.color() == expected, "the row \"%s\" is %08X, not %s", label, row.color(), pays ? "white" : "gray");
	}

	// ---- 2-19 shift-click and the inventory key

	/**
	 * 2-19: shift-click moves a stack of the inventory into the sell slot (the sell tab selects it with its count) and
	 * back; coins do not go in; the inventory key closes the screen and the items in the sell slot come back.
	 */
	static void shiftClick(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = ClientChecklistTests.openStall(s);
		GoodView good = snapshot(context, screen).goods().stream()
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && new ItemStack(itemOf(v)).getMaxStackSize() >= 10
						&& context.computeOnClient(client -> client.player.getInventory().countItem(itemOf(v))) == 0
						&& PriceModel.sellCapacity(v.params(), new GoodState(1, v.pressure(), v.stock(), 0)) >= 10)
				.findFirst().orElseThrow(() -> new AssertionError("no good the market buys 10 of"));
		Item item = itemOf(good);
		Item copper = ModItems.coin(CoinType.COPPER);
		context.setScreen(() -> null);
		int[] before = s.world.getServer().computeOnServer(server -> {
			giveItems(player(server), item, 10);
			giveItems(player(server), copper, 5);
			return new int[] {count(player(server), item), count(player(server), copper)};
		});
		screen = ClientChecklistTests.openStall(s);
		MarketScreen open = screen;
		check("BUY".equals(context.computeOnClient(client -> get(open, "tab").toString())), "the screen did not open on the buy tab");

		shiftClickSlot(context, open, inventorySlot(context, open, item));
		check(waitClient(context, client -> open.getMenu().sellSlotItem().getCount() == 10, 20),
				"shift-click put %d items in the sell slot, not 10", sellSlotCount(context, open));
		check(serverSellSlot(s) == 10, "the server's sell slot holds %d items after the shift-click", serverSellSlot(s));
		check(context.computeOnClient(client -> client.player.getInventory().countItem(item)) == 0, "the stack stayed in the inventory");
		context.waitTicks(3);
		String tab = context.computeOnClient(client -> get(open, "tab").toString());
		Object selected = context.computeOnClient(client -> get(open, "selected"));
		int quantity = context.computeOnClient(client -> (Integer) get(open, "quantity"));
		check("SELL".equals(tab), "after the shift-click the screen is on the %s tab", tab);
		check(good.goods().equals(selected), "the sell tab selected %s instead of %s", selected, good.goods());
		check(quantity == 10, "the quantity is %d, not the slot's 10", quantity);
		mouseAway(context);
		screenshot(context, result, "2-19_shift_click_in");

		// Back out with shift-click on the sell slot.
		shiftClickSlot(context, open, context.computeOnClient(client -> open.getMenu().slots.get((Integer) get(open.getMenu(), "sellSlotIndex"))));
		check(waitClient(context, client -> open.getMenu().sellSlotItem().isEmpty(), 20), "shift-click on the sell slot left %d items in it", sellSlotCount(context, open));
		check(serverSellSlot(s) == 0, "the server's sell slot still holds %d items", serverSellSlot(s));
		check(context.computeOnClient(client -> client.player.getInventory().countItem(item)) == 10, "the 10 items did not come back to the inventory");

		// Coins are money, not goods.
		shiftClickSlot(context, open, inventorySlot(context, open, copper));
		context.waitTicks(3);
		check(sellSlotCount(context, open) == 0 && serverSellSlot(s) == 0, "shift-click put coins in the sell slot");
		check(s.world.getServer().computeOnServer(server -> count(player(server), copper)) == before[1], "the shift-click on the coins changed them");

		// The inventory key closes the screen; the items in the sell slot come back.
		shiftClickSlot(context, open, inventorySlot(context, open, item));
		check(waitClient(context, client -> open.getMenu().sellSlotItem().getCount() == 10, 20), "the second shift-click did not fill the sell slot");
		mouseAway(context);
		context.getInput().pressKey(options -> options.keyInventory);
		check(waitClient(context, client -> client.gui.screen() == null, 20), "the inventory key did not close the trade screen (%s)",
				context.<Object, RuntimeException>computeOnClient(client -> client.gui.screen()));
		context.waitTicks(3);
		int after = s.world.getServer().computeOnServer(server -> count(player(server), item));
		check(after == before[0], "closing with the inventory key gave back %d of the 10 items", after - (before[0] - 10));
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			int left = 10;
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack st = player.getInventory().getItem(i);
				if (st.is(item)) {
					int n = Math.min(left, st.getCount());
					st.shrink(n);
					left -= n;
				}
			}
		});
	}

	private static Slot inventorySlot(ClientGameTestContext context, MarketScreen screen, Item item) {
		Slot slot = context.computeOnClient(client -> {
			for (Slot sl : screen.getMenu().slots) {
				if (sl.container == client.player.getInventory() && sl.getItem().is(item)) return sl;
			}
			return null;
		});
		check(slot != null, "no %s in the inventory slots of the screen", item);
		return slot;
	}

	private static void shiftClickSlot(ClientGameTestContext context, MarketScreen screen, Slot slot) {
		int left = context.computeOnClient(client -> leftPos(screen));
		int top = context.computeOnClient(client -> topPos(screen));
		moveTo(context, left + slot.x + 8, top + slot.y + 8);
		// Containers read shift from the click's modifiers, which TestInput.pressMouse sends as 0: send the click with shift.
		context.getInput().holdShift();
		try {
			context.runOnClient(client -> {
				MouseButtonInfo shiftLeft = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, SHIFT);
				try {
					var onButton = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
					onButton.setAccessible(true);
					onButton.invoke(client.mouseHandler, client.getWindow().handle(), shiftLeft, InputConstants.PRESS);
					onButton.invoke(client.mouseHandler, client.getWindow().handle(), shiftLeft, InputConstants.RELEASE);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("cannot click with shift", e);
				}
			});
			context.waitTicks(2);
		} finally {
			context.getInput().releaseShift();
		}
		context.waitTicks(2);
	}

	/** The left shift modifier bit of a click (InputWithModifiers.hasShiftDown tests the bits 3). */
	private static final int SHIFT = 1;

	private static int sellSlotCount(ClientGameTestContext context, MarketScreen screen) {
		return context.computeOnClient(client -> screen.getMenu().sellSlotItem().getCount());
	}

	private static int serverSellSlot(Session s) {
		return s.world.getServer().computeOnServer(server -> player(server).containerMenu instanceof MarketMenu m ? m.sellSlotItem().getCount() : -1);
	}

	// ---- 19-5 resizing the open window

	/**
	 * 19-5: the trade screen opened at one size is kept through window resizes and GUI scale changes, larger and
	 * smaller: the same screen, tab, selected good, quantity and scroll; the texts do not overlap or leave the panel,
	 * nor the window while the size decided at opening (A-4) fits it.
	 */
	static void resizeOpen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		List<String> problems = new ArrayList<>();
		int[] outgrown = {0};
		try {
			for (int[] start : new int[][] {{854, 480, 0}, {1920, 1080, 2}}) {
				context.setScreen(() -> null);
				resize(context, start[0], start[1], start[2]);
				MarketScreen screen = ClientChecklistTests.openStall(s);
				int rows = context.computeOnClient(client -> (Integer) get(screen, "rows"));
				// A tab with more goods than rows, to scroll.
				String tabKey = null;
				List<GoodView> goods = List.of();
				for (String t : List.of("buy", "sell")) {
					context.clickScreenButton(S + "tab." + t);
					context.waitTicks(2);
					goods = visibleGoods(context, screen);
					if (goods.size() > rows) {
						tabKey = t.toUpperCase(java.util.Locale.ROOT);
						break;
					}
				}
				check(tabKey != null, "neither the buy nor the sell tab has more goods than its %d rows (%d)", rows, goods.size());
				String expectedTab = tabKey;
				Identifier good = goods.getLast().goods();
				select(context, screen, good);
				context.runOnClient(client -> set(screen, "quantity", 3));
				showRow(context, screen, good);
				int scroll = context.computeOnClient(client -> (Integer) get(screen, "scroll"));
				check(scroll > 0, "the list did not scroll to the last good");
				String name = visibleGoods(context, screen).getLast().goods().toString();
				for (int[] size : new int[][] {{1920, 1080, 1}, {854, 480, 0}, {1280, 720, 3}, {1920, 1080, 4}, {854, 480, 1}, {start[0], start[1], start[2]}}) {
					resize(context, size[0], size[1], size[2]);
					String where = String.format("opened %dx%d GUI %s, now %dx%d GUI %s", start[0], start[1], start[2] == 0 ? "auto" : start[2],
							size[0], size[1], size[2] == 0 ? "auto" : size[2]);
					check(context.computeOnClient(client -> client.gui.screen()) == screen, "%s: the trade screen was replaced or closed (%s)", where,
							context.<Object, RuntimeException>computeOnClient(client -> client.gui.screen()));
					String tab = context.computeOnClient(client -> get(screen, "tab").toString());
					Object selected = context.computeOnClient(client -> get(screen, "selected"));
					int quantity = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
					int scrollNow = context.computeOnClient(client -> (Integer) get(screen, "scroll"));
					check(expectedTab.equals(tab), "%s: the tab is %s, not %s", where, tab, expectedTab);
					check(good.equals(selected), "%s: the selected good is %s, not %s", where, selected, name);
					check(quantity == 3, "%s: the quantity is %d, not 3", where, quantity);
					check(scrollNow == scroll, "%s: the list scrolled from %d to %d", where, scroll, scrollNow);
					boolean confirm = context.computeOnClient(client -> ((Button) get(screen, "confirmButton")).visible);
					check(confirm, "%s: the confirm button is gone", where);
					mouseAway(context);
					context.runOnClient(client -> client.gui.toastManager().clear());
					List<TextRecorder.Text> texts = TextRecorder.capture(context);
					// The size is decided when the screen opens (A-4): when it no longer fits the window, texts may leave the
					// window (not the panel) until the screen is opened again.
					boolean fits = context.computeOnClient(client -> screen.getMenu().layout().width() <= client.getWindow().getGuiScaledWidth()
							&& screen.getMenu().layout().height() <= client.getWindow().getGuiScaledHeight());
					List<String> found = context.computeOnClient(client -> LayoutCheck.check(where, texts,
							client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), ClientChecklistTests.panel(client.gui.screen())))
							.stream().filter(p -> fits || !p.contains(" leaves the screen ")).toList();
					if (!fits) outgrown[0]++;
					if (!found.isEmpty() && problems.stream().noneMatch(p -> p.startsWith(where))) screenshot(context, result, "19-5_" + where);
					problems.addAll(found);
				}
				screenshot(context, result, String.format("19-5_back_%dx%d", start[0], start[1]));
			}
		} finally {
			context.setScreen(() -> null);
			resize(context, 854, 480, 0);
		}
		check(outgrown[0] > 0, "the screen opened large always fit the smaller windows (the case means to check that too)");
		problems.forEach(p -> com.akf.tradeexpansion.test.AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	// ---- 2-20 the market changes under the open screen

	/**
	 * 2-20: a market of its own (a village 600 blocks west): with its trade screen open, the selected good leaves the
	 * market (as when the market shrinks) and then the market is deleted with {@code /aktradeex market delete}. The
	 * client does not crash, the good leaves the list and cannot be bought, every tab still draws, and buying after
	 * the delete is refused in red with nothing changed.
	 */
	static void marketGone(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		Vec3 home = s.world.getServer().computeOnServer(server -> player(server).position());
		BlockPos far = s.world.getServer().computeOnServer(server -> ground(server, (int) home.x - 600, (int) home.z));
		BlockPos stall = far.north(2);
		long[] id = {-1};
		try {
			teleport(s, far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			s.world.getServer().runOnServer(server -> {
				village(server.overworld(), far);
				holdStall(player(server));
				WalletService.give(player(server), 100_000);
			});
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
			context.getInput().lookAt(stall.below());
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			id[0] = s.world.getServer().computeOnServer(server -> {
				if (!server.overworld().getBlockState(stall).is(ModBlocks.MARKET_STALL)) return -1L;
				Market m = MarketRegistry.get(server).nearestWithin(server.overworld().dimension(), stall, 64);
				return m == null ? -1L : m.id();
			});
			check(id[0] > 0, "placing a stall in the village at %s founded no market", far);
			long homeMarket = s.world.getServer().computeOnServer(server -> market(server, s.stall).id());
			check(id[0] != homeMarket, "the stall at %s joined the shared market", far);

			context.getInput().lookAt(stall);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MarketScreen.class);
			context.waitTicks(10);
			MarketScreen screen = screen(context);
			MarketSnapshotS2C first = snapshot(context, screen);
			check(first.marketId() == id[0], "the screen shows market %d, not the new one %d", first.marketId(), id[0]);
			List<GoodView> buyable = first.goods().stream().filter(v -> v.buy() && v.unit() == 1 && v.stock() >= 2 && v.patch().isEmpty()).toList();
			check(buyable.size() >= 2, "the new market sells %d goods", buyable.size());
			Identifier gone = buyable.getFirst().goods();
			context.clickScreenButton(S + "tab.buy");
			select(context, screen, gone);
			check(context.computeOnClient(client -> ((Button) get(screen, "confirmButton")).active), "the buy button is not active for %s", gone);

			// The selected good leaves the market.
			long market = id[0];
			s.world.getServer().runOnServer(server -> {
				MarketRegistry registry = MarketRegistry.get(server);
				Market m = registry.get(market);
				m.goods().remove(gone);
				registry.setDirty();
				TradeService.broadcast(server, registry, Set.of(market), MarketClock.now(server.overworld()));
			});
			MarketSnapshotS2C shrunk = newSnapshot(context, screen, first);
			check(shrunk.goods().stream().noneMatch(v -> v.goods().equals(gone)), "the snapshot still has %s", gone);
			context.waitTicks(5);
			check(context.computeOnClient(client -> client.gui.screen()) == screen, "the trade screen closed when the selected good left the market");
			check(visibleGoods(context, screen).stream().noneMatch(v -> v.goods().equals(gone)), "%s is still in the list", gone);
			check(!context.computeOnClient(client -> ((Button) get(screen, "confirmButton")).active), "the buy button is still active for the good that left");
			mouseAway(context);
			check(!TextRecorder.capture(context).isEmpty(), "the screen draws no text after the good left");
			screenshot(context, result, "2-20_good_left");

			// The market is deleted.
			Identifier other = buyable.get(1).goods();
			select(context, screen, other);
			long money0 = serverMoney(s);
			int items0 = serverCount(s, other);
			s.world.getServer().runCommand("aktradeex market delete " + market);
			context.waitTicks(5);
			boolean deleted = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).get(market) == null);
			check(deleted, "market delete left the market %d", market);
			if (context.computeOnClient(client -> client.gui.screen()) == screen) {
				for (String tab : List.of("board", "quest", "exchange", "sell", "buy")) {
					context.clickScreenButton(S + "tab." + tab);
					context.waitTicks(5);
					mouseAway(context);
					check(!TextRecorder.capture(context).isEmpty(), "the %s tab draws no text after the delete", tab);
				}
				select(context, screen, other);
				context.runOnClient(client -> set(screen, "status", Component.empty()));
				if (context.computeOnClient(client -> ((Button) get(screen, "confirmButton")).active)) {
					context.clickScreenButton(S + "confirm.buy");
					boolean said = waitClient(context, client -> !((Component) get(screen, "status")).getString().isEmpty(), 40);
					check(said, "buying in the deleted market said nothing");
					int color = context.computeOnClient(client -> (Integer) get(screen, "statusColor"));
					check(color == constant(MarketScreen.class, "COLOR_RED"), "buying in the deleted market says \"%s\" in %08X, not red",
							context.computeOnClient(client -> ((Component) get(screen, "status")).getString()), color);
				}
				screenshot(context, result, "2-20_market_deleted");
			}
			context.waitTicks(5);
			check(serverMoney(s) == money0, "buying in the deleted market took %dc", money0 - serverMoney(s));
			check(serverCount(s, other) == items0, "buying in the deleted market gave %d items", serverCount(s, other) - items0);
			check(context.computeOnClient(client -> client.level != null), "the client left the world");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				var level = server.overworld();
				level.setBlock(stall, Blocks.AIR.defaultBlockState(), 3);
				level.getEntitiesOfClass(Villager.class, new AABB(far).inflate(24)).forEach(v -> v.discard());
				level.getEntitiesOfClass(com.akf.tradeexpansion.entity.TraderEntity.class, new AABB(far).inflate(24)).forEach(v -> v.discard());
				if (id[0] > 0) MarketRegistry.get(server).remove(id[0]);
				player(server).getInventory().setItem(8, ItemStack.EMPTY);
			});
			teleport(s, home.x, home.y, home.z);
			context.waitTicks(2);
		}
	}
}
