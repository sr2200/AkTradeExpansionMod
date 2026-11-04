package com.akf.tradeexpansion.client.config;

import com.akf.tradeexpansion.config.GoodsOverride;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Config screen part for the goods added by the config file and the consumables lists (design G, v0.27): pick an item
 * from the icon grid (with a search box), then "Add"; pick a listed entry, then "Remove". Edits {@code edit} and saves
 * through {@code onSave} when closed with "Done".
 */
public final class GoodsManagerScreen extends Screen {
	private static final String KEY = "config.trade_expansion.goods_manager.";
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 222;
	private static final int CELL = 18;
	private static final int GRID_COLS = 9;
	private static final int GRID_ROWS = 7;
	private static final int LIST_COLS = 9;
	private static final int LIST_ROWS = 3;

	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_GRAY = 0xFFA0A0A0;
	private static final int COLOR_SELECTED = 0xFF6F6F9F;
	private static final int COLOR_CELL = 0xFF3A3A3A;
	private static final int COLOR_ERROR = 0xFFFF6060;
	private static final int COLOR_OK = 0xFF60FF60;

	private enum Mode {
		GOODS, CONSUMABLE_ADD, CONSUMABLE_REMOVE
	}

	private final Screen parent;
	private final TradeExpansionConfig edit;
	private final Runnable onSave;
	private final List<Item> allItems = new ArrayList<>();

	private Mode mode = Mode.GOODS;
	private List<Item> filtered = new ArrayList<>();
	private int gridScroll;
	private int listScroll;
	private Item picked;
	/** Selected entry of the list: goods id (GOODS) or item id (consumables). */
	private String selectedEntry;
	private Component status = Component.empty();
	private int statusColor = COLOR_TEXT;

	private EditBox search;
	private EditBox price;
	private Button addButton;
	private Button removeButton;
	private final List<Button> modeButtons = new ArrayList<>();

