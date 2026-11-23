package com.akf.tradeexpansion.notebook;

import com.akf.tradeexpansion.api.TradeExpansionEvents;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.LedgerMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.NotebookRequestC2S;
import com.akf.tradeexpansion.network.NotebookS2C;
import com.akf.tradeexpansion.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The merchant's notebook and the morning report (design T, v0.81). Keeps each player's {@link Ledger}: trades at the
 * markets ({@link TradeExpansionEvents#TRADE}), delivered requests and what their staff made; in the morning (the day of
 * the overworld clock changed: sunrise, or waking up) tells each online player the takings of the days since their last
 * report. The notebook item shows the ledger and the prices of the markets the player last visited.
 */
public final class LedgerService {
	/** Goods shown for a day in the notebook. */
	public static final int TOP_GOODS = 5;

	private static int tickCounter;

	private LedgerService() {
	}

	private static TradeExpansionConfig.Notebook config() {
		return TradeExpansionConfig.get().notebook;
	}

	public static void register() {
		TradeExpansionEvents.TRADE.register(LedgerService::onTrade);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % 20 == 0) reportMornings(server);
		});
	}

	/** Today's day number: days of the overworld clock (day 0 starts at the first sunrise), so a day starts in the morning. */
	public static long today(MinecraftServer server) {
		return LedgerMath.dayOf(server.overworld().getOverworldClockTime(), PriceModel.TICKS_PER_DAY);
	}

	private static LedgerMath.Day page(MinecraftServer server, Ledger.Book book) {
		return book.day(today(server), config().keepDays);
	}

	// ---- recording ----

	/** A trade on the trade screen or "sell all": buying adds to what the goods cost the player, selling takes from it. */
	static void onTrade(TradeExpansionEvents.Trade trade) {
		MinecraftServer server = trade.player().level().getServer();
		Ledger ledger = Ledger.get(server);
		Ledger.Book book = ledger.book(trade.player().getUUID());
		LedgerMath.Day day = page(server, book);
		LedgerMath.Basis basis = book.basis(trade.goods());
		if (trade.buy()) {
			basis.add(trade.itemCount(), trade.total());
			day.bought(trade.total());
		} else {
			long cost = basis.take(trade.itemCount());
			day.sold(trade.goods().toString(), trade.itemCount(), trade.total(), cost);
		}
		ledger.setDirty();
	}

	/** J: a delivery request was fulfilled with {@code items} items of {@code goods} for {@code reward}. */
	public static void onRequestDelivered(ServerPlayer player, Identifier goods, int items, long reward) {
		MinecraftServer server = player.level().getServer();
		Ledger ledger = Ledger.get(server);
		Ledger.Book book = ledger.book(player.getUUID());
		long cost = goods == null ? 0 : book.basis(goods).take(items);
		page(server, book).quest(reward, cost);
		ledger.setDirty();
	}

	/** J-2: a transport request's cargo was delivered (the reward only; the deposit coming back is not income). */
	public static void onCargoDelivered(ServerPlayer player, long reward) {
		onRequestDelivered(player, null, 0, reward);
	}

	/**
	 * Q: the owner's staff made {@code amount}: a sale minus what the sold goods cost (positive), or a wage / commission
	 * paid (negative). Recorded while the owner is offline too.
	 */
	public static void onStaff(MinecraftServer server, UUID owner, long amount) {
		if (amount == 0) return;
		Ledger ledger = Ledger.get(server);
		page(server, ledger.book(owner)).staff(amount);
		ledger.setDirty();
	}

	/** A player opened the trade screen of {@code market}: the prices they see there go into the notebook. */
	public static void notePrices(ServerPlayer player, Market market, long now) {
		int keep = config().priceNotes;
		if (keep <= 0) return;
		UUID uuid = player.getUUID();
		List<Ledger.NotePrice> prices = new ArrayList<>();
		List<Identifier> ids = new ArrayList<>(market.goods().keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		for (Identifier goodsId : ids) {
			GoodState state = market.good(goodsId);
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (state == null || def == null || (!def.buy() && !def.sell()) || !Reputation.canSee(market, uuid, goodsId, now)) continue;
			GoodParams p = Reputation.params(market, uuid, goodsId, now);
			if (p == null) continue;
			long buy = def.buy() && PriceModel.canBuy(state) ? PriceModel.buyPrice(p, state.pressure()) : -1;
			long sell = def.sell() && PriceModel.canSell(p, state) ? PriceModel.sellPrice(p, state.pressure()) : -1;
			prices.add(new Ledger.NotePrice(goodsId, buy, sell));
		}
		Ledger ledger = Ledger.get(player.level().getServer());
		Ledger.Book book = ledger.book(uuid);
		book.notes.removeIf(n -> n.market() == market.id());
		book.notes.addFirst(new Ledger.PriceNote(market.id(), today(player.level().getServer()), prices));
		while (book.notes.size() > keep) book.notes.removeLast();
		ledger.setDirty();
	}

	// ---- the morning report ----

	private static void reportMornings(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) return;
		long today = today(server);
		Ledger ledger = Ledger.get(server);
		for (ServerPlayer player : players) {
			Ledger.Book book = ledger.book(player.getUUID());
			if (book.reportedDay == today) continue;
			long last = book.reportedDay;
			book.reportedDay = today;
			ledger.setDirty();
			// First time seen, or the clock was set back: nothing to report.
			if (last == Long.MIN_VALUE || last > today) continue;
			if (!config().dailyReport || book.reportOff) continue;
			long[] range = LedgerMath.reportRange(last, today, config().keepDays);
			LedgerMath.Day sum = new LedgerMath.Day(range[0]);
			for (LedgerMath.Day d : book.days.subMap(range[0], true, range[1], true).values()) sum.add(d);
			if (!sum.active()) continue;
			report(player, sum, range[1] - range[0] + 1).forEach(player::sendSystemMessage);
		}
	}

	/** The lines of a morning report covering {@code days} days. */
	public static List<Component> report(ServerPlayer player, LedgerMath.Day sum, long days) {
		List<Component> lines = new ArrayList<>();
		lines.add(days <= 1
				? Component.translatable("notebook.ak_trade_expansion.report.header").withStyle(ChatFormatting.GOLD)
				: Component.translatable("notebook.ak_trade_expansion.report.header_days", days).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("notebook.ak_trade_expansion.report.totals", CoinItem.formatCopper(sum.sales()),
				CoinItem.formatCopper(sum.purchases()), signed(sum.totalProfit())));
		List<LedgerMath.GoodsLine> top = LedgerMath.top(sum.goods(), 1);
		if (!top.isEmpty()) {
			LedgerMath.GoodsLine best = top.getFirst();
			lines.add(Component.translatable("notebook.ak_trade_expansion.report.best", goodsName(Identifier.parse(best.goods())),
					signed(best.profit()), best.items()));
		}
		if (sum.staff() != 0) lines.add(Component.translatable("notebook.ak_trade_expansion.report.staff", signed(sum.staff())));
		if (sum.quests() != 0) lines.add(Component.translatable("notebook.ak_trade_expansion.report.quests", CoinItem.formatCopper(sum.quests())));
		if (!player.getInventory().contains(new ItemStack(ModItems.MERCHANT_NOTEBOOK))) {
			lines.add(Component.translatable("notebook.ak_trade_expansion.report.hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	/** "+1,234c" / "-56c", green or red. */
	public static MutableComponent signed(long amount) {
		return Component.literal((amount >= 0 ? "+" : "-") + CoinItem.formatCopper(Math.abs(amount)))
				.withStyle(amount >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static Component goodsName(Identifier goodsId) {
		return item(goodsId).stack().getHoverName();
	}

	private static NotebookS2C.GoodsItem item(Identifier goodsId) {
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null) return new NotebookS2C.GoodsItem(BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BARRIER), net.minecraft.core.component.DataComponentPatch.EMPTY);
		return new NotebookS2C.GoodsItem(def.item(), GoodsCatalog.patch(goodsId));
	}

	// ---- the notebook ----

	/** Sends the player's notebook ({@code open}: and opens its screen). */
	public static void send(ServerPlayer player, boolean open) {
		MinecraftServer server = player.level().getServer();
		Ledger ledger = Ledger.get(server);
		Ledger.Book book = ledger.find(player.getUUID());
		long today = today(server);
		List<NotebookS2C.DayView> days = new ArrayList<>();
		List<NotebookS2C.NoteView> notes = new ArrayList<>();
		if (book != null) {
			for (LedgerMath.Day d : book.days.values()) {
				if (d.day() > today || d.day() < today - config().keepDays || !d.active()) continue;
				List<NotebookS2C.GoodsView> top = new ArrayList<>();
				for (LedgerMath.GoodsLine g : LedgerMath.top(d.goods(), TOP_GOODS)) {
					top.add(new NotebookS2C.GoodsView(item(Identifier.parse(g.goods())), g.items(), g.sales(), g.profit()));
				}
				days.add(new NotebookS2C.DayView(d.day(), d.sales(), d.purchases(), d.profit(), d.quests(), d.staff(), d.trades(), top));
			}
			MarketRegistry registry = MarketRegistry.get(server);
			for (Ledger.PriceNote note : book.notes) {
				Market m = registry.get(note.market());
				if (m == null) continue; // the market is gone
				List<NotebookS2C.PriceView> prices = new ArrayList<>();
				for (Ledger.NotePrice p : note.prices()) {
					GoodsDefinition def = GoodsCatalog.get(p.goods());
					if (def == null) continue;
					prices.add(new NotebookS2C.PriceView(p.goods(), item(p.goods()), def.unit(), p.buy(), p.sell()));
				}
				BlockPos c = m.center();
				notes.add(new NotebookS2C.NoteView(m.id(), MarketNames.title(m), m.dimension().identifier(), c.getX(), c.getZ(), note.day(), prices));
			}
		}
		boolean reportOn = book == null || !book.reportOff;
		List<com.akf.tradeexpansion.network.StaffS2C.View> staff = com.akf.tradeexpansion.staff.StaffService.views(MarketRegistry.get(server), null,
				player.getUUID()); // v0.83: the staff page
		ServerPlayNetworking.send(player, new NotebookS2C(open, reportOn, config().dailyReport, today, days, notes, staff));
	}

	/** The notebook screen's switch for the morning report. */
	public static void handle(ServerPlayer player, NotebookRequestC2S request) {
		if (!player.getInventory().contains(new ItemStack(ModItems.MERCHANT_NOTEBOOK))) return;
		if (request.action() != NotebookRequestC2S.REPORT_ON && request.action() != NotebookRequestC2S.REPORT_OFF) return;
		Ledger ledger = Ledger.get(player.level().getServer());
		ledger.book(player.getUUID()).reportOff = request.action() == NotebookRequestC2S.REPORT_OFF;
		ledger.setDirty();
		send(player, false);
	}
}
