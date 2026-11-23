package com.akf.tradeexpansion.staff;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.entity.HiredHandEntity;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Employees as villagers at their home market (design Q-6, v0.83, {@code staff.showEmployees}). Every few seconds each
 * employee that should be seen there (a clerk; a carrier at home; not one that quit) gets a {@link HiredHandEntity} near
 * the market's center when that place is loaded, and villagers no longer wanted go away (a carrier that left on a
 * trip, a dismissed employee). The villagers are not saved: a loaded chunk gets them again.
 */
public final class HiredHands {
	/** Employee id → its villager (only while it exists). */
	private static final Map<Integer, HiredHandEntity> SHOWN = new HashMap<>();
	/** Distance from the market's center of the ring the villagers stand on, and the height searched for a floor. */
	private static final int RING = 4;
	private static final int SEARCH_UP = 3;
	private static final int SEARCH_DOWN = 5;

	private HiredHands() {
	}

	/** Should employee {@code e} stand at its home market now? */
	static boolean shouldShow(Employee e) {
		TradeExpansionConfig.Staff c = TradeExpansionConfig.get().staff;
		if (!c.enabled || !c.showEmployees || e.quit()) return false;
		return e.role == Employee.Role.CLERK || e.phase == Employee.Phase.HOME;
	}

	/** {@code hand} is the villager placed for its employee and still wanted (it removes itself otherwise). */
	public static boolean wanted(HiredHandEntity hand) {
		return SHOWN.get(hand.employeeId()) == hand;
	}

	/** Places and removes the villagers (from the staff update, every few seconds). */
	public static void update(MinecraftServer server, MarketRegistry registry) {
		Map<Integer, Employee> byId = new HashMap<>();
		for (Employee e : registry.employees()) byId.put(e.id, e);
		// Villagers no longer wanted (or gone with their chunk) are forgotten.
		for (Iterator<Map.Entry<Integer, HiredHandEntity>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, HiredHandEntity> entry = it.next();
			HiredHandEntity hand = entry.getValue();
			Employee e = byId.get(entry.getKey());
			if (hand.isRemoved()) {
				it.remove();
			} else if (e == null || !shouldShow(e) || registry.get(e.home) == null) {
				if (e != null && inUse(server, hand)) continue; // its owner is looking at the staff tab it opened: a little later
				it.remove();
				hand.discard();
			} else {
				hand.setCustomName(name(e)); // the goods may have changed
			}
		}
		for (Employee e : registry.employees()) {
			if (SHOWN.containsKey(e.id) || !shouldShow(e)) continue;
			Market home = registry.get(e.home);
			if (home == null) continue;
			ServerLevel level = server.getLevel(home.dimension());
			if (level == null || !level.isPositionEntityTicking(home.center())) continue;
			BlockPos pos = standingPlace(level, home.center(), e.id);
			if (pos == null) continue;
			HiredHandEntity hand = ModEntities.HIRED_HAND.create(level, EntitySpawnReason.TRIGGERED);
			if (hand == null) continue;
			hand.bind(e.id, home.id(), e.owner, e.role == Employee.Role.CARRIER ? 0 : 1);
			hand.setCustomName(name(e));
			hand.setCustomNameVisible(true);
			float yaw = (float) Math.toDegrees(Math.atan2(home.center().getX() - (pos.getX() + 0.5), (pos.getZ() + 0.5) - home.center().getZ()));
			hand.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
			hand.setYHeadRot(yaw);
			SHOWN.put(e.id, hand); // before adding: its first tick asks wanted()
			if (!level.addFreshEntity(hand)) SHOWN.remove(e.id);
		}
	}

	/** Some player has the trade screen open that was opened by talking to {@code hand}. */
	private static boolean inUse(MinecraftServer server, HiredHandEntity hand) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof MarketMenu menu && menu.marketId() == hand.marketId() && menu.stallPos().equals(hand.blockPosition())) {
				return true;
			}
		}
		return false;
	}

	/** "Alex's carrier (iron ingot)". */
	static Component name(Employee e) {
		return Component.translatable("entity.ak_trade_expansion.hired_hand.name", e.ownerName, StaffService.roleName(e.role), StaffService.itemName(e.goods));
	}

	/**
	 * Where employee {@code id} stands: on a ring around {@code center} (each employee at its own angle, then the next
	 * ones), on a sturdy floor with two free blocks above, not in water. {@code null} when there is no such place.
	 */
	static BlockPos standingPlace(ServerLevel level, BlockPos center, int id) {
		int start = Math.floorMod(id * 3, 8);
		for (int radius = RING; radius <= RING + 2; radius++) {
			for (int k = 0; k < 8; k++) {
				double angle = Math.PI * 2 * ((start + k) % 8) / 8.0;
				int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
				int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
				for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
					BlockPos pos = new BlockPos(x, center.getY() + dy, z);
					if (standable(level, pos)) return pos;
				}
			}
		}
		return null;
	}

	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP)) return false;
		for (BlockPos p : new BlockPos[] {pos, pos.above()}) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	/** Removes every villager (the server stops). */
	public static void clear() {
		SHOWN.clear();
	}
}
