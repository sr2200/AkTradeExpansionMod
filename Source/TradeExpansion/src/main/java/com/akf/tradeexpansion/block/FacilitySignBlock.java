package com.akf.tradeexpansion.block;

import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Sign of a facility (design N-4 / N-5). {@code built=false}: the construction sign at the front of a building site
 * (right-click: what is being built and how long it takes). {@code built=true}: the core of a completed building; it
 * opens the trade screen on the facility's tab, and breaking it ends the facility's effect.
 */
public class FacilitySignBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<FacilityKind> KIND = EnumProperty.create("kind", FacilityKind.class);
	public static final BooleanProperty BUILT = BooleanProperty.create("built");
	/** A post with a board on top, defined facing north (matches the block models). */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(7, 0, 7, 9, 9, 9),
			Block.box(1, 9, 7, 15, 16, 9)));

	public FacilitySignBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(KIND, FacilityKind.WAREHOUSE).setValue(BUILT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, KIND, BUILT);
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
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			FacilityBuilder.useSign(serverPlayer, serverLevel, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	/** N-4: the core of a completed building was broken, blown up or replaced: the facility's effect ends. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (state.getValue(BUILT)) FacilityBuilder.onSignRemoved(level, pos);
	}
}
