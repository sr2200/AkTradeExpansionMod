package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
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

	public static void open(ServerPlayer player, ServerLevel level, Market market, ExtendedMenuProvider<MarketMenu.OpenData> provider) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		if (MarketFactory.growIfDue(level, market, registry.inDimension(level.dimension()))) {
			// D-4: tell the player the market has grown.
			player.sendSystemMessage(Component.translatable("message.trade_expansion.market_grew", market.id(),
					MarketStallBlockEntity.levelName(market.level())).withStyle(ChatFormatting.GREEN));
		}
		registry.setDirty();

		player.openMenu(provider);
		long now = MarketUpkeep.touch(level, market);
		ServerPlayNetworking.send(player, MarketSnapshotS2C.of(market, now));
		BankService.sendAccount(player);
	}
}
