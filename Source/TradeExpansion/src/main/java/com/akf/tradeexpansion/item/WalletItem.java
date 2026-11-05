package com.akf.tradeexpansion.item;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.WalletMath;
import com.akf.tradeexpansion.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Wallet (design E-6): stores coins as a copper amount. Coins go in by stacking them onto the wallet (like a bundle),
 * automatically on pickup, or as trade change; use the wallet to open its screen and take coins out.
 */
public class WalletItem extends Item {
	public WalletItem(Properties properties) {
		super(properties);
	}

	public static long balance(ItemStack wallet) {
		return wallet.getOrDefault(ModDataComponents.WALLET_BALANCE, 0L);
	}

	public static void setBalance(ItemStack wallet, long balance) {
		wallet.set(ModDataComponents.WALLET_BALANCE, Math.max(0, balance));
	}

	public static long capacity() {
		return TradeExpansionConfig.get().wallet.capacity;
	}

	/**
	 * Moves as many coins of {@code coins} into {@code wallet} as fit.
	 *
	 * @return number of coins moved
	 */
	public static int insertCoins(ItemStack wallet, ItemStack coins) {
		if (!(coins.getItem() instanceof CoinItem coin)) return 0;
		long value = coin.type().value();
		int n = (int) WalletMath.coinsThatFit(capacity() - balance(wallet), value, coins.getCount());
		if (n > 0) {
			setBalance(wallet, balance(wallet) + n * value);
			coins.shrink(n);
		}
		return n;
	}

	/** Wallet on the cursor clicked onto a coin stack. */
	@Override
	public boolean overrideStackedOnOther(ItemStack wallet, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY) return false;
		ItemStack coins = slot.getItem();
		if (!(coins.getItem() instanceof CoinItem) || !slot.mayPickup(player)) return false;
		if (insertCoins(wallet, coins) > 0) {
			slot.setChanged();
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
		}
		return true;
	}

	/** Coins on the cursor clicked onto the wallet. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack wallet, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.SECONDARY || !(other.getItem() instanceof CoinItem) || !slot.allowModification(player)) return false;
		if (insertCoins(wallet, other) > 0) {
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
		}
		return true;
	}

	/** The screen is opened on the client (see TradeExpansionClient); nothing to do on the server. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.SUCCESS;
	}

	/** Tooltip lines, added on the client through Fabric's ItemTooltipCallback (replaces the deprecated appendHoverText, E-1). */
	public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.wallet_balance",
				CoinItem.formatCopper(balance(stack)), CoinItem.formatCopper(capacity())).withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("tooltip.ak_trade_expansion.wallet_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
