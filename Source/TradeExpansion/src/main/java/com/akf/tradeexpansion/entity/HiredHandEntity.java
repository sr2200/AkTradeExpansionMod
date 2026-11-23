package com.akf.tradeexpansion.entity;

import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketAccess;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.staff.HiredHands;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
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

import java.util.UUID;

/**
 * An employee standing at its home market (design Q-6, v0.83): a carrier while it is at home, a clerk always. Shown and
 * taken away by {@link HiredHands}; never saved with the chunk (it is placed again when the chunk is loaded). Talking to
 * it opens the trade screen on the staff tab with the employee selected (its owner), or says whose employee it is.
 * Invulnerable, does not move or despawn.
 */
public class HiredHandEntity extends PathfinderMob {
	/** 0 = carrier, 1 = clerk (the clothes of the model). */
	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(HiredHandEntity.class, EntityDataSerializers.INT);

	private int employeeId = -1;
	private long marketId = -1;
	private UUID owner;

	public HiredHandEntity(EntityType<? extends HiredHandEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setPermanentlyInvulnerable(true);
	}

	/** Who this villager stands for (set by {@link HiredHands} before it is added to the level). */
	public void bind(int employeeId, long marketId, UUID owner, int role) {
		this.employeeId = employeeId;
		this.marketId = marketId;
		this.owner = owner;
		entityData.set(DATA_ROLE, role);
	}

	public int employeeId() {
		return employeeId;
	}

	public long marketId() {
		return marketId;
	}

	public int role() {
		return entityData.get(DATA_ROLE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes();
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		// One not placed by HiredHands (or no longer wanted there) goes away.
		if (level() instanceof ServerLevel && tickCount % 20 == 0 && !HiredHands.wanted(this)) discard();
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!(level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		Market market = MarketRegistry.get(level.getServer()).get(marketId);
		if (market == null) return InteractionResult.FAIL;
		lookAt(player, 30.0f, 30.0f);
		if (!player.getUUID().equals(owner)) {
			player.sendOverlayMessage(Component.translatable("message.ak_trade_expansion.hired_hand.other", getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		MarketAccess.open(serverPlayer, level, market, menuProvider(market));
		return InteractionResult.SUCCESS;
	}

	private ExtendedMenuProvider<MarketMenu.OpenData> menuProvider(Market market) {
		// The screen stays valid near where it stood, even when a carrier leaves for a trip (MarketMenu.HIRED_HAND).
		MarketMenu.OpenData data = new MarketMenu.OpenData(market.id(), blockPosition(), MarketMenu.HIRED_HAND, "staff:" + employeeId);
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

	// ---- stays put, never saved ----

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

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
}
