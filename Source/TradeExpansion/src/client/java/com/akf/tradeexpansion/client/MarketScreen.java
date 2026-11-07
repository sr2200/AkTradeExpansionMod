package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.menu.MarketLayout;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.ExchangeRequestC2S;
import com.akf.tradeexpansion.network.ExchangeResultS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.RequestView;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentResultS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.TransportView;
import com.akf.tradeexpansion.quest.Cargo;
import com.akf.tradeexpansion.client.map.MapWaypoints;
import com.akf.tradeexpansion.network.SellAllC2S;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.network.TradeResultS2C;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Trade screen (design A-4): buy / sell / bank / board / request / exchange tabs, goods list with price meter,
 * change since opening (▲▼) and price graph, quantity, preview and confirm, card / cash switch (E-8), sell slot.
 * All prices are computed with the same {@link PriceModel} the server uses.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
	private static final int LIST_X = 8;
	private static final int LIST_Y = 36;
	private static final int ROW_H = MarketLayout.ROW_H;
	private static final int PANEL_W = 78;
	private static final int TAB_W = 32;

	private static final int COLOR_BG = 0xFFC6C6C6;
	private static final int COLOR_DARK = 0xFF555555;
	private static final int COLOR_LIGHT = 0xFFFFFFFF;
	private static final int COLOR_LIST_BG = 0xFF8B8B8B;
	private static final int COLOR_TEXT = 0xFF404040;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_GREEN = 0xFF55FF55;
	private static final int COLOR_GOLD = 0xFFFFD700;
	private static final int COLOR_ORANGE = 0xFFFFAA33;
	private static final int COLOR_WARNING = 0xFFA02020;
	private static final int COLOR_UNAVAILABLE_ROW = 0xFF7E6A6A;
	private static final int COLOR_SPECIAL = 0xFF8FE3FF;
	/** Height of the header line above a market's goods on the board tab. */
	private static final int BOARD_HEADER_H = 10;
	/** Width kept free at the right end of the title row (other mods may put buttons there, A-4). */
	private static final int TITLE_RIGHT_RESERVE = 24;
	/** "Sell all" confirmation: over the list and the right panel (A-4, v0.32). */
	private static final int SCROLLBAR_W = 6;
	private static final int COLOR_SCROLL_TRACK = 0xFF2B2B2B;
	private static final int COLOR_SCROLL_THUMB = 0xFFC6C6C6;
	private static final int SELL_ALL_X = 8;
	private static final int SELL_ALL_Y = 18;


	private static final long[] BANK_AMOUNTS = {100, 1_000, 10_000, -1}; // -1 = everything
	/** N-6: coin donations of the development tab. */
	private static final long[] DONATIONS = {100, 1_000, 10_000};

	private enum Tab {
		BUY("buy"), SELL("sell"), BANK("bank"), BOARD("board"), QUEST("quest"), EXCHANGE("exchange"), TOWN("town");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	private MarketSnapshotS2C snapshot;
	private Tab tab = Tab.BUY;
	private Identifier selected;
	private int quantity = 1;
	private int scroll;
	private boolean pending;
	private Component status = Component.empty();
	private int statusColor = COLOR_TEXT;
	/** Unit prices when the screen received its first snapshot, for the ▲▼ marks (Q17). */
	private final Map<Identifier, Long> baselineBuy = new HashMap<>();
	private final Map<Identifier, Long> baselineSell = new HashMap<>();
	/** Latest bank balance / card validity from the server (E-7 / E-8); {@code null} until received. */
	private AccountS2C account;
	private boolean payByCard = true;
	/** Bulletin board (I): data and the market whose goods are shown ({@code -1} = market list). */
	private BoardS2C board;
	private long boardMarket = -1;
	/** Request tab (J): selected request id. */
	private int selectedRequest = -1;
	/** Sell slot (A-4 操作2): last item seen there, to auto-select its good once. */
	private ItemStack lastSellSlot = ItemStack.EMPTY;

	private final Map<Tab, Button> tabButtons = new HashMap<>();
	private Button paymentButton;
	private Button confirmButton;
	private Button deliverButton;
	private Button acceptButton;
	/** J-2 (v0.35): delivers the cargo crates for this market. */
	private Button cargoButton;
	/** I (v0.34): adds the market shown on the board to the map (JourneyMap) or writes its coordinates to the chat. */
	private Button mapButton;
	private Button sellAllButton;
	private Button sellAllConfirmButton;
	private Button sellAllCancelButton;
	/** A-4 (v0.32): the "sell all" confirmation is open (drawn over the list and the right panel). */
	private boolean sellAllOpen;
	private boolean sellAllPending;
	private final List<SellAllLine> sellAllLines = new ArrayList<>();
	private int sellAllScroll;
	/** Which scrollbar thumb is being dragged (A-4, v0.33.1). */
	private enum Drag { NONE, LIST, SELL_ALL }
	private Drag dragging = Drag.NONE;
	private double dragOffset;
	private Button boardBackButton;
	private final List<Button> tradeWidgets = new ArrayList<>();
	private final List<Button> bankWidgets = new ArrayList<>();
	private final List<Button> exchangeWidgets = new ArrayList<>();
	/** N (v0.36): development tab buttons (coin donations). */
	private final List<Button> townWidgets = new ArrayList<>();

	/** A-4 (v0.33): sizes from the layout the menu was made with (fits the window when the screen opened). */
	private final MarketLayout layout;
	private final int listW;
	private final int rows;
	private final int panelX;
	/** Extra width of the list over the base 200: added to the name columns. */
	private final int wide;
	private final int sellAllW;
	private final int sellAllH;
	private final int sellAllRows;

	public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.layout().width(), menu.layout().height());
		this.layout = menu.layout();
		this.wide = layout.extraWidth();
		this.listW = 200 + wide;
		this.rows = layout.rows();
		this.panelX = LIST_X + listW + 6;
		this.sellAllW = layout.width() - 16;
		this.sellAllH = 134 + layout.extraRows() * ROW_H;
		this.sellAllRows = 5 + layout.extraRows();
		this.snapshot = ClientMarketCache.get(menu.marketId());
		if (snapshot != null) recordBaseline(snapshot);
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;
		tabButtons.clear();
		tradeWidgets.clear();
		bankWidgets.clear();
		exchangeWidgets.clear();
		townWidgets.clear();

		Tab[] tabs = Tab.values();
		int tabW = Math.min(TAB_W, (listW + 1) / tabs.length - 1); // the tabs fit above the list
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			tabButtons.put(t, addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.tab." + t.key), b -> setTab(t))
					.bounds(x + LIST_X + i * (tabW + 1), y + 18, tabW, 14).build()));
		}
		paymentButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			payByCard = !payByCard;
			updateButtons();
		}).bounds(x + panelX, y + 18, PANEL_W, 14).build());

		// Trade (buy / sell) panel
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> setQuantity(quantity - 1)).bounds(x + panelX, y + 60, 14, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> setQuantity(quantity + 1)).bounds(x + panelX + 64, y + 60, 14, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("1"), b -> setQuantity(1)).bounds(x + panelX, y + 78, 16, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("8"), b -> setQuantity(8)).bounds(x + panelX + 18, y + 78, 16, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("64"), b -> setQuantity(64)).bounds(x + panelX + 36, y + 78, 20, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.max"), b -> setQuantity(maxQuantity(false)))
				.bounds(x + panelX + 58, y + 78, 20, 14).build()));
		confirmButton = addRenderableWidget(Button.builder(Component.empty(), b -> confirm()).bounds(x + panelX, y + 122, PANEL_W, 16).build());
		tradeWidgets.add(confirmButton);

		// Bank tab (E-7)
		for (int i = 0; i < BANK_AMOUNTS.length; i++) {
			long amount = BANK_AMOUNTS[i];
			Component label = amount < 0 ? Component.translatable("screen.ak_trade_expansion.bank.all") : Component.literal(CoinItem.formatCopper(amount));
			bankWidgets.add(addRenderableWidget(Button.builder(label, b -> bank(BankRequestC2S.Action.DEPOSIT, amount))
					.bounds(x + LIST_X + 4 + i * 49, y + LIST_Y + 44, 47, 14).build()));
			bankWidgets.add(addRenderableWidget(Button.builder(label, b -> bank(BankRequestC2S.Action.WITHDRAW, amount))
					.bounds(x + LIST_X + 4 + i * 49, y + LIST_Y + 74, 47, 14).build()));
		}
		bankWidgets.add(addRenderableWidget(Button.builder(Component.empty(), b -> bank(BankRequestC2S.Action.ISSUE_CARD, 0))
				.bounds(x + LIST_X + 4, y + LIST_Y + 92, 192, 14).build()));

		// Exchange tab (E-3): per tier above copper, break one tier down or make it from the tier below.
		CoinType[] coins = CoinType.values();
		for (int t = 1; t < coins.length; t++) {
			int tier = t;
			int ry = y + LIST_Y + 2 + (t - 1) * 26;
			exchangeWidgets.add(addRenderableWidget(Button.builder(Component.literal("↓1"), b -> exchange(tier, false, 1)).bounds(x + LIST_X + 112, ry, 20, 14).build()));
			exchangeWidgets.add(addRenderableWidget(Button.builder(Component.literal("↓10"), b -> exchange(tier, false, 10)).bounds(x + LIST_X + 133, ry, 20, 14).build()));
			exchangeWidgets.add(addRenderableWidget(Button.builder(Component.literal("↑1"), b -> exchange(tier, true, 1)).bounds(x + LIST_X + 156, ry, 20, 14).build()));
			exchangeWidgets.add(addRenderableWidget(Button.builder(Component.literal("↑10"), b -> exchange(tier, true, 10)).bounds(x + LIST_X + 177, ry, 20, 14).build()));
		}

		// Development tab (N-6): coin donations in the right panel.
		for (int i = 0; i < DONATIONS.length; i++) {
			long amount = DONATIONS[i];
			townWidgets.add(addRenderableWidget(Button.builder(Component.literal(CoinItem.formatCopper(amount)), b -> donate(amount))
					.bounds(x + panelX, y + 50 + i * 16, PANEL_W, 14).build()));
		}

		// Request tab (J) and board (I)
		deliverButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.quest.deliver"), b -> deliver())
				.bounds(x + panelX, y + 122, PANEL_W, 16).build());
		// A-4 (v0.32): sell everything the market takes from the main inventory (not the hotbar) and the sell slot.
		sellAllButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.sell_all"), b -> openSellAll())
				.bounds(x + panelX, y + 139, PANEL_W, 14).build());
		sellAllConfirmButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.sell_all.confirm"), b -> confirmSellAll())
				.bounds(x + SELL_ALL_X + sellAllW - 108, y + SELL_ALL_Y + sellAllH - 18, 52, 14).build());
		sellAllCancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> closeSellAll())
				.bounds(x + SELL_ALL_X + sellAllW - 54, y + SELL_ALL_Y + sellAllH - 18, 50, 14).build());
		// M (v0.29): accepting a request gives more reputation when delivered, and costs some if it expires.
		acceptButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.quest.accept"), b -> accept())
				.bounds(x + panelX, y + 102, PANEL_W, 14).build());
		cargoButton = addRenderableWidget(Button.builder(Component.empty(), b -> deliverCargo())
				.bounds(x + panelX, y + 139, PANEL_W, 14).build());
		mapButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.board.map"), b -> addToMap())
				.bounds(x + panelX, y + 104, PANEL_W, 14).build());
		boardBackButton = addRenderableWidget(Button.builder(Component.translatable("screen.ak_trade_expansion.board.back"), b -> {
			boardMarket = -1;
			scroll = 0;
			updateButtons();
		}).bounds(x + panelX, y + 122, PANEL_W, 16).build());
		updateButtons();
	}

	// ---- packets ----

	public void onSnapshot(MarketSnapshotS2C newSnapshot) {
		if (newSnapshot.marketId() != menu.marketId()) return;
		snapshot = newSnapshot;
		recordBaseline(newSnapshot);
		if (sellAllOpen) rebuildSellAll(); // latest prices while the confirmation is open
		updateButtons();
	}

	private void recordBaseline(MarketSnapshotS2C s) {
		for (GoodView view : s.goods()) {
			baselineBuy.putIfAbsent(view.goods(), PriceModel.buyPrice(view.params(), view.pressure()));
			baselineSell.putIfAbsent(view.goods(), PriceModel.sellPrice(view.params(), view.pressure()));
		}
	}

	public void onResult(TradeResultS2C result) {
		pending = false;
		if (sellAllPending) {
			// A-4: result of "sell all"
			sellAllPending = false;
			if (result.result() == TradeResult.SUCCESS) {
				sellAllOpen = false;
				setStatus(Component.translatable("screen.ak_trade_expansion.sell_all.done", result.quantity(), CoinItem.formatCopper(result.total())), true);
			} else {
				rebuildSellAll(); // latest prices (a new snapshot follows)
				setStatus(Component.translatable(result.result().translationKey()), false);
			}
			updateButtons();
			return;
		}
		if (result.result() == TradeResult.SUCCESS) {
			GoodView view = selectedView();
			Component name = view == null ? Component.empty() : view.displayStack().getHoverName();
			int items = result.quantity() * (view == null ? 1 : view.unit());
			setStatus(Component.translatable(result.buy() ? "screen.ak_trade_expansion.bought" : "screen.ak_trade_expansion.sold",
					name, items, CoinItem.formatCopper(result.total())), true);
		} else {
			setStatus(Component.translatable(result.result().translationKey()), false);
		}
		updateButtons();
	}

	public void onAccount(AccountS2C newAccount) {
		account = newAccount;
		if (newAccount.result() >= 0) {
			BankService.Result result = BankService.Result.values()[newAccount.result()];
			setStatus(Component.translatable("bank.ak_trade_expansion.result." + result.name().toLowerCase(Locale.ROOT)), result == BankService.Result.SUCCESS);
		}
		updateButtons();
	}

	public void onBoard(BoardS2C newBoard) {
		if (newBoard.marketId() != menu.marketId()) return;
		board = newBoard;
		updateButtons();
	}

	public void onQuestResult(QuestResultS2C result) {
		pending = false;
		if (result.result() == TradeResult.SUCCESS) {
			String amount = CoinItem.formatCopper(result.amount());
			setStatus(switch (result.kind()) {
				case DELIVER -> Component.translatable("screen.ak_trade_expansion.quest.delivered", amount);
				case TAKE_CARGO -> Component.translatable("screen.ak_trade_expansion.quest.cargo.taken", amount);
				case DELIVER_CARGO -> Component.translatable("screen.ak_trade_expansion.quest.cargo.delivered", result.count(), amount);
			}, true);
			if (result.kind() == QuestResultS2C.Kind.DELIVER) selectedRequest = -1;
		} else {
			setStatus(Component.translatable(result.result().translationKey()), false);
		}
		updateButtons();
	}

	/** N-6: outcome of a donation / build / marker request. */
	public void onDevelopmentResult(DevelopmentResultS2C result) {
		pending = false;
		if (result.result() == TradeResult.SUCCESS) {
			setStatus(switch (result.action()) {
				case DONATE_COINS -> Component.translatable("screen.ak_trade_expansion.town.donated", CoinItem.formatCopper(result.amount()), points(result.points()));
				case DONATE_ITEMS -> Component.translatable("screen.ak_trade_expansion.town.donated_items", result.amount(), points(result.points()));
				case BUILD -> Component.translatable("screen.ak_trade_expansion.town.build_started", points(result.points()));
				case MARKER -> Component.translatable("screen.ak_trade_expansion.town.marker_given");
			}, true);
		} else {
			setStatus(Component.translatable(result.result().translationKey()), false);
		}
		updateButtons();
	}

	/** Development points as a whole number with digit grouping. */
	private static String points(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value + 1e-9));
	}

	private boolean townAvailable() {
		return snapshot != null && snapshot.development().enabled();
	}

	private void donate(long amount) {
		if (pending) return;
		pending = true;
		ClientPlayNetworking.send(new DevelopmentRequestC2S(menu.marketId(), DevelopmentRequestC2S.Action.DONATE_COINS, amount, "", useCard()));
		updateButtons();
	}

	public void onExchangeResult(ExchangeResultS2C result) {
		boolean ok = result.result() == TradeResult.SUCCESS;
		setStatus(ok ? Component.translatable("screen.ak_trade_expansion.exchange.done") : Component.translatable(result.result().translationKey()), ok);
	}

	private void setStatus(Component text, boolean success) {
		status = text;
		statusColor = success ? COLOR_GREEN : COLOR_RED;
	}

	// ---- state ----

	private boolean tradeTab() {
		return tab == Tab.BUY || tab == Tab.SELL;
	}

	private boolean bankAvailable() {
		return snapshot != null && snapshot.level() >= TradeExpansionConfig.get().bank.minMarketLevel;
	}

	/** Card payment is used when chosen and a valid card is carried (E-8). */
	private boolean useCard() {
		return payByCard && account != null && account.validCard();
	}

	private void setTab(Tab newTab) {
		tab = newTab;
		scroll = 0;
		selected = null;
		selectedRequest = -1;
		quantity = 1;
		if (newTab == Tab.BOARD) {
			boardMarket = -1;
			ClientPlayNetworking.send(new BoardRequestC2S(menu.marketId()));
		}
		updateButtons();
	}

	private void bank(BankRequestC2S.Action action, long amount) {
		Player player = player();
		if (player == null) return;
		long value = amount;
		if (amount < 0) value = action == BankRequestC2S.Action.DEPOSIT ? WalletService.balance(player) : account == null ? 0 : account.bankBalance();
		ClientPlayNetworking.send(new BankRequestC2S(menu.marketId(), action, value));
	}

	/** E-3: {@code up} makes {@code count} coins of {@code tier} from the tier below; otherwise breaks {@code count} of them. */
	private void exchange(int tier, boolean up, int count) {
		ClientPlayNetworking.send(new ExchangeRequestC2S(menu.marketId(), up ? tier - 1 : tier, up, count));
	}

	private List<GoodView> visibleGoods() {
		List<GoodView> list = new ArrayList<>();
		if (snapshot == null) return list;
		for (GoodView view : snapshot.goods()) {
			if (tab == Tab.BUY ? view.buy() : view.sell()) list.add(view);
		}
		return list;
	}

	private GoodView selectedView() {
		if (selected == null || !tradeTab()) return null;
		for (GoodView view : visibleGoods()) {
			if (view.goods().equals(selected)) return view;
		}
		return null;
	}

	private RequestView selectedRequestView() {
		if (snapshot == null) return null;
		for (RequestView r : snapshot.requests()) {
			if (r.request().id() == selectedRequest) return r;
		}
		return null;
	}

	private BoardS2C.Entry boardEntry() {
		if (board == null || boardMarket < 0) return null;
		return board.markets().stream().filter(e -> e.id() == boardMarket).findFirst().orElse(null);
	}

	/** Number of rows of the current list (for scrolling). */
	private int rowCount() {
		return switch (tab) {
			case BUY, SELL -> visibleGoods().size();
			case QUEST -> snapshot == null ? 0 : snapshot.requests().size();
			// A market's goods list has a header line, so one row fewer fits: count it as one more row.
			case BOARD -> board == null ? 0 : boardEntry() != null ? boardEntry().goods().size() + 1 : board.markets().size();
			default -> 0;
		};
	}

	/** State extrapolated to the client's clock (lazy recovery, B-3). */
	private GoodState currentState(GoodView view) {
		GoodState state = view.state();
		if (minecraft != null && minecraft.level != null && snapshot != null) {
			double days = (MarketClock.now(minecraft.level) - snapshot.gameTime()) / (double) PriceModel.TICKS_PER_DAY;
			TradeExpansionConfig config = TradeExpansionConfig.get();
			PriceModel.recover(view.params(), state, days, config.recovery.restockMultiplier);
		}
		return state;
	}

	private Player player() {
		return minecraft == null ? null : minecraft.player;
	}

	private boolean shiftAll() {
		return minecraft != null && minecraft.hasShiftDown() && TradeExpansionConfig.get().trade.allowShiftAll;
	}

	/**
	 * Largest quantity (in trading units) the trade could succeed with right now (B-5).
	 *
	 * @param all Shift: beyond {@code trade.maxPerTransaction} (A-4, Q13)
	 */
	private int maxQuantity(boolean all) {
		GoodView view = selectedView();
		Player player = player();
		if (view == null || player == null) return 0;
		GoodParams params = view.params();
		GoodState state = currentState(view);
		InventoryUtil.Match match = view.match();
		int limit = all ? TradeService.MAX_ALL : TradeExpansionConfig.get().trade.maxPerTransaction;
		if (tab == Tab.BUY) {
			int max = (int) Math.min(limit, Math.max(0, Math.floor(state.stock())));
			max = Math.min(max, PriceModel.units(InventoryUtil.freeSpaceFor(player, match, !useCard()), view.unit()));
			long funds = useCard()
					? (long) Math.floor(account.bankBalance() / (1 + TradeExpansionConfig.get().bank.cardTransactionFee))
					: WalletService.balance(player);
			return PriceModel.maxAffordable(params, state.pressure(), funds, max, view.unit(), snapshot.bulkDiscount());
		}
		int held = PriceModel.units(InventoryUtil.count(player, match, menu.sellSlot()), view.unit());
		return Math.min(limit, Math.min(held, PriceModel.sellCapacity(params, state)));
	}

	private void setQuantity(int value) {
		quantity = Math.clamp(value, 1, TradeExpansionConfig.get().trade.maxPerTransaction);
		updateButtons();
	}

	private long previewTotal(GoodView view, int qty) {
		GoodState state = currentState(view);
		if (tab == Tab.BUY) return PriceModel.sumBuy(view.params(), state.pressure(), qty, view.unit(), snapshot.bulkDiscount());
		Player player = player();
		double[] ratios = player != null && view.unit() == 1 ? InventoryUtil.sellRatios(player, view.match(), menu.sellSlot(), qty) : null;
		return ratios != null ? PriceModel.sumSell(view.params(), state.pressure(), ratios) : PriceModel.sumSell(view.params(), state.pressure(), qty);
	}

	private void confirm() {
		GoodView view = selectedView();
		if (view == null || pending) return;
		boolean all = shiftAll();
		int qty = all ? maxQuantity(true) : quantity;
		if (qty <= 0) return;
		pending = true;
		ClientPlayNetworking.send(new TradeRequestC2S(menu.marketId(), view.goods(), tab == Tab.BUY, qty, previewTotal(view, qty), useCard(), all));
		updateButtons();
	}

	/** I (v0.34): the selected board market as a JourneyMap waypoint, or its coordinates in the chat. */
	private void addToMap() {
		BoardS2C.Entry entry = boardEntry();
		if (entry == null || minecraft == null || minecraft.level == null) return;
		boolean journeyMap = MapWaypoints.register(entry.id(), entry.title(), entry.center(), minecraft.level.dimension());
		setStatus(Component.translatable(journeyMap ? "screen.ak_trade_expansion.board.map.added" : "screen.ak_trade_expansion.board.map.chat",
				entry.title()), true);
	}

	private void accept() {
		RequestView r = selectedRequestView();
		if (r == null || r.accepted() || pending) return;
		// J-2: taking a cargo crate is answered with a result; plain accepting is not.
		if (r.transport().isPresent()) pending = true;
		ClientPlayNetworking.send(new QuestAcceptC2S(menu.marketId(), r.request().id(), useCard()));
		updateButtons();
	}

	/** J-2: delivers every cargo crate for this market. */
	private void deliverCargo() {
		if (pending) return;
		pending = true;
		ClientPlayNetworking.send(new CargoDeliverC2S(menu.marketId(), useCard()));
		updateButtons();
	}

	private void deliver() {
		RequestView r = selectedRequestView();
		if (r == null || pending) return;
		pending = true;
		ClientPlayNetworking.send(new QuestDeliverC2S(menu.marketId(), r.request().id(), useCard()));
		updateButtons();
	}

	private void updateButtons() {
		if (confirmButton == null) return;
		if (tab == Tab.BANK && !bankAvailable() || tab == Tab.TOWN && !townAvailable()) tab = Tab.BUY;
		tabButtons.forEach((t, b) -> b.active = t != tab);
		tabButtons.get(Tab.BANK).visible = bankAvailable();
		tabButtons.get(Tab.TOWN).visible = townAvailable();
		paymentButton.visible = (tradeTab() || tab == Tab.QUEST || tab == Tab.TOWN) && account != null && account.validCard();
		paymentButton.setMessage(Component.translatable(payByCard ? "screen.ak_trade_expansion.pay.card" : "screen.ak_trade_expansion.pay.cash"));
		for (Button b : tradeWidgets) b.visible = tradeTab();
		for (Button b : bankWidgets) b.visible = tab == Tab.BANK;
		for (Button b : exchangeWidgets) b.visible = tab == Tab.EXCHANGE;
		for (Button b : townWidgets) {
			b.visible = tab == Tab.TOWN;
			b.active = !pending;
		}
		bankWidgets.getLast().setMessage(Component.translatable("screen.ak_trade_expansion.bank.issue_card",
				CoinItem.formatCopper(TradeExpansionConfig.get().bank.cardFee)));
		confirmButton.setMessage(Component.translatable(tab == Tab.BUY ? "screen.ak_trade_expansion.confirm.buy" : "screen.ak_trade_expansion.confirm.sell"));
		confirmButton.active = !pending && selectedView() != null && (shiftAll() ? maxQuantity(true) > 0 : quantity <= maxQuantity(false));

		RequestView r = selectedRequestView();
		Player player = player();
		boolean transport = r != null && r.transport().isPresent();
		deliverButton.visible = tab == Tab.QUEST && !transport;
		deliverButton.active = !pending && r != null && player != null && !r.locked()
				&& InventoryUtil.count(player, InventoryUtil.Match.plain(BuiltInRegistries.ITEM.getValue(r.item())), menu.sellSlot()) >= r.request().quantity();
		// J-2: on a transport request the button takes the cargo crate (unless another player already took it).
		acceptButton.visible = tab == Tab.QUEST && r != null && !r.locked()
				&& (transport ? !r.request().taken() || r.accepted() : snapshot != null && snapshot.reputation().enabled());
		acceptButton.active = r != null && !r.accepted() && !pending;
		acceptButton.setMessage(Component.translatable(transport
				? (r.accepted() ? "screen.ak_trade_expansion.quest.cargo.took" : "screen.ak_trade_expansion.quest.cargo.take")
				: (r != null && r.accepted() ? "screen.ak_trade_expansion.quest.accepted" : "screen.ak_trade_expansion.quest.accept")));
		int crates = player == null ? 0 : Cargo.slotsFor(player, menu.marketId()).size();
		cargoButton.visible = tab == Tab.QUEST && crates > 0;
		cargoButton.active = !pending;
		cargoButton.setMessage(Component.translatable("screen.ak_trade_expansion.quest.cargo.deliver", crates));
		boardBackButton.visible = tab == Tab.BOARD && boardMarket >= 0;
		mapButton.visible = tab == Tab.BOARD && boardMarket >= 0;
		mapButton.setMessage(Component.translatable(MapWaypoints.journeyMapReady() ? "screen.ak_trade_expansion.board.map.journeymap" : "screen.ak_trade_expansion.board.map"));
		sellAllButton.visible = tab == Tab.SELL && !sellAllOpen;
		sellAllButton.active = !pending && snapshot != null;
		if (sellAllOpen) {
			// The confirmation covers the list and the right panel: only its own buttons work.
			for (Button b : tradeWidgets) b.visible = false;
			tabButtons.values().forEach(b -> b.visible = false);
			paymentButton.visible = false;
		} else {
			tabButtons.values().forEach(b -> b.visible = true);
			tabButtons.get(Tab.BANK).visible = bankAvailable();
			tabButtons.get(Tab.TOWN).visible = townAvailable();
		}
		sellAllConfirmButton.visible = sellAllOpen;
		sellAllCancelButton.visible = sellAllOpen;
		sellAllConfirmButton.active = sellAllOpen && !sellAllPending && sellAllTotal() > 0;
	}

	// ---- sell all (A-4, v0.32) ----

	/** One good of the "sell all" confirmation. */
	private record SellAllLine(GoodView view, ItemStack stack, String itemId, int heldUnits, int units, long total, boolean excluded) {
	}

	private void openSellAll() {
		sellAllOpen = true;
		sellAllScroll = 0;
		rebuildSellAll();
		updateButtons();
	}

	private void closeSellAll() {
		sellAllOpen = false;
		sellAllPending = false;
		updateButtons();
	}

	/** Goods the market takes that the player holds outside the hotbar, priced like single sales. */
	private void rebuildSellAll() {
		sellAllLines.clear();
		Player player = player();
		if (player == null || snapshot == null) return;
		TradeExpansionConfig.Client client = TradeExpansionConfig.get().client;
		for (GoodView view : snapshot.goods()) {
			if (!view.sell()) continue;
			InventoryUtil.Match match = view.match();
			int heldUnits = PriceModel.units(InventoryUtil.count(player, match, menu.sellSlot(), true), view.unit());
			if (heldUnits <= 0) continue;
			GoodState state = currentState(view);
			int units = Math.min(heldUnits, PriceModel.sellCapacity(view.params(), state));
			double[] ratios = view.unit() == 1 && units > 0 ? InventoryUtil.sellRatios(player, match, menu.sellSlot(), units, true) : null;
			long total = units <= 0 ? 0 : ratios != null ? PriceModel.sumSell(view.params(), state.pressure(), ratios) : PriceModel.sumSell(view.params(), state.pressure(), units);
			ItemStack stack = view.displayStack();
			String itemId = view.item().toString();
			// Tools and armour (items with durability) are left out unless the player included them.
			boolean excluded = client.sellAllExclude.contains(itemId) || stack.isDamageableItem() && !client.sellAllInclude.contains(itemId);
			sellAllLines.add(new SellAllLine(view, stack, itemId, heldUnits, units, total, excluded));
		}
	}

	private long sellAllTotal() {
		long total = 0;
		for (SellAllLine line : sellAllLines) if (!line.excluded()) total += line.total();
		return total;
	}

	private void confirmSellAll() {
		List<SellAllC2S.Line> lines = new ArrayList<>();
		for (SellAllLine line : sellAllLines) {
			if (!line.excluded() && line.units() > 0) lines.add(new SellAllC2S.Line(line.view().goods(), line.units()));
		}
		if (lines.isEmpty() || sellAllPending) return;
		sellAllPending = true;
		pending = true;
		ClientPlayNetworking.send(new SellAllC2S(menu.marketId(), lines, sellAllTotal(), useCard()));
		updateButtons();
	}

	/** Clicking a line includes / excludes its item; the choice is kept in the client settings (all worlds). */
	private void toggleSellAll(SellAllLine line) {
		TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
		List<String> exclude = live.client.sellAllExclude;
		List<String> include = live.client.sellAllInclude;
		exclude.remove(line.itemId());
		include.remove(line.itemId());
		boolean byDefault = line.stack().isDamageableItem();
		if (line.excluded()) {
			if (byDefault) include.add(line.itemId());
		} else {
			exclude.add(line.itemId());
		}
		TradeExpansionConfig.set(live);
		TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
		file.client = live.client;
		ConfigManager.write(ConfigManager.path(), file);
		rebuildSellAll();
		updateButtons();
	}

	private void extractSellAll(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = leftPos + SELL_ALL_X;
		int y = topPos + SELL_ALL_Y;
		g.fill(x - 1, y - 1, x + sellAllW + 1, y + sellAllH + 1, COLOR_DARK);
		g.fill(x, y, x + sellAllW, y + sellAllH, 0xFF3A3A3A);
		g.text(font, Component.translatable("screen.ak_trade_expansion.sell_all.title"), x + 4, y + 4, COLOR_GOLD);
		int listY = y + 16;
		if (sellAllLines.isEmpty()) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.sell_all.none"), x + 4, listY + 4, COLOR_GRAY);
		}
		SellAllLine hovered = null;
		for (int row = 0; row < sellAllRows && sellAllScroll + row < sellAllLines.size(); row++) {
			SellAllLine line = sellAllLines.get(sellAllScroll + row);
			int ry = listY + row * ROW_H;
			boolean over = mouseX >= x && mouseX < x + sellAllW && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) {
				g.fill(x + 1, ry, x + sellAllW - 1, ry + ROW_H, 0xFF505050);
				hovered = line;
			}
			g.item(line.stack(), x + 3, ry + 1);
			int color = line.excluded() ? COLOR_GRAY : COLOR_WHITE;
			int items = line.units() * line.view().unit();
			String count = line.units() < line.heldUnits()
					? Component.translatable("screen.ak_trade_expansion.sell_all.partial", items, line.heldUnits() * line.view().unit()).getString()
					: "×" + items;
			String right = line.excluded() ? Component.translatable("screen.ak_trade_expansion.sell_all.excluded").getString()
					: line.units() <= 0 ? Component.translatable("screen.ak_trade_expansion.not_buying").getString() : CoinItem.formatCopper(line.total());
			int rightWidth = font.width(right);
			g.text(font, right, x + sellAllW - 11 - rightWidth, ry + 5, line.excluded() ? COLOR_GRAY : line.units() <= 0 ? COLOR_RED : COLOR_GOLD);
			int countX = x + sellAllW - 19 - rightWidth - font.width(count);
			g.text(font, count, countX, ry + 5, color);
			fitText(g, line.stack().getHoverName().getString(), x + 22, ry + 5, countX - x - 26, color, true);
		}
		extractScrollbar(g, sellAllScrollbar(), sellAllScroll, dragging == Drag.SELL_ALL);
		int count = (int) sellAllLines.stream().filter(l -> !l.excluded() && l.units() > 0).count();
		g.text(font, Component.translatable("screen.ak_trade_expansion.sell_all.total", CoinItem.formatCopper(sellAllTotal()), count),
				x + 4, y + sellAllH - 14, COLOR_GOLD);
		g.text(font, Component.translatable("screen.ak_trade_expansion.sell_all.hint"), x + 4, y + sellAllH - 25, COLOR_GRAY);
		if (hovered != null) g.setTooltipForNextFrame(font, hovered.stack(), mouseX, mouseY);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		autoSelectFromSellSlot();
		// Inventory, balance, recovery and the Shift key change over time.
		updateButtons();
	}

	/** A-4 操作2: an item put into the sell slot selects its good on the sell tab with the slot's quantity. */
	private void autoSelectFromSellSlot() {
		ItemStack inSlot = menu.sellSlotItem();
		if (ItemStack.matches(inSlot, lastSellSlot)) return;
		lastSellSlot = inSlot.copy();
		if (inSlot.isEmpty() || snapshot == null) return;
		for (GoodView view : snapshot.goods()) {
			InventoryUtil.Match match = view.match();
			if (view.sell() && (match.exact(inSlot) || match.damagedRatio(inSlot) >= 0)) {
				if (tab != Tab.SELL) setTab(Tab.SELL);
				selected = view.goods();
				quantity = Math.clamp(PriceModel.units(inSlot.getCount(), view.unit()), 1, TradeExpansionConfig.get().trade.maxPerTransaction);
				return;
			}
		}
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int listScroll = clickScrollbar(listScrollbar(), scroll, event.x(), event.y(), false);
		if (listScroll >= 0) {
			scroll = listScroll;
			return true;
		}
		int sellScroll = clickScrollbar(sellAllScrollbar(), sellAllScroll, event.x(), event.y(), true);
		if (sellScroll >= 0) {
			sellAllScroll = sellScroll;
			return true;
		}
		if (sellAllOpen) {
			int x = leftPos + SELL_ALL_X;
			int listY = topPos + SELL_ALL_Y + 16;
			if (event.x() >= x && event.x() < x + sellAllW && event.y() >= listY && event.y() < listY + sellAllRows * ROW_H) {
				int index = sellAllScroll + (int) ((event.y() - listY) / ROW_H);
				if (index < sellAllLines.size() && !sellAllPending) toggleSellAll(sellAllLines.get(index));
				return true;
			}
			return super.mouseClicked(event, doubleClick); // its buttons and the inventory still work
		}
		double mx = event.x() - leftPos;
		double my = event.y() - topPos;
		if (mx >= LIST_X && mx < LIST_X + listW - SCROLLBAR_W && my >= LIST_Y && my < LIST_Y + rows * ROW_H
				&& (tradeTab() || tab == Tab.QUEST || tab == Tab.BOARD)) {
			int index = scroll + (int) ((my - LIST_Y) / ROW_H);
			switch (tab) {
				case BUY, SELL -> {
					List<GoodView> goods = visibleGoods();
					if (index < goods.size()) {
						selected = goods.get(index).goods();
						quantity = 1;
					}
				}
				case QUEST -> {
					if (snapshot != null && index < snapshot.requests().size()) selectedRequest = snapshot.requests().get(index).request().id();
				}
				case BOARD -> {
					if (board != null && boardMarket < 0 && index < board.markets().size()) {
						boardMarket = board.markets().get(index).id();
						scroll = 0;
					}
				}
				default -> {
				}
			}
			updateButtons();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (sellAllOpen) {
			int maxScroll = Math.max(0, sellAllLines.size() - sellAllRows);
			sellAllScroll = Math.clamp(sellAllScroll - (int) Math.signum(scrollY), 0, maxScroll);
			return true;
		}
		int maxScroll = Math.max(0, rowCount() - rows);
		if (maxScroll > 0 && mouseX - leftPos < panelX && mouseY - topPos < LIST_Y + rows * ROW_H) {
			scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- rendering ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = leftPos;
		int y = topPos;

		g.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BG);
		g.outline(x, y, imageWidth, imageHeight, COLOR_DARK);
		for (Slot slot : menu.slots) {
			g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, COLOR_DARK);
			g.fill(x + slot.x, y + slot.y, x + slot.x + 17, y + slot.y + 17, COLOR_LIGHT);
			g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, COLOR_LIST_BG);
		}

		if (sellSlotNotBuying()) {
			// A-4: red frame around the sell slot when the market does not take its item right now.
			int sx = x + layout.sellSlotX();
			int sy = y + layout.sellSlotY();
			g.outline(sx - 2, sy - 2, 20, 20, COLOR_RED);
		}

		g.fill(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + listW + 1, y + LIST_Y + rows * ROW_H + 1, COLOR_DARK);
		g.fill(x + LIST_X, y + LIST_Y, x + LIST_X + listW, y + LIST_Y + rows * ROW_H, COLOR_LIST_BG);

		int lx = x + LIST_X;
		int ly = y + LIST_Y;
		switch (tab) {
			case BANK -> extractBank(g, lx, ly);
			case EXCHANGE -> extractExchange(g, lx, ly);
			case TOWN -> extractTown(g, lx, ly, x + panelX, y);
			case QUEST -> {
				extractRequests(g, lx, ly, mouseX, mouseY);
				extractRequestPanel(g, x + panelX, y);
			}
			case BOARD -> extractBoard(g, lx, ly, mouseX, mouseY);
			default -> {
				if (snapshot == null) {
					g.text(font, Component.translatable("screen.ak_trade_expansion.loading"), lx + 4, ly + 4, COLOR_WHITE);
					return;
				}
				List<GoodView> goods = visibleGoods();
				if (goods.isEmpty()) g.text(font, Component.translatable("screen.ak_trade_expansion.empty"), lx + 4, ly + 4, COLOR_WHITE);
				for (int row = 0; row < rows && scroll + row < goods.size(); row++) {
					extractRow(g, goods.get(scroll + row), lx, ly + row * ROW_H, mouseX, mouseY);
				}
				extractPanel(g, x + panelX, y);
			}
		}
		extractScrollbar(g, listScrollbar(), scroll, dragging == Drag.LIST);
		if (sellAllOpen) extractSellAll(g, mouseX, mouseY);
	}

	/** The item in the sell slot is a good of this market that it does not take right now (stock at Scap). */
	private boolean sellSlotNotBuying() {
		ItemStack inSlot = menu.sellSlotItem();
		if (inSlot.isEmpty() || snapshot == null) return false;
		for (GoodView view : snapshot.goods()) {
			InventoryUtil.Match match = view.match();
			if (view.sell() && (match.exact(inSlot) || match.damagedRatio(inSlot) >= 0)) {
				return PriceModel.sellCapacity(view.params(), currentState(view)) < 1;
			}
		}
		return false;
	}

	// ---- scrollbars (A-4, v0.33.1) ----

	/** Scrollable area: where its bar is (screen coordinates) and how far it can scroll. */
	private record Scrollbar(int x, int y, int h, int total, int visible) {
		int max() {
			return Math.max(0, total - visible);
		}

		int thumbH() {
			return Math.max(10, h * visible / Math.max(1, total));
		}

		int thumbY(int scroll) {
			return max() == 0 ? 0 : (h - thumbH()) * scroll / max();
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + SCROLLBAR_W && my >= y && my < y + h;
		}

		/** Scroll position for the thumb's top at {@code thumbTop} (dragging). */
		int scrollAt(double thumbTop) {
			int range = h - thumbH();
			if (range <= 0 || max() == 0) return 0;
			return (int) Math.clamp(Math.round((thumbTop - y) / range * max()), 0, max());
		}
	}

	/** The goods / requests / board list, or {@code null} on tabs without a list. */
	private Scrollbar listScrollbar() {
		if (!(tradeTab() || tab == Tab.QUEST || tab == Tab.BOARD) || sellAllOpen) return null;
		return new Scrollbar(leftPos + LIST_X + listW - SCROLLBAR_W, topPos + LIST_Y, rows * ROW_H, rowCount(), rows);
	}

	private Scrollbar sellAllScrollbar() {
		if (!sellAllOpen) return null;
		return new Scrollbar(leftPos + SELL_ALL_X + sellAllW - SCROLLBAR_W - 1, topPos + SELL_ALL_Y + 16, sellAllRows * ROW_H,
				sellAllLines.size(), sellAllRows);
	}

	/** Track always; the thumb only when there is more than fits. */
	private void extractScrollbar(GuiGraphicsExtractor g, Scrollbar bar, int scrollPos, boolean active) {
		if (bar == null) return;
		g.fill(bar.x(), bar.y(), bar.x() + SCROLLBAR_W, bar.y() + bar.h(), COLOR_SCROLL_TRACK);
		if (bar.max() == 0) return;
		int top = bar.y() + bar.thumbY(scrollPos);
		int bottom = top + bar.thumbH();
		g.fill(bar.x(), top, bar.x() + SCROLLBAR_W, bottom, active ? COLOR_LIGHT : COLOR_SCROLL_THUMB);
		g.fill(bar.x() + 1, top + 1, bar.x() + SCROLLBAR_W - 1, bottom - 1, active ? 0xFFE0E0E0 : 0xFFA8A8A8);
	}

	/**
	 * Clicks on a scrollbar: on the thumb starts dragging, elsewhere on the track scrolls one page towards the click.
	 *
	 * @return the new scroll position, or -1 if the click was not on {@code bar}
	 */
	private int clickScrollbar(Scrollbar bar, int scrollPos, double mx, double my, boolean sellAll) {
		if (bar == null || !bar.contains(mx, my)) return -1;
		if (bar.max() == 0) return scrollPos;
		int top = bar.y() + bar.thumbY(scrollPos);
		if (my >= top && my < top + bar.thumbH()) {
			dragging = sellAll ? Drag.SELL_ALL : Drag.LIST;
			dragOffset = my - top;
			return scrollPos;
		}
		return Math.clamp(scrollPos + (my < top ? -bar.visible() : bar.visible()), 0, bar.max());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == Drag.LIST && listScrollbar() != null) {
			scroll = listScrollbar().scrollAt(event.y() - dragOffset);
			return true;
		}
		if (dragging == Drag.SELL_ALL && sellAllScrollbar() != null) {
			sellAllScroll = sellAllScrollbar().scrollAt(event.y() - dragOffset);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != Drag.NONE) {
			dragging = Drag.NONE;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void extractRow(GuiGraphicsExtractor g, GoodView view, int rx, int ry, int mouseX, int mouseY) {
		GoodParams params = view.params();
		GoodState state = currentState(view);
		boolean buy = tab == Tab.BUY;
		boolean available = buy ? PriceModel.canBuy(state) : PriceModel.sellCapacity(params, state) >= 1;
		boolean hovered = mouseX >= rx && mouseX < rx + listW && mouseY >= ry && mouseY < ry + ROW_H;

		if (view.goods().equals(selected)) g.fill(rx, ry, rx + listW, ry + ROW_H, 0xFF6F6F9F);
		else if (hovered) g.fill(rx, ry, rx + listW, ry + ROW_H, 0xFF9B9B9B);
		else if (!available) g.fill(rx, ry, rx + listW, ry + ROW_H, COLOR_UNAVAILABLE_ROW); // A-4: sold out / not buying at a glance

		ItemStack stack = view.displayStack();
		g.item(stack, rx + 2, ry + 1);
		int textY = ry + 5;
		// K: shortages in orange; D-5: today's specials in light blue with a star.
		int textColor = !available ? COLOR_GRAY : view.shortage() ? COLOR_ORANGE : view.special() ? COLOR_SPECIAL : COLOR_WHITE;
		String name = (view.special() ? "★" : "") + stack.getHoverName().getString();

		long unitPrice = buy ? PriceModel.buyPrice(params, state.pressure()) : PriceModel.sellPrice(params, state.pressure());
		String price = !available
				? Component.translatable(buy ? "screen.ak_trade_expansion.sold_out" : "screen.ak_trade_expansion.not_buying").getString()
				: CoinItem.formatCopper(unitPrice) + (view.unit() > 1 ? "/" + view.unit() : "");
		g.text(font, price, rx + 136 + wide - font.width(price), textY, available ? COLOR_GOLD : COLOR_RED);
		// The name uses the space up to the price and shrinks when it does not fit.
		fitText(g, name, rx + 20, textY, 136 + wide - 20 - font.width(price) - 4, textColor, true);

		// Q17: change since the screen opened.
		Long base = (buy ? baselineBuy : baselineSell).get(view.goods());
		if (available && base != null && base != unitPrice) {
			g.text(font, unitPrice > base ? "▲" : "▼", rx + 137 + wide, textY, unitPrice > base ? COLOR_RED : COLOR_GREEN);
		}

		String stock = (int) Math.floor(state.stock()) + "/" + (int) params.targetStock();
		g.text(font, stock, rx + 168 + wide - font.width(stock), textY, textColor);

		// Q17: market meter. Center = normal price; right = expensive (red), left = cheap (green); log scale, ends at ×3 / ÷3.
		double v = PriceModel.multiplier(params, state.pressure());
		int mx0 = rx + 170 + wide;
		int mw = 22;
		g.fill(mx0, ry + 7, mx0 + mw, ry + 11, COLOR_DARK);
		double pos = Math.clamp(Math.log(v) / Math.log(3.0), -1, 1);
		int center = mx0 + mw / 2;
		int end = center + (int) Math.round(pos * (mw / 2.0));
		g.fill(Math.min(center, end), ry + 7, Math.max(center, end) + 1, ry + 11, v > 1.005 ? COLOR_RED : v < 0.995 ? COLOR_GREEN : COLOR_WHITE);
		g.fill(center, ry + 6, center + 1, ry + 12, COLOR_LIGHT);

		if (hovered && mouseX < rx + 18) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		else if (hovered && !available) g.setTooltipForNextFrame(font, unavailableReason(view, buy), Optional.empty(), mouseX, mouseY);
		else if (hovered && (view.special() || TradeExpansionConfig.get().client.showDebugTooltip)) {
			List<Component> lines = new ArrayList<>();
			if (view.special()) lines.add(Component.translatable("screen.ak_trade_expansion.special.tooltip"));
			if (TradeExpansionConfig.get().client.showDebugTooltip) lines.addAll(debugLines(view));
			g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
		}
	}

	/**
	 * A-4 (client.showDebugTooltip): base price B, current multiplier V, stock and the recovery forecast, as the player
	 * sees them (shortage, special and reputation already in B).
	 */
	private List<Component> debugLines(GoodView view) {
		GoodParams p = view.params();
		GoodState state = currentState(view);
		double restock = TradeExpansionConfig.get().recovery.restockMultiplier;
		double v = PriceModel.multiplier(p, state.pressure());
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("screen.ak_trade_expansion.debug.base", CoinItem.formatCopper(Math.round(p.basePrice()))).withStyle(net.minecraft.ChatFormatting.GRAY));
		lines.add(Component.translatable(v > 1.005 ? "screen.ak_trade_expansion.debug.multiplier.high" : v < 0.995 ? "screen.ak_trade_expansion.debug.multiplier.low"
				: "screen.ak_trade_expansion.debug.multiplier.normal", String.format("%.2f", v)).withStyle(net.minecraft.ChatFormatting.GRAY));
		lines.add(Component.translatable("screen.ak_trade_expansion.debug.prices", CoinItem.formatCopper(PriceModel.buyPrice(p, state.pressure())),
				CoinItem.formatCopper(PriceModel.sellPrice(p, state.pressure()))).withStyle(net.minecraft.ChatFormatting.GRAY));
		String maxStock = p.maxStock() >= 1e9 ? "∞" : String.valueOf((long) p.maxStock()); // E-5: unlimited emerald coins
		lines.add(Component.translatable("screen.ak_trade_expansion.debug.stock", (long) Math.floor(state.stock()), (long) p.targetStock(),
				maxStock).withStyle(net.minecraft.ChatFormatting.GRAY));
		// Recovery forecast (B-3): the price gap halves every H days; stock moves to T at r (below) or c (above) per day.
		if (Math.abs(v - 1) >= 0.005) {
			lines.add(Component.translatable("screen.ak_trade_expansion.debug.price_recovery", minutes(p.halfLifeDays())).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		double gap = p.targetStock() - state.stock();
		double rate = gap > 0 ? p.restockPerDay() * restock : p.consumePerDay() * restock;
		if (Math.abs(gap) >= 1 && rate > 0) {
			lines.add(Component.translatable(gap > 0 ? "screen.ak_trade_expansion.debug.restock" : "screen.ak_trade_expansion.debug.consume",
					minutes(Math.abs(gap) / rate)).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		return lines;
	}

	/** A-4: why a good cannot be bought / sold right now, and when that ends. */
	private List<Component> unavailableReason(GoodView view, boolean buy) {
		GoodState state = currentState(view);
		double restock = TradeExpansionConfig.get().recovery.restockMultiplier;
		double days = buy ? PriceModel.daysUntilRestock(view.params(), state, restock) : PriceModel.daysUntilBuying(view.params(), state, restock);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(buy ? "screen.ak_trade_expansion.sold_out.reason" : "screen.ak_trade_expansion.not_buying.reason"));
		if (Double.isFinite(days)) {
			lines.add(Component.translatable(buy ? "screen.ak_trade_expansion.restock_in" : "screen.ak_trade_expansion.buying_in", minutes(days)));
		}
		return lines;
	}

	private static Component reputationName(int level) {
		return Component.translatable("screen.ak_trade_expansion.reputation.level." + Math.clamp(level, 0, 5));
	}

	/** M: what the reputation level gives and what the next level needs. */
	private List<Component> reputationTooltip() {
		MarketSnapshotS2C.ReputationView r = snapshot.reputation();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("screen.ak_trade_expansion.reputation.title", reputationName(r.level()), r.level()));
		lines.add(r.next() < 0
				? Component.translatable("screen.ak_trade_expansion.reputation.points_max", (long) Math.floor(r.points()))
				: Component.translatable("screen.ak_trade_expansion.reputation.points", (long) Math.floor(r.points()), r.next()));
		if (r.priceBonus() > 0) lines.add(Component.translatable("screen.ak_trade_expansion.reputation.price", percent(r.priceBonus())));
		if (r.rewardBonus() > 0) lines.add(Component.translatable("screen.ak_trade_expansion.reputation.reward", percent(r.rewardBonus())));
		lines.add(r.level() >= r.hardQuestLevel()
				? Component.translatable("screen.ak_trade_expansion.reputation.hard_quest.yes")
				: Component.translatable("screen.ak_trade_expansion.reputation.hard_quest.no", reputationName(r.hardQuestLevel())));
		lines.add(r.level() >= r.specialBonusLevel()
				? Component.translatable("screen.ak_trade_expansion.reputation.special.yes")
				: Component.translatable("screen.ak_trade_expansion.reputation.special.no", reputationName(r.specialBonusLevel())));
		lines.add(Component.translatable("screen.ak_trade_expansion.reputation.how").withStyle(net.minecraft.ChatFormatting.GRAY));
		return lines;
	}

	/** Smallest scale a name is shrunk to before it is cut with "…". */
	private static final float MIN_TEXT_SCALE = 0.6f;

	/**
	 * Draws {@code text} within {@code maxWidth}: as is when it fits, otherwise shrunk (down to {@link #MIN_TEXT_SCALE},
	 * vertically centered on the normal line), and cut with "…" only when even that is too wide.
	 */
	private void fitText(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color, boolean shadow) {
		int width = font.width(text);
		if (width <= maxWidth) {
			g.text(font, text, x, y, color, shadow);
			return;
		}
		float scale = Math.max(MIN_TEXT_SCALE, maxWidth / (float) width);
		String shown = text;
		if (width * scale > maxWidth) {
			int room = (int) (maxWidth / scale) - font.width("…");
			shown = font.plainSubstrByWidth(text, room) + "…";
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y + font.lineHeight * (1 - scale) / 2);
		g.pose().scale(scale, scale);
		g.text(font, shown, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** 0.1 → "10", 0.093 → "9.3". */
	private static String percent(double rate) {
		double value = Math.round(rate * 1000) / 10.0;
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	/** In-game days as real minutes (1 day = 20 minutes), rounded up. */
	private static int minutes(double days) {
		return (int) Math.ceil(days * 20);
	}

	private void extractPanel(GuiGraphicsExtractor g, int px, int y) {
		GoodView view = selectedView();
		if (view == null) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.select"), px, y + 38, PANEL_W, COLOR_TEXT, false);
			return;
		}
		GoodParams params = view.params();
		GoodState state = currentState(view);
		ItemStack stack = view.displayStack();
		g.item(stack, px, y + 36);
		// Long names wrap onto a second line (each line shrinks if it still does not fit).
		List<FormattedCharSequence> nameLines = font.split(stack.getHoverName(), PANEL_W - 18);
		if (nameLines.size() <= 1) {
			fitText(g, stack.getHoverName().getString(), px + 18, y + 40, PANEL_W - 18, COLOR_TEXT, false);
		} else {
			String full = stack.getHoverName().getString();
			String first = font.plainSubstrByWidth(full, PANEL_W - 18);
			g.text(font, first, px + 18, y + 35, COLOR_TEXT, false);
			fitText(g, full.substring(first.length()), px + 18, y + 45, PANEL_W - 18, COLOR_TEXT, false);
		}

		boolean all = shiftAll();
		int qty = all ? maxQuantity(true) : quantity;
		String qtyText = view.unit() > 1 ? qty + " (" + qty * view.unit() + ")" : String.valueOf(qty);
		g.text(font, qtyText, px + 39 - font.width(qtyText) / 2, y + 63, all ? 0xFF2050A0 : COLOR_TEXT, false);

		// Three lines between the quantity buttons (to y + 92) and the confirm button (from y + 122).
		int line1 = y + 95;
		int line2 = y + 104;
		int line3 = y + 113;
		fitText(g, Component.translatable("screen.ak_trade_expansion.total", CoinItem.formatCopper(previewTotal(view, Math.max(1, qty)))).getString(),
				px, line1, PANEL_W, COLOR_TEXT, false);
		if (tab == Tab.BUY) {
			// B-5: bulk discount reached by this quantity
			double rate = snapshot.bulkDiscount().rate(params, Math.max(1, qty), view.unit());
			if (rate > 0) {
				fitText(g, Component.translatable("screen.ak_trade_expansion.bulk_discount", percent(rate)).getString(), px, line3, PANEL_W, 0xFF207020, false);
			}
		}
		double restock = TradeExpansionConfig.get().recovery.restockMultiplier;
		if (tab == Tab.BUY && !PriceModel.canBuy(state)) {
			fitText(g, Component.translatable("screen.ak_trade_expansion.restock_in", minutes(PriceModel.daysUntilRestock(params, state, restock))).getString(),
					px, line2, PANEL_W, COLOR_WARNING, false);
		} else if (tab == Tab.SELL && PriceModel.sellCapacity(params, state) < 1) {
			// A-4: the market is full; say so instead of "max: 0".
			double days = PriceModel.daysUntilBuying(params, state, restock);
			fitText(g, Component.translatable("screen.ak_trade_expansion.not_buying").getString(), px, line2, PANEL_W, COLOR_WARNING, false);
			if (Double.isFinite(days)) {
				fitText(g, Component.translatable("screen.ak_trade_expansion.buying_in", minutes(days)).getString(), px, line3, PANEL_W, COLOR_WARNING, false);
			}
		} else {
			fitText(g, Component.translatable("screen.ak_trade_expansion.max_quantity", maxQuantity(false)).getString(), px, line2, PANEL_W, COLOR_DARK, false);
		}
		if (TradeExpansionConfig.get().trade.allowShiftAll && tab == Tab.BUY) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.shift_all"), px, y + 141, COLOR_DARK, false);
		}
	}

	/** E-7: balances, deposit / withdraw buttons, card issue. */
	private void extractBank(GuiGraphicsExtractor g, int bx, int by) {
		Player player = player();
		long bank = account == null ? 0 : account.bankBalance();
		g.text(font, Component.translatable("screen.ak_trade_expansion.bank.balance", CoinItem.formatCopper(bank)), bx + 4, by + 4, COLOR_GOLD);
		if (player != null) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.bank.cash", CoinItem.formatCopper(WalletService.balance(player))), bx + 4, by + 16, COLOR_WHITE);
		}
		TradeExpansionConfig.Bank bankConfig = TradeExpansionConfig.get().bank;
		if (bankConfig.interestRatePerDay > 0) {
			// E-7: interest rate (and the balance it is paid on)
			String rate = String.valueOf(Math.round(bankConfig.interestRatePerDay * 100_000) / 1000.0).replaceAll("\\.?0+$", "");
			Component text = bankConfig.interestMaxBalance > 0
					? Component.translatable("screen.ak_trade_expansion.bank.interest", rate, CoinItem.formatCopper(bankConfig.interestMaxBalance))
					: Component.translatable("screen.ak_trade_expansion.bank.interest.no_cap", rate);
			fitText(g, text.getString(), bx + 4, by + 25, listW - 8, COLOR_GRAY, true);
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.bank.deposit"), bx + 4, by + 34, COLOR_WHITE);
		g.text(font, Component.translatable("screen.ak_trade_expansion.bank.withdraw"), bx + 4, by + 64, COLOR_WHITE);
	}

	/** N-6: development points, how they are gained and the contributors; donations in the right panel. */
	private void extractTown(GuiGraphicsExtractor g, int tx, int ty, int px, int y) {
		if (snapshot == null) return;
		MarketSnapshotS2C.DevelopmentView d = snapshot.development();
		g.text(font, Component.translatable("screen.ak_trade_expansion.town.points", points(d.points())), tx + 4, ty + 4, COLOR_GOLD);
		fitText(g, Component.translatable("screen.ak_trade_expansion.town.how").getString(), tx + 4, ty + 15, listW - 12, COLOR_GRAY, true);
		g.text(font, Component.translatable("screen.ak_trade_expansion.town.contributors"), tx + 4, ty + 30, COLOR_WHITE);
		List<MarketSnapshotS2C.ContributorView> top = d.top();
		if (top.isEmpty()) g.text(font, Component.translatable("screen.ak_trade_expansion.town.contributors.none"), tx + 8, ty + 41, COLOR_GRAY);
		for (int i = 0; i < top.size(); i++) {
			MarketSnapshotS2C.ContributorView c = top.get(i);
			int rowY = ty + 41 + i * 10;
			String value = points(c.points());
			g.text(font, value, tx + listW - 12 - font.width(value), rowY, COLOR_GOLD);
			fitText(g, (i + 1) + ". " + c.name(), tx + 8, rowY, listW - 24 - font.width(value), COLOR_WHITE, true);
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.town.mine", points(d.mine())), tx + 4, ty + 94, COLOR_WHITE);
		g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.town.donate"), px, y + 38, PANEL_W, COLOR_TEXT, false);
	}

	/** E-3: loose coins per tier and the exchange buttons. */
	private void extractExchange(GuiGraphicsExtractor g, int ex, int ey) {
		Player player = player();
		long[] counts = player == null ? new long[CoinType.values().length] : WalletService.coinCounts(player);
		CoinType[] coins = CoinType.values();
		for (int t = 1; t < coins.length; t++) {
			int ry = ey + 2 + (t - 1) * 26;
			g.item(new ItemStack(ModItems.coin(coins[t])), ex + 2, ry - 1);
			g.text(font, "×" + counts[t], ex + 20, ry + 3, COLOR_WHITE);
			g.item(new ItemStack(ModItems.coin(coins[t - 1])), ex + 56, ry - 1);
			g.text(font, "×" + counts[t - 1], ex + 74, ry + 3, COLOR_WHITE);
		}
		double fee = TradeExpansionConfig.get().currency.exchangeFee;
		g.text(font, Component.translatable("screen.ak_trade_expansion.exchange.help", Math.round(fee * 100)), ex + 4, ey + rows * ROW_H - 10, COLOR_GRAY);
	}

	/** J: request list. */
	private void extractRequests(GuiGraphicsExtractor g, int rx, int ry, int mouseX, int mouseY) {
		if (snapshot == null || snapshot.requests().isEmpty()) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.quest.none"), rx + 4, ry + 4, COLOR_WHITE);
			return;
		}
		List<RequestView> requests = snapshot.requests();
		for (int row = 0; row < rows && scroll + row < requests.size(); row++) {
			RequestView r = requests.get(scroll + row);
			int y = ry + row * ROW_H;
			if (r.request().id() == selectedRequest) g.fill(rx, y, rx + listW, y + ROW_H, 0xFF6F6F9F);
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(r.item()));
			g.item(stack, rx + 2, y + 1);
			String label = stack.getHoverName().getString() + " ×" + r.request().quantity();
			if (r.transport().isPresent()) label += " → " + r.transport().get().destinationName().getString(); // J-2
			boolean othersCargo = r.request().taken() && !r.accepted(); // J-2: another player carries it
			fitText(g, label, rx + 20, y + 5, 100 + wide, othersCargo ? COLOR_GRAY : COLOR_WHITE, true);
			g.text(font, "★".repeat(r.request().difficulty()), rx + 132 + wide, y + 5, r.locked() ? COLOR_GRAY : COLOR_GOLD);
			if (r.accepted()) g.text(font, "✔", rx + 124 + wide, y + 5, COLOR_GREEN);
			String reward = othersCargo ? Component.translatable("screen.ak_trade_expansion.quest.cargo.carrying").getString()
					: CoinItem.formatCopper(r.request().reward());
			g.text(font, reward, rx + listW - 8 - font.width(reward), y + 5, othersCargo ? COLOR_GRAY : COLOR_GOLD);
			if (mouseX >= rx + 2 && mouseX < rx + 18 && mouseY >= y && mouseY < y + ROW_H) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	private void extractRequestPanel(GuiGraphicsExtractor g, int px, int y) {
		RequestView r = selectedRequestView();
		if (r == null) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.quest.select"), px, y + 38, PANEL_W, COLOR_TEXT, false);
			return;
		}
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(r.item()));
		g.item(stack, px, y + 36);
		g.text(font, "×" + r.request().quantity(), px + 18, y + 40, COLOR_TEXT, false);
		if (r.transport().isPresent()) {
			extractTransportPanel(g, px, y, r, r.transport().get());
			return;
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.quest.reward", CoinItem.formatCopper(r.request().reward())), px, y + 60, COLOR_TEXT, false);
		long minutes = 0;
		if (minecraft != null && minecraft.level != null) {
			long left = r.request().deadline() - MarketClock.now(minecraft.level);
			minutes = Math.max(0, (long) Math.ceil(left / (double) PriceModel.TICKS_PER_DAY * 20));
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.quest.time_left", minutes), px, y + 72, COLOR_TEXT, false);
		Player player = player();
		int held = player == null ? 0 : InventoryUtil.count(player, InventoryUtil.Match.plain(stack.getItem()), menu.sellSlot());
		g.text(font, Component.translatable("screen.ak_trade_expansion.quest.held", held), px, y + 84, held >= r.request().quantity() ? 0xFF207020 : 0xFFA02020, false);
		if (r.locked() && snapshot != null) {
			// M: 3-star requests need reputation.hardQuestLevel.
			int needed = snapshot.reputation().hardQuestLevel();
			fitText(g, Component.translatable("screen.ak_trade_expansion.quest.locked", reputationName(needed)).getString(), px, y + 104, PANEL_W, COLOR_WARNING, false);
		}
	}

	/** J-2 (v0.35): destination, direction / distance, reward, deposit and time left of a transport request. */
	private void extractTransportPanel(GuiGraphicsExtractor g, int px, int y, RequestView r, TransportView t) {
		fitText(g, "→ " + t.destinationName().getString(), px, y + 53, PANEL_W, COLOR_TEXT, false);
		fitText(g, Component.translatable("screen.ak_trade_expansion.quest.cargo.where", direction(t.dx(), t.dz()), t.distance()).getString(),
				px, y + 63, PANEL_W, COLOR_TEXT, false);
		fitText(g, Component.translatable("screen.ak_trade_expansion.quest.reward", CoinItem.formatCopper(r.request().reward())).getString(),
				px, y + 73, PANEL_W, COLOR_TEXT, false);
		fitText(g, Component.translatable("screen.ak_trade_expansion.quest.cargo.deposit", CoinItem.formatCopper(t.deposit())).getString(),
				px, y + 83, PANEL_W, COLOR_TEXT, false);
		long minutes = 0;
		if (minecraft != null && minecraft.level != null) {
			long left = r.request().deadline() - MarketClock.now(minecraft.level);
			minutes = Math.max(0, (long) Math.ceil(left / (double) PriceModel.TICKS_PER_DAY * 20));
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.quest.time_left", minutes), px, y + 93, COLOR_TEXT, false);
		if (r.locked() && snapshot != null) {
			int needed = snapshot.reputation().hardQuestLevel();
			fitText(g, Component.translatable("screen.ak_trade_expansion.quest.locked", reputationName(needed)).getString(), px, y + 104, PANEL_W, COLOR_WARNING, false);
		} else if (r.request().taken() && !r.accepted()) {
			fitText(g, Component.translatable("screen.ak_trade_expansion.quest.cargo.carrying").getString(), px, y + 104, PANEL_W, COLOR_GRAY, false);
		}
	}

	private static final int COLOR_DEAL_BUY = 0xFF55FFFF;
	private static final int COLOR_DEAL_SELL = 0xFFFF88FF;

	/** I: nearby markets (with their deals, v0.24), or the goods of the chosen one. */
	private void extractBoard(GuiGraphicsExtractor g, int bx, int by, int mouseX, int mouseY) {
		if (board == null) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.loading"), bx + 4, by + 4, COLOR_WHITE);
			return;
		}
		BoardS2C.Entry entry = boardEntry();
		if (entry == null) {
			if (board.markets().isEmpty()) {
				g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.board.none", TradeExpansionConfig.get().board.radius),
						bx + 4, by + 4, listW - 8, COLOR_WHITE, false);
				return;
			}
			BoardS2C.Entry hovered = null;
			for (int row = 0; row < rows && scroll + row < board.markets().size(); row++) {
				BoardS2C.Entry e = board.markets().get(scroll + row);
				int rowY = by + row * ROW_H;
				if (mouseX >= bx && mouseX < bx + listW && mouseY >= rowY && mouseY < rowY + ROW_H) {
					hovered = e;
					g.fill(bx, rowY, bx + listW, rowY + ROW_H, 0xFF9B9B9B);
				}
				int y = rowY + 5;
				String where = Component.translatable("screen.ak_trade_expansion.board.where", direction(e.dx(), e.dz()),
						(int) Math.round(Math.sqrt((double) e.dx() * e.dx() + (double) e.dz() * e.dz()))).getString();
				int right = bx + listW - 8 - font.width(where);
				g.text(font, where, right, y, COLOR_GOLD);
				// Deal counts: cheaper to buy than here (cyan) / pays more than here (pink).
				long buyDeals = e.goods().stream().filter(gd -> gd.buyDeal() > 0).count();
				long sellDeals = e.goods().stream().filter(gd -> gd.sellDeal() > 0).count();
				int x = right - 4;
				if (sellDeals > 0) {
					String text = Component.translatable("screen.ak_trade_expansion.board.deal_sell_count", sellDeals).getString();
					x -= font.width(text);
					g.text(font, text, x, y, COLOR_DEAL_SELL);
					x -= 3;
				}
				if (buyDeals > 0) {
					String text = Component.translatable("screen.ak_trade_expansion.board.deal_buy_count", buyDeals).getString();
					x -= font.width(text);
					g.text(font, text, x, y, COLOR_DEAL_BUY);
					x -= 3;
				}
				g.text(font, font.plainSubstrByWidth(e.title().getString(), x - bx - 4), bx + 4, y, COLOR_WHITE);
			}
			extractDeals(g, hovered);
			return;
		}
		g.textWithWordWrap(font, entry.title(), leftPos + panelX, topPos + 38, PANEL_W, COLOR_TEXT, false);
		g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.board.columns"), leftPos + panelX, topPos + 62, PANEL_W, COLOR_DARK, false);
		// Header line: goods / buy price (the player pays) / sell price (the player gets).
		g.fill(bx, by, bx + listW, by + BOARD_HEADER_H, COLOR_DARK);
		int headerY = by + 1;
		String buyHeader = Component.translatable("screen.ak_trade_expansion.board.header.buy").getString();
		String sellHeader = Component.translatable("screen.ak_trade_expansion.board.header.sell").getString();
		g.text(font, Component.translatable("screen.ak_trade_expansion.board.header.goods"), bx + 20, headerY, COLOR_WHITE);
		g.text(font, buyHeader, bx + 130 + wide - font.width(buyHeader), headerY, COLOR_GOLD);
		g.text(font, sellHeader, bx + listW - 8 - font.width(sellHeader), headerY, COLOR_GREEN);
		if (mouseX >= bx && mouseX < bx + listW && mouseY >= by && mouseY < by + BOARD_HEADER_H) {
			g.setTooltipForNextFrame(font, List.of(Component.translatable("screen.ak_trade_expansion.board.header.buy.tooltip"),
					Component.translatable("screen.ak_trade_expansion.board.header.sell.tooltip")), Optional.empty(), mouseX, mouseY);
		}
		List<BoardS2C.Good> goods = entry.goods();
		int goodsRows = (rows * ROW_H - BOARD_HEADER_H) / ROW_H;
		for (int row = 0; row < goodsRows && scroll + row < goods.size(); row++) {
			BoardS2C.Good good = goods.get(scroll + row);
			int y = by + BOARD_HEADER_H + row * ROW_H;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(good.item()));
			g.item(stack, bx + 2, y + 1);
			fitText(g, stack.getHoverName().getString(), bx + 20, y + 5, 64 + wide, good.shortage() ? COLOR_ORANGE : COLOR_WHITE, true);
			String buy = good.buy() < 0 ? "-" : (good.buyDeal() > 0 ? "\u2193" : "") + CoinItem.formatCopper(good.buy());
			String sell = good.sell() < 0 ? "-" : (good.sellDeal() > 0 ? "\u2191" : "") + CoinItem.formatCopper(good.sell());
			g.text(font, buy, bx + 130 + wide - font.width(buy), y + 5, good.buyDeal() > 0 ? COLOR_DEAL_BUY : COLOR_GOLD);
			g.text(font, sell, bx + listW - 8 - font.width(sell), y + 5, good.sellDeal() > 0 ? COLOR_DEAL_SELL : COLOR_GREEN);
			if (mouseX >= bx && mouseX < bx + listW && mouseY >= y && mouseY < y + ROW_H && (good.buyDeal() > 0 || good.sellDeal() > 0)) {
				List<Component> lines = new ArrayList<>();
				lines.add(stack.getHoverName());
				if (good.buyDeal() > 0) lines.add(Component.translatable("screen.ak_trade_expansion.board.deal_buy", good.buyDeal()));
				if (good.sellDeal() > 0) lines.add(Component.translatable("screen.ak_trade_expansion.board.deal_sell", good.sellDeal()));
				g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	/** I (v0.24): the best deals (2 each) of the market under the mouse, in the right panel. */
	private void extractDeals(GuiGraphicsExtractor g, BoardS2C.Entry entry) {
		int px = leftPos + panelX;
		int y = topPos + 38;
		if (entry == null) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.board.deal_hint"), px, y, PANEL_W, COLOR_DARK, false);
			return;
		}
		List<BoardS2C.Good> cheap = entry.goods().stream().filter(gd -> gd.buyDeal() > 0)
				.sorted(java.util.Comparator.comparingInt(BoardS2C.Good::buyDeal).reversed()).limit(2).toList();
		List<BoardS2C.Good> dear = entry.goods().stream().filter(gd -> gd.sellDeal() > 0)
				.sorted(java.util.Comparator.comparingInt(BoardS2C.Good::sellDeal).reversed()).limit(2).toList();
		if (cheap.isEmpty() && dear.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.board.deal_none"), px, y, PANEL_W, COLOR_DARK, false);
			return;
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.board.deal_buy_title"), px, y, 0xFF1F6F7F, false);
		y = extractDealLines(g, cheap, true, px, y + 10);
		g.text(font, Component.translatable("screen.ak_trade_expansion.board.deal_sell_title"), px, y + 2, 0xFF8F2F7F, false);
		extractDealLines(g, dear, false, px, y + 12);
	}

	private int extractDealLines(GuiGraphicsExtractor g, List<BoardS2C.Good> goods, boolean buy, int px, int y) {
		if (goods.isEmpty()) {
			g.text(font, "-", px + 2, y + 4, COLOR_DARK, false);
			return y + 16;
		}
		for (BoardS2C.Good good : goods) {
			g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(good.item())), px, y);
			String text = buy ? "-" + good.buyDeal() + "%" : "+" + good.sellDeal() + "%";
			g.text(font, text, px + 18, y + 4, buy ? 0xFF1F6F7F : 0xFF8F2F7F, false);
			y += 16;
		}
		return y;
	}

	private static String direction(int dx, int dz) {
		if (dx == 0 && dz == 0) return Component.translatable("direction.ak_trade_expansion.here").getString();
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
		String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return Component.translatable("direction.ak_trade_expansion." + keys[index]).getString();
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// Title, then (M) the player's reputation right after it: "市場名（町）│ 評判：常連". The right end stays free for
		// buttons other mods add there; a long title shrinks so the reputation always shows.
		int room = imageWidth - 16 - TITLE_RIGHT_RESERVE;
		Player viewer = player();
		if (viewer != null && TradeExpansionConfig.get().client.balancePosition == TradeExpansionConfig.BalancePosition.TOP_RIGHT) {
			// The balance takes the right end of the title row: the title and the reputation stop before it.
			room = imageWidth - 16 - font.width(topRightBalance(viewer)) - 8;
		}
		String titleText = title.getString();
		if (snapshot != null && snapshot.reputation().enabled()) {
			String label = Component.translatable("screen.ak_trade_expansion.reputation.label", reputationName(snapshot.reputation().level())).getString();
			String separator = " │ ";
			int labelWidth = font.width(separator + label);
			int titleWidth = Math.min(font.width(titleText), room - labelWidth);
			fitText(g, titleText, 8, 6, titleWidth, COLOR_TEXT, false);
			int lx = 8 + titleWidth;
			g.text(font, separator, lx, 6, COLOR_GRAY, false);
			int rx = lx + font.width(separator);
			g.text(font, label, rx, 6, 0xFF6040A0, false);
			// Details on hover.
			int mx = mouseX - leftPos;
			int my = mouseY - topPos;
			if (mx >= rx && mx < rx + font.width(label) && my >= 4 && my < 16) g.setTooltipForNextFrame(font, reputationTooltip(), Optional.empty(), mouseX, mouseY);
		} else {
			fitText(g, titleText, 8, 6, room, COLOR_TEXT, false);
		}
		Player player = player();
		if (player != null) extractBalance(g, player, mouseX - leftPos, mouseY - topPos, mouseX, mouseY);
		if (!sellAllOpen) g.text(font, font.plainSubstrByWidth(status.getString(), imageWidth - 16), 8, LIST_Y + rows * ROW_H + 3,
				statusColor == COLOR_GREEN ? 0xFF207020 : statusColor == COLOR_RED ? 0xFFA02020 : COLOR_TEXT, false);
		g.text(font, Component.translatable("screen.ak_trade_expansion.sell_slot"), layout.sellSlotX() - 6, layout.sellSlotY() - 11, COLOR_TEXT, false);
	}

	/**
	 * Cash = coins + wallets (E-6); the bank balance is shown once known (E-7). Position: {@code client.balancePosition}
	 * (A-4). Hovering shows the breakdown (Q17).
	 */
	/** The one-line balance shown at the right end of the title row ({@code client.balancePosition = TOP_RIGHT}). */
	private Component topRightBalance(Player player) {
		long cash = WalletService.balance(player);
		return account == null
				? Component.translatable("screen.ak_trade_expansion.balance", CoinItem.formatCopper(cash))
				: Component.translatable("screen.ak_trade_expansion.balance_bank", CoinItem.formatCopper(cash), CoinItem.formatCopper(account.bankBalance()));
	}

	private void extractBalance(GuiGraphicsExtractor g, Player player, int mx, int my, int mouseX, int mouseY) {
		long cash = WalletService.balance(player);
		int x0;
		int y0;
		int x1;
		int y1;
		TradeExpansionConfig.BalancePosition position = TradeExpansionConfig.get().client.balancePosition;
		if (position == TradeExpansionConfig.BalancePosition.TOP_RIGHT) {
			Component balance = topRightBalance(player);
			x0 = imageWidth - 8 - font.width(balance);
			x1 = imageWidth - 8;
			y0 = 5;
			y1 = 15;
			g.text(font, balance, x0, 6, COLOR_TEXT, false);
		} else {
			// Two-line column: right of the player inventory (A, default) or below the sell slot (B).
			boolean right = position == TradeExpansionConfig.BalancePosition.INVENTORY_RIGHT;
			x0 = right ? layout.inventoryX() + 9 * 18 + 4 : 8;
			x1 = right ? imageWidth - 6 : layout.inventoryX() - 4;
			y0 = right ? layout.inventoryY() + 4 : layout.sellSlotY() + 22;
			int width = x1 - x0;
			int y = y0;
			g.text(font, Component.translatable("screen.ak_trade_expansion.cash_label"), x0, y, COLOR_DARK, false);
			g.text(font, fit(cash, width), x0, y + 10, COLOR_TEXT, false);
			y += 20;
			if (account != null) {
				g.text(font, Component.translatable("screen.ak_trade_expansion.bank_label"), x0, y, COLOR_DARK, false);
				g.text(font, fit(account.bankBalance(), width), x0, y + 10, COLOR_TEXT, false);
				y += 20;
			}
			y1 = y;
		}
		if (mx >= x0 && mx < x1 && my >= y0 && my < y1) {
			g.setTooltipForNextFrame(font, balanceBreakdown(player), Optional.empty(), mouseX, mouseY);
		}
	}

	/** The amount as "12,345c", or shortened ("1.2Mc") when it does not fit {@code width}; the tooltip has the exact value. */
	private String fit(long copper, int width) {
		String full = CoinItem.formatCopper(copper);
		if (font.width(full) <= width) return full;
		String[] suffixes = {"k", "M", "G", "T"};
		double value = copper;
		int i = -1;
		while (Math.abs(value) >= 1000 && i < suffixes.length - 1) {
			value /= 1000;
			i++;
		}
		return (i < 0 ? String.valueOf(copper) : String.format(Locale.ROOT, value < 100 ? "%.1f%s" : "%.0f%s", value, suffixes[i])) + "c";
	}

	/** Q17: what the cash is made of. */
	private List<Component> balanceBreakdown(Player player) {
		List<Component> lines = new ArrayList<>();
		long[] counts = WalletService.coinCounts(player);
		CoinType[] types = CoinType.values();
		for (int t = types.length - 1; t >= 0; t--) {
			if (counts[t] > 0) lines.add(Component.translatable("item.ak_trade_expansion." + types[t].path()).append(" ×" + counts[t]));
		}
		List<ItemStack> wallets = WalletService.wallets(player);
		if (!wallets.isEmpty()) {
			long total = wallets.stream().mapToLong(WalletItem::balance).sum();
			lines.add(Component.translatable("screen.ak_trade_expansion.balance.wallet", CoinItem.formatCopper(total)));
		}
		if (account != null) lines.add(Component.translatable("screen.ak_trade_expansion.balance.bank", CoinItem.formatCopper(account.bankBalance())));
		if (lines.isEmpty()) lines.add(Component.translatable("screen.ak_trade_expansion.balance.none"));
		return lines;
	}

	/** Price graph of the hovered good (A-4, Q17), drawn above everything else. */
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (!tradeTab() || snapshot == null || sellAllOpen) return;
		int rx = leftPos + LIST_X;
		int ry = topPos + LIST_Y;
		if (mouseX < rx + 18 || mouseX >= rx + listW - SCROLLBAR_W || mouseY < ry || mouseY >= ry + rows * ROW_H) return;
		int index = scroll + (mouseY - ry) / ROW_H;
		List<GoodView> goods = visibleGoods();
		if (index >= goods.size()) return;
		boolean tooltipRight = TradeExpansionConfig.get().client.showDebugTooltip || goods.get(index).special();
		extractGraph(g, goods.get(index), tooltipRight ? mouseX - 10 - 124 : mouseX + 10, mouseY + 6);
	}

	private void extractGraph(GuiGraphicsExtractor g, GoodView view, int gx, int gy) {
		boolean buy = tab == Tab.BUY;
		List<Double> xs = new ArrayList<>(view.history());
		xs.add(currentState(view).pressure());
		long[] prices = new long[xs.size()];
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (int i = 0; i < prices.length; i++) {
			prices[i] = buy ? PriceModel.buyPrice(view.params(), xs.get(i)) : PriceModel.sellPrice(view.params(), xs.get(i));
			min = Math.min(min, prices[i]);
			max = Math.max(max, prices[i]);
		}
		int w = 120;
		int h = 64;
		gx = Math.min(gx, width - w - 4);
		gy = Math.min(gy, height - h - 4);
		g.fill(gx, gy, gx + w, gy + h, 0xF0100010);
		g.outline(gx, gy, w, h, 0xFF5000FF);
		g.text(font, Component.translatable(buy ? "screen.ak_trade_expansion.graph.buy" : "screen.ak_trade_expansion.graph.sell"), gx + 4, gy + 3, COLOR_WHITE);
		double v = PriceModel.multiplier(view.params(), currentState(view).pressure());
		String pct = Math.round(v * 100) + "%";
		g.text(font, pct, gx + w - 4 - font.width(pct), gy + 3, v > 1.005 ? COLOR_RED : v < 0.995 ? COLOR_GREEN : COLOR_WHITE);

		int px0 = gx + 6;
		int py0 = gy + 16;
		int pw = w - 12;
		int ph = h - 30;
		g.fill(px0, py0 + ph, px0 + pw, py0 + ph + 1, COLOR_GRAY);
		long span = Math.max(1, max - min);
		int prevX = -1;
		int prevY = -1;
		for (int i = 0; i < prices.length; i++) {
			int px = prices.length == 1 ? px0 + pw : px0 + i * pw / (prices.length - 1);
			int py = py0 + ph - (int) Math.round((prices[i] - min) * (double) ph / span);
			if (prevX >= 0) line(g, prevX, prevY, px, py, COLOR_GOLD);
			g.fill(px - 1, py - 1, px + 1, py + 1, COLOR_WHITE);
			prevX = px;
			prevY = py;
		}
		g.text(font, CoinItem.formatCopper(min) + " - " + CoinItem.formatCopper(max), gx + 4, gy + h - 11, COLOR_GRAY);
		String days = Component.translatable("screen.ak_trade_expansion.graph.days", Math.max(0, prices.length - 1)).getString();
		g.text(font, days, gx + w - 4 - font.width(days), gy + h - 11, COLOR_GRAY);
	}

	/** Draws a 1px line with small rectangles (the GUI has no line primitive). */
	private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int s = 0; s <= steps; s++) {
			int x = x0 + (steps == 0 ? 0 : (x1 - x0) * s / steps);
			int y = y0 + (steps == 0 ? 0 : (y1 - y0) * s / steps);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}
}
