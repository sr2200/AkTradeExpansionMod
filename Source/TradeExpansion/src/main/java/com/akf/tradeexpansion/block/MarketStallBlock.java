package com.akf.tradeexpansion.block;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Market stall (design A-3). Right-click opens the trade screen.
 */
public class MarketStallBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Outline of the stall model (counter, posts, awning), defined facing north and rotated per facing. */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(0, 0, 0, 16, 10, 10),
			Block.box(0, 10, 0.5, 1.5, 14, 2),
			Block.box(14.5, 10, 0.5, 16, 14, 2),
			Block.box(0, 0, 14, 1.5, 14, 15.5),
			Block.box(14.5, 0, 14, 16, 14, 15.5),
			Block.box(0, 12, 0, 16, 15.5, 16)));

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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
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
			player.sendSystemMessage(tooCloseMessage(assignment));
			level.destroyBlock(pos, !player.isCreative());
		} else {
			player.sendSystemMessage(Component.translatable("message.trade_expansion.stall_joined", assignment.market().id())
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component tooCloseMessage(MarketRegistry.Assignment assignment) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		return Component.translatable("message.trade_expansion.stall_too_close",
				(int) assignment.nearestDistance(), (int) config.distance.mergeRadius, (int) config.distance.minNewMarketDistance)
				.withStyle(ChatFormatting.RED);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
			MarketRegistry.Assignment assignment = stall.resolveMarket(serverLevel);
			Market market = assignment.market();
			if (market == null) {
				// A crafted stall whose market was deleted and that may not found a new one here (A-3): keep the block.
				player.sendSystemMessage(tooCloseMessage(assignment));
				return InteractionResult.FAIL;
			}

			MarketRegistry registry = MarketRegistry.get(serverLevel.getServer());
			if (MarketFactory.growIfDue(serverLevel, market, registry.inDimension(serverLevel.dimension()))) {
				// D-4: tell the player the market has grown.
				player.sendSystemMessage(Component.translatable("message.trade_expansion.market_grew", market.id(),
						MarketStallBlockEntity.levelName(market.level())).withStyle(ChatFormatting.GREEN));
			}
			registry.setDirty();

			serverPlayer.openMenu(stall);
			long now = MarketUpkeep.touch(serverLevel, market);
			ServerPlayNetworking.send(serverPlayer, MarketSnapshotS2C.of(market, now));
			BankService.sendAccount(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}
