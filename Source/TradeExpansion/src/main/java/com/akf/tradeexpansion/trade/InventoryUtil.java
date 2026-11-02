package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.item.CoinItem;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Main-inventory helpers shared by the server (trade) and the client (preview / max quantity).
 */
public final class InventoryUtil {
	private InventoryUtil() {
	}

	/** Goods match only plain stacks (no name, enchantment, damage, ...: D-2). */
	public static boolean isPlain(ItemStack stack, Item item) {
		return stack.is(item) && stack.getComponentsPatch().isEmpty();
	}

	public static int countPlain(Player player, Item item) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isPlain(stack, item)) count += stack.getCount();
		}
		return count;
	}

	public static void removePlain(Player player, Item item, int qty) {
		Inventory inventory = player.getInventory();
		int remaining = qty;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isPlain(stack, item)) {
				int n = Math.min(remaining, stack.getCount());
				stack.shrink(n);
				remaining -= n;
			}
		}
		inventory.setChanged();
	}

	/**
	 * How many {@code item} fit in the main inventory. Coin slots count as empty because the coins are
	 * collected before the goods are handed over (E-3).
	 */
	public static int freeSpaceFor(Player player, Item item) {
		return freeSpaceFor(player, item, true);
	}

	/** @param coinSlotsFree count coin slots as empty (cash payments collect the coins first; card payments do not, E-8) */
	public static int freeSpaceFor(Player player, Item item, boolean coinSlotsFree) {
		Inventory inventory = player.getInventory();
		int maxStack = new ItemStack(item).getMaxStackSize();
		int space = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || (coinSlotsFree && stack.getItem() instanceof CoinItem)) {
				space += maxStack;
			} else if (isPlain(stack, item)) {
				space += Math.max(0, maxStack - stack.getCount());
			}
		}
		return space;
	}

	public static void give(Player player, Item item, int qty) {
		Inventory inventory = player.getInventory();
		int maxStack = new ItemStack(item).getMaxStackSize();
		int remaining = qty;
		while (remaining > 0) {
			int n = Math.min(remaining, maxStack);
			inventory.placeItemBackInInventory(new ItemStack(item, n), Prediction.SERVER_ONLY);
			remaining -= n;
		}
		inventory.setChanged();
	}
}
