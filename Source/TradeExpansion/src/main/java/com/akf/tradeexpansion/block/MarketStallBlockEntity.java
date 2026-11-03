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
 * Remembers which market a stall belongs to and how it was placed (A-3). {@code -1} = not assigned yet
 * (naturally generated stalls get their market on first use). The origin decides the C-1 distance rule when the
 * stall has to be reassigned after its market was deleted.
 */
public class MarketStallBlockEntity extends BlockEntity implements ExtendedMenuProvider<MarketMenu.OpenData> {
	public static final long UNASSIGNED = -1;

	private long marketId = UNASSIGNED;
	/** Natural until a player places it (A-3). */
	private MarketOrigin origin = MarketOrigin.NATURAL;

	public MarketStallBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MARKET_STALL, pos, state);
	}

	/** Natural (village) or crafted stall (A-3); natural ones may be protected from breaking. */
	public MarketOrigin origin() {
		return origin;
	}

	public long marketId() {
		return marketId;
	}

	/** Assigns this stall to a market (C-1) and remembers the origin. */
	public MarketRegistry.Assignment assign(ServerLevel level, MarketOrigin origin) {
		this.origin = origin;
		setChanged();
		MarketRegistry.Assignment assignment = MarketRegistry.get(level.getServer()).assignStall(level, getBlockPos(), origin);
		if (assignment.market() != null) {
			marketId = assignment.market().id();
			setChanged();
		}
		return assignment;
	}

	/**
	 * The stall's market. Assigns one if it has none yet or its market was deleted, using the stored origin:
	 * a crafted stall may then be refused (C-1), in which case {@code market()} is {@code null}.
	 */
	public MarketRegistry.Assignment resolveMarket(ServerLevel level) {
		Market market = marketId == UNASSIGNED ? null : MarketRegistry.get(level.getServer()).get(marketId);
		if (market != null) return new MarketRegistry.Assignment(market, null, 0);
		return assign(level, origin);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		marketId = input.getLongOr("market_id", UNASSIGNED);
		// Stalls saved before stage 8 have no origin: assigned ones count as crafted (the safe side, A-3).
		origin = input.read("origin", MarketOrigin.CODEC).orElse(marketId == UNASSIGNED ? MarketOrigin.NATURAL : MarketOrigin.CRAFTED);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("market_id", marketId);
		output.store("origin", MarketOrigin.CODEC, origin);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel && marketId != UNASSIGNED) {
			MarketRegistry.get(serverLevel.getServer()).removeStall(serverLevel.dimension(), pos, marketId);
		}
	}

	/** Title of the trade screen: "Market #12 (Town)" (D-4). */
	@Override
	public Component getDisplayName() {
		Market market = level instanceof ServerLevel serverLevel && marketId != UNASSIGNED
				? MarketRegistry.get(serverLevel.getServer()).get(marketId) : null;
		if (market == null) return Component.translatable("container.trade_expansion.market", marketId);
		return Component.translatable("container.trade_expansion.market_level", marketId, levelName(market.level()));
	}

	/** "Hamlet" .. "City" (D-4). */
	public static Component levelName(int level) {
		return Component.translatable("market_level.trade_expansion." + level);
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
