package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.development.FacilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * N-4 ({@code development.protectFacilities}): explosions leave completed facility buildings standing.
 */
@Mixin(value = ServerExplosion.class, remap = false)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void tradeExpansion$protectFacilities(CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> positions = cir.getReturnValue();
		if (positions == null || positions.isEmpty() || !FacilityBuilder.protectionOn()) return;
		List<BlockPos> kept = new ArrayList<>(positions.size());
		for (BlockPos pos : positions) {
			if (!FacilityBuilder.isProtected(level, pos)) kept.add(pos);
		}
		if (kept.size() != positions.size()) cir.setReturnValue(kept);
	}
}
