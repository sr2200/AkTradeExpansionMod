package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.StaffMath;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The staff tab of the trade screen (design Q, v0.48): the player's employees (all markets), hiring a carrier (good →
 * destination → settings) or a clerk (good → settings), and the selected employee's purse, goods and settings.
 * All changes are made by the server ({@link StaffRequestC2S}); the list is asked for again every few seconds.
 */
final class StaffTab {
	private static final int PANEL_W = 78;
	private static final int ROW_H = 18;
	private static final int LINE_H = 10;
	private static final int COLOR_TEXT = 0xFF404040;
	private static final int COLOR_DARK = 0xFF555555;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_GREEN = 0xFF55FF55;
	private static final int COLOR_GOLD = 0xFFFFD700;
	private static final int COLOR_ORANGE = 0xFFFFAA33;
	private static final int COLOR_WARNING = 0xFFA02020;
	private static final int COLOR_OK = 0xFF207020;
	private static final int COLOR_SELECTED = 0xFF6F6F9F;
	private static final int COLOR_HOVER = 0xFF9B9B9B;
	/** Ticks between list requests while the tab is open. */
	private static final int REFRESH_TICKS = 100;

	/** Roles and phases / statuses by ordinal (as {@code Employee}). */
	private static final int CARRIER = 0;
	private static final String[] STATUSES = {"working", "wait_price", "wait_stock", "wait_funds", "wait_sell", "not_dealt", "unpaid", "paused",
			"sold_out", "resting", "quit", "home_gone", "no_margin"};
	private static final int PHASE_HOME = 0;
	private static final int PHASE_OUTBOUND = 1;
	private static final int PHASE_AWAY = 2;
	private static final int PHASE_RETURNING = 3;
	private static final int PHASE_QUIT = 4;

	private enum Mode { LIST, PICK_GOODS, PICK_DESTINATION, SETUP }

	private final MarketScreen screen;
	private StaffS2C data;
	private Mode mode = Mode.LIST;
	/** Selected employee id on the list, or -1. */
	private int selected = -1;
	/** Hiring: carrier (true) or clerk; the good and the destination chosen so far. Editing: {@link #editing} &gt;= 0. */
	private boolean hiringCarrier;
	private Identifier goods;
	private StaffS2C.Destination destination;
	private int editing = -1;
	/** v0.48.3: the employee whose good / destination is being chosen again, or -1 (a new hire). */
	private int changing = -1;
	/** Destinations of {@link #destinationGoods} from the server ({@code null} while asked for). */
	private List<StaffS2C.Destination> destinations;
	private Identifier destinationGoods;
	/** v0.48.1: recommendations of every good here ({@code null} while asked for). */
	private List<StaffS2C.CarrierHint> carrierHints;
	private List<StaffS2C.ClerkHint> clerkHints;
	/** Setup values: units per trip (carrier) or left with the clerk, highest buy price, lowest sell price. */
	private int quantity;
	private long buyLimit;
	private long sellLimit;
	private int refresh;
	private boolean pending;

	private final List<Button> listButtons = new ArrayList<>();
	private Button hireCarrierButton;
	private Button hireClerkButton;
	private Button depositSmallButton;
	private Button depositLargeButton;
	private Button withdrawButton;
	private Button configureButton;
	private Button depositItemsButton;
	private Button withdrawItemsButton;
	private Button pauseButton;
	private Button changeButton;
	private Button dismissButton;
	private Button backButton;
	private Button confirmButton;
	/** Setup spinners: [row][0 = minus, 1 = plus]. */
	private final Button[][] spinners = new Button[3][2];

	StaffTab(MarketScreen screen) {
		this.screen = screen;
	}

	// ---- widgets ----

