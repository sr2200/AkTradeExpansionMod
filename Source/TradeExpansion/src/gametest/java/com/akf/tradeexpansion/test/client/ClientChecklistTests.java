package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.config.ConfigScreen;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client game test (runClientGameTest, entrypoint "fabric-client-gametest"): a real client window, in Japanese
 * (screens are checked as Japanese players see them). Fabric stops at the first failing test, so this one test runs
 * every case itself, records each result (build/autotest/client-results.json) and fails at the end if any case failed.
 * Cases that need a world share one singleplayer world with a small village and a stall placed with the use key.
 * Screenshots go to build/autotest/screenshots for a person to look at. The cases are in this class,
 * {@link ClientTradeCases}, {@link ClientDisplayCases} and {@link ClientConfigCases}.
 * System properties: {@code ak.autotest.only} as for the server cases; {@code ak.autotest.minimal} (the run without
 * Mod Menu and YACL, runClientGameTestMinimal): only the startup case, results in client-minimal-results.json.
 */
public class ClientChecklistTests implements FabricClientGameTest {
	/** The language the screens are checked in; English is checked once more for the layout only. */
	static final String LANGUAGE = "ja_jp";

	@FunctionalInterface
	interface Body {
		void run(Session session, CaseResult.Builder result, CaseResult.Builder also) throws Exception;
	}

	@FunctionalInterface
	interface SimpleBody {
		void run(Session session, CaseResult.Builder result) throws Exception;
	}

	/**
	 * One case. {@code alsoChecklist} / {@code alsoPartial}: items the case covers too, recorded as a second result
	 * (with the same outcome, unless the case throws {@link AlsoFailed} for the second one only).
	 */
	record ClientCase(String name, List<String> checklist, String partial, boolean world, Body body, List<String> alsoChecklist, String alsoPartial) {
		ClientCase(String name, List<String> checklist, String partial, boolean world, SimpleBody body) {
			this(name, checklist, partial, world, (s, r, a) -> body.run(s, r), List.of(), null);
		}

		ClientCase(String name, List<String> checklist, String partial, boolean world, SimpleBody body, List<String> alsoChecklist, String alsoPartial) {
			this(name, checklist, partial, world, (s, r, a) -> body.run(s, r), alsoChecklist, alsoPartial);
		}
	}

	/** Thrown by a case when only its second item ({@code alsoChecklist}) failed: the first is recorded as passed. */
	static class AlsoFailed extends RuntimeException {
		AlsoFailed(Throwable cause) {
			super(cause.getMessage(), cause);
		}
	}

	/** The test context and, while world cases run, the shared world and the stall in it. */
	static final class Session {
		final ClientGameTestContext context;
		TestSingleplayerContext world;
		BlockPos stall;

		Session(ClientGameTestContext context) {
			this.context = context;
		}
	}

