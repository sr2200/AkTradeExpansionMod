package com.akf.tradeexpansion.client.compat.rei;

import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPricePanel;
import com.akf.tradeexpansion.registry.ModBlocks;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** I (v0.47): REI's "nearby markets" category, drawn by {@link MarketPricePanel} like the JEI page. */
final class MarketPriceDisplayCategory implements DisplayCategory<MarketPriceDisplay> {
	/** Space between REI's recipe frame and the page. */
	private static final int PADDING = 5;

	@Override
	public CategoryIdentifier<? extends MarketPriceDisplay> getCategoryIdentifier() {
		return MarketPriceDisplay.CATEGORY;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("recipe_viewer.ak_trade_expansion.title");
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(ModBlocks.MARKET_STALL);
	}

	@Override
	public int getDisplayWidth(MarketPriceDisplay display) {
		return MarketPricePanel.WIDTH + PADDING * 2;
	}

	@Override
	public int getDisplayHeight() {
		return MarketPricePanel.HEIGHT + PADDING * 2;
	}

	@Override
	public int getMaximumDisplaysPerPage() {
		return 1;
	}

	@Override
	public List<Widget> setupDisplay(MarketPriceDisplay display, Rectangle bounds) {
		int x = bounds.x + PADDING;
		int y = bounds.y + PADDING;
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));
		widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> MarketPricePanel.draw(graphics, display.recipe().item(), x, y)));
		widgets.add(Widgets.createSlot(new Point(x + MarketPricePanel.SLOT_X + 1, y + MarketPricePanel.SLOT_Y + 1))
				.entries(display.getOutputEntries().getFirst())
				.markOutput());
		widgets.add(Widgets.createTooltip(point -> {
			List<Component> lines = MarketPricePanel.tooltip(display.recipe().item(), point.x - x, point.y - y);
			return lines.isEmpty() ? null : Tooltip.create(point, lines);
		}));
		return widgets;
	}
}
