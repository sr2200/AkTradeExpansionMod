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
	/**
	 * Layout of the screen on the client (A-4, v0.33): set by the client to fit the window when the menu is created. The
	 * server keeps the default; slot coordinates are only used for drawing and clicks on the client.
	 */
	public static volatile java.util.function.Supplier<MarketLayout> clientLayout = () -> MarketLayout.DEFAULT;

	/**
	 * Sent to the client when the menu opens (Extended Menu Type).
	 *
	 * @param traderId entity id of the trader NPC the screen was opened from (A-5), or {@code -1} for a stall
	 * @param tab      tab to show first (N-4, v0.38: a facility's sign opens its tab), empty for the buy tab
	 */
	public record OpenData(long marketId, BlockPos stallPos, int traderId, String tab) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, OpenData::marketId,
				BlockPos.STREAM_CODEC, OpenData::stallPos,
				ByteBufCodecs.VAR_INT, OpenData::traderId,
				ByteBufCodecs.STRING_UTF8, OpenData::tab,
				OpenData::new);

		public OpenData(long marketId, BlockPos stallPos) {
			this(marketId, stallPos, -1, "");
		}

		public OpenData(long marketId, BlockPos stallPos, int traderId) {
			this(marketId, stallPos, traderId, "");
		}
	}

	private final long marketId;
	private final BlockPos stallPos;
	private final int traderId;
	private final String initialTab;
	private final SimpleContainer sellSlot = new SimpleContainer(1);
	private final int sellSlotIndex;
	private final MarketLayout layout;

	public MarketMenu(int containerId, Inventory inventory, OpenData data) {
		super(ModMenus.MARKET, containerId);
		this.marketId = data.marketId();
		this.stallPos = data.stallPos();
		this.traderId = data.traderId();
		this.initialTab = data.tab();
		this.layout = inventory.player.level().isClientSide() ? clientLayout.get() : MarketLayout.DEFAULT;
		this.sellSlotIndex = slots.size();
		addSlot(new Slot(sellSlot, 0, layout.sellSlotX(), layout.sellSlotY()) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Coins are money, not goods (E-1). The emerald coin is not a CoinItem, so it can be sold from here (E-5).
				return !(stack.getItem() instanceof CoinItem);
			}
		});
		addStandardInventorySlots(inventory, layout.inventoryX(), layout.inventoryY());
	}

	/** Screen size and slot positions (A-4, v0.33). */
	public MarketLayout layout() {
		return layout;
	}

	public long marketId() {
		return marketId;
	}

	public BlockPos stallPos() {
		return stallPos;
	}

	/** N-4: tab key to open first, empty for the default. */
	public String initialTab() {
		return initialTab;
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
		// N-4: a facility's sign opens the screen like a stall.
		return (player.level().getBlockState(stallPos).is(ModBlocks.MARKET_STALL) || player.level().getBlockState(stallPos).is(ModBlocks.FACILITY_SIGN))
				&& player.distanceToSqr(Vec3.atCenterOf(stallPos)) <= max * max;
	}
}