	public GoodsManagerScreen(Screen parent, TradeExpansionConfig edit, Runnable onSave) {
		super(Component.translatable(KEY + "title"));
		this.parent = parent;
		this.edit = edit;
		this.onSave = onSave;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR) allItems.add(item);
		}
		filtered = allItems;
	}

	private int left() {
		return width / 2 - PANEL_W / 2;
	}

	private int top() {
		return height / 2 - PANEL_H / 2;
	}

	@Override
	protected void init() {
		int left = left();
		int top = top();
		modeButtons.clear();
		Mode[] modes = Mode.values();
		for (int i = 0; i < modes.length; i++) {
			Mode m = modes[i];
			modeButtons.add(addRenderableWidget(Button.builder(Component.translatable(KEY + "mode." + m.name().toLowerCase(Locale.ROOT)), b -> setMode(m))
					.bounds(left + i * 114, top, 112, 18).build()));
		}
		String query = search == null ? "" : search.getValue();
		search = addRenderableWidget(new EditBox(font, left, top + 24, GRID_COLS * CELL, 16, Component.translatable(KEY + "search")));
		search.setHint(Component.translatable(KEY + "search"));
		search.setValue(query);
		search.setResponder(this::filter);

		int px = left + 176;
		String priceText = price == null ? "" : price.getValue();
		price = addRenderableWidget(new EditBox(font, px + 70, top + 60, 70, 16, Component.translatable(KEY + "price")));
		price.setMaxLength(12);
		price.setValue(priceText);
		addButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "add"), b -> add()).bounds(px, top + 80, 78, 18).build());
		removeButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "remove"), b -> remove()).bounds(px + 82, top + 80, 78, 18).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
			onSave.run();
			onClose();
		}).bounds(width / 2 - 102, top + PANEL_H - 18, 100, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 2, top + PANEL_H - 18, 100, 18).build());
		updateWidgets();
	}

	private void setMode(Mode newMode) {
		mode = newMode;
		selectedEntry = null;
		listScroll = 0;
		status = Component.empty();
		updateWidgets();
	}

	private void filter(String query) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			filtered = allItems;
		} else {
			filtered = new ArrayList<>();
			for (Item item : allItems) {
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				if (id.contains(q) || new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) filtered.add(item);
			}
		}
		gridScroll = 0;
	}

	private void updateWidgets() {
		for (int i = 0; i < modeButtons.size(); i++) modeButtons.get(i).active = Mode.values()[i] != mode;
		price.visible = mode == Mode.GOODS;
		addButton.active = picked != null;
		removeButton.active = selectedEntry != null;
	}

	// ---- entries ----

	/** Entries of the current mode: goods id → item (GOODS), or item id → item (consumables). */
	private List<Map.Entry<String, Item>> entries() {
		List<Map.Entry<String, Item>> list = new ArrayList<>();
		switch (mode) {
			case GOODS -> edit.goods.forEach((goodsId, o) -> {
				if (o.addsGood()) list.add(Map.entry(goodsId, itemOf(o.item().get())));
			});
			case CONSUMABLE_ADD -> edit.consumables.add.forEach(id -> list.add(Map.entry(id, itemOf(id))));
			case CONSUMABLE_REMOVE -> edit.consumables.remove.forEach(id -> list.add(Map.entry(id, itemOf(id))));
		}
		return list;
	}

	private static Item itemOf(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.BARRIER);
	}

	private static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Goods id for a config-added good: {@code trade_expansion:<path>}, with the namespace for other mods' items. */
	private static String goodsIdFor(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		String path = id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "_" + id.getPath();
		return "trade_expansion:" + path;
	}

	private void add() {
		if (picked == null) return;
		String itemId = idOf(picked);
		switch (mode) {
			case GOODS -> {
				double value;
				try {
					value = Double.parseDouble(price.getValue().trim());
				} catch (NumberFormatException e) {
					value = -1;
				}
				if (value < GoodsOverride.NumberKey.BASE_PRICE.min || value > GoodsOverride.NumberKey.BASE_PRICE.max) {
					setStatus(Component.translatable(KEY + "bad_price"), false);
					return;
				}
				for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
					if (e.getValue().item().toString().equals(itemId) && !GoodsCatalog.fromConfig(e.getKey())) {
						setStatus(Component.translatable(KEY + "already_goods", e.getKey().toString()), false);
						return;
					}
				}
				String goodsId = goodsIdFor(picked);
				GoodsOverride current = edit.goods.getOrDefault(goodsId, GoodsOverride.EMPTY);
				edit.goods.put(goodsId, current.with(GoodsOverride.NumberKey.BASE_PRICE, Optional.of(value)).withItem(Optional.of(itemId)));
				selectedEntry = goodsId;
			}
			case CONSUMABLE_ADD -> {
				if (!edit.consumables.add.contains(itemId)) edit.consumables.add.add(itemId);
				edit.consumables.remove.remove(itemId);
				selectedEntry = itemId;
			}
			case CONSUMABLE_REMOVE -> {
				if (!edit.consumables.remove.contains(itemId)) edit.consumables.remove.add(itemId);
				edit.consumables.add.remove(itemId);
				selectedEntry = itemId;
			}
		}
		setStatus(Component.translatable(KEY + "added", new ItemStack(picked).getHoverName()), true);
	}

	private void remove() {
		if (selectedEntry == null) return;
		switch (mode) {
			case GOODS -> edit.goods.remove(selectedEntry);
			case CONSUMABLE_ADD -> edit.consumables.add.remove(selectedEntry);
			case CONSUMABLE_REMOVE -> edit.consumables.remove.remove(selectedEntry);
		}
		setStatus(Component.translatable(KEY + "removed", selectedEntry), true);
		selectedEntry = null;
	}

	private void setStatus(Component text, boolean ok) {
		status = text;
		statusColor = ok ? COLOR_OK : COLOR_ERROR;
		updateWidgets();
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int gx = left();
		int gy = top() + 44;
		int col = (int) Math.floor((event.x() - gx) / CELL);
		int row = (int) Math.floor((event.y() - gy) / CELL);
		if (col >= 0 && col < GRID_COLS && row >= 0 && row < GRID_ROWS) {
			int index = (gridScroll + row) * GRID_COLS + col;
			if (index < filtered.size()) {
				picked = filtered.get(index);
				updateWidgets();
				return true;
			}
		}
		int lx = left() + 176;
		int ly = top() + 114;
		col = (int) Math.floor((event.x() - lx) / CELL);
		row = (int) Math.floor((event.y() - ly) / CELL);
		if (col >= 0 && col < LIST_COLS && row >= 0 && row < LIST_ROWS) {
			List<Map.Entry<String, Item>> entries = entries();
			int index = (listScroll + row) * LIST_COLS + col;
			if (index < entries.size()) {
				selectedEntry = entries.get(index).getKey();
				if (mode == Mode.GOODS) {
					edit.goods.get(selectedEntry).basePrice().ifPresent(p -> price.setValue(String.valueOf(p)));
					picked = entries.get(index).getValue();
				}
				updateWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = (int) -Math.signum(scrollY);
		if (mouseX < left() + 176) {
			int maxScroll = Math.max(0, (filtered.size() + GRID_COLS - 1) / GRID_COLS - GRID_ROWS);
			gridScroll = Math.clamp(gridScroll + step, 0, maxScroll);
		} else {
			int maxScroll = Math.max(0, (entries().size() + LIST_COLS - 1) / LIST_COLS - LIST_ROWS);
			listScroll = Math.clamp(listScroll + step, 0, maxScroll);
		}
		return true;
	}

	// ---- rendering ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int left = left();
		int top = top();

		// Item grid
		int gx = left;
		int gy = top + 44;
		Item hovered = null;
		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				int x = gx + col * CELL;
				int y = gy + row * CELL;
				int index = (gridScroll + row) * GRID_COLS + col;
				g.fill(x, y, x + CELL - 1, y + CELL - 1, COLOR_CELL);
				if (index >= filtered.size()) continue;
				Item item = filtered.get(index);
				if (item == picked) g.fill(x, y, x + CELL - 1, y + CELL - 1, COLOR_SELECTED);
				g.item(new ItemStack(item), x, y);
				if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) hovered = item;
			}
		}
		g.text(font, Component.translatable(KEY + "count", filtered.size()), gx, gy + GRID_ROWS * CELL + 3, COLOR_GRAY);

		// Picked item and add / remove
		int px = left + 176;
		g.text(font, Component.translatable(KEY + "help." + mode.name().toLowerCase(Locale.ROOT)), px, top + 24, COLOR_GRAY);
		if (picked != null) {
			g.item(new ItemStack(picked), px, top + 36);
			g.text(font, font.plainSubstrByWidth(new ItemStack(picked).getHoverName().getString(), 140), px + 20, top + 36, COLOR_TEXT);
			g.text(font, font.plainSubstrByWidth(idOf(picked), 140), px + 20, top + 46, COLOR_GRAY);
		} else {
			g.text(font, Component.translatable(KEY + "pick"), px, top + 40, COLOR_GRAY);
		}
		if (mode == Mode.GOODS) g.text(font, Component.translatable(KEY + "price"), px, top + 64, COLOR_TEXT);

		// Listed entries
		List<Map.Entry<String, Item>> entries = entries();
		g.text(font, Component.translatable(KEY + "listed", entries.size()), px, top + 103, COLOR_TEXT);
		int ly = top + 114;
		String hoveredEntry = null;
		for (int row = 0; row < LIST_ROWS; row++) {
			for (int col = 0; col < LIST_COLS; col++) {
				int x = px + col * CELL;
				int y = ly + row * CELL;
				int index = (listScroll + row) * LIST_COLS + col;
				g.fill(x, y, x + CELL - 1, y + CELL - 1, COLOR_CELL);
				if (index >= entries.size()) continue;
				Map.Entry<String, Item> entry = entries.get(index);
				if (entry.getKey().equals(selectedEntry)) g.fill(x, y, x + CELL - 1, y + CELL - 1, COLOR_SELECTED);
				g.item(new ItemStack(entry.getValue()), x, y);
				if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) hoveredEntry = entry.getKey();
			}
		}
		g.text(font, status, px, ly + LIST_ROWS * CELL + 4, statusColor);

		if (hovered != null) {
			g.setTooltipForNextFrame(font, List.of(new ItemStack(hovered).getHoverName(), Component.literal(idOf(hovered))), Optional.empty(), mouseX, mouseY);
		} else if (hoveredEntry != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hoveredEntry));
			if (mode == Mode.GOODS) {
				edit.goods.get(hoveredEntry).basePrice().ifPresent(p ->
						lines.add(Component.translatable(KEY + "base_price", CoinItem.formatCopper(Math.round(p)))));
			}
			g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.gui.setScreen(parent);
	}
}
