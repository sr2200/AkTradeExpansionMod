package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.compat.jei.TradeExpansionJeiPlugin;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * JEI's side of {@link ClientViewerCases}, through JEI's API and the runtime our plugin keeps. Loaded only in
 * runClientGameTestJei (the other runs leave JEI out).
 */
final class JeiViewer implements ClientViewerCases.Viewer {
	private static IJeiRuntime runtime() {
		return (IJeiRuntime) ClientUtil.get(null, TradeExpansionJeiPlugin.class, "runtime");
	}

	private static IFocus<ItemStack> focus(RecipeIngredientRole role, ItemStack stack) {
		return runtime().getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack);
	}

	@Override
	public String name() {
		return "JEI";
	}

	@Override
	public boolean ready() {
		return runtime() != null;
	}

	@Override
	public List<String> categories(ItemStack stack, boolean recipes) {
		return runtime().getRecipeManager().createRecipeCategoryLookup()
				.limitFocus(List.of(focus(recipes ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, stack)))
				.get().map(c -> c.getRecipeType().getUid().toString()).toList();
	}

	@Override
	public boolean ourStation(ItemStack stack) {
		return runtime().getRecipeManager().createRecipeCategoryLookup()
				.limitFocus(List.of(focus(RecipeIngredientRole.CRAFTING_STATION, stack)))
				.get().anyMatch(c -> c.getRecipeType().getUid().toString().equals(ClientViewerCases.OUR_CATEGORY));
	}

	@Override
	public boolean isViewerScreen(Screen screen) {
		return screen != null && screen.getClass().getName().startsWith("mezz.jei.");
	}

	@Override
	public List<String> shownCategories(Screen screen) {
		if (!isViewerScreen(screen)) return List.of();
		Object logic = ClientViewerCases.field(screen, "logic");
		List<?> categories = (List<?>) ClientUtil.call(logic, "getRecipeCategories");
		return categories.stream().map(c -> ((IRecipeCategory<?>) c).getRecipeType().getUid().toString()).toList();
	}

	@Override
	public double[] tab(Screen screen, String category) {
		Object tabs = ClientViewerCases.field(screen, "recipeGuiTabs");
		for (Object tab : (List<?>) ClientViewerCases.field(tabs, "tabs")) {
			if (!tab.getClass().getSimpleName().equals("RecipeCategoryTab")) continue;
			IRecipeCategory<?> c = (IRecipeCategory<?>) ClientViewerCases.field(tab, "category");
			if (!c.getRecipeType().getUid().toString().equals(category)) continue;
			Object area = ClientViewerCases.field(tab, "area");
			int x = (int) ClientViewerCases.field(area, "x");
			int y = (int) ClientViewerCases.field(area, "y");
			int w = (int) ClientViewerCases.field(area, "width");
			int h = (int) ClientViewerCases.field(area, "height");
			return new double[] {x + w / 2.0, y + h / 2.0};
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void showPage(ItemStack stack) {
		IRecipeType<MarketPriceRecipe> type = (IRecipeType<MarketPriceRecipe>) runtime().getRecipeManager()
				.getRecipeType(Identifier.parse(ClientViewerCases.OUR_CATEGORY)).orElseThrow();
		IRecipeCategory<MarketPriceRecipe> category = runtime().getRecipeManager().getRecipeCategory(type);
		MarketPriceRecipe recipe = MarketPriceRecipe.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		runtime().getRecipesGui().showRecipes(category, List.of(recipe), List.of(focus(RecipeIngredientRole.OUTPUT, stack)));
	}
}
