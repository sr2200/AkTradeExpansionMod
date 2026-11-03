package com.akf.tradeexpansion.mixin.client;

import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stage 14a (design H): shows the coin price over the emerald cost of each coin trade in the list, and the price and
 * the player's cash under the payment slots.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
	/** Same layout as vanilla: 7 visible offers, 20 px apart. */
	private static final int TRADE_EXPANSION$ROWS = 7;

	@Shadow private int shopItem;
	@Shadow private int scrollOff;

	private MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void tradeExpansion$coinPrices(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Player player = minecraft == null ? null : minecraft.player;
		if (player == null || !VillagerCoins.active(player)) return;
		MerchantOffers offers = menu.getOffers();
		int first = offers.size() > TRADE_EXPANSION$ROWS ? scrollOff : 0;
		for (int row = 0; row < TRADE_EXPANSION$ROWS && first + row < offers.size(); row++) {
			long price = VillagerCoins.price(player, first + row);
			if (price < 0) continue;
			int x = leftPos + 10;
			int y = topPos + 19 + row * 20;
			String text = tradeExpansion$short(price);
			int w = font.width(text);
			g.fill(x - 1, y + 8, x + w + 1, y + 17, 0xE0202020);
			g.text(font, text, x, y + 9, 0xFFFFD700);
		}
		if (shopItem >= 0 && shopItem < offers.size()) {
			long price = VillagerCoins.price(player, shopItem);
			if (price >= 0) {
				long cash = WalletService.balance(player);
				Component line = Component.translatable("screen.trade_expansion.villager_price", CoinItem.formatCopper(price), CoinItem.formatCopper(cash));
				g.text(font, line, leftPos + 136, topPos + 58, cash >= price ? 0xFF404040 : 0xFFA02020, false);
			}
		}
	}

	/** "150c", "1.9kc": fits over a 16 px item icon and the gap next to it. */
	private static String tradeExpansion$short(long copper) {
		if (copper < 10_000) return CoinItem.formatCopper(copper);
		return copper < 1_000_000 ? String.format(java.util.Locale.ROOT, "%.1fkc", copper / 1000.0) : String.format(java.util.Locale.ROOT, "%.1fMc", copper / 1_000_000.0);
	}
}
