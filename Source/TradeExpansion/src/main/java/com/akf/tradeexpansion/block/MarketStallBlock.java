package com.akf.tradeexpansion.block;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Market stall (design A-3). Right-click opens the trade screen.
 */
public class MarketStallBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public MarketStallBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MarketStallBlockEntity(pos, state);
	}

	/** A player-placed stall joins a nearby market or founds a new one; too close to a market = refused (C-1). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)) return;
		if (!(level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall)) return;

		MarketRegistry.Assignment assignment = stall.assign(serverLevel, MarketOrigin.CRAFTED);
		if (assignment.market() == null) {
			TradeExpansionConfig config = TradeExpansionConfig.get();
			player.sendSystemMessage(Component.translatable("message.trade_expansion.stall_too_close",
					(int) assignment.nearestDistance(), (int) config.mergeRadius(), (int) config.minNewMarketDistance())
					.withStyle(ChatFormatting.RED));
			level.destroyBlock(pos, !player.isCreative());
		} else {
			player.sendSystemMessage(Component.translatable("message.trade_expansion.stall_joined", assignment.market().id())
					.withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
			Market market = stall.resolveMarket(serverLevel);
			if (market == null) return InteractionResult.FAIL;

			serverPlayer.openMenu(stall);
			long now = serverLevel.getGameTime();
			market.refresh(now);
			ServerPlayNetworking.send(serverPlayer, MarketSnapshotS2C.of(market, now));
		}
		return InteractionResult.SUCCESS;
	}
}