	void init(UnaryOperator<Button> add, int x, int y, int panelX) {
		listButtons.clear();
		int px = x + panelX;
		hireCarrierButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.hire.carrier"), b -> startHire(true))
				.bounds(px, y + 104, PANEL_W, 14).build());
		hireClerkButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.hire.clerk"), b -> startHire(false))
				.bounds(px, y + 121, PANEL_W, 14).build());
		depositSmallButton = add.apply(Button.builder(Component.literal("+100c"), b -> deposit(100)).bounds(px, y + 80, 38, 14).build());
		depositLargeButton = add.apply(Button.builder(Component.literal("+1,000c"), b -> deposit(1000)).bounds(px + 40, y + 80, 38, 14).build());
		withdrawButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.withdraw"),
				b -> send(StaffRequestC2S.Action.WITHDRAW_MONEY, 0)).bounds(px, y + 95, 38, 14).build());
		configureButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.configure"), b -> startEdit())
				.bounds(px + 40, y + 95, 38, 14).build());
		depositItemsButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.deposit_items"),
				b -> send(StaffRequestC2S.Action.DEPOSIT_ITEMS, 0)).bounds(px, y + 110, 38, 14).build());
		withdrawItemsButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.withdraw_items"),
				b -> send(StaffRequestC2S.Action.WITHDRAW_ITEMS, 0)).bounds(px + 40, y + 110, 38, 14).build());
		pauseButton = add.apply(Button.builder(Component.empty(), b -> send(StaffRequestC2S.Action.PAUSE, 0)).bounds(px, y + 125, 38, 14).build());
		changeButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.change"), b -> startChange())
				.bounds(px + 40, y + 125, 38, 14).build());
		dismissButton = add.apply(Button.builder(Component.empty(), b -> send(StaffRequestC2S.Action.DISMISS, 0)).bounds(px, y + 140, PANEL_W, 14).build());
		backButton = add.apply(Button.builder(Component.translatable("screen.ak_trade_expansion.staff.back"), b -> back())
				.bounds(px, y + 137, PANEL_W, 14).build());
		confirmButton = add.apply(Button.builder(Component.empty(), b -> confirm()).bounds(px, y + 121, PANEL_W, 14).build());
		for (int row = 0; row < spinners.length; row++) {
			int r = row;
			spinners[row][0] = add.apply(Button.builder(Component.literal("-"), b -> spin(r, -1)).bounds(px, y + 45 + row * 26, 14, 14).build());
			spinners[row][1] = add.apply(Button.builder(Component.literal("+"), b -> spin(r, 1)).bounds(px + 64, y + 45 + row * 26, 14, 14).build());
		}
		listButtons.addAll(List.of(depositSmallButton, depositLargeButton, withdrawButton, configureButton, depositItemsButton, withdrawItemsButton,
				pauseButton, changeButton, dismissButton));
	}

	void updateButtons(boolean shown) {
		if (confirmButton == null) return;
		StaffS2C.View view = shown && mode == Mode.LIST ? selectedView() : null;
		boolean list = shown && mode == Mode.LIST;
		boolean canHire = data != null && data.info().enabled() && hireBlocker() == null;
		hireCarrierButton.visible = list && view == null;
		hireClerkButton.visible = list && view == null;
		hireCarrierButton.active = canHire && !pending && data != null && !data.carrierGoods().isEmpty();
		hireClerkButton.active = canHire && !pending && data != null && !data.clerkGoods().isEmpty();
		if (data != null) {
			Component blocker = hireBlocker();
			hireCarrierButton.setTooltip(Tooltip.create(blocker != null ? blocker : Component.translatable("screen.ak_trade_expansion.staff.hire.carrier.tooltip",
					CoinItem.formatCopper(data.info().carrierFee()), percent(data.info().commission()))));
			hireClerkButton.setTooltip(Tooltip.create(blocker != null ? blocker : Component.translatable("screen.ak_trade_expansion.staff.hire.clerk.tooltip",
					CoinItem.formatCopper(data.info().clerkFee()), CoinItem.formatCopper(data.info().clerkWage()))));
		}
		for (Button b : listButtons) b.visible = false;
		if (view != null) {
			boolean quit = view.phase() == PHASE_QUIT;
			boolean home = view.atHome();
			boolean carrier = view.role() == CARRIER;
			boolean carrierHome = !carrier || view.phase() == PHASE_HOME || quit;
			depositSmallButton.visible = home && !quit;
			depositLargeButton.visible = home && !quit;
			withdrawButton.visible = home;
			configureButton.visible = home && !quit;
			depositItemsButton.visible = home && !quit && !carrier;
			withdrawItemsButton.visible = home && (!carrier || view.stock() > 0);
			pauseButton.visible = !quit;
			changeButton.visible = home && !quit;
			changeButton.active = !pending && carrierHome;
			changeButton.setTooltip(Tooltip.create(Component.translatable(carrierHome ? "screen.ak_trade_expansion.staff.change.tooltip"
					: "trade.ak_trade_expansion.result.staff_away")));
			dismissButton.visible = home;
			withdrawButton.active = !pending && view.purse() > 0;
			withdrawItemsButton.active = !pending && view.stock() > 0 && carrierHome;
			depositItemsButton.active = !pending && heldUnits(view.goods()) > 0;
			depositSmallButton.active = depositLargeButton.active = configureButton.active = pauseButton.active = !pending;
			dismissButton.active = !pending && carrierHome;
			boolean shift = shift();
			depositSmallButton.setMessage(Component.literal(shift ? "+1,000c" : "+100c"));
			depositLargeButton.setMessage(Component.literal(shift ? "+10,000c" : "+1,000c"));
			Tooltip depositTip = Tooltip.create(Component.translatable("screen.ak_trade_expansion.staff.deposit.tooltip"));
			depositSmallButton.setTooltip(depositTip);
			depositLargeButton.setTooltip(depositTip);
			withdrawButton.setTooltip(Tooltip.create(Component.translatable("screen.ak_trade_expansion.staff.withdraw.tooltip")));
			depositItemsButton.setTooltip(Tooltip.create(Component.translatable("screen.ak_trade_expansion.staff.deposit_items.tooltip",
					heldUnits(view.goods()) * view.unit())));
			withdrawItemsButton.setTooltip(Tooltip.create(Component.translatable(carrierHome ? "screen.ak_trade_expansion.staff.withdraw_items.tooltip"
					: "trade.ak_trade_expansion.result.staff_away")));
			pauseButton.setMessage(Component.translatable(view.paused() ? "screen.ak_trade_expansion.staff.resume" : "screen.ak_trade_expansion.staff.pause"));
			pauseButton.setTooltip(Tooltip.create(Component.translatable(carrier ? "screen.ak_trade_expansion.staff.pause.carrier.tooltip"
					: "screen.ak_trade_expansion.staff.pause.clerk.tooltip")));
			dismissButton.setMessage(Component.translatable(quit ? "screen.ak_trade_expansion.staff.settle" : "screen.ak_trade_expansion.staff.dismiss"));
			dismissButton.setTooltip(Tooltip.create(Component.translatable(!carrierHome ? "trade.ak_trade_expansion.result.staff_away"
					: quit ? "screen.ak_trade_expansion.staff.settle.tooltip" : "screen.ak_trade_expansion.staff.dismiss.tooltip")));
		}
		backButton.visible = shown && mode != Mode.LIST;
		backButton.active = true;
		boolean setup = shown && mode == Mode.SETUP;
		confirmButton.visible = setup;
		for (int row = 0; row < spinners.length; row++) {
			boolean visible = setup && spinnerShown(row);
			spinners[row][0].visible = visible;
			spinners[row][1].visible = visible;
		}
		if (setup && data != null) {
			confirmButton.setMessage(editing >= 0 || changing >= 0 ? Component.translatable("screen.ak_trade_expansion.staff.apply")
					: Component.translatable("screen.ak_trade_expansion.staff.confirm",
					CoinItem.formatCopper(hiringCarrier ? data.info().carrierFee() : data.info().clerkFee())));
			confirmButton.active = !pending && (hiringCarrier || editing >= 0
					|| (changing >= 0 ? quantity <= availableUnits(goods) : quantity <= heldUnits(goods) && quantity > 0));
		}
	}

	/** Why hiring is not possible here now, or {@code null}. */
	private Component hireBlocker() {
		if (data == null) return null;
		StaffS2C.Info info = data.info();
		if (!info.enabled()) return Component.translatable("screen.ak_trade_expansion.staff.disabled");
		long hired = data.staff().stream().filter(v -> v.phase() != PHASE_QUIT).count();
		if (hired >= info.maxPerPlayer()) return Component.translatable("trade.ak_trade_expansion.result.staff_limit");
		if (info.reputation() && info.level() < info.minLevel()) {
			return Component.translatable("screen.ak_trade_expansion.staff.reason.reputation", reputationName(info.minLevel()));
		}
		return null;
	}

	// ---- packets and actions ----

	void open() {
		mode = Mode.LIST;
		selected = -1;
		editing = -1;
		changing = -1;
		refresh = 0;
		requestList();
	}

	private void requestList() {
		ClientPlayNetworking.send(StaffRequestC2S.simple(screen.marketId(), StaffRequestC2S.Action.LIST, -1));
	}

	void tick(boolean shown) {
		if (!shown) return;
		if (++refresh >= REFRESH_TICKS) {
			refresh = 0;
			if (!pending) requestList();
		}
	}

	void onData(StaffS2C newData) {
		if (newData.marketId() != screen.marketId()) return;
		data = newData;
		if (newData.destinationGoods().isPresent()) {
			destinationGoods = newData.destinationGoods().get();
			destinations = newData.destinations();
		}
		if (newData.hints()) {
			carrierHints = newData.carrierHints();
			clerkHints = newData.clerkHints();
		}
		if (newData.action() >= 0) {
			pending = false;
			StaffRequestC2S.Action action = StaffRequestC2S.Action.values()[newData.action()];
			TradeResult result = newData.result().orElse(TradeResult.SUCCESS);
			if (result == TradeResult.SUCCESS) {
				String amount = CoinItem.formatCopper(newData.amount());
				screen.status(switch (action) {
					case HIRE_CARRIER, HIRE_CLERK -> Component.translatable("screen.ak_trade_expansion.staff.done.hired", amount);
					case DEPOSIT_MONEY -> Component.translatable("screen.ak_trade_expansion.staff.done.deposit", amount);
					case WITHDRAW_MONEY -> Component.translatable("screen.ak_trade_expansion.staff.done.withdraw", amount);
					case DEPOSIT_ITEMS -> Component.translatable("screen.ak_trade_expansion.staff.done.deposit_items", newData.amount());
					case WITHDRAW_ITEMS -> Component.translatable("screen.ak_trade_expansion.staff.done.withdraw_items", newData.amount());
					case DISMISS -> Component.translatable("screen.ak_trade_expansion.staff.done.dismiss", amount);
					case CONFIGURE -> Component.translatable("screen.ak_trade_expansion.staff.done.configure");
					case CHANGE -> Component.translatable("screen.ak_trade_expansion.staff.done.change", newData.amount());
					default -> Component.translatable("screen.ak_trade_expansion.staff.done.other");
				}, true);
				if (action == StaffRequestC2S.Action.HIRE_CARRIER || action == StaffRequestC2S.Action.HIRE_CLERK || action == StaffRequestC2S.Action.CONFIGURE
						|| action == StaffRequestC2S.Action.CHANGE) {
					mode = Mode.LIST;
					editing = -1;
					changing = -1;
					selected = newData.subject();
				}
				if (action == StaffRequestC2S.Action.DISMISS) selected = -1;
			} else {
				screen.status(Component.translatable(result.translationKey()), false);
			}
		}
		if (selectedView() == null) selected = -1;
		screen.refreshButtons();
	}

	private void send(StaffRequestC2S.Action action, long amount) {
		StaffS2C.View view = selectedView();
		if (view == null || pending) return;
		pending = true;
		ClientPlayNetworking.send(new StaffRequestC2S(screen.marketId(), action, view.id(), view.goods(), view.destination(), view.quantity(),
				view.buyLimit(), view.sellLimit(), amount, screen.payingByCard()));
		screen.refreshButtons();
	}

	private void deposit(long amount) {
		send(StaffRequestC2S.Action.DEPOSIT_MONEY, shift() ? amount * 10 : amount);
	}

	private void startHire(boolean carrier) {
		hiringCarrier = carrier;
		editing = -1;
		changing = -1;
		goods = null;
		destination = null;
		mode = Mode.PICK_GOODS;
		requestHints();
		screen.resetScroll();
		screen.refreshButtons();
	}

	/** v0.48.1: asks for the recommendations of every good (they change with the prices). */
	private void requestHints() {
		carrierHints = null;
		clerkHints = null;
		ClientPlayNetworking.send(StaffRequestC2S.simple(screen.marketId(), StaffRequestC2S.Action.GOODS, -1));
	}

	private void requestDestinations(Identifier goodsId) {
		destinations = null;
		destinationGoods = null;
		ClientPlayNetworking.send(new StaffRequestC2S(screen.marketId(), StaffRequestC2S.Action.DESTINATIONS, -1, goodsId, -1, 0, 0, 0, 0, false));
	}

	private StaffS2C.CarrierHint carrierHint(Identifier goodsId) {
		if (carrierHints == null) return null;
		for (StaffS2C.CarrierHint h : carrierHints) if (h.goods().equals(goodsId)) return h;
		return null;
	}

	private StaffS2C.ClerkHint clerkHint(Identifier goodsId) {
		if (clerkHints == null) return null;
		for (StaffS2C.ClerkHint h : clerkHints) if (h.goods().equals(goodsId)) return h;
		return null;
	}

	/** Destinations, the most profitable first (then the nearest). */
	private List<StaffS2C.Destination> sortedDestinations() {
		if (destinations == null || !goodsMatch()) return List.of();
		List<StaffS2C.Destination> list = new ArrayList<>(destinations);
		list.sort(java.util.Comparator.comparingLong((StaffS2C.Destination d) -> -d.profit()).thenComparingInt(StaffS2C.Destination::distance));
		return list;
	}

	/** The destination entry of the carrier being set up (a new hire's choice, or the edited carrier's destination). */
	private StaffS2C.Destination setupDestination() {
		if (destination != null) return destination;
		StaffS2C.View edited = editedView();
		if (edited == null || destinations == null || !goodsMatch()) return null;
		for (StaffS2C.Destination d : destinations) if (d.id() == edited.destination()) return d;
		return null;
	}

	private StaffS2C.View editedView() {
		if (editing < 0 || data == null) return null;
		for (StaffS2C.View v : data.staff()) if (v.id() == editing) return v;
		return null;
	}

	/** v0.48.3: chooses the selected employee's good (and destination) again, like hiring. */
	private void startChange() {
		StaffS2C.View view = selectedView();
		if (view == null) return;
		changing = view.id();
		editing = -1;
		hiringCarrier = view.role() == CARRIER;
		goods = null;
		destination = null;
		mode = Mode.PICK_GOODS;
		requestHints();
		screen.resetScroll();
		screen.refreshButtons();
	}

	private StaffS2C.View changedView() {
		if (changing < 0 || data == null) return null;
		for (StaffS2C.View v : data.staff()) if (v.id() == changing) return v;
		return null;
	}

	/** Units of the new good a clerk could be given: what the player carries, plus its present goods when they are the same. */
	private int availableUnits(Identifier goodsId) {
		int held = heldUnits(goodsId);
		StaffS2C.View old = changedView();
		if (old != null && goodsId != null && goodsId.equals(old.goods())) held += old.stock();
		return held;
	}

	private void startEdit() {
		StaffS2C.View view = selectedView();
		if (view == null) return;
		editing = view.id();
		hiringCarrier = view.role() == CARRIER;
		goods = view.goods();
		destination = null;
		quantity = view.quantity();
		buyLimit = view.buyLimit();
		sellLimit = view.sellLimit();
		mode = Mode.SETUP;
		// v0.48.1: today's recommendation for comparison
		if (hiringCarrier) requestDestinations(goods);
		else requestHints();
		screen.refreshButtons();
	}

	private void back() {
		mode = switch (mode) {
			case SETUP -> editing >= 0 ? Mode.LIST : hiringCarrier ? Mode.PICK_DESTINATION : Mode.PICK_GOODS;
			case PICK_DESTINATION -> Mode.PICK_GOODS;
			default -> Mode.LIST;
		};
		if (mode == Mode.LIST) {
			editing = -1;
			changing = -1;
		}
		screen.resetScroll();
		screen.refreshButtons();
	}

	private void confirm() {
		if (pending || data == null) return;
		StaffRequestC2S request;
		if (changing >= 0) {
			if (goods == null || hiringCarrier && destination == null) return;
			request = new StaffRequestC2S(screen.marketId(), StaffRequestC2S.Action.CHANGE, changing, goods, hiringCarrier ? destination.id() : -1, quantity,
					buyLimit, sellLimit, 0, screen.payingByCard());
		} else if (editing >= 0) {
			StaffS2C.View view = data.staff().stream().filter(v -> v.id() == editing).findFirst().orElse(null);
			if (view == null) return;
			request = new StaffRequestC2S(screen.marketId(), StaffRequestC2S.Action.CONFIGURE, editing, view.goods(), view.destination(), quantity,
					buyLimit, sellLimit, 0, screen.payingByCard());
		} else if (hiringCarrier) {
			if (goods == null || destination == null) return;
			request = new StaffRequestC2S(screen.marketId(), StaffRequestC2S.Action.HIRE_CARRIER, -1, goods, destination.id(), quantity, buyLimit,
					sellLimit, 0, screen.payingByCard());
		} else {
			if (goods == null) return;
			request = new StaffRequestC2S(screen.marketId(), StaffRequestC2S.Action.HIRE_CLERK, -1, goods, -1, quantity, 0, sellLimit, 0,
					screen.payingByCard());
		}
		pending = true;
		ClientPlayNetworking.send(request);
		screen.refreshButtons();
	}

	/** Setup rows: 0 = units (carrier per trip / clerk to leave, not when editing a clerk), 1 = buy limit (carrier), 2 = sell limit. */
	private boolean spinnerShown(int row) {
		return switch (row) {
			case 0 -> hiringCarrier || editing < 0;
			case 1 -> hiringCarrier && !auto();
			default -> !auto();
		};
	}

	/** The setup row's on-screen position (rows that are not shown leave no gap). */
	private int spinnerSlot(int row) {
		int slot = 0;
		for (int r = 0; r < row; r++) if (spinnerShown(r)) slot++;
		return slot;
	}

	private void spin(int row, int direction) {
		boolean large = shift();
		switch (row) {
			case 0 -> {
				int min = !hiringCarrier && changing >= 0 ? 0 : 1;
				int held = changing >= 0 ? availableUnits(goods) : heldUnits(goods);
				int max = hiringCarrier ? data.info().carrierCapacity() : Math.min(data.info().clerkCapacity(), Math.max(min, held));
				int step = large ? 10 : 1;
				quantity = Math.clamp(quantity + direction * step, min, Math.max(min, max));
			}
			case 1 -> buyLimit = Math.max(1, buyLimit + direction * StaffMath.priceStep(buyLimit, large));
			default -> sellLimit = Math.max(1, sellLimit + direction * StaffMath.priceStep(sellLimit, large));
		}
		screen.refreshButtons();
	}

	// ---- list ----

	int rowCount() {
		return switch (mode) {
			case LIST -> data == null ? 0 : data.staff().size();
			case PICK_GOODS -> pickableGoods().size() + autoRows();
			case PICK_DESTINATION -> sortedDestinations().size();
			case SETUP -> 0;
		};
	}

	/** The list scrolls on every mode but the setup (a page of text). */
	boolean scrollable() {
		return mode != Mode.SETUP;
	}

	private boolean goodsMatch() {
		return goods != null && goods.equals(destinationGoods);
	}

	void clickRow(int index) {
		switch (mode) {
			case LIST -> {
				if (data != null && index < data.staff().size()) {
					int id = data.staff().get(index).id();
					selected = id == selected ? -1 : id;
				}
			}
			case PICK_GOODS -> {
				if (hiringCarrier && index == 0) {
					goods = com.akf.tradeexpansion.staff.Employee.AUTO; // v0.48.1: おまかせ
					mode = Mode.PICK_DESTINATION;
					requestDestinations(goods);
					screen.resetScroll();
					screen.refreshButtons();
					return;
				}
				index -= autoRows();
				List<GoodView> list = pickableGoods();
				if (index >= list.size()) return;
				GoodView view = list.get(index);
				goods = view.goods();
				if (hiringCarrier) {
					mode = Mode.PICK_DESTINATION;
					requestDestinations(goods);
				} else {
					int available = changing >= 0 ? availableUnits(goods) : heldUnits(goods);
					quantity = Math.max(changing >= 0 ? 0 : 1, Math.min(data.info().clerkCapacity(), available));
					// v0.48.1: the recommended lowest price (85% of the sell price now)
					StaffS2C.ClerkHint hint = clerkHint(goods);
					sellLimit = hint != null ? hint.limit() : Math.max(1, (long) Math.floor(sellPriceHere(view) * 0.85));
					mode = Mode.SETUP;
				}
				screen.resetScroll();
			}
			case PICK_DESTINATION -> {
				List<StaffS2C.Destination> list = sortedDestinations();
				if (index >= list.size()) return;
				destination = list.get(index);
				// v0.48.1: the recommendation of this trip (the carrier still buys only what should sell for more)
				quantity = data.info().carrierCapacity();
				if (auto()) {
					buyLimit = 1;
					sellLimit = 1;
					mode = Mode.SETUP;
					screen.resetScroll();
					break;
				}
				buyLimit = destination.units() > 0 ? Math.max(1, (long) Math.ceil(destination.buyLimit() * 1.05)) : Math.max(1, destination.buyPrice());
				sellLimit = Math.max(1, destination.sellLimit());
				mode = Mode.SETUP;
				screen.resetScroll();
			}
			default -> {
			}
		}
		screen.refreshButtons();
	}

	private List<GoodView> pickableGoods() {
		List<GoodView> list = new ArrayList<>();
		MarketSnapshotS2C snapshot = screen.snapshot();
		if (snapshot == null || data == null) return list;
		List<Identifier> allowed = hiringCarrier ? data.carrierGoods() : data.clerkGoods();
		for (GoodView view : snapshot.goods()) {
			if (allowed.contains(view.goods())) list.add(view);
		}
		// v0.48.1: the most promising first (carrier: expected profit; clerk: goods you carry, then the sales of a day)
		if (hiringCarrier && carrierHints != null) {
			list.sort(java.util.Comparator.comparingLong(v -> {
				StaffS2C.CarrierHint h = carrierHint(v.goods());
				return h == null ? 0 : -h.profit();
			}));
		} else if (!hiringCarrier && clerkHints != null) {
			list.sort(java.util.Comparator.comparing((GoodView v) -> heldUnits(v.goods()) <= 0).thenComparingLong(v -> {
				StaffS2C.ClerkHint h = clerkHint(v.goods());
				return h == null ? 0 : -h.revenue();
			}));
		}
		return list;
	}

	private GoodView goodView(Identifier goodsId) {
		MarketSnapshotS2C snapshot = screen.snapshot();
		if (snapshot == null || goodsId == null) return null;
		for (GoodView view : snapshot.goods()) if (view.goods().equals(goodsId)) return view;
		return null;
	}

	private long buyPriceHere(GoodView view) {
		return PriceModel.buyPrice(view.params(), screen.currentState(view).pressure());
	}

	private long sellPriceHere(GoodView view) {
		return PriceModel.sellPrice(view.params(), screen.currentState(view).pressure());
	}

	/** Plain items of the good the player carries (inventory and sell slot), in trading units. */
	private int heldUnits(Identifier goodsId) {
		GoodView view = goodView(goodsId);
		Player player = Minecraft.getInstance().player;
		if (view == null || player == null) return 0;
		InventoryUtil.Match plain = new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(view.item()), view.patch(), false);
		return PriceModel.units(InventoryUtil.count(player, plain, screen.sellSlot()), view.unit());
	}

	private StaffS2C.View selectedView() {
		if (data == null || selected < 0) return null;
		for (StaffS2C.View v : data.staff()) if (v.id() == selected) return v;
		return null;
	}

	/** v0.48.1: the carrier being set up chooses its goods itself (おまかせ). */
	private boolean auto() {
		return hiringCarrier && com.akf.tradeexpansion.staff.Employee.AUTO.equals(goods);
	}

	/** Pick-goods rows: a carrier's first row is おまかせ. */
	private int autoRows() {
		return hiringCarrier ? 1 : 0;
	}

	private static Component goodsName(StaffS2C.View v, ItemStack stack) {
		return v.auto() ? Component.translatable("staff.ak_trade_expansion.auto") : stack.getHoverName();
	}

	private static boolean shift() {
		return Minecraft.getInstance().hasShiftDown();
	}

	// ---- drawing ----

	void extract(GuiGraphicsExtractor g, int lx, int ly, int listW, int rows, int scroll, int px, int y, int mouseX, int mouseY) {
		Font font = screen.screenFont();
		if (data == null) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.loading"), lx + 4, ly + 4, COLOR_WHITE);
			return;
		}
		switch (mode) {
			case LIST -> {
				extractStaffList(g, font, lx, ly, listW, rows, scroll, mouseX, mouseY);
				StaffS2C.View view = selectedView();
				if (view == null) extractHirePanel(g, font, px, y);
				else extractEmployeePanel(g, font, view, px, y);
			}
			case PICK_GOODS -> {
				extractGoodsList(g, font, lx, ly, listW, rows, scroll, mouseX, mouseY);
				g.textWithWordWrap(font, Component.translatable(hiringCarrier ? "screen.ak_trade_expansion.staff.pick_goods.carrier"
						: "screen.ak_trade_expansion.staff.pick_goods.clerk"), px, y + 38, PANEL_W, COLOR_TEXT, false);
			}
			case PICK_DESTINATION -> {
				extractDestinations(g, font, lx, ly, listW, rows, scroll, mouseX, mouseY);
				extractGoodsHeader(g, font, px, y);
				g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.staff.pick_destination"), px, y + 56, PANEL_W, COLOR_TEXT, false);
			}
			case SETUP -> {
				extractSummary(g, font, lx, ly, listW, rows);
				extractSetupPanel(g, font, px, y);
			}
		}
	}

	private void extractStaffList(GuiGraphicsExtractor g, Font font, int lx, int ly, int listW, int rows, int scroll, int mouseX, int mouseY) {
		List<StaffS2C.View> staff = data.staff();
		if (staff.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.staff.none"), lx + 4, ly + 4, listW - 12, COLOR_WHITE, false);
			return;
		}
		StaffS2C.View hovered = null;
		for (int row = 0; row < rows && scroll + row < staff.size(); row++) {
			StaffS2C.View v = staff.get(scroll + row);
			int ry = ly + row * ROW_H;
			boolean over = mouseX >= lx && mouseX < lx + listW - 6 && mouseY >= ry && mouseY < ry + ROW_H;
			if (v.id() == selected) g.fill(lx, ry, lx + listW, ry + ROW_H, COLOR_SELECTED);
			else if (over) g.fill(lx, ry, lx + listW, ry + ROW_H, COLOR_HOVER);
			if (over) hovered = v;
			ItemStack stack = stack(v.item());
			g.item(stack, lx + 2, ry + 1);
			StatusText status = statusText(v);
			int statusWidth = font.width(status.text());
			g.text(font, status.text(), lx + listW - 9 - statusWidth, ry + 5, status.color());
			String label = role(v.role()).getString() + "：" + goodsName(v, stack).getString()
					+ (v.role() == CARRIER ? " → " + v.destinationName().getString() : "");
			if (!v.atHome()) label += " ＠" + v.homeName().getString();
			screen.fit(g, label, lx + 20, ry + 5, listW - 34 - statusWidth, v.phase() == PHASE_QUIT ? COLOR_GRAY : COLOR_WHITE, true);
		}
		if (hovered != null) g.setTooltipForNextFrame(screen.screenFont(), tooltip(hovered), Optional.empty(), mouseX, mouseY);
	}

	private record StatusText(String text, int color) {
	}

	/** Short status of a row: where the employee is or what it waits for, with the minutes left when travelling. */
	private StatusText statusText(StaffS2C.View v) {
		String status = STATUSES[Math.clamp(v.status(), 0, STATUSES.length - 1)];
		String key = "screen.ak_trade_expansion.staff.status." + status;
		int minutes = minutesUntil(v.nextTime());
		return switch (status) {
			case "quit", "home_gone", "unpaid", "wait_funds", "not_dealt" -> new StatusText(Component.translatable(key).getString(), COLOR_RED);
			case "paused", "sold_out" -> new StatusText(Component.translatable(key).getString(), COLOR_GRAY);
			case "resting" -> new StatusText(Component.translatable(key, minutes).getString(), COLOR_ORANGE);
			case "working" -> switch (v.phase()) {
				case PHASE_OUTBOUND -> new StatusText(Component.translatable("screen.ak_trade_expansion.staff.phase.outbound", minutes).getString(), COLOR_GOLD);
				case PHASE_RETURNING -> new StatusText(Component.translatable("screen.ak_trade_expansion.staff.phase.returning", minutes).getString(), COLOR_GOLD);
				case PHASE_AWAY -> new StatusText(Component.translatable("screen.ak_trade_expansion.staff.phase.away").getString(), COLOR_GREEN);
				default -> new StatusText(Component.translatable(v.role() == CARRIER ? "screen.ak_trade_expansion.staff.phase.home.carrier"
						: "screen.ak_trade_expansion.staff.phase.home.clerk").getString(), COLOR_GREEN);
			};
			default -> new StatusText(Component.translatable(key).getString(), COLOR_ORANGE);
		};
	}

	private List<Component> tooltip(StaffS2C.View v) {
		List<Component> lines = new ArrayList<>();
		ItemStack stack = stack(v.item());
		lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.title", role(v.role()), goodsName(v, stack)));
		String status = STATUSES[Math.clamp(v.status(), 0, STATUSES.length - 1)];
		lines.add(Component.translatable("screen.ak_trade_expansion.staff.hint." + status).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.home", v.homeName()));
		if (v.role() == CARRIER) {
			lines.add(v.auto()
					? Component.translatable("screen.ak_trade_expansion.staff.tooltip.route.auto", v.destinationName(), v.distance(), v.quantity(), v.kinds())
					: Component.translatable("screen.ak_trade_expansion.staff.tooltip.route", v.destinationName(), v.distance(),
					v.quantity() * v.unit(), CoinItem.formatCopper(v.buyLimit()), CoinItem.formatCopper(v.sellLimit())));
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.carrier_totals", v.trips(), CoinItem.formatCopper(v.earned()),
					CoinItem.formatCopper(v.spent()), CoinItem.formatCopper(v.lost())));
		} else {
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.clerk", CoinItem.formatCopper(v.sellLimit()), v.sold() * v.unit(),
					CoinItem.formatCopper(v.earned())));
		}
		lines.add(v.role() == CARRIER && v.wage() <= 0
				? Component.translatable("screen.ak_trade_expansion.staff.tooltip.commission", percent(data.info().commission()), CoinItem.formatCopper(v.wages()))
				: Component.translatable("screen.ak_trade_expansion.staff.tooltip.wages", CoinItem.formatCopper(v.wage()), CoinItem.formatCopper(v.wages())));
		long profit = v.earned() - v.spent() - v.wages();
		lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.profit", (profit < 0 ? "-" : "") + CoinItem.formatCopper(Math.abs(profit)))
				.withStyle(profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		if (!v.log().isEmpty()) {
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.tooltip.log").withStyle(ChatFormatting.GOLD));
			for (int i = 0; i < Math.min(6, v.log().size()); i++) lines.add(Component.literal("・").append(v.log().get(i)).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	private void extractHirePanel(GuiGraphicsExtractor g, Font font, int px, int y) {
		StaffS2C.Info info = data.info();
		long hired = data.staff().stream().filter(v -> v.phase() != PHASE_QUIT).count();
		screen.fit(g, Component.translatable("screen.ak_trade_expansion.staff.count", hired, info.maxPerPlayer()).getString(), px, y + 38, PANEL_W,
				COLOR_TEXT, false);
		Component blocker = hireBlocker();
		Component text = blocker != null ? blocker : Component.translatable("screen.ak_trade_expansion.staff.hire_hint");
		// Up to the hire buttons (y + 104).
		wrapped(g, font, text, px, y + 50, PANEL_W, 52, blocker != null ? COLOR_WARNING : COLOR_DARK);
	}

	/**
	 * Draws {@code text} wrapped within {@code width} × {@code height}: shrunk (down to 60%) when the lines do not fit,
	 * and only then cut with "…".
	 */
	private static void wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int height, int color) {
		float scale = 1.0f;
		List<FormattedCharSequence> lines = font.split(text, width);
		while (lines.size() * font.lineHeight * scale > height && scale > 0.6f) {
			scale -= 0.05f;
			lines = font.split(text, (int) (width / scale));
		}
		int maxLines = Math.max(1, (int) (height / (font.lineHeight * scale)));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			FormattedCharSequence line = lines.get(i);
			if (i == maxLines - 1 && lines.size() > maxLines) {
				StringBuilder sb = new StringBuilder();
				line.accept((index, style, codePoint) -> {
					sb.appendCodePoint(codePoint);
					return true;
				});
				String cut = font.plainSubstrByWidth(sb.toString(), (int) (width / scale) - font.width("…")) + "…";
				g.text(font, cut, 0, i * font.lineHeight, color, false);
			} else {
				g.text(font, line, 0, i * font.lineHeight, color, false);
			}
		}
		g.pose().popMatrix();
	}

	private void extractEmployeePanel(GuiGraphicsExtractor g, Font font, StaffS2C.View v, int px, int y) {
		ItemStack stack = stack(v.item());
		g.item(stack, px, y + 34);
		screen.fit(g, role(v.role()).getString(), px + 18, y + 34, PANEL_W - 18, COLOR_TEXT, false);
		screen.fit(g, goodsName(v, stack).getString(), px + 18, y + 43, PANEL_W - 18, COLOR_DARK, false);
		StatusText status = statusText(v);
		screen.fit(g, status.text(), px, y + 53, PANEL_W, status.color() == COLOR_RED ? COLOR_WARNING : status.color() == COLOR_GREEN ? COLOR_OK
				: status.color() == COLOR_GRAY ? COLOR_DARK : 0xFF906010, false);
		String stock = Component.translatable(v.role() == CARRIER ? "screen.ak_trade_expansion.staff.cargo" : "screen.ak_trade_expansion.staff.stock",
				v.stock() * v.unit()).getString();
		// The purse and the goods on lines of their own (one line was too narrow for both).
		screen.fit(g, Component.translatable("screen.ak_trade_expansion.staff.purse", CoinItem.formatCopper(v.purse())).getString(), px, y + 62, PANEL_W,
				COLOR_TEXT, false);
		screen.fit(g, stock, px, y + 71, PANEL_W, COLOR_TEXT, false);
		if (!v.atHome() && v.phase() != PHASE_QUIT) {
			// Up to the pause button (y + 125).
			wrapped(g, font, Component.translatable("screen.ak_trade_expansion.staff.not_home", v.homeName()), px, y + 82, PANEL_W, 42, COLOR_DARK);
		} else if (!v.atHome()) {
			// No buttons below: room down to the bottom of the panel.
			wrapped(g, font, Component.translatable("screen.ak_trade_expansion.staff.quit_not_home", v.homeName()), px, y + 82, PANEL_W, 72, COLOR_WARNING);
		}
		// Quit at home: the money buttons take the space; "Settle" explains itself in its tooltip.
	}

	private void extractGoodsHeader(GuiGraphicsExtractor g, Font font, int px, int y) {
		GoodView view = goodView(goods);
		if (view == null) return;
		ItemStack stack = view.displayStack();
		g.item(stack, px, y + 36);
		screen.fit(g, stack.getHoverName().getString(), px + 18, y + 40, PANEL_W - 18, COLOR_TEXT, false);
	}

	private void extractGoodsList(GuiGraphicsExtractor g, Font font, int lx, int ly, int listW, int rows, int scroll, int mouseX, int mouseY) {
		List<GoodView> list = pickableGoods();
		if (list.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.staff.no_goods"), lx + 4, ly + 4, listW - 12, COLOR_WHITE, false);
			return;
		}
		int extra = autoRows();
		for (int row = 0; row < rows && scroll + row < list.size() + extra; row++) {
			int ry = ly + row * ROW_H;
			if (scroll + row < extra) {
				extractAutoRow(g, font, lx, ry, listW, mouseX, mouseY);
				continue;
			}
			GoodView view = list.get(scroll + row - extra);
			boolean over = mouseX >= lx && mouseX < lx + listW - 6 && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) g.fill(lx, ry, lx + listW, ry + ROW_H, COLOR_HOVER);
			ItemStack stack = view.displayStack();
			g.item(stack, lx + 2, ry + 1);
			String right;
			int color = COLOR_GOLD;
			List<Component> tip = new ArrayList<>();
			if (hiringCarrier) {
				StaffS2C.CarrierHint h = carrierHint(view.goods());
				if (h == null) {
					right = carrierHints == null ? "…" : Component.translatable("screen.ak_trade_expansion.staff.hint.none").getString();
					color = COLOR_GRAY;
				} else if (h.units() > 0 && h.profit() > 0) {
					right = Component.translatable("screen.ak_trade_expansion.staff.hint.profit", CoinItem.formatCopper(h.profit())).getString();
					color = COLOR_GREEN;
					tip.add(Component.translatable("screen.ak_trade_expansion.staff.hint.carrier", h.destinationName(), h.units() * view.unit(),
							CoinItem.formatCopper(h.profit())));
				} else {
					right = Component.translatable("screen.ak_trade_expansion.staff.hint.none").getString();
					color = COLOR_GRAY;
					tip.add(Component.translatable("screen.ak_trade_expansion.staff.hint.carrier.none"));
				}
			} else {
				StaffS2C.ClerkHint h = clerkHint(view.goods());
				int held = heldUnits(view.goods()) * view.unit();
				right = h == null ? Component.translatable("screen.ak_trade_expansion.staff.price.sell_held", CoinItem.formatCopper(sellPriceHere(view)), held).getString()
						: Component.translatable("screen.ak_trade_expansion.staff.hint.clerk.row", CoinItem.formatCopper(h.revenue()), held).getString();
				color = held > 0 ? COLOR_GOLD : COLOR_GRAY;
				if (h != null) {
					tip.add(Component.translatable("screen.ak_trade_expansion.staff.hint.clerk", CoinItem.formatCopper(h.limit()), h.items(),
							CoinItem.formatCopper(h.revenue())));
				}
			}
			int rw = font.width(right);
			g.text(font, right, lx + listW - 9 - rw, ry + 5, color);
			screen.fit(g, stack.getHoverName().getString(), lx + 20, ry + 5, listW - 34 - rw, COLOR_WHITE, true);
			if (over && mouseX < lx + 18) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			else if (over && !tip.isEmpty()) {
				tip.addFirst(stack.getHoverName());
				g.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	/** v0.48.1: the おまかせ row of a carrier's goods list (chest icon, the best expected profit of all goods together). */
	private void extractAutoRow(GuiGraphicsExtractor g, Font font, int lx, int ry, int listW, int mouseX, int mouseY) {
		boolean over = mouseX >= lx && mouseX < lx + listW - 6 && mouseY >= ry && mouseY < ry + ROW_H;
		g.fill(lx, ry, lx + listW, ry + ROW_H, over ? COLOR_HOVER : 0xFF7A8A6A);
		g.item(new ItemStack(net.minecraft.world.item.Items.CHEST), lx + 2, ry + 1);
		StaffS2C.CarrierHint h = carrierHint(com.akf.tradeexpansion.staff.Employee.AUTO);
		String right;
		int color;
		if (h == null) {
			right = carrierHints == null ? "…" : Component.translatable("screen.ak_trade_expansion.staff.hint.none").getString();
			color = COLOR_GRAY;
		} else if (h.profit() > 0) {
			right = Component.translatable("screen.ak_trade_expansion.staff.hint.profit", CoinItem.formatCopper(h.profit())).getString();
			color = COLOR_GREEN;
		} else {
			right = Component.translatable("screen.ak_trade_expansion.staff.hint.none").getString();
			color = COLOR_GRAY;
		}
		int rw = font.width(right);
		g.text(font, right, lx + listW - 9 - rw, ry + 5, color);
		screen.fit(g, Component.translatable("screen.ak_trade_expansion.staff.auto.row").getString(), lx + 20, ry + 5, listW - 34 - rw, COLOR_GOLD, true);
		if (over) {
			List<Component> tip = new ArrayList<>();
			tip.add(Component.translatable("screen.ak_trade_expansion.staff.auto.row"));
			tip.add(Component.translatable("screen.ak_trade_expansion.staff.auto.tooltip").withStyle(ChatFormatting.GRAY));
			if (h != null && h.profit() > 0) {
				tip.add(Component.translatable("screen.ak_trade_expansion.staff.hint.auto", h.destinationName(), h.units(), CoinItem.formatCopper(h.profit()))
						.withColor(COLOR_GREEN));
			} else if (h != null) {
				tip.add(Component.translatable("screen.ak_trade_expansion.staff.hint.carrier.none").withStyle(ChatFormatting.GRAY));
			}
			g.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
		}
	}

	private void extractDestinations(GuiGraphicsExtractor g, Font font, int lx, int ly, int listW, int rows, int scroll, int mouseX, int mouseY) {
		if (destinations == null || !goodsMatch()) {
			g.text(font, Component.translatable("screen.ak_trade_expansion.loading"), lx + 4, ly + 4, COLOR_WHITE);
			return;
		}
		List<StaffS2C.Destination> sorted = sortedDestinations();
		if (sorted.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.ak_trade_expansion.staff.no_destinations", (long) data.info().maxDistance()),
					lx + 4, ly + 4, listW - 12, COLOR_WHITE, false);
			return;
		}
		GoodView here = goodView(goods);
		int unit = unit(here);
		for (int row = 0; row < rows && scroll + row < sorted.size(); row++) {
			StaffS2C.Destination d = sorted.get(scroll + row);
			int ry = ly + row * ROW_H;
			boolean over = mouseX >= lx && mouseX < lx + listW - 6 && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) g.fill(lx, ry, lx + listW, ry + ROW_H, COLOR_HOVER);
			String price;
			int color;
			if (d.sellPrice() < 0) {
				price = Component.translatable("screen.ak_trade_expansion.not_buying").getString();
				color = COLOR_RED;
			} else if (d.units() > 0 && d.profit() > 0) {
				price = Component.translatable("screen.ak_trade_expansion.staff.hint.profit", CoinItem.formatCopper(d.profit())).getString();
				color = COLOR_GREEN;
			} else {
				price = Component.translatable("screen.ak_trade_expansion.staff.hint.none").getString();
				color = COLOR_GRAY;
			}
			int pw = font.width(price);
			g.text(font, price, lx + listW - 9 - pw, ry + 5, color);
			String where = Directions.of(d.dx(), d.dz()) + " " + d.distance() + "m";
			int ww = font.width(where);
			g.text(font, where, lx + listW - 15 - pw - ww, ry + 5, COLOR_GRAY);
			screen.fit(g, d.name().getString(), lx + 4, ry + 5, listW - 24 - pw - ww, COLOR_WHITE, true);
			if (over) {
				List<Component> lines = new ArrayList<>();
				lines.add(d.name());
				if (auto()) {
					lines.add(autoLine(d));
				} else {
					lines.add(Component.translatable("screen.ak_trade_expansion.staff.destination.tooltip", CoinItem.formatCopper(Math.max(0, d.buyPrice())),
							d.sellPrice() < 0 ? Component.translatable("screen.ak_trade_expansion.not_buying").getString() : CoinItem.formatCopper(d.sellPrice()))
							.withStyle(ChatFormatting.GRAY));
					lines.add(recommendLine(d, unit));
				}
				lines.add(travelLine(d.distance()).withStyle(ChatFormatting.GRAY));
				g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	/** "片道 約 6分・事故の確率 7%". */
	private net.minecraft.network.chat.MutableComponent travelLine(int distance) {
		StaffS2C.Info info = data.info();
		long ticks = StaffMath.travelTicks(distance, info.blocksPerDay());
		double chance = StaffMath.accidentChance(distance, info.accidentBase(), info.accidentPerThousand(), info.accidentMax());
		return Component.translatable("screen.ak_trade_expansion.staff.travel", minutes(ticks), percent(chance));
	}

	/** The setup page: what the employee will do and what it costs. */
	private void extractSummary(GuiGraphicsExtractor g, Font font, int lx, int ly, int listW, int rows) {
		List<Component> lines = new ArrayList<>();
		StaffS2C.Info info = data.info();
		GoodView view = goodView(goods);
		Component item = view == null ? Component.literal(String.valueOf(goods)) : view.displayStack().getHoverName();
		StaffS2C.View edited = editing < 0 ? null : data.staff().stream().filter(v -> v.id() == editing).findFirst().orElse(null);
		if (hiringCarrier) {
			Component to = destination != null ? destination.name() : edited != null ? edited.destinationName() : Component.empty();
			int distance = destination != null ? destination.distance() : edited != null ? edited.distance() : 0;
			StaffS2C.Destination d = setupDestination();
			if (auto()) {
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.auto", to).withColor(COLOR_GOLD));
				lines.add(travelLine(distance));
				if (d != null) lines.add(autoLine(d));
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.auto.how", quantity));
			} else {
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.carrier", item, to).withColor(COLOR_GOLD));
			lines.add(travelLine(distance));
			if (d != null) {
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.prices", CoinItem.formatCopper(Math.max(0, d.buyPrice())),
						d.sellPrice() < 0 ? Component.translatable("screen.ak_trade_expansion.not_buying").getString() : CoinItem.formatCopper(d.sellPrice())));
				lines.add(recommendLine(d, unit(view)));
			}
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.carrier.how", quantity * unit(view), CoinItem.formatCopper(buyLimit),
					CoinItem.formatCopper(sellLimit)));
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.carrier.auto"));
			}
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.carrier.wait", minutes(Math.round(info.maxWaitDays() * PriceModel.TICKS_PER_DAY))));
			lines.add(info.quitChance() > 0 ? Component.translatable("screen.ak_trade_expansion.staff.summary.accident.quit", percent(info.quitChance()))
					: Component.translatable("screen.ak_trade_expansion.staff.summary.accident"));
		} else {
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.clerk", item).withColor(COLOR_GOLD));
			if (view != null) {
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.clerk.price", CoinItem.formatCopper(sellPriceHere(view))));
			}
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.clerk.how", CoinItem.formatCopper(sellLimit)));
			StaffS2C.ClerkHint hint = clerkHint(goods);
			if (hint != null) {
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.hint.clerk", CoinItem.formatCopper(hint.limit()), hint.items(),
						CoinItem.formatCopper(hint.revenue())).withColor(COLOR_GREEN));
			}
			if (editing < 0) lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.clerk.held", heldUnits(goods) * unit(view)));
		}
		StaffS2C.View old = changedView();
		if (old != null && old.stock() > 0) {
			lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.change", old.stock() * (old.auto() ? 1 : old.unit())).withColor(COLOR_GOLD));
		}
		if (editing < 0 && changing < 0) {
			lines.add(hiringCarrier
					? Component.translatable("screen.ak_trade_expansion.staff.summary.cost.carrier", CoinItem.formatCopper(info.carrierFee()), percent(info.commission()))
					: Component.translatable("screen.ak_trade_expansion.staff.summary.cost", CoinItem.formatCopper(info.clerkFee()), CoinItem.formatCopper(info.clerkWage())));
			if (hiringCarrier && info.carrierWage() > 0) {
				lines.add(Component.translatable("screen.ak_trade_expansion.staff.summary.cost.carrier_wage", CoinItem.formatCopper(info.carrierWage())));
			}
			lines.add(Component.translatable(hiringCarrier ? "screen.ak_trade_expansion.staff.summary.purse.carrier"
					: "screen.ak_trade_expansion.staff.summary.purse.clerk").withStyle(ChatFormatting.GRAY));
		}
		int ty = ly + 3;
		int bottom = ly + rows * ROW_H - 2;
		for (Component line : lines) {
			for (FormattedCharSequence part : font.split(line, listW - 8)) {
				if (ty + LINE_H > bottom) return;
				g.text(font, part, lx + 4, ty, COLOR_WHITE);
				ty += LINE_H;
			}
		}
	}

	/** v0.48.1: "今なら1往復 約40個（5種類）・見込み +60c" of an automatic carrier. */
	private static Component autoLine(StaffS2C.Destination d) {
		if (d.units() <= 0 || d.profit() <= 0) return Component.translatable("screen.ak_trade_expansion.staff.hint.carrier.none").withColor(COLOR_GRAY);
		return Component.translatable("screen.ak_trade_expansion.staff.hint.auto.trip", d.units(), d.kinds(), CoinItem.formatCopper(d.profit())).withColor(COLOR_GREEN);
	}

	/** v0.48.1: "おすすめ：上限 66c・下限 70c、今なら1往復 約12個・見込み +45c". */
	private static Component recommendLine(StaffS2C.Destination d, int unit) {
		if (d.units() <= 0 || d.profit() <= 0) return Component.translatable("screen.ak_trade_expansion.staff.hint.carrier.none").withColor(COLOR_GRAY);
		return Component.translatable("screen.ak_trade_expansion.staff.hint.recommend", CoinItem.formatCopper((long) Math.ceil(d.buyLimit() * 1.05)),
				CoinItem.formatCopper(d.sellLimit()), d.units() * unit, CoinItem.formatCopper(d.profit())).withColor(COLOR_GREEN);
	}

	private static int unit(GoodView view) {
		return view == null ? 1 : view.unit();
	}

	private void extractSetupPanel(GuiGraphicsExtractor g, Font font, int px, int y) {
		for (int row = 0; row < spinners.length; row++) {
			if (!spinnerShown(row)) continue;
			int slot = spinnerSlot(row);
			// Move the row's buttons to its slot (rows that are not shown leave no gap).
			int by = y + 45 + slot * 26;
			spinners[row][0].setY(by);
			spinners[row][1].setY(by);
			String label = switch (row) {
				case 0 -> Component.translatable(hiringCarrier ? "screen.ak_trade_expansion.staff.setup.quantity" : "screen.ak_trade_expansion.staff.setup.deposit").getString();
				case 1 -> Component.translatable("screen.ak_trade_expansion.staff.setup.buy_limit").getString();
				default -> Component.translatable("screen.ak_trade_expansion.staff.setup.sell_limit").getString();
			};
			screen.fit(g, label, px, by - 9, PANEL_W, COLOR_TEXT, false);
			GoodView view = goodView(goods);
			String value = switch (row) {
				case 0 -> "×" + quantity * unit(view);
				case 1 -> CoinItem.formatCopper(buyLimit);
				default -> CoinItem.formatCopper(sellLimit);
			};
			int vw = Math.min(font.width(value), 48);
			screen.fit(g, value, px + 39 - vw / 2, by + 3, 48, COLOR_TEXT, false);
		}
		g.text(font, Component.translatable("screen.ak_trade_expansion.staff.setup.shift"), px, y + 112, COLOR_DARK, false);
	}

	// ---- helpers ----

	private static ItemStack stack(Identifier item) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(item));
	}

	private static Component role(int role) {
		return Component.translatable("staff.ak_trade_expansion.role." + (role == CARRIER ? "carrier" : "clerk"));
	}

	private static Component reputationName(int level) {
		return Component.translatable("screen.ak_trade_expansion.reputation.level." + Math.clamp(level, 0, 5));
	}

	private static int minutes(long ticks) {
		return (int) Math.max(1, Math.ceil(ticks / (double) PriceModel.TICKS_PER_DAY * 20));
	}

	private static int minutesUntil(long time) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		long left = time - MarketClock.now(mc.level);
		return left <= 0 ? 0 : minutes(left);
	}

	private static String percent(double rate) {
		double value = Math.round(rate * 1000) / 10.0;
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}
}