	private static List<ClientCase> cases() {
		List<ClientCase> cases = new ArrayList<>();
		cases.add(new ClientCase("startup_with_modmenu_yacl", List.of("1-2"), "Mod Menu・YACL ありで起動してタイトル画面が出ることのみ（Mod 一覧のアイコンは見ていない）",
				false, ClientChecklistTests::startup));
		cases.add(new ClientCase("config_screen_tabs", List.of("1-3"), "設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認）",
				false, ClientChecklistTests::configScreen));
		cases.add(new ClientCase("layout_config_screen", List.of("1-3"), "タイトル画面から開いた設定画面の各タブの文字が重ならず、画面からはみ出さず、翻訳キーがそのまま出ないこと（日本語と英語、854×480 と 1920×1080）。タイトル画面では「追加の商品と消耗品」が押せないこと",
				false, ClientConfigCases::layout));
		cases.add(new ClientCase("stall_place_and_open", List.of("2-8"), null, true, ClientChecklistTests::stallScreen));
		cases.add(new ClientCase("placement_hint", List.of("2-2"), null, true, ClientTradeCases::placementHint));
		cases.add(new ClientCase("buy_with_screen_buttons", List.of("3-10"), null, true, ClientChecklistTests::buyWithButtons,
				List.of("2-9", "2-10"), "交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない）"));
		cases.add(new ClientCase("price_arrows", List.of("3-1"), null, true, ClientTradeCases::priceArrows));
		cases.add(new ClientCase("balance_display", List.of("3-4"), null, true, ClientTradeCases::balance));
		cases.add(new ClientCase("row_states", List.of("3-8", "3-9"), null, true, ClientTradeCases::rowStates,
				List.of("3-7"), null));
		cases.add(new ClientCase("max_with_exact_money", List.of("3-11"), null, true, ClientTradeCases::maxWithExactMoney));
		cases.add(new ClientCase("sell_slot_selects_good", List.of("2-13"), null, true, ClientTradeCases::sellSlot));
		cases.add(new ClientCase("title_reputation_trends", List.of("16-1", "24-1"), null, true, ClientDisplayCases::titleRow));
		cases.add(new ClientCase("trend_position_setting", List.of("24-1b"), null, true, ClientDisplayCases::trendPosition));
		cases.add(new ClientCase("debug_tooltip", List.of("16-11"), null, true, ClientDisplayCases::debugTooltip));
		cases.add(new ClientCase("board_news_and_markets", List.of("23-3", "23-4", "24-4"), null, true, ClientDisplayCases::board,
				List.of("9-1"), "市場の行に名前と方角・距離が出て、ニュースから開いた市場の商品の一覧に列の見出し（列とずれていない、マウスで説明）があり、ホイールで最後の商品まで見えること（市場一覧の並び・買値以外の説明は見ていない）"));
		cases.add(new ClientCase("board_deals", List.of("9-1b"), null, true, ClientDisplayCases::boardDeals));
		cases.add(new ClientCase("map_coordinates_to_chat", List.of("20-5"), "JourneyMap なしで「この市場の座標」が掲示板の市場の一覧に出て、押すとこの市場の名前と座標が自分のチャットに出る（クリックでコピー）こと。市場を選ぶと消えること（近くに市場が無い時は見ていない）",
				true, ClientScreenCases::coordinates, List.of("20-1"), "JourneyMap なしで、選んだ市場の「座標を表示」を押すと、市場名と座標が自分のチャットに出て、クリックで座標がコピーされること（マルチの他のプレイヤーは見ていない）"));
		cases.add(new ClientCase("list_scrollbars", List.of("19-4"), null, true, ClientScreenCases::scrollbars));
		cases.add(new ClientCase("goods_names", List.of("30-1"), "交易画面と掲示板に並ぶ品（このワールドの市場の品）の名前が翻訳されていて、耐久値のある品が新品で並ぶこと（アイコンの見た目は画像で見る。JEI／REI は見ていない）",
				true, ClientScreenCases::goodsNames));
		cases.add(new ClientCase("staff_needs_reputation", List.of("26-2"), null, true, ClientScreenCases::staffNeedsReputation));
		cases.add(new ClientCase("sell_all_box", List.of("18-1", "31-2"), null, true, ClientDisplayCases::sellAll,
				List.of("18-2"), "行のクリックで除外が切り替わって合計が変わり、開き直しても残ること（道具・防具が最初から除外なのは、市場が買い取る道具・防具がある時だけ見る）"));
		cases.add(new ClientCase("goods_order", List.of("31-1"), null, true, ClientDisplayCases::goodsOrder));
		cases.add(new ClientCase("town_tab", List.of("22-20", "22-21", "31-3"), null, true, ClientDisplayCases::townTab,
				List.of("28-1", "29-1", "34-1", "34-2"), "噴水広場・公園を含む14種類の建物が出て、入りきらない時はホイールでスクロールでき、スクロールした後もクリックで正しい建物が選ばれること。行のツールチップに大きさ、選んだ建物の名前の右に大きさが出て、マウスで詳しい大きさが出ること（倉庫で。砂漠と平原の高さの違い・行の見え方は画像で見る）"));
		cases.add(new ClientCase("exchange_tab", List.of("8-6"), null, true, ClientDisplayCases::exchange));
		cases.add(new ClientCase("wallet_screen", List.of("7-4"), null, true, ClientDisplayCases::wallet));
		cases.add(new ClientCase("trader_opens_market", List.of("6-3"), null, true, ClientDisplayCases::traderOpens));
		cases.add(new ClientCase("advancements_screen", List.of("27-1"), null, true, (ses, r, a) -> ClientScreenCases.advancements(ses, r, a),
				List.of("27-11"), null));
		cases.add(new ClientCase("clock_advance_updates_open_screen", List.of("13-10"),
				"開いたままの交易画面に新しい時計が届き、依頼の残り時間の表示がすぐ減ること（建設・雇い人の残り時間は見ていない）", true, ClientChecklistTests::clockAdvance));
		cases.add(new ClientCase("villager_coin_prices", List.of("12-1"), null, true, ClientScreenCases::villagerPrices,
				List.of("12-4"), "支払い欄の下の「代金（所持）」が、所持金が足りない時は赤、足りる時は灰色になること（品物が出ない・取れないはサーバーのテストで見ている）"));
		cases.add(new ClientCase("bank_card_buttons", List.of("7-8", "7-9"), null, true, ClientBankQuestCases::cardButtons));
		cases.add(new ClientCase("layout_market_screen_ja", List.of("3-6", "19-1", "19-2", "19-3", "22-1", "26-1"),
				"交易画面の各タブ（掲示板で市場を選んだ状態・依頼と輸送依頼を選んだ状態・発展タブで建物を選んだ状態・「まとめて売る」の枠・カードを持っていて支払いのボタンがある状態も）の文字が重ならず、画面と枠からはみ出さず、翻訳キーがそのまま出ないこと（日本語、854×480 と 1920×1080、GUIの大きさ 1〜4）。一覧の行数・スクロールバーは見ていない",
				true, (s, r) -> layout(s, r, LANGUAGE)));
		cases.add(new ClientCase("layout_market_screen_en", List.of("26-1"), "英語でも交易画面の各タブ（日本語と同じ状態）の文字が重ならず、はみ出さず、翻訳キーがそのまま出ないこと",
				true, (s, r) -> layout(s, r, "en_us")));
		cases.add(new ClientCase("config_save_applies", List.of("13-1"), null, true, ClientConfigCases::saveApplies));
		cases.add(new ClientCase("special_settings", List.of("33-3"), "設定画面の「市場」タブに2つの設定があり、変えて保存するとすぐ設定とファイルに入ること（次の日の掘り出し物は見ていない）",
				true, ClientConfigCases::specialSettings));
		cases.add(new ClientCase("layout_goods_manager", List.of("1-3"), "「追加の商品と消耗品」画面（3つのモード）の文字が重ならず、画面からはみ出さず、翻訳キーがそのまま出ないこと（ワールドの中で開く。日本語と英語、854×480 と 1920×1080）",
				true, ClientConfigCases::goodsManagerLayout));
		cases.add(new ClientCase("goods_manager", List.of("13-6", "13-8", "13-9"), null, true, ClientConfigCases::goodsManager));
		cases.add(new ClientCase("bank_tab_by_level", List.of("5-4"), null, true, ClientBankQuestCases::bankTabByLevel,
				List.of("22-30"), "銀行の建物があればレベル1でも銀行タブが出て、そこで預けると利息の行が「200,000c まで・〇〇の銀行に登録」になること（カード払いの手数料は見ていない）"));
		cases.add(new ClientCase("bank_tab", List.of("7-7"), "銀行タブのボタン（預け入れ 1,000c・引き出し 100c・全額）で残高と現金が増減し、残高の行が変わること（死んだ時は見ていない）",
				true, ClientBankQuestCases::bankTab, List.of("16-13"), "銀行タブの利息の行「利息：1日 0.03%（100,000c まで）」（利息が付くことは見ていない）"));
		cases.add(new ClientCase("quest_tab", List.of("9-2"), null, true, ClientBankQuestCases::requests,
				List.of("16-5"), "「依頼を受ける」で「受付済み」になり、一覧に ✔ が出ること（評判の増え方は見ていない）"));
		cases.add(new ClientCase("quest_locked", List.of("16-4"), null, true, ClientBankQuestCases::lockedRequest, List.of("16-7"), null));
		cases.add(new ClientCase("transport_request", List.of("21-1", "21-5", "21-7"), null, true, ClientBankQuestCases::transport,
				List.of("21-2"), "「荷箱を受け取る」で保証金が引かれて荷箱が手に入り、ボタンが「受け取り済み」になること（荷箱の説明は見ていない）"));
		cases.add(new ClientCase("transport_taken_by_other", List.of("21-4"), "ほかの人が受け取った輸送依頼（受けた人をサーバーで足したもの）が一覧で灰色の「運送中」になり、受け取るボタンが出ないこと（本物の2人では見ていない）",
				true, ClientBankQuestCases::transportTaken));
		cases.add(new ClientCase("town_donations", List.of("22-2", "22-3", "22-4"), null, true, ClientTownCases::donations, List.of("28-8"), null));
		cases.add(new ClientCase("town_build_reasons", List.of("22-22", "22-25"), null, true, ClientTownCases::buildReasons,
				List.of("22-23b"), "資材が足りない時に「建てる」のツールチップが「資材があと N 個必要です」になり、development.requireMaterials を OFF にすると押せること（資材の数の倍率は見ていない）"));
		cases.add(new ClientCase("town_materials_and_stake", List.of("22-23"), "「資材を寄付（N）」の数が持っている資材の数で、押すと資材が減り、発展度が増え、残りの数が減ること（上限まで寄付した後は見ていない）",
				true, ClientTownCases::materialsAndStake, List.of("22-35"), "「杭をもらう」で杭が手に入り、持っている時はもらえないこと（置いた時は見ていない）"));
		cases.add(new ClientCase("town_row_states", List.of("22-24", "22-26", "22-33", "29-13"),
				"発展タブの行の「建設中 約N分」「✔ 完成」「壊れた（建て直しの発展度）」「✔ 今日は祭り／祭りまでN日」（建物はサーバーで直接作る。建設・完成・壊す流れは見ていない）",
				true, ClientTownCases::rowStates));
		cases.add(new ClientCase("facility_sign_opens_tab", List.of("22-32"), null, true, ClientTownCases::signOpensTab));
		cases.add(new ClientCase("tabs_turned_off", List.of("22-9", "26-20", "24-7"),
				"設定を OFF にすると画面を開き直した時に「発展」タブが消え、雇い人がいない人には「雇い人」タブが無く、いる人には出ること。流行の数を 0 にすると次の期間に「流行」が出ないこと（取引しても発展度が増えないこと・雇い人の操作は見ていない）",
				true, ClientTownCases::tabsTurnedOff));
		// The staff cases share their employees (in this order); staff_layout lets them go.
		cases.add(new ClientCase("staff_hire_carrier", List.of("26-4"), null, true, ClientStaffCases::hireCarrier,
				List.of("26-3"), "品の一覧の先頭が「おまかせ（売れる品を何種類も）」で、各行に「見込み +○c/往復」か「今は利益なし」が出て見込みの大きい順に並び、ツールチップにおすすめの行き先と数が出ること（宝箱のアイコンは画像で見る）"));
		cases.add(new ClientCase("staff_carrier_trip", List.of("26-6", "26-7", "26-9", "26-14"),
				"運び屋の行の「資金不足」「→運送 N分」「←帰路 N分」「休み」「値下がり待ち」、Shift で +1,000c／+10,000c になり10倍入ること、出かけている間は「解雇する」「変更」が押せないこと、「休止」が「再開」になること、往復の後にツールチップのログに往復が出ること、「設定」におすすめが出ること（「利益なし」は見ていない）",
				true, ClientStaffCases::carrierTrip));
		cases.add(new ClientCase("staff_clerk", List.of("26-13"), null, true, ClientStaffCases::clerk,
				List.of("26-11"), "番頭の品の一覧（手持ちのある品が先・「1日 約○c・手持ち○」・おすすめのツールチップ）、下限の初期値がおすすめ（売値の85%）、雇うと品が預けられること（少しずつ売れ続けるのは見ていない）"));
		cases.add(new ClientCase("staff_limit_and_other_market", List.of("26-15", "26-17"), null, true, ClientStaffCases::limitAndOtherMarket,
				List.of("26-5"), "おまかせを選ぶと設定が「1回に運ぶ数」だけになり、画面から雇えること"));
		cases.add(new ClientCase("staff_layout", List.of("26-25"), null, true, ClientStaffCases::layout));
		cases.add(new ClientCase("reopen_world_keeps_market",List.of("4-3"), null, true, ClientTradeCases::reopenWorld));
		// Breaks the stall: the last case of the world.
		cases.add(new ClientCase("screen_closes_when_away", List.of("2-16"), null, true, ClientTradeCases::closesWhenAway));
		cases.add(new ClientCase("dedicated_server_one_player", List.of("14-1"),
				"専用サーバー（このプロセスの中で起動）に1人で接続し、村に交易所を置いて交易画面を開けることのみ（2人は見ていない）", false,
				ClientConfigCases::dedicatedServer, List.of("13-2"), null));
		return cases;
	}

