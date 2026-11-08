package com.akf.tradeexpansion.client.compat.jei;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPricePanel;
import com.akf.tradeexpansion.client.compat.recipeviewer.MarketPriceRecipe;
import com.akf.tradeexpansion.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * I (v0.47): JEI's "nearby markets" category. The item is both an output (found with "recipes", R: it can be bought) and
 * an input (found with "uses", U: it can be sold).
 */
final class MarketPriceCategory extends AbstractRecipeCategory<MarketPriceRecipe> {
	static final IRecipeType<MarketPriceRecipe> TYPE = IRecipeType.create(TradeExpansion.MOD_ID, "market_prices", MarketPriceRecipe.class);

	MarketPriceCategory(IGuiHelper guiHelper) {
		super(TYPE, Component.translatable("recipe_viewer.ak_trade_expansion.title"), guiHelper.createDrawableItemLike(ModBlocks.MARKET_STALL),
				MarketPricePanel.WIDTH, MarketPricePanel.HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, MarketPriceRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.OUTPUT, MarketPricePanel.SLOT_X + 1, MarketPricePanel.SLOT_Y + 1)
				.setStandardSlotBackground()
				.add(recipe.stack());
		builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).add(recipe.stack());
	}

	@Override
	public void draw(MarketPriceRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
		MarketPricePanel.draw(guiGraphics, recipe.item(), 0, 0);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, MarketPriceRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
		tooltip.addAll(MarketPricePanel.tooltip(recipe.item(), mouseX, mouseY));
	}

	@Override
	public @Nullable Identifier getIdentifier(MarketPriceRecipe recipe) {
		return TradeExpansion.id("market_prices/" + recipe.item().getNamespace() + "/" + recipe.item().getPath());
	}
}
