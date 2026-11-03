package com.akf.tradeexpansion.villager;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.VillagerPricesS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager trades paid in coins (design H, stage 14a): the emeralds an offer asks for are paid from the player's
 * wallet and coins instead ({@code villager.coinTrades}). Vanilla offers stay as they are, so discounts, demand,
 * restocking and experience keep working; only the payment changes. Emeralds are not accepted for such offers.
 * <p>
 * The coin price of every offer is worked out on the server when the offers are sent (open / refresh) and sent to
 * the client ({@link VillagerPricesS2C}); both sides look prices up here by offer index. {@code -1} = not a coin offer
 * (no emerald in the cost, e.g. selling crops to the villager).
 */
public final class VillagerCoins {
	/** Prices of the trade screen a player has open: container id + price per offer index. */
	public record Prices(int containerId, long[] prices) {
	}

	private static final Map<UUID, Prices> PRICES = new ConcurrentHashMap<>();

	private VillagerCoins() {
	}

	public static void put(UUID player, Prices prices) {
		PRICES.put(player, prices);
	}

	public static void clear(UUID player) {
		PRICES.remove(player);
	}

	/** Coin price of offer {@code index} in the player's open trade screen, or {@code -1}. */
	public static long price(Player player, int index) {
		Prices p = PRICES.get(player.getUUID());
		if (p == null || player.containerMenu == null || player.containerMenu.containerId != p.containerId()) return -1;
		return index >= 0 && index < p.prices().length ? p.prices()[index] : -1;
	}

	public static boolean active(Player player) {
		Prices p = PRICES.get(player.getUUID());
		return p != null && player.containerMenu != null && player.containerMenu.containerId == p.containerId();
	}

	public static boolean isEmerald(ItemCost cost) {
		return cost.item().value() == Items.EMERALD;
	}

	/** The non-emerald part of an offer's cost (e.g. the book of an enchanted book trade), as a stack with the price count. */
	public static @Nullable ItemStack otherCost(MerchantOffer offer) {
		if (!isEmerald(offer.getItemCostA())) return offer.getCostA();
		Optional<ItemCost> b = offer.getItemCostB();
		return b.isPresent() && !isEmerald(b.get()) ? offer.getCostB() : null;
	}

	/** Whether the payment slots hold the non-emerald part of the cost (always true when there is none). */
	public static boolean otherCostPaid(MerchantOffer offer, ItemStack slotA, ItemStack slotB) {
		ItemCost other = otherItemCost(offer);
		if (other == null) return true;
		int need = otherCost(offer).getCount();
		return (other.test(slotA) && slotA.getCount() >= need) || (other.test(slotB) && slotB.getCount() >= need);
	}

	public static @Nullable ItemCost otherItemCost(MerchantOffer offer) {
		if (!isEmerald(offer.getItemCostA())) return offer.getItemCostA();
		return offer.getItemCostB().filter(b -> !isEmerald(b)).orElse(null);
	}