	/** The run without Mod Menu and YACL (checklist 1-1). */
	private static List<ClientCase> minimalCases() {
		return List.of(new ClientCase("startup_without_modmenu_yacl", List.of("1-1"), null, false, ClientChecklistTests::startupMinimal));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean minimal = Boolean.getBoolean("ak.autotest.minimal");
		setLanguage(context, LANGUAGE);
		if (Boolean.getBoolean("ak.autotest.shots")) {
			// shots.bat (runClientGameTestShots): only the screenshots for the download page, no checklist results.
			PostScreenshots.run(context);
			backToTitle(context);
			return;
		}
		Session session = new Session(context);
		List<CaseResult> results = new ArrayList<>();
		for (ClientCase c : minimal ? minimalCases() : filter(cases())) {
			CaseResult.Builder result = new CaseResult.Builder(c.name(), c.checklist(), c.partial());
			CaseResult.Builder also = new CaseResult.Builder(c.name(), c.alsoChecklist(), c.alsoPartial());
			try {
				if (c.world() && session.world == null) openWorld(session);
				if (!c.world() && session.world != null) closeWorld(session);
				c.body().run(session, result, also);
				results.add(result.pass());
				if (!c.alsoChecklist().isEmpty()) results.add(also.pass());
			} catch (Throwable t) {
				AutotestReport.LOGGER.error("Case {} failed", c.name(), t);
				if (t instanceof AlsoFailed) {
					// Only the second item failed.
					results.add(result.pass());
					results.add(also.fail(t.getCause()));
				} else {
					results.add(result.fail(t));
					if (!c.alsoChecklist().isEmpty()) results.add(also.fail(t));
				}
			}
			if (session.world != null) {
				context.setScreen(() -> null);
			} else {
				backToTitle(context);
			}
		}
		if (session.world != null) closeWorld(session);
		backToTitle(context);
		AutotestReport.write(minimal ? "client-minimal" : "client", results);
		long failed = results.stream().filter(r -> !r.ok()).count();
		if (failed > 0) throw new AssertionError(failed + " of " + results.size() + " client checklist results failed (see build/autotest/client-results.json)");
	}

