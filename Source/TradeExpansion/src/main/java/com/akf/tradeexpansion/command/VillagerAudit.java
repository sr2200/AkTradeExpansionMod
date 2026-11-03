package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.villager.VillagerCoins;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /tradeex audit villagers} (design H-4, 14c): for the villagers and wandering traders around the player, the
 * coin trades whose goods a market of the dimension buys for more than the villager asks (buy from the villager, sell
 * at the market). Uses the prices the player would pay here; helps tuning {@code villager.emeraldPrice} and
 * {@code villager.minGoodsPriceMultiplier}.
 */
final class VillagerAudit {
	private static final double RADIUS = 32;
	private static final int MAX_LINES = 15;

	private VillagerAudit() {
	}

	private record Finding(String merchant, String goods, int count, long price, long sell) {
		long profit() {
			return sell - price;
		}
	}

	static int run(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TradeExpansionConfig config = TradeExpansionConfig.get();
		List<AbstractVillager> merchants = player.level().getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(RADIUS));
		List<Finding> findings = new ArrayList<>();
		int checked = 0;
		for (AbstractVillager merchant : merchants) {
			for (MerchantOffer offer : merchant.getOffers()) {
				long price = VillagerCoins.price(player, offer, config);
				if (price < 0) continue;
				checked++;
				ItemStack result = offer.getResult();
				Identifier goodsId = VillagerCoins.goodsOf(result);
				if (goodsId == null) continue;
				long sell = VillagerCoins.bestMarketSell(player.level(), player.blockPosition(), goodsId, result.getCount());
				if (sell > price) {
					findings.add(new Finding(merchant.getName().getString(), BuiltInRegistries.ITEM.getKey(result.getItem()).getPath(),
							result.getCount(), price, sell));
				}
			}
		}
		findings.sort(Comparator.comparingLong(Finding::profit).reversed());
		int merchantCount = merchants.size();
		int offerCount = checked;
		source.sendSuccess(() -> Component.translatable("command.trade_expansion.audit_villagers", merchantCount, offerCount, findings.size()), false);
		findings.stream().limit(MAX_LINES).forEach(f -> source.sendSuccess(() -> Component.literal(String.format(" %s: %s x%d  %s -> %s (+%s)",
				f.merchant(), f.goods(), f.count(), CoinItem.formatCopper(f.price()), CoinItem.formatCopper(f.sell()), CoinItem.formatCopper(f.profit()))), false));
		return findings.size();
	}
}
