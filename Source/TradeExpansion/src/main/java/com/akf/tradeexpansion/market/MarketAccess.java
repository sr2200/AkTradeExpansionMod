package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Opens the trade screen of a market, from a stall (A-3) or a trader NPC (A-5). */
public final class MarketAccess {
	private MarketAccess() {
	}

	private static String formatMultiplier(double m) {
		return m == Math.rint(m) ? String.valueOf((long) m) : String.valueOf(m);
	}

	public static void open(ServerPlayer player, ServerLevel level, Market market, ExtendedMenuProvider<MarketMenu.OpenData> provider) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		MarketFactory.ensureAutoGoods(level, market, registry.linkedTo(level.dimension())); // e.g. the emerald coin (v0.30)
		int resized = MarketFactory.resizeIfDue(level, market, registry.linkedTo(level.dimension()));
		if (resized != 0) {
			// D-4: tell the player the market has grown / shrunk.
			player.sendSystemMessage(Component.translatable(resized > 0 ? "message.ak_trade_expansion.market_grew" : "message.ak_trade_expansion.market_shrank",
					market.id(), MarketStallBlockEntity.levelName(market.level())).withStyle(resized > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		}
		registry.setDirty();

		player.openMenu(provider);
		long now = MarketUpkeep.touch(level, market);
		if (com.akf.tradeexpansion.development.Festival.today(market, now)) { // v0.51 festival plaza
			player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.festival.today", MarketNames.name(market),
					formatMultiplier(TradeExpansionConfig.get().development.festivalMultiplier)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		ServerPlayNetworking.send(player, MarketSnapshotS2C.of(market, player, now));
		BankService.sendAccount(player);
		com.akf.tradeexpansion.advancement.TradeAdvancements.checkMarket(player, market, now); // R: progress made before
		com.akf.tradeexpansion.advancement.TradeAdvancements.checkWealth(player);
	}
}
