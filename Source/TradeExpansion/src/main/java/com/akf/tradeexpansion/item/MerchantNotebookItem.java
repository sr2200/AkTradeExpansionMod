package com.akf.tradeexpansion.item;

import com.akf.tradeexpansion.notebook.LedgerService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The merchant's notebook (design T, v0.81): using it opens the player's ledger (takings of the last days, the most
 * profitable goods, price notes of the markets visited). The ledger is kept by the server for each player, not in the
 * item: every notebook of a player shows the same pages.
 */
public class MerchantNotebookItem extends Item {
	public MerchantNotebookItem(Properties properties) {
		super(properties);
	}

	/** The server sends the ledger; the client opens the screen when it arrives. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) LedgerService.send(serverPlayer, true);
		return InteractionResult.SUCCESS;
	}
}
