package com.akf.tradeexpansion.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets {@link com.akf.tradeexpansion.worldgen.VillageStalls} add the stall building to vanilla village pools (design stage 9). */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	/** Weighted list the pool draws from: each element appears {@code weight} times. */
	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> tradeExpansion$templates();

	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> tradeExpansion$rawTemplates();

	/** The codec may hand the pool an immutable list. */
	@Mutable
	@Accessor("rawTemplates")
	void tradeExpansion$setRawTemplates(List<Pair<StructurePoolElement, Integer>> rawTemplates);
}
