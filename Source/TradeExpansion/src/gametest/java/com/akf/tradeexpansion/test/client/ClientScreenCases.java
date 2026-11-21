package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.villager.VillagerCoins;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the lists' scrollbars, the market coordinates in the chat, the advancements screen, the names of the
 * goods, the staff tab's hiring rules and the coin prices on a villager's trade screen (checklist chapters 12, 19, 20,
 * 26, 27 and 30).
 */
final class ClientScreenCases {
	private ClientScreenCases() {
	}

	private static final String NS = "ak_trade_expansion";

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	// ---- 12-1 / 12-4 the villager's trade screen

	/** A farmer that does not move, with coin trades (rows 1, 3 and 4) and one trade that pays emeralds (row 2). */
	private static UUID farmer(Session s, BlockPos pos) {
		return s.world.getServer().computeOnServer(server -> {
			var level = server.overworld();
			var v = net.minecraft.world.entity.EntityTypes.VILLAGER.spawn(level, pos, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(2));
			MerchantOffers offers = new MerchantOffers();
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
			offers.add(new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), java.util.Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.BELL), 12, 1, 0.05f));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BELL), 12, 1, 0.05f));
			v.setOffers(offers);
			return v.getUUID();
		});
	}

	/**
	 * 12-1: on a villager's trade screen each coin trade shows its coin price in gold over the emerald, inside the
	 * trade's row (its left part, where the cost is); the trade that pays emeralds shows none. 12-4: with a coin trade
	 * chosen, the line "代金 N（所持 M）" under the payment slots is red when the cash is short, dark gray when enough.
	 */
	static void villagerPrices(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos feet = s.world.getServer().computeOnServer(server -> player(server).blockPosition());
		BlockPos at = feet.east(2);
		UUID id = farmer(s, at);
		// No cash at first (12-4); given back at the end.
		long cash = s.world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			long all = WalletService.balance(player);
			WalletService.pay(player, all);
			return all;
		});
		try {
			context.waitTicks(5);
			context.getInput().lookAt(at);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MerchantScreen.class);
			context.waitTicks(10);
			mouseAway(context);
			MerchantScreen screen = (MerchantScreen) context.computeOnClient(c -> c.gui.screen());
			int left = context.computeOnClient(c -> leftPos(screen));
			int top = context.computeOnClient(c -> topPos(screen));
			long[] prices = context.computeOnClient(c -> new long[] {VillagerCoins.price(c.player, 0), VillagerCoins.price(c.player, 1),
					VillagerCoins.price(c.player, 2), VillagerCoins.price(c.player, 3)});
			check(prices[0] > 0 && prices[1] < 0 && prices[2] > 0 && prices[3] > 0, "the coin prices of the 4 trades are %s", java.util.Arrays.toString(prices));
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			for (int row = 0; row < 4; row++) {
				// The trade buttons of the vanilla screen: x 5..93, 20 pixels a row from y 16; the cost is on their left part.
				int y0 = top + 16 + row * 20;
				int number = row + 1;
				List<TextRecorder.Text> inRow = texts.stream().filter(t -> t.x1() > left + 5 && t.x0() < left + 93 && t.y0() >= y0 && t.y0() < y0 + 20).toList();
				if (prices[row] < 0) {
					check(inRow.stream().noneMatch(t -> t.text().matches("[\\d,.]+[kM]?c")), "the emerald trade (row %d) shows a coin price: %s", number, inRow);
					continue;
				}
				String price = CoinItem.formatCopper(prices[row]);
				TextRecorder.Text t = inRow.stream().filter(x -> x.text().equals(price)).findFirst()
						.orElseThrow(() -> new AssertionError("row " + number + " does not show " + price + ": " + inRow));
				// The text's bounds include the line below the letters (1 pixel into the next row).
				check(t.x0() >= left + 5 && t.x1() <= left + 45 && t.y0() >= y0 && t.y1() <= y0 + 21,
						"the price %s of row %d is at %s (the cost of the row is %d,%d-%d,%d)", price, number, t, left + 5, y0, left + 45, y0 + 20);
				check((t.color() & 0xFFFFFF) == 0xFFD700, "the price %s is not gold (%08x)", price, t.color());
			}
			screenshot(context, result, "12-1_villager_coin_prices");

			try {
				// Choose the bread (row 1) with no cash: the line is red.
				clickAt(context, left + 5 + 44, top + 16 + 10);
				mouseAway(context);
				String key = "screen." + NS + ".villager_price";
				String prefix = Language.getInstance().getOrDefault(key).split("%s", -1)[0];
				TextRecorder.Text line = priceLine(context, prefix);
				String expected = text(key, CoinItem.formatCopper(prices[0]), CoinItem.formatCopper(0));
				check(line.text().equals(expected), "the line says \"%s\", not \"%s\"", line.text(), expected);
				check(line.color() == 0xFFA02020, "with no cash the line \"%s\" is %08x, not red", line.text(), line.color());
				screenshot(context, also, "12-4_villager_price_short");
				s.world.getServer().runOnServer(server -> WalletService.give(player(server), prices[0] * 2));
				context.waitTicks(5);
				line = priceLine(context, prefix);
				check(line.color() == 0xFF404040, "with enough cash the line \"%s\" is %08x, not dark gray", line.text(), line.color());
			} catch (Throwable e) {
				throw new ClientChecklistTests.AlsoFailed(e);
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				var v = server.overworld().getEntity(id);
				if (v != null) v.discard();
				ServerPlayer player = player(server);
				WalletService.pay(player, WalletService.balance(player));
				WalletService.give(player, cash);
			});
			context.waitTicks(2);
		}
	}

	private static TextRecorder.Text priceLine(ClientGameTestContext context, String prefix) {
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		return texts.stream().filter(t -> t.text().startsWith(prefix)).findFirst()
				.orElseThrow(() -> new AssertionError("no \"" + prefix + "…\" line under the payment slots: " + strings(texts)));
	}

	// ---- 19-4 the scrollbars

	/** A scrollbar as the screen has it now (its private record), or {@code null}. */
	private record Bar(int x, int y, int h, int total, int visible) {
		int max() {
			return Math.max(0, total - visible);
		}

		int thumbH() {
			return Math.max(10, h * visible / Math.max(1, total));
		}
	}

	private static final int BAR_SLACK = 3;

	private static Bar bar(ClientGameTestContext context, MarketScreen screen, String method) {
		return context.computeOnClient(c -> {
			Object record = call(screen, method);
			if (record == null) return null;
			return new Bar((Integer) accessor(record, "x"), (Integer) accessor(record, "y"), (Integer) accessor(record, "h"),
					(Integer) accessor(record, "total"), (Integer) accessor(record, "visible"));
		});
	}

	private static Object accessor(Object record, String name) {
		try {
			var m = record.getClass().getDeclaredMethod(name);
			m.setAccessible(true);
			return m.invoke(record);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read " + record.getClass().getSimpleName() + "." + name, e);
		}
	}

	private static int scroll(ClientGameTestContext context, MarketScreen screen, String field) {
		return context.computeOnClient(c -> (Integer) get(screen, field));
	}

	/**
	 * Checks one list's scrollbar. When the list does not fit: the wheel moves it one row, a click on the track below the
	 * thumb one page, dragging the thumb to the bottom to the end; when it fits, neither the track nor the wheel moves
	 * it. The texts of the rows end before the bar. Returns whether the list did not fit.
	 */
	private static boolean checkBar(ClientGameTestContext context, MarketScreen screen, String what, String method, String field, boolean sellAll) {
		context.runOnClient(c -> set(screen, field, 0));
		context.waitTicks(1);
		Bar bar = bar(context, screen, method);
		check(bar != null, "%s has no scrollbar", what);
		double bx = bar.x() + 3;
		double wheelX = bar.x() - 30;
		double midY = bar.y() + bar.h() / 2.0;
		// The texts of the rows (with the mouse away) stop before the bar.
		mouseAway(context);
		List<TextRecorder.Text> texts = TextRecorder.capture(context).stream()
				.filter(t -> t.y0() >= bar.y() && t.y1() <= bar.y() + bar.h() && t.x0() < bar.x() && (!sellAll || t.afterMark())).toList();
		// The recorded area of a text is a few pixels wider than what is drawn (a text right-aligned 2 pixels before the bar
		// is recorded up to 1 pixel into it): only more than that counts.
		List<TextRecorder.Text> over = texts.stream().filter(t -> t.x1() > bar.x() + BAR_SLACK).toList();
		check(over.isEmpty(), "texts of %s run into its scrollbar (from x %d): %s", what, bar.x(), over);
		if (bar.max() == 0) {
			clickAt(context, bx, bar.y() + bar.h() - 2);
			check(scroll(context, screen, field) == 0, "a click on the track of %s (it fits) scrolled it", what);
			moveTo(context, wheelX, midY);
			context.getInput().scroll(-1);
			context.waitTicks(2);
			check(scroll(context, screen, field) == 0, "the wheel scrolled %s (it fits)", what);
			mouseAway(context);
			return false;
		}
		// The wheel: one row.
		moveTo(context, wheelX, midY);
		context.getInput().scroll(-1);
		context.waitTicks(2);
		check(scroll(context, screen, field) == 1, "the wheel scrolled %s to %d, not 1", what, scroll(context, screen, field));
		context.runOnClient(c -> set(screen, field, 0));
		// The track below the thumb: one page.
		clickAt(context, bx, bar.y() + bar.h() - 2);
		int page = Math.min(bar.visible(), bar.max());
		check(scroll(context, screen, field) == page, "a click on the track of %s scrolled it to %d, not one page (%d)", what, scroll(context, screen, field), page);
		mouseAway(context);
		context.runOnClient(c -> set(screen, field, 0));
		context.waitTicks(1);
		// Drag the thumb to the bottom (past the end of the track: it stops at the end).
		moveTo(context, bx, bar.y() + bar.thumbH() / 2.0);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
		moveTo(context, bx, bar.y() + bar.h() / 2.0);
		moveTo(context, bx, bar.y() + bar.h() + 20);
		context.waitTicks(1);
		int dragged = scroll(context, screen, field);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
		check(dragged == bar.max(), "dragging the thumb of %s to the bottom scrolled it to %d, not the end (%d)", what, dragged, bar.max());
		mouseAway(context);
		return true;
	}

	/**
	 * 19-4: the scrollbars of the buy list, the requests, the board (the markets and a market's goods) and the "sell all"
	 * box. At least one list that does not fit is checked with the wheel, the track and the thumb.
	 */
	static void scrollbars(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		// Many goods outside the hotbar, so "sell all" has more lines than fit.
		MarketScreen first = reopen(s);
		List<GoodView> sellable = snapshot(context, first).goods().stream().filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty()
				&& !v.displayStack().isDamageableItem()).limit(14).toList();
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			int slot = 9;
			for (GoodView v : sellable) {
				while (slot < 36 && !player.getInventory().getItem(slot).isEmpty()) slot++;
				if (slot >= 36) break;
				player.getInventory().setItem(slot, new ItemStack(itemOf(v), 2));
			}
		});
		context.waitTicks(3);
		List<String> overflowed = new ArrayList<>();
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		if (checkBar(context, screen, "the buy list", "listScrollbar", "scroll", false)) overflowed.add("buy");
		screenshot(context, result, "19-4_buy_list");
		context.clickScreenButton("screen.ak_trade_expansion.tab.quest");
		context.waitTicks(2);
		if (checkBar(context, screen, "the requests", "listScrollbar", "scroll", false)) overflowed.add("quest");
		context.clickScreenButton("screen.ak_trade_expansion.tab.board");
		check(waitClient(context, c -> get(screen, "board") != null, 60), "the board did not arrive");
		if (checkBar(context, screen, "the board", "listScrollbar", "scroll", false)) overflowed.add("board");
		BoardS2C board = context.computeOnClient(c -> (BoardS2C) get(screen, "board"));
		if (!board.markets().isEmpty()) {
			long id = board.markets().getFirst().id();
			context.runOnClient(c -> {
				set(screen, "boardMarket", id);
				call(screen, "updateButtons");
			});
			context.waitTicks(2);
			if (checkBar(context, screen, "the goods of a market on the board", "listScrollbar", "scroll", false)) overflowed.add("board market");
			screenshot(context, result, "19-4_board_market");
			context.runOnClient(c -> {
				set(screen, "boardMarket", -1L);
				call(screen, "updateButtons");
			});
		}
		context.clickScreenButton("screen.ak_trade_expansion.tab.sell");
		context.clickScreenButton("screen.ak_trade_expansion.sell_all");
		context.waitTicks(2);
		check(context.computeOnClient(c -> (Boolean) get(screen, "sellAllOpen")), "the \"sell all\" box did not open");
		if (checkBar(context, screen, "the \"sell all\" box", "sellAllScrollbar", "sellAllScroll", true)) overflowed.add("sell all");
		screenshot(context, result, "19-4_sell_all");
		context.clickScreenButton("gui.cancel");
		context.waitTicks(2);
		check(overflowed.contains("buy"), "the buy list fits on the screen: the scrollbar's thumb was not checked");
		check(overflowed.contains("sell all"), "the \"sell all\" box fits: its thumb was not checked");
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (GoodView v : sellable) {
				for (int i = 9; i < 36; i++) if (player.getInventory().getItem(i).is(itemOf(v))) player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		});
	}

	// ---- 20-1 / 20-5 the coordinates in the chat

	static List<GuiMessage> chat(Minecraft client) {
		@SuppressWarnings("unchecked")
		List<GuiMessage> messages = (List<GuiMessage>) get(client.gui.hud.getChat(), "allMessages");
		return new ArrayList<>(messages);
	}

	/** The chat line with the market's name and coordinates (click: copy "x y z"). */
	static void checkCoordsInChat(ClientGameTestContext context, Component name, BlockPos pos, String what) {
		boolean found = waitClient(context, c -> chat(c).stream().anyMatch(m -> isCoords(m.content(), pos)), 40);
		List<String> lines = context.computeOnClient(c -> chat(c).stream().map(m -> m.content().getString()).toList());
		check(found, "the chat has no coordinates of %s (%s): %s", what, pos.toShortString(), lines);
		Component line = context.computeOnClient(c -> chat(c).stream().map(GuiMessage::content).filter(m -> isCoords(m, pos)).findFirst().orElseThrow());
		String shown = text("message." + NS + ".map.coords", name, pos.getX(), pos.getY(), pos.getZ());
		check(line.getString().equals(shown), "the chat line of %s says \"%s\", not \"%s\"", what, line.getString(), shown);
		ClickEvent click = line.getStyle().getClickEvent();
		check(click instanceof ClickEvent.CopyToClipboard copy && copy.value().equals(pos.getX() + " " + pos.getY() + " " + pos.getZ()),
				"a click on the chat line of %s does not copy \"%d %d %d\" (%s)", what, pos.getX(), pos.getY(), pos.getZ(), click);
	}

	private static boolean isCoords(Component message, BlockPos pos) {
		return message.getContents() instanceof TranslatableContents t && t.getKey().equals("message." + NS + ".map.coords")
				&& t.getArgs().length >= 4 && String.valueOf(t.getArgs()[1]).equals(String.valueOf(pos.getX()))
				&& String.valueOf(t.getArgs()[3]).equals(String.valueOf(pos.getZ()));
	}

	static boolean visible(ClientGameTestContext context, String key) {
		return context.computeOnClient(c -> c.gui.screen().children().stream().anyMatch(ch -> ch instanceof AbstractWidget w && w.visible
				&& w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)));
	}

	/**
	 * 20-5 / 20-1 without JourneyMap: the market list of the board has "この市場の座標"; it writes this market's name and
	 * coordinates to the chat (a click copies them) and the status line says so. With a market selected that button is
	 * gone and "座標を表示" writes the selected market's.
	 */
	static void coordinates(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
		MarketScreen screen = reopen(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.board");
		check(waitClient(context, c -> get(screen, "board") != null, 60), "the board did not arrive");
		mouseAway(context);
		String here = "screen." + NS + ".board.map.here";
		String map = "screen." + NS + ".board.map";
		check(visible(context, here), "the market list of the board has no \"%s\"", text(here));
		check(!visible(context, map), "the market list of the board shows \"%s\" with no market selected", text(map));
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		Component title = context.computeOnClient(c -> screen.getTitle());
		context.clickScreenButton(here);
		mouseAway(context);
		checkCoordsInChat(context, title, snapshot.center(), "this market");
		String status = text("screen." + NS + ".board.map.chat", title);
		check(TextRecorder.capture(context).stream().anyMatch(t -> status.startsWith(t.text().replace("…", "")) && t.text().length() >= 4),
				"the status line does not say \"%s\"", status);
		screenshot(context, result, "20-5_here_coords");

		BoardS2C board = context.computeOnClient(c -> (BoardS2C) get(screen, "board"));
		check(!board.markets().isEmpty(), "the board lists no other market (the case placement_hint founds one)");
		BoardS2C.Entry entry = board.markets().getFirst();
		context.runOnClient(c -> {
			set(screen, "boardMarket", entry.id());
			call(screen, "updateButtons");
		});
		context.waitTicks(2);
		check(!visible(context, here), "\"%s\" is still there with a market selected", text(here));
		check(visible(context, map), "no \"%s\" with a market selected", text(map));
		context.clickScreenButton(map);
		mouseAway(context);
		checkCoordsInChat(context, entry.title(), entry.center(), "the selected market");
		screenshot(context, result, "20-1_board_coords");
		context.setScreen(() -> null);
		context.waitTicks(2);
		screenshot(context, result, "20-1_chat");
	}

	// ---- 27-1 / 27-11 the advancements

	private static final Identifier ROOT = Identifier.fromNamespaceAndPath(NS, "root");

	private static AdvancementProgress progress(Minecraft client, Identifier id) {
		for (Map.Entry<AdvancementHolder, AdvancementProgress> e : client.player.connection.getAdvancements().progress().entrySet()) {
			if (e.getKey().id().equals(id)) return e.getValue();
		}
		return null;
	}

	/**
	 * 27-1: the advancements key (L) opens the screen with a tab "商いの始まり" on spruce planks; with the root revoked,
	 * picking up a coin gives it again. 27-11: in English every advancement of the mod has its own title and
	 * description (no missing translation), and the tab is named in English.
	 */
	static void advancements(Session s, CaseResult.Builder result, CaseResult.Builder english) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		s.world.getServer().runCommand("advancement revoke @a only " + ROOT);
		check(waitClient(context, c -> {
			AdvancementProgress p = progress(c, ROOT);
			return p != null && !p.isDone();
		}, 60), "the root advancement is still done after it was revoked");
		// A coin dropped at the player's feet: picked up.
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ItemEntity coin = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(),
					new ItemStack(com.akf.tradeexpansion.registry.ModItems.COPPER_COIN));
			coin.setNoPickUpDelay();
			player.level().addFreshEntity(coin);
		});
		check(waitClient(context, c -> {
			AdvancementProgress p = progress(c, ROOT);
			return p != null && p.isDone();
		}, 100), "picking up a coin did not give \"%s\"", text("advancements." + NS + ".root.title"));

		context.getInput().pressKey(options -> options.keyAdvancements);
		context.waitForScreen(AdvancementsScreen.class);
		context.waitTicks(5);
		String tabTitle = text("advancements." + NS + ".root.title");
		AdvancementTab tab = modTab(context);
		check(tab.getTitle().getString().equals(tabTitle), "the mod's tab is \"%s\", not \"%s\"", tab.getTitle().getString(), tabTitle);
		Identifier background = context.computeOnClient(c -> (Identifier) get(tab, "background"));
		check(background != null && background.getPath().contains("spruce_planks"), "the tab's background is %s, not the spruce planks", background);
		context.runOnClient(c -> call(c.gui.screen(), "onSelectedTabChanged", tab.getRootAdvancement()));
		context.waitTicks(3);
		screenshot(context, result, "27-1_advancements_tab");
		context.setScreen(() -> null);

		// 27-11: English.
		try {
			ClientChecklistTests.setLanguage(context, "en_us");
			List<String> missing = context.computeOnClient(c -> {
				List<String> list = new ArrayList<>();
				for (AdvancementHolder holder : c.player.connection.getAdvancements().tree().nodes().stream().map(n -> n.holder()).toList()) {
					if (!holder.id().getNamespace().equals(NS)) continue;
					DisplayInfo display = holder.value().display().orElse(null);
					if (display == null) continue;
					for (Component part : List.of(display.title(), display.description())) {
						if (part.getContents() instanceof TranslatableContents t && !Language.getInstance().has(t.getKey())) list.add(t.getKey());
					}
				}
				return list;
			});
			check(missing.isEmpty(), "English has no text for %s", missing);
			context.getInput().pressKey(options -> options.keyAdvancements);
			context.waitForScreen(AdvancementsScreen.class);
			context.waitTicks(5);
			AdvancementTab en = modTab(context);
			String enTitle = text("advancements." + NS + ".root.title");
			check(!enTitle.equals(tabTitle) && en.getTitle().getString().equals(enTitle), "the English tab is \"%s\" (\"%s\" expected)", en.getTitle().getString(), enTitle);
			context.runOnClient(c -> call(c.gui.screen(), "onSelectedTabChanged", en.getRootAdvancement()));
			context.waitTicks(3);
			screenshot(context, english, "27-11_advancements_en");
		} catch (Throwable t) {
			throw new ClientChecklistTests.AlsoFailed(t);
		} finally {
			context.setScreen(() -> null);
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
		}
	}

	private static AdvancementTab modTab(ClientGameTestContext context) {
		return context.computeOnClient(c -> {
			@SuppressWarnings("unchecked")
			Map<AdvancementHolder, AdvancementTab> tabs = (Map<AdvancementHolder, AdvancementTab>) get(c.gui.screen(), "tabs");
			return tabs.entrySet().stream().filter(e -> e.getKey().id().equals(ROOT)).map(Map.Entry::getValue).findFirst()
					.orElseThrow(() -> new AssertionError("the advancements screen has no tab of the mod: " + tabs.keySet().stream().map(AdvancementHolder::id).toList()));
		});
	}

	// ---- 30-1 the names of the goods

	/**
	 * 30-1 (partly): every good of the stall's market and of the markets on the board has a translated name (not its
	 * key), and goods that wear out (tools, armour, the elytra, the trident) are listed new (no damage).
	 */
	static void goodsNames(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		// New goods of goods-design v0.7 put in the stall's market for the picture (taken out again at the end).
		List<Identifier> newGoods = List.of("elytra", "trident", "diamond_pickaxe", "diamond_chestplate").stream()
				.map(n -> Identifier.fromNamespaceAndPath("ak_trade_expansion", n)).toList();
		BlockPos stall = s.stall;
		List<Identifier> added = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			long now = com.akf.tradeexpansion.market.MarketUpkeep.touch(server.overworld(), market);
			List<Identifier> put = new ArrayList<>();
			for (Identifier id : newGoods) {
				if (market.good(id) != null) continue;
				market.addGood(id, new com.akf.tradeexpansion.core.GoodState(1.0, 0, com.akf.tradeexpansion.data.GoodsCatalog.get(id).targetStock(), now));
				put.add(id);
			}
			MarketRegistry.get(server).setDirty();
			return put;
		});
		try {
			goodsNamesWith(s, result, newGoods);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				added.forEach(market.goods()::remove);
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	private static void goodsNamesWith(Session s, CaseResult.Builder result, List<Identifier> newGoods) {
		ClientGameTestContext context = s.context;
		MarketScreen screen = reopen(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		for (Identifier id : newGoods) {
			GoodView v = snapshot.goods().stream().filter(g -> g.goods().equals(id)).findFirst().orElse(null);
			check(v != null, "the new good %s is not in the market", id);
			check(v.displayStack().getItem() == item(id), "%s is shown as %s", id, v.displayStack().getItem());
		}
		List<String> problems = new ArrayList<>();
		for (GoodView v : snapshot.goods()) {
			ItemStack stack = v.displayStack();
			checkName(stack, v.goods().toString(), problems);
			if (stack.isDamageableItem() && stack.getDamageValue() != 0) problems.add(v.goods() + " is listed with damage " + stack.getDamageValue());
		}
		context.clickScreenButton("screen.ak_trade_expansion.tab.board");
		check(waitClient(context, c -> get(screen, "board") != null, 60), "the board did not arrive");
		BoardS2C board = context.computeOnClient(c -> (BoardS2C) get(screen, "board"));
		int checked = snapshot.goods().size();
		for (BoardS2C.Entry e : board.markets()) {
			for (BoardS2C.Good g : e.goods()) {
				checkName(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(g.item())), g.item().toString(), problems);
				checked++;
			}
		}
		check(problems.isEmpty(), "%s", problems);
		check(checked > 0, "no goods to check");
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		mouseAway(context);
		screenshot(context, result, "30-1_goods");
		showRow(context, screen, newGoods.getFirst());
		mouseAway(context);
		screenshot(context, result, "30-1_new_goods");
	}

	private static void checkName(ItemStack stack, String what, List<String> problems) {
		Component name = stack.getHoverName();
		if (name.getContents() instanceof TranslatableContents t && !Language.getInstance().has(t.getKey())) {
			problems.add(what + " has no name (" + t.getKey() + ")");
		}
		if (stack.isEmpty()) problems.add(what + " shows no item");
	}

	// ---- 26-2 hiring needs a reputation

	/**
	 * 26-2: with the reputation "よそ者" (0 points) at the market, "運び屋を雇う" and "番頭を雇う" cannot be pressed and the
	 * right panel says from which reputation one can hire, all of it (not cut with "…"). With enough reputation they can.
	 */
	static void staffNeedsReputation(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		context.setScreen(() -> null);
		BlockPos stall = s.stall;
		Market.Standing old = s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			UUID id = player(server).getUUID();
			Market.Standing before = market.standing(id);
			market.setStanding(new Market.Standing(id, 0, com.akf.tradeexpansion.market.MarketClock.now(server.overworld())));
			MarketRegistry.get(server).setDirty();
			return before;
		});
		try {
			MarketScreen screen = ClientChecklistTests.openStall(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.staff");
			Object staff = context.computeOnClient(c -> get(screen, "staffTab"));
			check(waitClient(context, c -> get(staff, "data") != null, 60), "the staff tab got no data");
			context.waitTicks(2);
			int minLevel = context.computeOnClient(c -> {
				Object info = accessor(get(staff, "data"), "info");
				return (Integer) accessor(info, "minLevel");
			});
			check(minLevel > 0, "hiring needs no reputation (staff.minReputationLevel %d)", minLevel);
			for (String key : List.of("screen." + NS + ".staff.hire.carrier", "screen." + NS + ".staff.hire.clerk")) {
				AbstractWidget button = widget(context, key);
				check(button.visible && !button.active, "\"%s\" can be pressed with the reputation よそ者", text(key));
			}
			mouseAway(context);
			String reason = text("screen." + NS + ".staff.reason.reputation", Component.translatable("screen." + NS + ".reputation.level." + minLevel));
			int px = context.computeOnClient(c -> leftPos(screen) + (Integer) get(screen, "panelX"));
			int top = context.computeOnClient(c -> topPos(screen));
			List<TextRecorder.Text> lines = TextRecorder.capture(context).stream().filter(t -> t.x0() >= px && t.y0() >= top + 49 && t.y0() < top + 103)
					.sorted(java.util.Comparator.comparingInt(TextRecorder.Text::y0)).toList();
			String joined = String.join("", strings(lines)).replaceAll("\\s", "");
			check(joined.equals(reason.replaceAll("\\s", "")), "the panel says \"%s\", not all of \"%s\"", joined, reason);
			screenshot(context, result, "26-2_staff_needs_reputation");

			// Enough reputation: the buttons work.
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				UUID id = player(server).getUUID();
				market.setStanding(new Market.Standing(id, 100_000, com.akf.tradeexpansion.market.MarketClock.now(server.overworld())));
			});
			MarketScreen again = reopen(s);
			context.clickScreenButton("screen.ak_trade_expansion.tab.staff");
			Object staff2 = context.computeOnClient(c -> get(again, "staffTab"));
			check(waitClient(context, c -> get(staff2, "data") != null, 60), "the staff tab got no data");
			context.waitTicks(2);
			check(widget(context, "screen." + NS + ".staff.hire.carrier").active || widget(context, "screen." + NS + ".staff.hire.clerk").active,
					"with a high reputation neither hire button can be pressed");
		} finally {
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				UUID id = player(server).getUUID();
				if (old != null) market.setStanding(old);
				else market.setStanding(new Market.Standing(id, 0, com.akf.tradeexpansion.market.MarketClock.now(server.overworld())));
				MarketRegistry.get(server).setDirty();
			});
			context.setScreen(() -> null);
		}
	}

	private static AbstractWidget widget(ClientGameTestContext context, String key) {
		return context.computeOnClient(c -> c.gui.screen().children().stream()
				.filter(ch -> ch instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key))
				.map(ch -> (AbstractWidget) ch).findFirst().orElseThrow(() -> new AssertionError("no button " + key)));
	}

}
