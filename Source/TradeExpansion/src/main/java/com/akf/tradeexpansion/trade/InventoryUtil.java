package com.akf.tradeexpansion.trade;

import com.akf.tradeexpansion.item.CoinItem;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Main-inventory helpers shared by the server (trade) and the client (preview / max quantity).
 * The optional {@code extra} container is the sell slot of the trade screen (A-4 操作2); it is used before the inventory.
 */
public final class InventoryUtil {
	private InventoryUtil() {
	}

	/**
	 * Which stacks count as a good (D-2): the item with exactly {@code patch} (empty = plain items only), and with
	 * {@code acceptDamaged} also plain items whose only difference is damage (A-4 reduced-price buying).
	 */
	public record Match(Item item, DataComponentPatch patch, boolean acceptDamaged) {
		public static Match plain(Item item) {
			return new Match(item, DataComponentPatch.EMPTY, false);
		}

		public boolean exact(ItemStack stack) {
			return stack.is(item) && stack.getComponentsPatch().equals(patch);
		}

		/** Remaining durability ratio of a damaged-only stack, or -1 if it does not qualify. */
		public double damagedRatio(ItemStack stack) {
			if (!acceptDamaged || !patch.isEmpty() || !stack.is(item) || !stack.isDamageableItem()) return -1;
			DataComponentPatch p = stack.getComponentsPatch();
			if (p.isEmpty() || !p.forget(t -> t == DataComponents.DAMAGE).isEmpty()) return -1;
			return Math.max(0, 1.0 - stack.getDamageValue() / (double) stack.getMaxDamage());
		}
	}

	/** Goods match only plain stacks (no name, enchantment, damage, ...: D-2). */
	public static boolean isPlain(ItemStack stack, Item item) {
		return Match.plain(item).exact(stack);
	}

	public static int countPlain(Player player, Item item) {
		return count(player, Match.plain(item), null);
	}

	public static void removePlain(Player player, Item item, int qty) {
		remove(player, Match.plain(item), null, qty);
	}

	private static List<ItemStack> stacks(Player player, Container extra) {
		List<ItemStack> list = new ArrayList<>();
		if (extra != null) {
			for (int i = 0; i < extra.getContainerSize(); i++) list.add(extra.getItem(i));
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) list.add(inventory.getItem(slot));
		return list;
	}

	/** Exact matches plus (if accepted) damaged-only items. */
	public static int count(Player player, Match match, Container extra) {
		int count = 0;
		for (ItemStack stack : stacks(player, extra)) {
			if (match.exact(stack) || match.damagedRatio(stack) >= 0) count += stack.getCount();
		}
		return count;
	}

	/** One entry per item in selling order: exact items first (1.0), then damaged ones by remaining durability. */
	private record Unit(ItemStack stack, double ratio) {
	}

	private static List<Unit> sellOrder(Player player, Match match, Container extra) {
		List<Unit> exact = new ArrayList<>();
		List<Unit> damaged = new ArrayList<>();
		for (ItemStack stack : stacks(player, extra)) {
			if (match.exact(stack)) {
				for (int i = 0; i < stack.getCount(); i++) exact.add(new Unit(stack, 1.0));
			} else {
				double ratio = match.damagedRatio(stack);
				if (ratio >= 0) for (int i = 0; i < stack.getCount(); i++) damaged.add(new Unit(stack, ratio));
			}
		}
		damaged.sort(Comparator.comparingDouble(Unit::ratio).reversed());
		exact.addAll(damaged);
		return exact;
	}

	/** Price ratios of the first {@code qty} items that would be sold, or {@code null} if there are fewer. */
	public static double[] sellRatios(Player player, Match match, Container extra, int qty) {
		List<Unit> order = sellOrder(player, match, extra);
		if (order.size() < qty) return null;
		double[] ratios = new double[qty];
		for (int i = 0; i < qty; i++) ratios[i] = order.get(i).ratio();
		return ratios;
	}

	/** Removes {@code qty} items in selling order (exact first, then the best damaged ones). */
	public static void remove(Player player, Match match, Container extra, int qty) {
		List<Unit> order = sellOrder(player, match, extra);
		for (int i = 0; i < qty && i < order.size(); i++) order.get(i).stack().shrink(1);
		player.getInventory().setChanged();
		if (extra != null) extra.setChanged();
	}

	public static int freeSpaceFor(Player player, Item item) {
		return freeSpaceFor(player, item, true);
	}

	/** @param coinSlotsFree count coin slots as empty (cash payments collect the coins first; card payments do not, E-8) */
	public static int freeSpaceFor(Player player, Item item, boolean coinSlotsFree) {
		return freeSpaceFor(player, Match.plain(item), coinSlotsFree);
	}

	/**
	 * How many items of {@code match} (with its components) fit in the main inventory. Coin slots count as empty because
	 * the coins are collected before the goods are handed over (E-3).
	 */
	public static int freeSpaceFor(Player player, Match match, boolean coinSlotsFree) {
		Inventory inventory = player.getInventory();
		int maxStack = new ItemStack(match.item()).getMaxStackSize();
		int space = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || (coinSlotsFree && stack.getItem() instanceof CoinItem)) {
				space += maxStack;
			} else if (match.exact(stack)) {
				space += Math.max(0, maxStack - stack.getCount());
			}
		}
		return space;
	}

	public static void give(Player player, Item item, int qty) {
		give(player, Match.plain(item), qty);
	}

	/** Gives {@code qty} items with the good's components; what does not fit is dropped. */
	public static void give(Player player, Match match, int qty) {
		Inventory inventory = player.getInventory();
		int maxStack = new ItemStack(match.item()).getMaxStackSize();
		int remaining = qty;
		while (remaining > 0) {
			int n = Math.min(remaining, maxStack);
			inventory.placeItemBackInInventory(new ItemStack(match.item().builtInRegistryHolder(), n, match.patch()), Prediction.SERVER_ONLY);
			remaining -= n;
		}
		inventory.setChanged();
	}
}
