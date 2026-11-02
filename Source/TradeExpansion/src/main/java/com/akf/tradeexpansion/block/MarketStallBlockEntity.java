package com.akf.tradeexpansion.block;

import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.registry.ModBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Remembers which market a stall belongs to (A-3). {@code -1} = not assigned yet
 * (naturally generated stalls get their market on first use).
 */
public class MarketStallBlockEntity extends BlockEntity implements ExtendedMenuProvider<MarketMenu.OpenData> {
	public static final long UNASSIGNED = -1;

	private long marketId = UNASSIGNED;

	public MarketStallBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MARKET_STALL, pos, state);
	}

	public long marketId() {
		return marketId;
	}

	/** Assigns this stall to a market (C-1). */
	public MarketRegistry.Assignment assign(ServerLevel level, MarketOrigin origin) {
		MarketRegistry.Assignment assignment = MarketRegistry.get(level.getServer()).assignStall(level, getBlockPos(), origin);
		if (assignment.market() != null) {
			marketId = assignment.market().id();
			setChanged();
		}
		return assignment;
	}

	/** The stall's market; assigns one (as natural) if missing. */
	public Market resolveMarket(ServerLevel level) {
		Market market = marketId == UNASSIGNED ? null : MarketRegistry.get(level.getServer()).get(marketId);
		if (market != null) return market;
		return assign(level, MarketOrigin.NATURAL).market();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		marketId = input.getLongOr("market_id", UNASSIGNED);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("market_id", marketId);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel && marketId != UNASSIGNED) {
			MarketRegistry.get(serverLevel.getServer()).removeStall(serverLevel.dimension(), pos, marketId);
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.trade_expansion.market", marketId);
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new MarketMenu(containerId, inventory, new MarketMenu.OpenData(marketId, getBlockPos()));
	}

	@Override
	public MarketMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new MarketMenu.OpenData(marketId, getBlockPos());
	}
}
