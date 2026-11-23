package com.akf.tradeexpansion.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Lets {@link com.akf.tradeexpansion.development.FacilityBuilder} compare the blocks of two looks of a building (N-11,
 * v0.86), so that raising it to the next level changes only the blocks that differ.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public interface StructureTemplateAccessor {
	@Accessor("palettes")
	List<StructureTemplate.Palette> tradeExpansion$palettes();
}
