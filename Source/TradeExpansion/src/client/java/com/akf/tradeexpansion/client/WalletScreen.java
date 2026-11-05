package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.network.WalletWithdrawC2S;
import com.akf.tradeexpansion.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Wallet screen (design E-6): shows the balance of the wallet in hand and takes coins out by tier.
 * The balance is read from the held stack, which the server keeps in sync.
 */
public class WalletScreen extends Screen {
	private static final int[] COUNTS = {1, 10, 64};
	private static final int ROW_H = 22;

	private final InteractionHand hand;

	public WalletScreen(InteractionHand hand) {
		super(Component.translatable("item.ak_trade_expansion.wallet"));
		this.hand = hand;
	}

	private ItemStack wallet() {
		return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
	}

	@Override
	protected void init() {
		int left = width / 2 - 100;
		int top = height / 2 - 60;
		CoinType[] types = CoinType.values();
		for (int t = 0; t < types.length; t++) {
			CoinType type = types[types.length - 1 - t]; // largest first
			for (int c = 0; c < COUNTS.length; c++) {
				int count = COUNTS[c];
				addRenderableWidget(Button.builder(Component.literal("×" + count), b -> withdraw(type, count))
						.bounds(left + 110 + c * 31, top + 24 + t * ROW_H, 29, 18).build());
			}
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 40, top + 24 + types.length * ROW_H + 6, 80, 18).build());
	}

	private void withdraw(CoinType type, int count) {
		ClientPlayNetworking.send(new WalletWithdrawC2S(hand == InteractionHand.OFF_HAND, type.ordinal(), count));
	}

	@Override
	public void tick() {
		// Close if the wallet left the hand.
		if (!(wallet().getItem() instanceof WalletItem)) onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int left = width / 2 - 100;
		int top = height / 2 - 60;
		long balance = WalletItem.balance(wallet());
		g.centeredText(font, Component.translatable("screen.ak_trade_expansion.wallet.balance",
				CoinItem.formatCopper(balance), CoinItem.formatCopper(WalletItem.capacity())), width / 2, top + 6, 0xFFFFD700);
		CoinType[] types = CoinType.values();
		for (int t = 0; t < types.length; t++) {
			CoinType type = types[types.length - 1 - t];
			int y = top + 24 + t * ROW_H;
			g.item(new ItemStack(ModItems.coin(type)), left, y + 1);
			g.text(font, CoinItem.formatCopper(type.value()), left + 20, y + 5, balance >= type.value() ? 0xFFFFFFFF : 0xFF808080);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
