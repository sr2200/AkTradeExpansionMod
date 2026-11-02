package com.akf.tradeexpansion.menu;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Container of the trade screen. Holds only the player inventory; the market data travels as
 * {@code MarketSnapshotS2C} packets.
 */
public class MarketMenu extends AbstractContainerMenu {
	/** Top-left of the player inventory inside the screen. */
	public static final int INVENTORY_X = 70;
	public static final int INVENTORY_Y = 156;

	/** Sent to the client when the menu opens (Extended Menu Type). */
	public record OpenData(long marketId, BlockPos stallPos) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, OpenData::marketId,
				BlockPos.STREAM_CODEC, OpenData::stallPos,
				OpenData::new);
	}

	private final long marketId;
	private final BlockPos stallPos;

	public MarketMenu(int containerId, Inventory inventory, OpenData data) {
		super(ModMenus.MARKET, containerId);
		this.marketId = data.marketId();
		this.stallPos = data.stallPos();
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public long marketId() {
		return marketId;
	}

	public BlockPos stallPos() {
		return stallPos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	/** Closes automatically when the stall is broken or the player walks away (A-3, F-2). */
	@Override
	public boolean stillValid(Player player) {
		double max = TradeExpansionConfig.get().trade.maxDistance;
		return player.level().getBlockState(stallPos).is(ModBlocks.MARKET_STALL)
				&& player.distanceToSqr(Vec3.atCenterOf(stallPos)) <= max * max;
	}
}
