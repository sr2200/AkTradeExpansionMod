package com.akf.tradeexpansion.menu;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import com.akf.tradeexpansion.entity.TraderEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Container of the trade screen: the player inventory plus one sell slot (A-4 操作2). Items put into the sell slot
 * are sold before the inventory and go back to the player when the screen closes. The market data travels as
 * {@code MarketSnapshotS2C} packets.
 */
public class MarketMenu extends AbstractContainerMenu {
	/** Top-left of the player inventory inside the screen. */
	public static final int INVENTORY_X = 70;
	public static final int INVENTORY_Y = 156;
	/** The sell slot, left of the inventory. */
	public static final int SELL_SLOT_X = 26;
	public static final int SELL_SLOT_Y = 176;

	/**
	 * Sent to the client when the menu opens (Extended Menu Type).
	 *
	 * @param traderId entity id of the trader NPC the screen was opened from (A-5), or {@code -1} for a stall
	 */
	public record OpenData(long marketId, BlockPos stallPos, int traderId) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, OpenData::marketId,
				BlockPos.STREAM_CODEC, OpenData::stallPos,
				ByteBufCodecs.VAR_INT, OpenData::traderId,
				OpenData::new);

		public OpenData(long marketId, BlockPos stallPos) {
			this(marketId, stallPos, -1);
		}
	}

	private final long marketId;
	private final BlockPos stallPos;
	private final int traderId;
	private final SimpleContainer sellSlot = new SimpleContainer(1);
	private final int sellSlotIndex;

	public MarketMenu(int containerId, Inventory inventory, OpenData data) {
		super(ModMenus.MARKET, containerId);
		this.marketId = data.marketId();
		this.stallPos = data.stallPos();
		this.traderId = data.traderId();
		this.sellSlotIndex = slots.size();
		addSlot(new Slot(sellSlot, 0, SELL_SLOT_X, SELL_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Coins are money, not goods (E-1). The emerald coin is not a CoinItem, so it can be sold from here (E-5).
				return !(stack.getItem() instanceof CoinItem);
			}
		});
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public long marketId() {
		return marketId;
	}

	public BlockPos stallPos() {
		return stallPos;
	}

	/** The sell slot container (A-4 操作2), used before the inventory when selling. */
	public Container sellSlot() {
		return sellSlot;
	}

	public ItemStack sellSlotItem() {
		return sellSlot.getItem(0);
	}

	/** Shift-click moves between the sell slot and the inventory. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index == sellSlotIndex) {
			if (!moveItemStackTo(stack, sellSlotIndex + 1, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!slots.get(sellSlotIndex).mayPlace(stack) || !moveItemStackTo(stack, sellSlotIndex, sellSlotIndex + 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	/** Items left in the sell slot go back to the player (dropped if the inventory is full). */
	@Override
	public void removed(Player player) {
		super.removed(player);
		clearContainer(player, sellSlot);
	}

	/** Closes automatically when the stall (or trader, A-5) is gone or the player walks away (A-3, F-2). */
	@Override
	public boolean stillValid(Player player) {
		double max = TradeExpansionConfig.get().trade.maxDistance;
		if (traderId >= 0) {
			Entity trader = player.level().getEntity(traderId);
			return trader instanceof TraderEntity && trader.isAlive() && player.distanceToSqr(trader) <= max * max;
		}
		return player.level().getBlockState(stallPos).is(ModBlocks.MARKET_STALL)
				&& player.distanceToSqr(Vec3.atCenterOf(stallPos)) <= max * max;
	}
}
