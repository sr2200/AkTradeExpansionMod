package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.network.AccountS2C;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.network.TradeResultS2C;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trade screen (design A-4): buy / sell / bank tabs, goods list, quantity, preview and confirm,
 * card / cash payment switch (E-8).
 * All numbers are computed with the same {@link PriceModel} the server uses.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 240;

	private static final int LIST_X = 8;
	private static final int LIST_Y = 36;
	private static final int LIST_W = 200;
	private static final int ROW_H = 18;
	private static final int ROWS = 6;
	private static final int PANEL_X = 214;
	private static final int PANEL_W = 78;

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

	private MarketSnapshotS2C snapshot;
	private boolean buyTab = true;
	private Identifier selected;
	private int quantity = 1;
	private int scroll;
	private boolean pending;
	private Component status = Component.empty();
	private int statusColor = COLOR_TEXT;

	private Button buyTabButton;
	private Button sellTabButton;
	private Button confirmButton;

	/** Bank tab (E-7): shown in markets of at least {@code bank.minMarketLevel}. */
	private boolean bankTab;
	private Button bankTabButton;
	/** Latest bank balance / card validity from the server (E-7 / E-8); {@code null} until received. */
	private AccountS2C account;
	private boolean payByCard = true;
	private Button paymentButton;
	private final List<Button> tradeWidgets = new ArrayList<>();
	private final List<Button> bankWidgets = new ArrayList<>();
	private static final long[] BANK_AMOUNTS = {100, 1_000, 10_000, -1}; // -1 = everything

	public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.snapshot = ClientMarketCache.get(menu.marketId());
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;
		buyTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.trade_expansion.tab.buy"), b -> setTab(true))
				.bounds(x + LIST_X, y + 18, 50, 14).build());
		sellTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.trade_expansion.tab.sell"), b -> setTab(false))
				.bounds(x + LIST_X + 52, y + 18, 50, 14).build());
		bankTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.trade_expansion.tab.bank"), b -> setBankTab())
				.bounds(x + LIST_X + 104, y + 18, 50, 14).build());
		paymentButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			payByCard = !payByCard;
			updateButtons();
		}).bounds(x + PANEL_X, y + 18, PANEL_W, 14).build());

		tradeWidgets.clear();
		bankWidgets.clear();
		for (int i = 0; i < BANK_AMOUNTS.length; i++) {
			long amount = BANK_AMOUNTS[i];
			Component label = amount < 0 ? Component.translatable("screen.trade_expansion.bank.all") : Component.literal(CoinItem.formatCopper(amount));
			bankWidgets.add(addRenderableWidget(Button.builder(label, b -> bank(BankRequestC2S.Action.DEPOSIT, amount))
					.bounds(x + LIST_X + 4 + i * 49, y + LIST_Y + 44, 47, 14).build()));
			bankWidgets.add(addRenderableWidget(Button.builder(label, b -> bank(BankRequestC2S.Action.WITHDRAW, amount))
					.bounds(x + LIST_X + 4 + i * 49, y + LIST_Y + 74, 47, 14).build()));
		}
		bankWidgets.add(addRenderableWidget(Button.builder(Component.empty(), b -> bank(BankRequestC2S.Action.ISSUE_CARD, 0))
				.bounds(x + LIST_X + 4, y + LIST_Y + 92, 192, 14).build()));

		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> setQuantity(quantity - 1)).bounds(x + PANEL_X, y + 60, 14, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> setQuantity(quantity + 1)).bounds(x + PANEL_X + 64, y + 60, 14, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("1"), b -> setQuantity(1)).bounds(x + PANEL_X, y + 78, 16, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("8"), b -> setQuantity(8)).bounds(x + PANEL_X + 18, y + 78, 16, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.literal("64"), b -> setQuantity(64)).bounds(x + PANEL_X + 36, y + 78, 20, 14).build()));
		tradeWidgets.add(addRenderableWidget(Button.builder(Component.translatable("screen.trade_expansion.max"), b -> setQuantity(maxQuantity()))
				.bounds(x + PANEL_X + 58, y + 78, 20, 14).build()));

		confirmButton = addRenderableWidget(Button.builder(Component.translatable("screen.trade_expansion.confirm.buy"), b -> confirm())
				.bounds(x + PANEL_X, y + 122, PANEL_W, 16).build());
		tradeWidgets.add(confirmButton);
		updateButtons();
	}

	// ---- packets ----

	public void onSnapshot(MarketSnapshotS2C newSnapshot) {
		if (newSnapshot.marketId() != menu.marketId()) return;
		snapshot = newSnapshot;
		updateButtons();
	}

	public void onResult(TradeResultS2C result) {
		pending = false;
		if (result.result() == TradeResult.SUCCESS) {
			GoodView view = selectedView();
			Component name = view == null ? Component.empty() : stackOf(view.item()).getHoverName();
			int items = result.quantity() * (view == null ? 1 : view.unit());
			status = Component.translatable(result.buy() ? "screen.trade_expansion.bought" : "screen.trade_expansion.sold",
					name, items, CoinItem.formatCopper(result.total()));
			statusColor = COLOR_GREEN;
		} else {
			status = Component.translatable(result.result().translationKey());
			statusColor = COLOR_RED;
		}
		updateButtons();
	}

	public void onAccount(AccountS2C newAccount) {
		account = newAccount;
		if (newAccount.result() >= 0) {
			BankService.Result result = BankService.Result.values()[newAccount.result()];
			status = Component.translatable("bank.trade_expansion.result." + result.name().toLowerCase(Locale.ROOT));
			statusColor = result == BankService.Result.SUCCESS ? COLOR_GREEN : COLOR_RED;
		}
		updateButtons();
	}

	// ---- state ----

	private boolean bankAvailable() {
		return snapshot != null && snapshot.level() >= TradeExpansionConfig.get().bank.minMarketLevel;
	}

	/** Card payment is used when chosen and a valid card is carried (E-8). */
	private boolean useCard() {
		return payByCard && account != null && account.validCard();
	}

	private void setBankTab() {
		bankTab = true;
		selected = null;
		updateButtons();
	}

	private void bank(BankRequestC2S.Action action, long amount) {
		Player player = player();
		if (player == null) return;
		long value = amount;
		if (amount < 0) value = action == BankRequestC2S.Action.DEPOSIT ? WalletService.balance(player) : account == null ? 0 : account.bankBalance();
		ClientPlayNetworking.send(new BankRequestC2S(menu.marketId(), action, value));
	}

	private void setTab(boolean buy) {
		bankTab = false;
		buyTab = buy;
		scroll = 0;
		selected = null;
		quantity = 1;
		updateButtons();
	}

	private List<GoodView> visibleGoods() {
		List<GoodView> list = new ArrayList<>();
		if (snapshot == null) return list;
		for (GoodView view : snapshot.goods()) {
			if (buyTab ? view.buy() : view.sell()) list.add(view);
		}
		return list;
	}

	private GoodView selectedView() {
		if (selected == null) return null;
		for (GoodView view : visibleGoods()) {
			if (view.goods().equals(selected)) return view;
		}
		return null;
	}

	/** State extrapolated to the client's game time (lazy recovery, B-3). */
	private GoodState currentState(GoodView view) {
		GoodState state = view.state();
		if (minecraft != null && minecraft.level != null && snapshot != null) {
			double days = (MarketClock.now(minecraft.level) - snapshot.gameTime()) / (double) PriceModel.TICKS_PER_DAY;
			TradeExpansionConfig config = TradeExpansionConfig.get();
			PriceModel.recover(view.params(), state, days, config.recovery.halfLifeDays, config.recovery.restockMultiplier);
		}
		return state;
	}

	private static ItemStack stackOf(Identifier id) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}

	private Player player() {
		return minecraft == null ? null : minecraft.player;
	}

	/** Largest quantity (in trading units) the trade could succeed with right now (B-5). */
	private int maxQuantity() {
		GoodView view = selectedView();
		Player player = player();
		if (view == null || player == null) return 0;
		GoodParams params = view.params();
		GoodState state = currentState(view);
		Item item = BuiltInRegistries.ITEM.getValue(view.item());
		int limit = TradeExpansionConfig.get().trade.maxPerTransaction;
		if (buyTab) {
			int max = (int) Math.min(limit, Math.max(0, Math.floor(state.stock())));
			max = Math.min(max, InventoryUtil.freeSpaceFor(player, item, !useCard()) / view.unit());
			long funds = useCard()
					? (long) Math.floor(account.bankBalance() / (1 + TradeExpansionConfig.get().bank.cardTransactionFee))
					: WalletService.balance(player);
			return PriceModel.maxAffordable(params, state.pressure(), funds, max);
		}
		return Math.min(limit, Math.min(InventoryUtil.countPlain(player, item) / view.unit(), PriceModel.sellCapacity(params, state)));
	}

	private void setQuantity(int value) {
		int limit = TradeExpansionConfig.get().trade.maxPerTransaction;
		quantity = Math.clamp(value, 1, limit);
		updateButtons();
	}

	private long previewTotal(GoodView view) {
		GoodState state = currentState(view);
		return buyTab ? PriceModel.sumBuy(view.params(), state.pressure(), quantity)
				: PriceModel.sumSell(view.params(), state.pressure(), quantity);
	}

	private void confirm() {
		GoodView view = selectedView();
		if (view == null || pending) return;
		pending = true;
		ClientPlayNetworking.send(new TradeRequestC2S(menu.marketId(), view.goods(), buyTab, quantity, previewTotal(view), useCard()));
		updateButtons();
	}

	private void updateButtons() {
		if (buyTabButton == null) return;
		buyTabButton.active = bankTab || !buyTab;
		sellTabButton.active = bankTab || buyTab;
		if (bankTab && !bankAvailable()) bankTab = false;
		bankTabButton.visible = bankAvailable();
		bankTabButton.active = !bankTab;
		paymentButton.visible = !bankTab && account != null && account.validCard();
		paymentButton.setMessage(Component.translatable(payByCard ? "screen.trade_expansion.pay.card" : "screen.trade_expansion.pay.cash"));
		for (Button b : tradeWidgets) b.visible = !bankTab;
		for (Button b : bankWidgets) b.visible = bankTab;
		bankWidgets.getLast().setMessage(Component.translatable("screen.trade_expansion.bank.issue_card",
				CoinItem.formatCopper(TradeExpansionConfig.get().bank.cardFee)));
		confirmButton.setMessage(Component.translatable(buyTab ? "screen.trade_expansion.confirm.buy" : "screen.trade_expansion.confirm.sell"));
		confirmButton.active = !pending && selectedView() != null && quantity <= maxQuantity();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// Inventory, balance and recovery change over time.
		updateButtons();
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x() - leftPos;
		double my = event.y() - topPos;
		if (!bankTab && mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + ROWS * ROW_H) {
			int index = scroll + (int) ((my - LIST_Y) / ROW_H);
			List<GoodView> goods = visibleGoods();
			if (index < goods.size()) {
				selected = goods.get(index).goods();
				quantity = 1;
				updateButtons();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int maxScroll = Math.max(0, visibleGoods().size() - ROWS);
		if (!bankTab && maxScroll > 0 && mouseX - leftPos < PANEL_X) {
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

		// panel
		g.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BG);
		g.outline(x, y, imageWidth, imageHeight, COLOR_DARK);

		// slot backgrounds
		for (Slot slot : menu.slots) {
			g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, COLOR_DARK);
			g.fill(x + slot.x, y + slot.y, x + slot.x + 17, y + slot.y + 17, COLOR_LIGHT);
			g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, COLOR_LIST_BG);
		}

		// goods list
		g.fill(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + LIST_W + 1, y + LIST_Y + ROWS * ROW_H + 1, COLOR_DARK);
		g.fill(x + LIST_X, y + LIST_Y, x + LIST_X + LIST_W, y + LIST_Y + ROWS * ROW_H, COLOR_LIST_BG);

		if (bankTab) {
			extractBank(g, x + LIST_X, y + LIST_Y);
			return;
		}
		if (snapshot == null) {
			g.text(font, Component.translatable("screen.trade_expansion.loading"), x + LIST_X + 4, y + LIST_Y + 4, COLOR_WHITE);
			return;
		}

		List<GoodView> goods = visibleGoods();
		if (goods.isEmpty()) {
			g.text(font, Component.translatable("screen.trade_expansion.empty"), x + LIST_X + 4, y + LIST_Y + 4, COLOR_WHITE);
		}
		for (int row = 0; row < ROWS && scroll + row < goods.size(); row++) {
			extractRow(g, goods.get(scroll + row), x + LIST_X, y + LIST_Y + row * ROW_H, mouseX, mouseY);
		}
		if (goods.size() > ROWS) {
			int trackH = ROWS * ROW_H;
			int thumbH = Math.max(8, trackH * ROWS / goods.size());
			int thumbY = (trackH - thumbH) * scroll / (goods.size() - ROWS);
			g.fill(x + LIST_X + LIST_W - 3, y + LIST_Y + thumbY, x + LIST_X + LIST_W, y + LIST_Y + thumbY + thumbH, COLOR_LIGHT);
		}

		extractPanel(g, x + PANEL_X, y);
	}

	private void extractRow(GuiGraphicsExtractor g, GoodView view, int rx, int ry, int mouseX, int mouseY) {
		GoodParams params = view.params();
		GoodState state = currentState(view);
		boolean available = buyTab ? PriceModel.canBuy(state) : PriceModel.canSell(params, state);
		boolean hovered = mouseX >= rx && mouseX < rx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;

		if (view.goods().equals(selected)) {
			g.fill(rx, ry, rx + LIST_W, ry + ROW_H, 0xFF6F6F9F);
		} else if (hovered) {
			g.fill(rx, ry, rx + LIST_W, ry + ROW_H, 0xFF9B9B9B);
		}

		ItemStack stack = stackOf(view.item());
		g.item(stack, rx + 2, ry + 1);
		int textY = ry + 5;
		int textColor = available ? COLOR_WHITE : COLOR_GRAY;
		g.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), 76), rx + 20, textY, textColor);

		String price;
		if (!available) {
			price = buyTab ? Component.translatable("screen.trade_expansion.sold_out").getString()
					: Component.translatable("screen.trade_expansion.not_buying").getString();
		} else {
			long unit = buyTab ? PriceModel.buyPrice(params, state.pressure()) : PriceModel.sellPrice(params, state.pressure());
			price = CoinItem.formatCopper(unit) + (view.unit() > 1 ? "/" + view.unit() : "");
		}
		g.text(font, price, rx + 140 - font.width(price), textY, available ? COLOR_GOLD : COLOR_RED);

		String stock = (int) Math.floor(state.stock()) + "/" + (int) params.targetStock();
		g.text(font, stock, rx + 172 - font.width(stock), textY, textColor);

		double v = PriceModel.multiplier(params, state.pressure());
		String pct = Math.round(v * 100) + "%";
		// Above 100% = expensive (red), below = cheap (green).
		int pctColor = v > 1.005 ? COLOR_RED : v < 0.995 ? COLOR_GREEN : COLOR_WHITE;
		g.text(font, pct, rx + LIST_W - 5 - font.width(pct), textY, pctColor);

		if (hovered && mouseX < rx + 18) {
			g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	/** Bank tab (E-7): balances, deposit / withdraw buttons, card issue. */
	private void extractBank(GuiGraphicsExtractor g, int bx, int by) {
		Player player = player();
		long bank = account == null ? 0 : account.bankBalance();
		g.text(font, Component.translatable("screen.trade_expansion.bank.balance", CoinItem.formatCopper(bank)), bx + 4, by + 4, COLOR_GOLD);
		if (player != null) {
			g.text(font, Component.translatable("screen.trade_expansion.bank.cash", CoinItem.formatCopper(WalletService.balance(player))), bx + 4, by + 16, COLOR_WHITE);
		}
		g.text(font, Component.translatable("screen.trade_expansion.bank.deposit"), bx + 4, by + 34, COLOR_WHITE);
		g.text(font, Component.translatable("screen.trade_expansion.bank.withdraw"), bx + 4, by + 64, COLOR_WHITE);
	}

	private void extractPanel(GuiGraphicsExtractor g, int px, int y) {
		GoodView view = selectedView();
		if (view == null) {
			g.textWithWordWrap(font, Component.translatable("screen.trade_expansion.select"), px, y + 38, PANEL_W, COLOR_TEXT, false);
			return;
		}
		GoodParams params = view.params();
		GoodState state = currentState(view);
		ItemStack stack = stackOf(view.item());
		g.item(stack, px, y + 36);
		g.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), PANEL_W - 18), px + 18, y + 40, COLOR_TEXT, false);

		// Trading units: "3 (30)" means 3 units of 10 items.
		String qty = view.unit() > 1 ? quantity + " (" + quantity * view.unit() + ")" : String.valueOf(quantity);
		g.text(font, qty, px + 39 - font.width(qty) / 2, y + 63, COLOR_TEXT, false);

		long total = previewTotal(view);
		g.text(font, Component.translatable("screen.trade_expansion.total", CoinItem.formatCopper(total)), px, y + 98, COLOR_TEXT, false);
		if (buyTab && !PriceModel.canBuy(state)) {
			// One in-game day = 20 real minutes.
			double minutes = PriceModel.daysUntilRestock(params, state, TradeExpansionConfig.get().recovery.restockMultiplier) * 20;
			g.text(font, Component.translatable("screen.trade_expansion.restock_in", (int) Math.ceil(minutes)), px, y + 110, 0xFFA02020, false);
		} else {
			g.text(font, Component.translatable("screen.trade_expansion.max_quantity", maxQuantity()), px, y + 110, COLOR_DARK, false);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(font, title, 8, 6, COLOR_TEXT, false);
		Player player = player();
		if (player != null) {
			// Cash = coins + wallets (E-6); the bank balance is shown once known (E-7).
			Component balance = account == null
					? Component.translatable("screen.trade_expansion.balance", CoinItem.formatCopper(WalletService.balance(player)))
					: Component.translatable("screen.trade_expansion.balance_bank", CoinItem.formatCopper(WalletService.balance(player)),
					CoinItem.formatCopper(account.bankBalance()));
			g.text(font, balance, imageWidth - 8 - font.width(balance), 6, COLOR_TEXT, false);
		}
		g.text(font, font.plainSubstrByWidth(status.getString(), imageWidth - 16), 8, LIST_Y + ROWS * ROW_H + 3,
				statusColor == COLOR_GREEN ? 0xFF207020 : statusColor == COLOR_RED ? 0xFFA02020 : COLOR_TEXT, false);
	}
}