	/**
	 * Server: works out and sends the coin prices when a merchant's offers are sent to {@code player}. Villagers trade in
	 * coins while {@code villager.coinTrades} is on (14a); the wandering trader also needs {@code villager.wanderingTraderCoins}
	 * (14b). Other merchants (other mods) keep vanilla payment.
	 */
	public static void sendPrices(ServerPlayer player, int containerId, MerchantOffers offers, net.minecraft.world.item.trading.Merchant merchant) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		boolean coins = config.villager.coinTrades && (merchant instanceof net.minecraft.world.entity.npc.villager.Villager
				|| (merchant instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader && config.villager.wanderingTraderCoins));
		if (!coins) {
			clear(player.getUUID());
			ServerPlayNetworking.send(player, new VillagerPricesS2C(containerId, new long[0]));
			return;
		}
		long[] prices = new long[offers.size()];
		for (int i = 0; i < offers.size(); i++) prices[i] = price(player, offers.get(i), config);
		put(player.getUUID(), new Prices(containerId, prices));
		ServerPlayNetworking.send(player, new VillagerPricesS2C(containerId, prices));
	}

	/**
	 * H-2: emeralds × {@code villager.emeraldPrice}, but for goods at least (案A, 14a) the nearest market's buy price and
	 * (14c) {@code base_price × villager.minGoodsPriceMultiplier}, so a villager far from any market cannot sell goods
	 * below what markets pay for them. {@code -1} = not paid in emeralds.
	 */
	public static long price(ServerPlayer player, MerchantOffer offer, TradeExpansionConfig config) {
		int emeralds = 0;
		if (isEmerald(offer.getItemCostA())) emeralds += offer.getCostA().getCount();
		if (offer.getItemCostB().filter(VillagerCoins::isEmerald).isPresent()) emeralds += offer.getCostB().getCount();
		if (emeralds == 0) return -1;
		long price = (long) emeralds * config.villager.emeraldPrice;
		ItemStack result = offer.getResult();
		Identifier goodsId = goodsOf(result);
		if (goodsId == null) return price;
		long definitionFloor = (long) Math.ceil(GoodsCatalog.get(goodsId).basePrice() * config.villager.minGoodsPriceMultiplier - 1e-9) * result.getCount();
		return Math.max(price, Math.max(definitionFloor, marketFloor(player.level(), player.blockPosition(), goodsId, result.getCount())));
	}

	/** The plain single-unit good a stack is, or {@code null} (enchanted / named items, maps, ... are not goods). */
	public static @Nullable Identifier goodsOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return null;
		Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			GoodsDefinition def = e.getValue();
			if (def.item().equals(item) && def.unit() == 1 && def.components().isEmpty()) return e.getKey();
		}
		return null;
	}

	/** 案A: buy price of the good at the nearest market (within {@code distance.farRadius}) that sells it, else 0. */
	private static long marketFloor(ServerLevel level, BlockPos pos, Identifier id, int count) {
		if (!GoodsCatalog.get(id).buy()) return 0;
		double far = TradeExpansionConfig.get().distance.farRadius;
		Market market = MarketRegistry.get(level.getServer()).inDimension(level.dimension()).stream()
				.filter(m -> m.good(id) != null && m.horizontalDistanceTo(pos) <= far)
				.min(Comparator.comparingDouble(m -> m.horizontalDistanceTo(pos)))
				.orElse(null);
		if (market == null) return 0;
		MarketUpkeep.touch(level, market);
		GoodParams params = market.params(id);
		GoodState state = market.good(id);
		return params == null ? 0 : PriceModel.buyPrice(params, state.pressure()) * count;
	}

	/** 14c ({@code /tradeex audit villagers}): the best price a market of the dimension pays for {@code count} of the good, or 0. */
	public static long bestMarketSell(ServerLevel level, BlockPos pos, Identifier id, int count) {
		if (!GoodsCatalog.get(id).sell()) return 0;
		double far = TradeExpansionConfig.get().distance.farRadius;
		long best = 0;
		for (Market market : MarketRegistry.get(level.getServer()).inDimension(level.dimension())) {
			if (market.good(id) == null || market.horizontalDistanceTo(pos) > far) continue;
			MarketUpkeep.touch(level, market);
			GoodParams params = market.params(id);
			GoodState state = market.good(id);
			if (params != null && PriceModel.canSell(params, state)) best = Math.max(best, PriceModel.sumSell(params, state.pressure(), count));
		}
		return best;
	}

	/** Server: charges a coin trade. */
	public static boolean charge(Player player, long price, MerchantOffer offer) {
		if (!com.akf.tradeexpansion.currency.WalletService.pay(player, price)) return false;
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} VILLAGER {} x{} total={}", player.getGameProfile().name(),
					BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()), offer.getResult().getCount(), CoinItem.formatCopper(price));
		}
		return true;
	}
}
