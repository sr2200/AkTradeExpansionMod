package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.entity.TraderEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/** Trader NPC (design A-5): the villager model with the wandering trader's robe. */
public class TraderRenderer extends MobRenderer<TraderEntity, VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/wandering_trader/wandering_trader.png");

	public TraderRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5f);
	}

	@Override
	public VillagerRenderState createRenderState() {
		return new VillagerRenderState();
	}

	@Override
	public Identifier getTextureLocation(VillagerRenderState state) {
		return TEXTURE;
	}
}
