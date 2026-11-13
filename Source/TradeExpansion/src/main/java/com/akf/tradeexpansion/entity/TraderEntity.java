package com.akf.tradeexpansion.entity;

import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.TraderPalette;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketAccess;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Trader NPC (design A-5): opens the same trade screen as a stall. Linked traders (spawned when their stall is opened,
 * or placed with a village stall building) follow that stall's market and disappear with it; free traders (spawn egg,
 * {@code /summon}) join an existing market within {@code mergeRadius} and never found one. Invulnerable except for
 * {@code /kill}, does not despawn and stays where it is.
 */
public class TraderEntity extends PathfinderMob {
	private static final int STALL_CHECK_INTERVAL = 100;
	/** Robe colour (A-5, v0.60): picked at random when the trader first appears, saved, and synced for rendering. */
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(TraderEntity.class, EntityDataSerializers.INT);

	private long marketId = MarketStallBlockEntity.UNASSIGNED;
	/** Linked stall; {@code null} for a free trader. */
	private @Nullable BlockPos stallPos;
	/** Placed by a village stall building (stage 9): links to the stall next to it on its first tick. */
	private boolean linkNearbyStall;

	public TraderEntity(EntityType<? extends TraderEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setPermanentlyInvulnerable(true);
		// Loading a saved trader overwrites this; traders from old saves and structure NBT without a variant keep the random one.
		if (!level.isClientSide()) setVariant(random.nextInt(TraderPalette.VARIANT_COUNT));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
	}

	public int variant() {
		return TraderPalette.clamp(entityData.get(DATA_VARIANT));
	}

	public void setVariant(int variant) {
		entityData.set(DATA_VARIANT, TraderPalette.clamp(variant));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes();
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	/** Links this trader to a stall (spawn on open, A-5 ①). */
	public void linkTo(BlockPos stall, long market) {
		this.stallPos = stall.immutable();
		this.marketId = market;
		this.linkNearbyStall = false;
	}

	public @Nullable BlockPos stallPos() {
		return stallPos;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) return;
		if (linkNearbyStall) {
			linkNearbyStall = false;
			// A-5 ④: worldgen.spawnTraderNpc decides whether the building's trader stays.
			if (!TradeExpansionConfig.get().worldgen.spawnTraderNpc) {
				discard();
				return;
			}
			linkToNearbyStall(level);
		}
		if (stallPos != null && tickCount % STALL_CHECK_INTERVAL == 0 && level.isLoaded(stallPos)
				&& !(level.getBlockEntity(stallPos) instanceof MarketStallBlockEntity)) {
			discard(); // the linked stall is gone (A-3)
		}
	}

	private void linkToNearbyStall(ServerLevel level) {
		for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-2, -1, -2), blockPosition().offset(2, 1, 2))) {
			if (level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall && stall.adoptTrader(level, this)) {
				linkTo(pos, stall.marketId());
				return;
			}
		}
	}

	/** The market to trade with, or {@code null} (no market near a free trader, or the stall refuses, A-5). */
	private @Nullable Market resolveMarket(ServerLevel level) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		if (stallPos != null) {
			if (!(level.getBlockEntity(stallPos) instanceof MarketStallBlockEntity stall)) return null;
			Market market = stall.resolveMarket(level).market();
			if (market != null) marketId = market.id();
			return market;
		}
		Market market = marketId == MarketStallBlockEntity.UNASSIGNED ? null : registry.get(marketId);
		if (market == null) {
			// Free traders only join an existing market; they never found one (A-5).
			market = registry.nearestWithin(level.dimension(), blockPosition(), TradeExpansionConfig.get().distance.mergeRadius);
			marketId = market == null ? MarketStallBlockEntity.UNASSIGNED : market.id();
		}
		return market;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.getItemInHand(hand).is(net.minecraft.world.item.Items.NAME_TAG)) return super.mobInteract(player, hand);
		if (level() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
			Market market = resolveMarket(level);
			if (market == null) {
				player.sendOverlayMessage(Component.translatable("message.ak_trade_expansion.trader_no_market").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			lookAt(player, 30.0f, 30.0f);
			MarketAccess.open(serverPlayer, level, market, menuProvider(market));
		}
		return InteractionResult.SUCCESS;
	}

	private ExtendedMenuProvider<MarketMenu.OpenData> menuProvider(Market market) {
		MarketMenu.OpenData data = new MarketMenu.OpenData(market.id(), blockPosition(), getId());
		return new ExtendedMenuProvider<>() {
			@Override
			public MarketMenu.OpenData getScreenOpeningData(ServerPlayer player) {
				return data;
			}

			@Override
			public Component getDisplayName() {
				return MarketNames.title(market);
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
				return new MarketMenu(containerId, inventory, data);
			}
		};
	}

	// ---- stays put ----

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	// ---- save ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("market_id", marketId);
		output.putInt("variant", variant());
		if (stallPos != null) output.store("stall", BlockPos.CODEC, stallPos);
		if (linkNearbyStall) output.putBoolean("link_nearby_stall", true);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		marketId = input.getLongOr("market_id", MarketStallBlockEntity.UNASSIGNED);
		stallPos = input.read("stall", BlockPos.CODEC).orElse(null);
		linkNearbyStall = input.getBooleanOr("link_nearby_stall", false);
		input.getInt("variant").ifPresent(this::setVariant);
	}
}