	private static List<ClientCase> filter(List<ClientCase> cases) {
		String only = System.getProperty("ak.autotest.only");
		if (only == null || only.isBlank()) return cases;
		List<String> words = List.of(only.split("[,+]"));
		return cases.stream().filter(c -> words.stream().map(String::trim).filter(w -> !w.isEmpty())
				.anyMatch(w -> c.name().contains(w) || c.checklist().contains(w) || c.alsoChecklist().contains(w))).toList();
	}

	/** Fabric requires every test to end on the title screen with no world open. */
	private static void backToTitle(ClientGameTestContext context) {
		boolean inWorld = context.computeOnClient(client -> client.level != null);
		if (!inWorld && !(context.computeOnClient(client -> client.gui.screen()) instanceof TitleScreen)) {
			context.setScreen(() -> new TitleScreen());
		}
	}

	static void setLanguage(ClientGameTestContext context, String code) {
		if (code.equals(context.computeOnClient(client -> client.getLanguageManager().getSelected()))) return;
		var reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(code);
			client.options.languageCode = code;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone(), 2400);
		// The loading overlay fades out after the reload and takes the input until it is gone.
		context.waitFor(client -> client.gui.overlay() == null, 2400);
		context.waitTicks(5);
	}

	// ---- the shared world

	private static void openWorld(Session s) {
		ClientGameTestContext context = s.context;
		s.world = context.worldBuilder().create();
		s.world.getConnection().waitForChunksRender();
		s.world.getServer().runCommand("gamemode survival @a");
		applyTestConfig(s.world.getServer());
		s.stall = villageAndStall(s.world.getServer());
		context.waitTicks(5);
	}

	private static void closeWorld(Session s) {
		s.context.setScreen(() -> null);
		s.world.close();
		s.world = null;
		s.stall = null;
	}

	/** Opens the stall's trade screen with the use key (placing the stall first when no case did yet). */
	static MarketScreen openStall(Session s) {
		ClientGameTestContext context = s.context;
		if (context.computeOnClient(client -> client.gui.screen()) instanceof MarketScreen screen) return screen;
		context.setScreen(() -> null);
		ensureStall(s);
		context.getInput().lookAt(s.stall);
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(MarketScreen.class);
		context.waitTicks(10);
		return (MarketScreen) context.computeOnClient(client -> client.gui.screen());
	}

	private static boolean stallPlaced(Session s) {
		BlockPos target = s.stall;
		return s.world.getServer().computeOnServer(server -> server.overworld().getBlockState(target).is(ModBlocks.MARKET_STALL));
	}

	/** Places the shared stall with the use key (aiming at the ground in front) when no case did yet. */
	static void ensureStall(Session s) {
		if (stallPlaced(s)) return;
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> holdStall(player(server)));
		context.getInput().lookAt(s.stall.below());
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(5);
		check(stallPlaced(s), "the stall was not placed with the use key at %s", s.stall);
	}

	// ---- cases

	private static void startup(Session s, CaseResult.Builder result) {
		check(FabricLoader.getInstance().isModLoaded("modmenu"), "Mod Menu is not loaded");
		check(FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3"), "YACL is not loaded");
		s.context.waitForScreen(TitleScreen.class);
		screenshot(s.context, result, "1-2_title");
	}

	/** 1-1: Fabric API only (Mod Menu and YACL left out by Fabric Loader): the title screen comes up. */
	private static void startupMinimal(Session s, CaseResult.Builder result) {
		check(FabricLoader.getInstance().isModLoaded("ak_trade_expansion"), "AK Trade Expansion is not loaded");
		check(!FabricLoader.getInstance().isModLoaded("modmenu"), "Mod Menu is loaded (the run should leave it out)");
		check(!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3"), "YACL is loaded (the run should leave it out)");
		s.context.waitForScreen(TitleScreen.class);
		screenshot(s.context, result, "1-1_title_without_modmenu_yacl");
	}

	private static final List<String> CONFIG_TABS = List.of("currency", "price", "goods", "recovery", "distance", "market", "wallet",
			"bank", "trade", "other", "village", "reputation", "development", "staff", "client");

	private static void configScreen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> ConfigScreen.create(new TitleScreen()));
		context.waitTicks(5);
		Screen screen = context.computeOnClient(client -> client.gui.screen());
		check(screen != null && !(screen instanceof TitleScreen), "the config screen did not open (%s)", screen);
		// YACL's tabs are a TabNavigationBar, not buttons: select each tab through it.
		List<String> titles = context.computeOnClient(client -> tabBar(client.gui.screen()).getTabs().stream().map(t -> t.getTabTitle().getString()).toList());
		List<String> expected = CONFIG_TABS.stream().map(t -> Component.translatable("config.ak_trade_expansion.category." + t).getString()).toList();
		List<String> missing = expected.stream().filter(t -> !titles.contains(t)).toList();
		check(missing.isEmpty(), "config tabs not found: %s (tabs: %s)", missing, titles);
		for (int i = 0; i < titles.size(); i++) {
			int index = i;
			context.runOnClient(client -> tabBar(client.gui.screen()).selectTab(index, false));
			context.waitTicks(3);
			screenshot(context, result, String.format("1-3_config_%02d", i + 1));
		}
	}

	private static net.minecraft.client.gui.components.tabs.TabNavigationBar tabBar(Screen screen) {
		return screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
				.map(c -> (net.minecraft.client.gui.components.tabs.TabNavigationBar) c).findFirst()
				.orElseThrow(() -> new AssertionError("the config screen has no tab bar"));
	}

	private static void stallScreen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		BlockPos target = s.stall;
		// Place it with the use key, aiming at the ground in front.
		context.getInput().lookAt(target.below());
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(5);
		check(stallPlaced(s), "the stall was not placed with the use key at %s", target);
		String name = s.world.getServer().computeOnServer(server -> {
			Market market = MarketRegistry.get(server).nearestWithin(server.overworld().dimension(), target, 64);
			return market == null ? null : MarketNames.name(market).getString();
		});
		check(name != null, "placing the stall founded no market");
		// Open it with the use key.
		openStall(s);
		String title = context.computeOnClient(client -> client.gui.screen().getTitle().getString());
		check(title.contains(name), "the screen title \"%s\" does not show the market name \"%s\"", title, name);
		mouseAway(context);
		screenshot(context, result, "2-8_trade_screen");
	}

	/**
	 * 2-9 / 2-10 / 3-10 with the screen's own buttons: the quantity buttons change the total, 16 and 64 show the bulk
	 * discount, and "buy" takes exactly the shown total (the server refuses any other total: trade.priceTolerance 0).
	 */
	private static void buyWithButtons(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ensureStall(s);
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 1_000_000));
		context.waitTicks(3);
		MarketScreen screen = openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
		context.waitTicks(2);
		// The most expensive good with stock for 1 + 16 + 64 (cheap goods hide the bulk discount in rounding).
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		Identifier good = snapshot.goods().stream()
				.filter(v -> v.buy() && v.unit() == 1 && v.stock() >= 82 && v.patch().isEmpty())
				.max(Comparator.comparingDouble(v -> v.params().basePrice()))
				.orElseThrow(() -> new AssertionError("no good with stock for 81")).goods();
		context.runOnClient(client -> {
			set(screen, "selected", good);
			call(screen, "updateButtons");
		});
		String bulkLabel = Language.getInstance().getOrDefault("screen.ak_trade_expansion.bulk_discount").split("%s")[0];
		long previous = -1;
		for (int qty : new int[] {1, 16, 64}) {
			switch (qty) {
				case 1 -> clickWidget(context, "1");
				case 16 -> {
					clickWidget(context, "8");
					for (int i = 0; i < 8; i++) clickWidget(context, "+");
				}
				default -> clickWidget(context, "64");
			}
			mouseAway(context);
			int shownQty = context.computeOnClient(client -> (Integer) get(screen, "quantity"));
			check(shownQty == qty, "the quantity buttons set %d instead of %d", shownQty, qty);
			long shown = context.computeOnClient(client -> (Long) call(screen, "previewTotal", view(context, screen, good), qty));
			check(shown > previous, "the total did not grow with the quantity (%d -> %d at %d)", previous, shown, qty);
			previous = shown;
			List<TextRecorder.Text> texts = TextRecorder.capture(context);
			boolean discountShown = texts.stream().anyMatch(t -> t.text().startsWith(bulkLabel));
			check(discountShown == (qty >= 16), "the bulk discount line is %s at %d (texts: %s)", discountShown ? "shown" : "missing", qty, strings(texts));
			if (qty == 64) screenshot(context, result, "3-10_bulk_64");
			long before = serverMoney(s);
			int itemsBefore = serverCount(s, good);
			context.clickScreenButton("screen.ak_trade_expansion.confirm.buy");
			long after = before;
			for (int i = 0; i < 40 && after == before; i++) {
				context.waitTick();
				after = serverMoney(s);
			}
			check(before - after == shown, "buying %d took %dc, the screen showed %dc", qty, before - after, shown);
			int got = serverCount(s, good) - itemsBefore;
			check(got == qty, "buying %d gave %d items", qty, got);
			context.waitTicks(3); // the new snapshot
		}
	}

	/**
	 * 13-10: {@code /aktradeex clock advance} while the trade screen is open. The client gets the new clock offset and a
	 * fresh snapshot, and the time left of the selected request shrinks by the advanced time at once.
	 */
	private static void clockAdvance(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ensureStall(s);
		MarketScreen screen = openStall(s);
		context.clickScreenButton("screen.ak_trade_expansion.tab.quest");
		context.waitTicks(2);
		MarketSnapshotS2C before = snapshot(context, screen);
		// The request with the most time left (a request lasts quest.durationDays, 3 days by default).
		MarketSnapshotS2C.RequestView request = before.requests().stream()
				.max(Comparator.comparingLong(r -> r.request().deadline()))
				.orElseThrow(() -> new AssertionError("the market has no request"));
		context.runOnClient(client -> {
			set(screen, "selectedRequest", request.request().id());
			call(screen, "updateButtons");
		});
		mouseAway(context);
		long left0 = timeLeft(context);
		check(left0 > 15, "the request has only %d minutes left", left0);

		s.world.getServer().runCommand("aktradeex clock advance 0.5"); // = 10 minutes of the time left
		MarketSnapshotS2C after = newSnapshot(context, screen, before);
		check(after.gameTime() - before.gameTime() >= PriceModel.TICKS_PER_DAY / 2, "the snapshot's market time moved by %d ticks",
				after.gameTime() - before.gameTime());
		long serverOffset = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).clockOffset());
		long clientOffset = context.computeOnClient(client -> MarketClock.offset(client.level));
		check(clientOffset == serverOffset, "the client's clock offset is %d, the server's %d", clientOffset, serverOffset);
		context.waitTicks(2);
		long left1 = timeLeft(context);
		check(left0 - left1 >= 9 && left0 - left1 <= 11, "the time left went %d -> %d minutes (expected 10 less)", left0, left1);
		screenshot(context, result, "13-10_quest_after_clock_advance");
	}

	/** Minutes of "残り約%s分" on the screen. */
	private static long timeLeft(ClientGameTestContext context) {
		String[] parts = Language.getInstance().getOrDefault("screen.ak_trade_expansion.quest.time_left").split("%s", -1);
		List<TextRecorder.Text> texts = TextRecorder.capture(context);
		for (TextRecorder.Text t : texts) {
			String text = t.text();
			if (text.startsWith(parts[0]) && text.endsWith(parts[1])) {
				String number = text.substring(parts[0].length(), text.length() - parts[1].length()).replace(",", "").trim();
				if (number.matches("\\d+")) return Long.parseLong(number);
			}
		}
		throw new AssertionError("no time left on the screen (texts: " + strings(texts) + ")");
	}

	/** Window sizes and GUI scales (0 = auto, the largest that fits) the layout is checked at. */
	private static final int[][] SIZES = {{854, 480, 0}, {854, 480, 1}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 0}};

	/**
	 * Every tab of the trade screen, at every size: no texts on top of each other, none outside the screen or panel.
	 * The board is checked again with a market selected (the second market of {@link ClientTradeCases#placementHint}),
	 * the requests with a request and a transport request selected, the development tab with a building selected, and
	 * the sell tab with the "sell all" box open. The player carries a bank card: the payment button shows on the tabs.
	 */
	private static void layout(Session s, CaseResult.Builder result, String language) {
		ClientGameTestContext context = s.context;
		ensureStall(s);
		// Goods to sell, for the "sell all" box.
		MarketScreen first = openStall(s);
		MarketSnapshotS2C snapshot = snapshot(context, first);
		List<MarketSnapshotS2C.GoodView> sellable = snapshot.goods().stream().filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty()).limit(4).toList();
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> sellable.forEach(v -> giveItems(player(server), itemOf(v), 8)));
		ClientBankQuestCases.giveCard(s);
		int transport = ClientBankQuestCases.addTransport(s, null);
		context.waitTicks(3);
		setLanguage(context, language);
		openStall(s);
		List<String> problems = new ArrayList<>();
		boolean[] seen = new boolean[5]; // board market, request, sell all, transport, building
		try {
			for (int[] size : SIZES) {
				context.getInput().resizeWindow(size[0], size[1]);
				context.runOnClient(client -> {
					client.options.guiScale().set(size[2]);
					client.resizeGui();
				});
				context.waitTicks(3);
				List<String> tabs = context.computeOnClient(client -> visibleTabs(client.gui.screen()));
				check(!tabs.isEmpty(), "no tab buttons on the trade screen");
				for (String tab : tabs) {
					context.clickScreenButton(tab);
					String where = String.format("%s %dx%d GUI %s %s", language, size[0], size[1], size[2] == 0 ? "auto" : size[2],
							tab.substring(tab.lastIndexOf('.') + 1));
					// Japanese at the first size: every tab, for a person to look at; otherwise the first problem of a size.
					boolean everyTab = language.equals(LANGUAGE) && size == SIZES[0];
					checkLayout(context, result, where, everyTab, problems, null);
					MarketScreen screen = screen(context);
					switch (tab.substring(tab.lastIndexOf('.') + 1)) {
						case "board" -> {
							BoardS2C board = context.computeOnClient(client -> (BoardS2C) get(screen, "board"));
							if (board != null && !board.markets().isEmpty()) {
								setOnScreen(context, screen, "boardMarket", board.markets().getFirst().id());
								checkLayout(context, result, where + "(market)", everyTab, problems, null);
								setOnScreen(context, screen, "boardMarket", -1L);
								seen[0] = true;
							}
						}
						case "quest" -> {
							MarketSnapshotS2C now = snapshot(context, screen);
							var plain = now.requests().stream().filter(r -> r.transport().isEmpty()).findFirst();
							if (plain.isPresent()) {
								setOnScreen(context, screen, "selectedRequest", plain.get().request().id());
								checkLayout(context, result, where + "(request)", everyTab, problems, null);
								seen[1] = true;
							}
							if (now.requests().stream().anyMatch(r -> r.request().id() == transport)) {
								setOnScreen(context, screen, "selectedRequest", transport);
								checkLayout(context, result, where + "(transport)", everyTab, problems, null);
								seen[3] = true;
							}
							setOnScreen(context, screen, "selectedRequest", -1);
						}
						case "town" -> {
							MarketSnapshotS2C now = snapshot(context, screen);
							if (!now.development().facilities().isEmpty()) {
								setOnScreen(context, screen, "selectedFacility", now.development().facilities().getLast().id());
								checkLayout(context, result, where + "(building)", everyTab, problems, null);
								setOnScreen(context, screen, "selectedFacility", null);
								seen[4] = true;
							}
						}
						case "sell" -> {
							context.clickScreenButton("screen.ak_trade_expansion.sell_all");
							context.waitTicks(2);
							int[] box = context.computeOnClient(client -> {
								int x = leftPos(screen) + constant(MarketScreen.class, "SELL_ALL_X");
								int y = topPos(screen) + constant(MarketScreen.class, "SELL_ALL_Y");
								return new int[] {x, y, x + (Integer) get(screen, "sellAllW"), y + (Integer) get(screen, "sellAllH")};
							});
							check(context.computeOnClient(client -> (Boolean) get(screen, "sellAllOpen")), "the \"sell all\" box did not open");
							checkLayout(context, result, where + "(sell all)", everyTab, problems, box);
							context.clickScreenButton("gui.cancel");
							context.waitTicks(2);
							seen[2] = true;
						}
						default -> {
						}
					}
				}
			}
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
			});
			setLanguage(context, LANGUAGE);
			context.setScreen(() -> null);
			ClientBankQuestCases.takeCards(s);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, s.stall);
				market.requests().removeIf(r -> r.id() == transport);
				MarketRegistry.get(server).setDirty();
			});
		}
		problems.forEach(p -> AutotestReport.LOGGER.info("LAYOUT {}", p));
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
		check(seen[0], "the board listed no other market to select (the case placement_hint founds one)");
		check(seen[1], "the market has no request to select");
		check(seen[2], "the sell tab was not checked with the \"sell all\" box");
		check(seen[3], "the transport request was not listed");
		check(seen[4], "the development tab listed no building");
	}

	private static void setOnScreen(ClientGameTestContext context, MarketScreen screen, String field, Object value) {
		context.runOnClient(client -> {
			set(screen, field, value);
			set(screen, "scroll", 0);
			call(screen, "updateButtons");
		});
	}

	/**
	 * Checks the texts of the screen as it is now. {@code box}: the "sell all" box drawn over the list (x0, y0, x1,
	 * y1): the list texts under it are left out.
	 */
	private static void checkLayout(ClientGameTestContext context, CaseResult.Builder result, String where, boolean screenshotAlways,
			List<String> problems, int[] box) {
		mouseAway(context);
		// Toasts (advancements) slide in from outside the screen: not part of the screen's layout.
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(4);
		List<TextRecorder.Text> all = TextRecorder.capture(context);
		List<TextRecorder.Text> texts = box == null ? all
				: all.stream().filter(t -> t.afterMark() || !t.inside(box[0], box[1], box[2], box[3])).toList();
		List<String> found = context.computeOnClient(client -> LayoutCheck.check(where, texts,
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), panel(client.gui.screen())));
		String size = where.substring(0, where.lastIndexOf(' '));
		if (screenshotAlways || !found.isEmpty() && problems.stream().noneMatch(p -> p.startsWith(size))) {
			screenshot(context, result, "layout_" + where);
		}
		problems.addAll(found);
	}

	private static List<String> visibleTabs(Screen screen) {
		List<String> keys = new ArrayList<>();
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().startsWith("screen.ak_trade_expansion.tab.")) {
				keys.add(t.getKey());
			}
		}
		return keys;
	}

	static LayoutCheck.Panel panel(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> c)) return null;
		int left = leftPos(c);
		int top = topPos(c);
		int w = (Integer) get(c, AbstractContainerScreen.class, "imageWidth");
		int h = (Integer) get(c, AbstractContainerScreen.class, "imageHeight");
		return new LayoutCheck.Panel(left, top, left + w, top + h);
	}

}
