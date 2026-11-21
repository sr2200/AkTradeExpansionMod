package com.akf.tradeexpansion.test.client;

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * REI's side of {@link ClientViewerCases}, through REI's API. Loaded only in runClientGameTestRei (the other runs leave
 * REI out).
 */
final class ReiViewer implements ClientViewerCases.Viewer {
	private static CategoryIdentifier<?> ours() {
		return CategoryIdentifier.of(Identifier.parse(ClientViewerCases.OUR_CATEGORY));
	}

	@Override
	public String name() {
		return "REI";
	}

	@Override
	public boolean ready() {
		try {
			return REIRuntime.getInstance() != null && CategoryRegistry.getInstance().tryGet(ours()).isPresent();
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public List<String> categories(ItemStack stack, boolean recipes) {
		ViewSearchBuilder builder = ViewSearchBuilder.builder();
		if (recipes) builder.addRecipesFor(EntryStacks.of(stack));
		else builder.addUsagesFor(EntryStacks.of(stack));
		return builder.buildMapInternal().keySet().stream().map(c -> c.getCategoryIdentifier().getIdentifier().toString()).toList();
	}

	@Override
	public boolean ourStation(ItemStack stack) {
		for (EntryIngredient ingredient : CategoryRegistry.getInstance().get(ours()).getWorkstations()) {
			for (EntryStack<?> entry : ingredient) {
				if (entry.getValue() instanceof ItemStack s && ItemStack.isSameItem(s, stack)) return true;
			}
		}
		return false;
	}

	@Override
	public boolean isViewerScreen(Screen screen) {
		return screen != null && screen.getClass().getName().startsWith("me.shedaniel.rei.");
	}

	@Override
	public List<String> shownCategories(Screen screen) {
		if (!isViewerScreen(screen)) return List.of();
		List<?> categories = (List<?>) ClientViewerCases.field(screen, "categories");
		return categories.stream().map(c -> ((DisplayCategory<?>) c).getCategoryIdentifier().getIdentifier().toString()).toList();
	}

	@Override
	public double[] tab(Screen screen, String category) {
		Object container = ClientViewerCases.field(screen, "tabs");
		for (Object wrapped : (List<?>) ClientViewerCases.field(container, "widgets")) {
			// The tabs are wrapped (DelegateWidget) to clip them to the tab bar.
			Object widget = wrapped instanceof me.shedaniel.rei.api.client.gui.widgets.DelegateWidget ? ClientViewerCases.field(wrapped, "widget") : wrapped;
			if (!(widget instanceof WidgetWithBounds w) || !widget.getClass().getSimpleName().equals("TabWidget")) continue;
			Object c = ClientViewerCases.field(widget, "category");
			if (c instanceof DisplayCategory<?> d && d.getCategoryIdentifier().getIdentifier().toString().equals(category)) {
				var b = w.getBounds();
				return new double[] {b.getCenterX(), b.getCenterY()};
			}
		}
		return null;
	}

	@Override
	public void showPage(ItemStack stack) {
		ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(stack)).filterCategory(ours()).setPreferredOpenedCategory(ours()).open();
	}
}
