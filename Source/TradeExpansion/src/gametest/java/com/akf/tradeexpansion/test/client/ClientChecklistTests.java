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
 * {@link ClientTradeCases}, {@link ClientDisplayCases}, {@link ClientListCases}, {@link ClientItemCases}, {@link ClientWindowCases}, {@link ClientLongTextCases} and {@link ClientConfigCases}.
 * System properties: {@code ak.autotest.only} as for the server cases; {@code ak.autotest.variant} for the runs with
 * other mods left out: {@code minimal} (without Mod Menu and YACL, runClientGameTestMinimal: the startup and trade
 * cases), {@code modmenu-only} (runClientGameTestModMenuOnly) and {@code yacl-only} (runClientGameTestYaclOnly), each
 * with results in client-<variant>-results.json; and for the runs with one more mod: {@code jei}, {@code rei}
 * (runClientGameTestJei / Rei: the recipe viewer pages) and {@code journeymap} (runClientGameTestJourneyMap); {@code looks}
 * (runClientGameTestLooks) takes the pictures of the buildings.
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
		cases.add(new ClientCase("startup_with_modmenu_yacl", List.of("1-2"), null, false, (ses, r) -> {
			startup(ses, r);
			ClientItemCases.modList(ses, r);
		}));
		cases.add(new ClientCase("config_screen_tabs", List.of("1-3"), "設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認）",
				false, ClientChecklistTests::configScreen));
		cases.add(new ClientCase("layout_config_screen", List.of("1-3"), "タイトル画面から開いた設定画面の各タブの文字が重ならず、画面からはみ出さず、翻訳キーがそのまま出ないこと（日本語と英語、854×480 と 1920×1080）。タイトル画面では「追加の商品と消耗品」が押せないこと",
				false, ClientConfigCases::layout));
		cases.add(new ClientCase("stall_place_and_open", List.of("2-8"), null, true, ClientChecklistTests::stallScreen));
		cases.add(new ClientCase("creative_tab", List.of("1-4"), null, true, ClientItemCases::creativeTab));
		cases.add(new ClientCase("item_tooltips", List.of("1-5"), null, true, ClientItemCases::tooltips));
		cases.add(new ClientCase("cargo_tooltip", List.of("21-2"), "荷箱の説明に中身・届け先・場所（ネザーなら「（ネザー）」）・報酬と保証金・依頼元・残り時間が出ること",
				true, ClientItemCases::cargoTooltip, List.of("21-8"), "期限を過ぎた荷箱の説明が赤い「期限切れ（届けられません）」になり、残り時間が消えること"));
		cases.add(new ClientCase("placement_hint", List.of("2-2"), null, true, ClientTradeCases::placementHint));
		cases.add(new ClientCase("buy_with_screen_buttons", List.of("3-10"), null, true, ClientChecklistTests::buyWithButtons,
				List.of("2-9", "2-10"), "交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない）"));
		cases.add(new ClientCase("price_arrows", List.of("3-1"), null, true, ClientTradeCases::priceArrows));
		cases.add(new ClientCase("balance_display", List.of("3-4"), null, true, ClientTradeCases::balance));
		cases.add(new ClientCase("balance_position_setting", List.of("3-5"), null, true, ClientListCases::balancePosition));
		cases.add(new ClientCase("row_states", List.of("3-8", "3-9"), null, true, ClientTradeCases::rowStates,
				List.of("3-7", "23-11"), null));
		cases.add(new ClientCase("row_meter_and_graph", List.of("3-2", "3-3"), null, true, ClientListCases::meterAndGraph));
		cases.add(new ClientCase("row_rival_and_trend_tooltips", List.of("23-2", "24-2"), null, true, ClientListCases::rivalAndTrendTooltips));
		cases.add(new ClientCase("max_with_exact_money", List.of("3-11"), null, true, ClientTradeCases::maxWithExactMoney));
		cases.add(new ClientCase("max_and_change", List.of("2-9", "2-10"), null, true, ClientBuySellCases::maxAndChange));
		cases.add(new ClientCase("shift_confirm_all", List.of("2-14"), null, true, ClientBuySellCases::shiftConfirm));
		cases.add(new ClientCase("special_and_emerald_rows", List.of("5-7"), "本日の掘り出し物の行の名前に★が付いて水色になり、ツールチップに説明が出ること（入れ替わり・レベルごとの数はサーバーのテストで見ている）",
				true, ClientBuySellCases::specialsAndEmeralds, List.of("5-2"), "売却タブのエメラルド貨の行が「4c/10」のように10枚あたりの値段で、選ぶと数量が「1 (10)」と10枚単位になること（ネザー・エンド・前のバージョンの市場は見ていない）"));
		cases.add(new ClientCase("other_player_buys", List.of("14-2"), "サーバーに足した2人目のプレイヤー（同じ屋台の画面を開いている）が10個買うと、開いたままの画面の行の値段と在庫がすぐ変わること（本物の2人では見ていない）",
				true, ClientBuySellCases::otherPlayerBuys));
		cases.add(new ClientCase("regular_customer_prices", List.of("16-3"), null, true, ClientBuySellCases::regularCustomer));
		cases.add(new ClientCase("price_changed_message", List.of("23-10"), "選んだ品の値段をサーバーで変えてから（日をまたいで商会や流行が動いた時の代わり）表示どおりの額で買うと、赤い「価格が変動しました…」が出て、品もお金も動かず、画面が最新の値段になること",
				true, ClientBuySellCases::priceChanged));
		cases.add(new ClientCase("sell_slot_selects_good", List.of("2-13"), null, true, ClientTradeCases::sellSlot));
		cases.add(new ClientCase("shift_click_sell_slot", List.of("2-19"), null, true, ClientWindowCases::shiftClick));
		cases.add(new ClientCase("title_reputation_trends", List.of("16-1", "24-1"), null, true, ClientDisplayCases::titleRow));
		cases.add(new ClientCase("trend_position_setting", List.of("24-1b"), null, true, ClientDisplayCases::trendPosition));
		cases.add(new ClientCase("debug_tooltip", List.of("16-11"), null, true, ClientDisplayCases::debugTooltip));
		cases.add(new ClientCase("board_news_and_markets", List.of("23-3", "23-4", "24-4"), null, true, ClientDisplayCases::board,
				List.of("9-1"), "市場の行に名前と方角・距離が出て、ニュースから開いた市場の商品の一覧に列の見出し（列とずれていない、マウスで説明）があり、ホイールで最後の商品まで見えること（市場一覧の並び・買値以外の説明は見ていない）"));
		cases.add(new ClientCase("board_deals", List.of("9-1b"), null, true, ClientDisplayCases::boardDeals));
		cases.add(new ClientCase("map_coordinates_to_chat", List.of("20-5"), "JourneyMap なしで「この市場の座標」が掲示板の市場の一覧に出て、押すとこの市場の名前と座標が自分のチャットに出る（クリックでコピー）こと。市場を選ぶと消えること（近くに市場が無い時は見ていない）",
				true, ClientScreenCases::coordinates, List.of("20-1"), "JourneyMap なしで、選んだ市場の「座標を表示」を押すと、市場名と座標が自分のチャットに出て、クリックで座標がコピーされること（マルチの他のプレイヤーは見ていない）"));
		cases.add(new ClientCase("list_scrollbars", List.of("19-4"), null, true, ClientScreenCases::scrollbars));
		cases.add(new ClientCase("board_market_list", List.of("9-1"), null, true, ClientBoardCases::marketList,
				List.of("10-1"), "サーバーで付けた市場の名前（名札と同じ処理）が、ほかの村の掲示板の市場の行と、選んだ市場の題に出ること（名札を使う操作・チャット・名札が減ることはサーバーのテストで見ている）"));
		cases.add(new ClientCase("board_theater_trends", List.of("29-11"), "野外劇場の村の流行の倍率（人気は 1+(m−1)×1.5、余り気味は 1−(1−m)÷1.5）が、ほかの村の掲示板の市場の行のツールチップと、その村の交易画面の「流行」のツールチップに出ること（値段への効きはサーバーのテストで見ている）",
				true, ClientBoardCases::theaterTrends));
		cases.add(new ClientCase("board_no_markets", List.of("20-5"), "近くに市場が無い時（board.radius 150）も「この市場の座標」が出て、押すとこの市場の名前と座標が自分のチャットに出ること（JourneyMap なし）",
				true, ClientBoardCases::noMarkets, List.of("35-2"), "1人用の世界で、近くに市場が無い時の「半径 ◯ ブロック以内にほかの市場はありません」の数字が board.radius で、酒場のある村ではサーバーが使った半径（tavernBoardMultiplier 倍）になること（サーバーと自分の設定が違う時は見ていない）"));
		cases.add(new ClientCase("goods_names", List.of("30-1"), "交易画面と掲示板に並ぶ品（このワールドの市場の品と、市場に足した新しい商品：エリトラ・トライデント・ダイヤのツルハシとチェストプレート）の名前が翻訳されていて、正しいアイテムで、耐久値のある品が新品で並ぶこと（アイコンの見た目は画像で見る。JEI／REI は viewer_new_goods で見る）",
				true, ClientScreenCases::goodsNames));
		cases.add(new ClientCase("staff_needs_reputation", List.of("26-2"), null, true, ClientScreenCases::staffNeedsReputation));
		cases.add(new ClientCase("sell_all_box", List.of("18-1", "31-2"), null, true, ClientDisplayCases::sellAll,
				List.of("18-2"), "行のクリックで除外が切り替わって合計が変わり、開き直しても残ること（道具・防具が最初から除外なのは、市場が買い取る道具・防具がある時だけ見る）"));
		cases.add(new ClientCase("sell_all_tools_and_sale", List.of("18-2", "18-3"), null, true, ClientSellAllCases::toolsAndSale));
		cases.add(new ClientCase("sell_all_limits_and_other_player", List.of("18-4", "18-5"), null, true, ClientSellAllCases::limitsAndOtherPlayer));
		cases.add(new ClientCase("goods_order", List.of("31-1"), null, true, ClientDisplayCases::goodsOrder));
		cases.add(new ClientCase("town_tab", List.of("22-20", "22-21", "31-3"), null, true, ClientDisplayCases::townTab,
				List.of("28-1", "29-1", "34-1", "34-2"), "噴水広場・公園を含む15種類の建物が出て、入りきらない時はホイールでスクロールでき、スクロールした後もクリックで正しい建物が選ばれること。行のツールチップに大きさ、選んだ建物の名前の右に大きさが出て、マウスで詳しい大きさが出ること（倉庫で。砂漠と平原の高さの違い・行の見え方は画像で見る）"));
		cases.add(new ClientCase("exchange_tab", List.of("8-6"), null, true, ClientDisplayCases::exchange));
		cases.add(new ClientCase("wallet_screen", List.of("7-4"), null, true, ClientDisplayCases::wallet));
		cases.add(new ClientCase("wallet_offhand_and_limits", List.of("7-12"), null, true, ClientWindowCases::wallet));
		cases.add(new ClientCase("trader_opens_market", List.of("6-3"), null, true, ClientDisplayCases::traderOpens));
		cases.add(new ClientCase("advancements_screen", List.of("27-1"), null, true, (ses, r, a) -> ClientScreenCases.advancements(ses, r, a),
				List.of("27-11"), null));
		cases.add(new ClientCase("clock_advance_updates_open_screen", List.of("13-10"),
				"開いたままの交易画面に新しい時計が届き、依頼の残り時間の表示がすぐ減ること（建設・雇い人の残り時間は clock_advance_staff_and_town で見る）", true, ClientChecklistTests::clockAdvance));
		cases.add(new ClientCase("villager_coin_prices", List.of("12-1"), null, true, ClientScreenCases::villagerPrices,
				List.of("12-4"), "支払い欄の下の「代金（所持）」が、所持金が足りない時は赤、足りる時は灰色になること（品物が出ない・取れないはサーバーのテストで見ている）"));
		cases.add(new ClientCase("bank_card_buttons", List.of("7-8", "7-9"), null, true, ClientBankQuestCases::cardButtons));
		cases.add(new ClientCase("screen_size_by_gui_scale", List.of("19-1", "19-2"), null, true, ClientListCases::sizeByGuiScale));
		cases.add(new ClientCase("resize_open_screen", List.of("19-5"), null, true, ClientWindowCases::resizeOpen));
		cases.add(new ClientCase("long_names_big_numbers", List.of("19-6"), null, true, ClientLongTextCases::longTexts));
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
		cases.add(new ClientCase("trade_refused_messages", List.of("2-18"), null, true, ClientRefusalCases::trades));
		cases.add(new ClientCase("bank_refused_messages", List.of("7-11"), null, true, ClientRefusalCases::bank));
		cases.add(new ClientCase("exchange_refused_and_fee", List.of("8-7"), null, true, ClientRefusalCases::exchange));
		cases.add(new ClientCase("exchange_fee_from_server", List.of("35-1"),
				"自分の設定の手数料が 0% の時に、サーバーから手数料 30% の設定（ConfigSyncS2C）が届くと、両替タブの「手数料 ◯%」が 30% になること（テストのサーバーとクライアントは同じプロセスで設定を共有するので、サーバーの設定ファイルを自分と違えた時は見ていない）",
				true, ClientRefusalCases::serverFee));
		cases.add(new ClientCase("quest_tab", List.of("9-2"), null, true, ClientBankQuestCases::requests,
				List.of("16-5"), null));
		cases.add(new ClientCase("quest_locked", List.of("16-4"), null, true, ClientBankQuestCases::lockedRequest, List.of("16-7"), null));
		cases.add(new ClientCase("transport_request", List.of("21-1", "21-5", "21-7"), null, true, ClientBankQuestCases::transport,
				List.of("21-2"), "「荷箱を受け取る」で保証金が引かれて荷箱が手に入り、ボタンが「受け取り済み」になること（荷箱の説明は見ていない）"));
		cases.add(new ClientCase("transport_taken_by_other", List.of("21-4"), "ほかの人が受け取った輸送依頼（受けた人をサーバーで足したもの）が一覧で灰色の「運送中」になり、受け取るボタンが出ないこと（本物の2人では見ていない）",
				true, ClientBankQuestCases::transportTaken));
		cases.add(new ClientCase("town_donations", List.of("22-2", "22-3", "22-4"), null, true, ClientTownCases::donations, List.of("28-8"), null));
		cases.add(new ClientCase("town_other_player_donates", List.of("22-10"),
				"サーバーに足した2人目のプレイヤー（同じ屋台の画面を開いている）が寄付すると、開いたままの発展タブの発展度と、寄付した人の名前の並び（多い順）がすぐ変わること（本物の2人では見ていない）",
				true, ClientTownCases::otherPlayerDonates));
		cases.add(new ClientCase("town_build_reasons", List.of("22-22", "22-25"), null, true, ClientTownCases::buildReasons,
				List.of("22-23b"), "資材が足りない時に「建てる」のツールチップが「資材があと N 個必要です」になり、development.requireMaterials を OFF にすると押せること（資材の数の倍率は見ていない）"));
		cases.add(new ClientCase("town_materials_and_stake", List.of("22-23"), "「資材を寄付（N）」の数が持っている資材の数で、押すと資材が減り、発展度が増え、残りの数が減ること（上限まで寄付した後は見ていない）",
				true, ClientTownCases::materialsAndStake, List.of("22-35"), "「杭をもらう」で杭が手に入り、持っている時はもらえないこと（置いた時は見ていない）"));
		cases.add(new ClientCase("town_material_cap", List.of("22-23"), null, true, ClientTownCases::materialCap,
				List.of("22-23b"), null));
		cases.add(new ClientCase("town_row_states", List.of("22-24", "22-26", "22-33", "29-13"),
				"発展タブの行の「建設中 約N分」「✔ 完成」「壊れた（建て直しの発展度）」「✔ 今日は祭り／祭りまでN日」（建物はサーバーで直接作る。建設・完成・壊す流れは見ていない）",
				true, ClientTownCases::rowStates));
		cases.add(new ClientCase("town_size_by_style", List.of("34-2"), null, true, ClientTownCases::sizeByStyle));
		cases.add(new ClientCase("facility_sign_opens_tab", List.of("22-32"), null, true, ClientTownCases::signOpensTab));
		cases.add(new ClientCase("mint_panel", List.of("37-2"),
				"鋳造所の看板で両替タブが開き、右に鋳造所（銅ブロックの数・「9個→96c」・鉄は「設定で無効」）が重ならずに出て、「9個」で銅ブロックが9個減り 96c 増えること（建物はサーバーで直接作る。「全部」は見ていない）",
				true, ClientTownCases::mintPanel, List.of("37-1"), "鋳造所の看板を右クリックすると両替タブが開くこと（建物の見た目・建てる流れは見ていない）"));
		cases.add(new ClientCase("tabs_turned_off", List.of("26-20"), null, true, ClientTownCases::tabsTurnedOff,
				List.of("22-9", "24-7"),
				"設定を OFF にすると画面を開き直した時に「発展」タブが消えること。流行の数を 0 にすると次の期間に「流行」が出ないこと（取引しても発展度が増えないことは見ていない）"));
		// The staff cases share their employees (in this order); staff_layout lets them go.
		cases.add(new ClientCase("staff_hire_carrier", List.of("26-4"), null, true, ClientStaffCases::hireCarrier,
				List.of("26-3"), "品の一覧の先頭が「おまかせ（売れる品を何種類も）」で、各行に「見込み +○c/往復」か「今は利益なし」が出て見込みの大きい順に並び、ツールチップにおすすめの行き先と数が出ること（宝箱のアイコンは画像で見る）"));
		cases.add(new ClientCase("staff_carrier_trip", List.of("26-9"), null, true, ClientStaffCases::carrierTrip,
				List.of("26-6", "26-7", "26-14"),
				"運び屋の行の「資金不足」「→運送 N分」「←帰路 N分」「休み」、Shift で +1,000c／+10,000c になり10倍入ること、出かけている間は「解雇する」「変更」が押せないこと、「休止」が「再開」になること、往復の後にツールチップのログに往復が出ること（「利益なし」は見ていない）"));
		cases.add(new ClientCase("staff_clerk", List.of("26-13"), null, true, ClientStaffCases::clerk,
				List.of("26-11"), "番頭の品の一覧（手持ちのある品が先・「1日 約○c・手持ち○」・おすすめのツールチップ）、下限の初期値がおすすめ（売値の85%）、雇うと品が預けられること（少しずつ売れ続けるのは見ていない）"));
		cases.add(new ClientCase("staff_limit_and_other_market", List.of("26-15", "26-17"), null, true, ClientStaffCases::limitAndOtherMarket,
				List.of("26-5"), "おまかせを選ぶと設定が「1回に運ぶ数」だけになり、画面から雇えること"));
		cases.add(new ClientCase("staff_layout", List.of("26-25"), null, true, ClientStaffCases::layout));
		// Each starts without employees; staff_accident puts the settings back.
		cases.add(new ClientCase("staff_carrier_change", List.of("26-23"), null, true, ClientStaffMoreCases::carrierChange,
				List.of("26-27", "26-28", "26-16"), "売れ残りと帰り荷を持って戻った運び屋の右パネルが「荷 ×○個・帰り荷 ×○個」になり、ツールチップのログに帰り荷の仕入れが出ること。「変更」でおまかせに変えると、設定の画面に「今持っている荷 ×○個は手元に戻ります」が出て、売れ残りと帰り荷が手元に戻ること。画面の「解雇する」で、所持金と、売れ残りと2種類の帰り荷（サーバーで持たせる）が種類ごとに数どおり戻り、一覧から消えること（帰り荷の売れた文・「引取」での帰り荷は見ていない）"));
		cases.add(new ClientCase("staff_clerk_change", List.of("26-24", "26-12"), null, true, ClientStaffMoreCases::clerkChange,
				List.of("26-16"), "番頭を画面の「解雇する」で解雇すると所持金が戻り、一覧から消えること（運び屋の売れ残りは staff_carrier_change で見る）"));
		cases.add(new ClientCase("staff_auto_carrier", List.of("26-8", "26-27"), null, true, ClientStaffMoreCases::autoCarrier,
				List.of("26-28"), "売れ残りと帰り荷を持って戻った運び屋の「引取」で、荷と帰り荷の品が種類ごとに数どおり手元に戻ること（「変更」「解雇する」は staff_carrier_change で見る）"));
		cases.add(new ClientCase("staff_harbor_watchtower", List.of("35-3"),
				"港のある村では行き先のツールチップと設定の画面の「片道 約◯分」が harborTravelMultiplier の分だけ短く、雇った運び屋の出発時に決まる着く時刻もその時間になること。見張り塔のある村では行き先のツールチップの「事故の確率」が watchtowerAccidentMultiplier 倍になること（建物はサーバーで直接作る。着いたところまでは見ていない）",
				true, ClientStaffMoreCases::harborWatchtower));
		cases.add(new ClientCase("clock_advance_staff_and_town", List.of("13-10"),
				"交易画面を開いたまま clock advance すると、雇い人タブの運び屋の「→運送 N分」と発展タブの「建設中 約N分」が開き直さずにすぐ減ること（建物はサーバーで直接作る）",
				true, ClientStaffMoreCases::clockAdvance));
		cases.add(new ClientCase("staff_backhaul_hint", List.of("26-26"), null, true, ClientBackhaulCases::hint,
				List.of("26-30"), "staff.carrierBackhaul を OFF にすると、雇う画面の行き先の見込みに帰り荷の分が入らず、ツールチップと設定の画面に帰り荷の説明が出ないこと（帰り荷を仕入れない・空荷で出かけないはサーバーのテストで見ている）"));
		cases.add(new ClientCase("staff_empty_trip", List.of("26-29"), null, true, ClientBackhaulCases::emptyTrip));
		cases.add(new ClientCase("staff_carrier_moves_price", List.of("26-22"), null, true, ClientBackhaulCases::carrierMovesPrice));
		cases.add(new ClientCase("staff_accident", List.of("26-10"), null, true, ClientStaffMoreCases::accident,
				List.of("26-6"), "行き先で高く売れない時に運び屋の行が「利益なし」になり、仕入れずに出かけないこと"));
		cases.add(new ClientCase("market_gone_while_open", List.of("2-20"), null, true, ClientWindowCases::marketGone));
		cases.add(new ClientCase("reopen_world_keeps_market",List.of("4-3"), null, true, ClientTradeCases::reopenWorld));
		cases.add(new ClientCase("reopen_world_keeps_town_and_staff", List.of("22-11", "22-40", "23-8", "24-8"), null, true, ClientReopenCases::townAndStaff,
				List.of("26-18"), "運び屋（運送中）と、おまかせの運び屋（2種類の荷と帰り荷、休止中）の所持金・荷（種類ごと）・様子・ログが保存データでも雇い人タブでも同じで、運送中の運び屋が続きから行き先に着くこと（番頭は見ていない）"));
		cases.add(new ClientCase("where_show_lines", List.of("11-2"),
				"where show で、黄（同じ市場の範囲）・赤（新しい市場を作れない範囲）・青（数える範囲）の点がそれぞれの円の上に、地面（いちばん上のブロック＋0.3）に届き、where hide と30秒で止まること（緑の村の範囲は where_show_natural_village で見る。見やすさは画像で確認）",
				true, ClientParticleCases::whereShow));
		cases.add(new ClientCase("construction_effects", List.of("22-24b"),
				"建設中の場所（サーバーで直接作った 7×7 の建物）に、床の高さのオレンジの点が枠の上に、木くず（オークの板）が範囲の中に、工事の音が範囲の中で届き、音の字幕が「工事の音」であること。48 ブロックより離れた時・constructionEffects を OFF にして reload した時・完成した後は届かないこと（見え方と、字幕が画面に出るところは見ていない）",
				true, ClientParticleCases::constructionEffects));
		cases.add(new ClientCase("stall_facing_and_shape", List.of("2-6"),
				"使うキーでプレイヤーの東西南北に置いた交易所が、それぞれプレイヤーの方を向くこと（見た目は画像で確認）", true, ClientTraderCases::stallFacing,
				List.of("2-7"), "交易所が隣のブロックの面を隠さず（透けたり黒くなったりしない）、当たり判定が1ブロックいっぱいでも空でもないこと（形どおりかは画像で確認）"));
		cases.add(new ClientCase("trader_robe_colours", List.of("6-10b"),
				"/summon で出した24人の商人の服の色がばらばら（8色のうち6色以上）で、クライアントにも同じ色が届き、遠くへ行ってチャンクごと保存・読み込みし直しても、リソースの再読み込み（F3+T）の後も色が変わらず、色を変えたテクスチャが残ること（スポーンエッグ・ワールドの入り直し・肌と縁取りの色は見ていない。見た目は画像で確認）",
				true, ClientTraderCases::robeColours));
		cases.add(new ClientCase("trader_name_tag_and_eggs", List.of("6-10"),
				"名札を使うと商人に名前が付き、クライアントにも名前が見えること（村人の形と行商人の服は画像で見る）", true, ClientTraderCases::nameTagAndEggs,
				List.of("6-10b"), "スポーンエッグを地面に使って出した12人の商人の服の色がばらばら（4色以上）で、クライアントにも同じ色が届き、ワールドを閉じて開き直しても色と名札の名前が変わらないこと（肌と縁取りの色は画像で見る）"));
		cases.add(new ClientCase("wallet_picks_up_coins", List.of("7-2"), null, true, ClientMoreCases::walletPickup));
		cases.add(new ClientCase("stake_outlines", List.of("22-36b"),
				"杭を右クリックすると（サーバーでプレイヤーの右クリックを呼ぶ）、チャットに全部の建物が大きさつきで並び、大きさごとに1つ、その大きさの枠（四隅の柱）が、建てられる大きさは緑・建てられない大きさは赤で、杭を正面の真ん中にして届き、30秒で止まること（市場の建物は一時的に外す。杭を置いた時・もう1本置いた時・完成した建物の枠が出ないことは見ていない）",
				true, ClientParticleCases::stakeOutlines, List.of("28-10"), "杭を右クリックすると、噴水広場（11×11）・公園（13×13）もチャットに並び、その枠も緑／赤で届くこと"));
		cases.add(new ClientCase("stake_placed", List.of("22-36b"),
				"杭を置くと（使うキーと同じくサーバーで杭を使う。setPlacedBy）、置いた場所の文と建物の一覧がチャットに出て、すぐ枠が届き、完成した建物（依頼所）は一覧に出ないこと。もう1本置くと「先に置いた杭（先の杭の座標）がある間は、そちらに建ちます」と出ること（杭の右クリックと枠の形・色・30秒は stake_outlines で見る）",
				true, ClientParticleCases::stakePlaced));
		cases.add(new ClientCase("sold_out_and_not_buying", List.of("2-11"), null, true, ClientMoreCases::soldOutAndFull, List.of("2-12"), null));
		cases.add(new ClientCase("debug_tooltip_warehouse_and_consumables", List.of("22-27"), null, true, ClientMoreCases::debugStorageAndConsumables,
				List.of("30-7", "32-2"), null));
		cases.add(new ClientCase("died_with_wallet", List.of("7-6"), null, true, ClientMoreCases::diedWithWallet,
				List.of("7-7"), "財布を持って死んで生き返った後も、銀行タブの残高が変わらないこと（預け入れ・引き出しは bank_tab で見る）"));
		// Breaks the stall: the last case of the world.
		cases.add(new ClientCase("screen_closes_when_away", List.of("2-16"), null, true, ClientTradeCases::closesWhenAway));
		cases.add(new ClientCase("dedicated_server_one_player", List.of("14-1", "14-5"),
				"専用サーバー（このプロセスの中で起動）に1人で接続し、村に交易所を置いて交易画面を開け、買う・売るが1往復できること。サーバーで clock advance と設定（同じプロセスなので設定は共有）を変えた後に切断すると、設定が自分の設定ファイルの値に、市場の時計のずれが 0 に戻ること（2人と、切断の後に1人用の世界に入るところは見ていない）", false,
				ClientConfigCases::dedicatedServer, List.of("13-2"), null));
		cases.add(new ClientCase("where_show_natural_village", List.of("11-2"),
				"決まったシードの普通の世界で、生成された村の中で where show をすると、緑の点（村の範囲）が村の構造物の枠の辺の上に、地面（いちばん上のブロック＋0.3）に届くこと（見やすさは画像で確認）",
				false, ClientVillageCases::naturalVillage, List.of("6-1", "6-2"),
				"決まったシードの世界で、スポーンの近くの村（最大7つ）のどれかに生成された屋台の建物があり、交易所のまわりに樽・干し草・ランタン・柵があり、建物の床（7×7）の上に草や花が無いこと。交易所の正面が建物の入口に向き、交易所と入口の間に通れないブロックが無く、入口の外に村の道（土の道）があること（村の種類ごとには見ていない。建物が道や家と自然につながって見えるかは画像で確認）"));
		return cases;
	}

	/** The run without Mod Menu and YACL (checklist 1-1). */
	/** The run with Mod Menu but without YACL (checklist 1-6). */
	private static List<ClientCase> modMenuOnlyCases() {
		return List.of(new ClientCase("startup_modmenu_without_yacl", List.of("1-6"), null, false, ClientItemCases::modMenuWithoutYacl));
	}

	/** The run with YACL but without Mod Menu (checklist 1-7). */
	private static List<ClientCase> yaclOnlyCases() {
		return List.of(new ClientCase("startup_yacl_without_modmenu", List.of("1-7"), null, false, ClientConfigCases::yaclWithoutModMenu));
	}

	/** The run with JEI or REI (runClientGameTestJei / Rei, checklist 25-2〜25-8, 30-1). */
	private static List<ClientCase> viewerCases(String viewer) {
		boolean jei = viewer.equals("JEI");
		List<ClientCase> cases = new ArrayList<>();
		cases.add(new ClientCase("viewer_keys", List.of(jei ? "25-2" : "25-7"), jei ? null
				: "REI で、R と U のどちらでも「市場の相場」のページが出て、岩盤には出ないこと（25-2 の分）",
				true, ClientViewerCases::keys, jei ? List.of("25-6") : List.of(), null));
		cases.add(new ClientCase("viewer_page", List.of(jei ? "25-3" : "25-7"),
				jei ? "近い順に5つの市場の名前・方角と距離・買値・売値・在庫、値段が屋台の画面と同じ（評判 300 点の割引）、品不足の市場の名前がオレンジ、行のツールチップに市場名と品不足の注意が出ること（売り切れ・買い取りの上限・何個ずつの取引かの注意は見ていない）"
						: "REI で、25-3 のページの中身（近い順に5つ、屋台と同じ値段、品不足のオレンジとツールチップ）が出ること",
				true, ClientViewerCases::page, List.of(jei ? "25-4" : "25-7"),
				jei ? "ページを開いたまま、屋台の値段をサーバーで動かすと（相場の押しを変える。実際の売買ではない）、数秒で新しい値段になること"
						: "REI で、ページを開いたまま値段が変わると数秒で新しくなること（25-4 の分。サーバーで相場の押しを変える）"));
		cases.add(new ClientCase("viewer_page_none", List.of(jei ? "25-5" : "25-7"), jei
				? "board.radius を 16 にして市場から 40 ブロック離れた所で開くと「近くにこの品を扱う市場はありません…」と出て、ヘッダーの半径が 16 になること（512 ブロックの既定では見ていない）"
				: "REI で、近くに市場が無い時に「近くにこの品を扱う市場はありません…」が出ること（25-5 の分。board.radius を 16 にした）",
				true, ClientViewerCases::none));
		cases.add(new ClientCase("viewer_reload", List.of(jei ? "25-8" : "25-7"), jei ? null
				: "REI で、/aktradeex goods で品を外す・戻す、設定ファイルで品を足して /aktradeex reload すると、ページが消える・出ること（25-8 の分）",
				true, ClientViewerCases::reload));
		cases.add(new ClientCase("viewer_new_goods", List.of("30-1"), viewer + " で、新しい商品（エリトラ・トライデント・ダイヤのツルハシとチェストプレート）に「市場の相場」のページがあり、ページの品が耐久値の減っていない新品であること（アイコンの見た目は画像で見る）",
				true, ClientViewerCases::newGoods));
		return cases;
	}

	/** The pictures of the buildings (runClientGameTestLooks; a person looks at them). */
	private static List<ClientCase> looksCases() {
		return List.of(new ClientCase("building_looks", List.of("22-50", "22-52", "22-53", "22-54", "22-55", "22-56", "28-2", "28-3", "28-4", "28-5", "28-6", "29-2", "29-14"),
				"15種類の建物を7種類の見た目で、完成の時と同じく構造物を置いて、正面の斜め上から撮った画像（105枚、look_<見た目>_<建物>.png）があり、どの建物にも看板があること（材料と形の見た目・扉や窓や屋根の向き・水の張り方は画像で見る。向きは1つ。鐘を鳴らせる・寝られる・村人が来るは見ていない）",
				true, ClientLookCases::buildings));
	}

	/** The run with JourneyMap (runClientGameTestJourneyMap, checklist 20-3, 20-5). */
	private static List<ClientCase> journeyMapCases() {
		return List.of(new ClientCase("journeymap_waypoints", List.of("20-3"), null, true, ClientJourneyMapCases::waypointButtons,
				List.of("20-5"), "JourneyMap がある時、掲示板の市場の一覧の「この市場を登録」で今いる市場の金色の地点が JourneyMap に追加され（チャットには出ない）、もう一度押しても増えないこと（近くに市場が無い時は JourneyMap なしでだけ見た）"));
	}

	private static List<ClientCase> minimalCases() {
		return List.of(new ClientCase("startup_without_modmenu_yacl", List.of("1-1"), null, false, ClientChecklistTests::startupMinimal),
				new ClientCase("trade_without_modmenu_yacl", List.of("15-3"),
						"Mod Menu・YACL を外した開発用の起動構成（テスト用の Mod は入っている）で、新しいワールドに入って村に交易所を置き、交易画面で1つ買い1つ売れることのみ（配布 jar だけの起動構成では見ていない）",
						true, ClientChecklistTests::tradeMinimal));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String variant = System.getProperty("ak.autotest.variant", "");
		setLanguage(context, LANGUAGE);
		if (Boolean.getBoolean("ak.autotest.shots")) {
			// shots.bat (runClientGameTestShots): only the screenshots for the download page, no checklist results.
			PostScreenshots.run(context);
			backToTitle(context);
			return;
		}
		Session session = new Session(context);
		List<CaseResult> results = new ArrayList<>();
		List<ClientCase> run = switch (variant) {
			case "minimal" -> minimalCases();
			case "modmenu-only" -> modMenuOnlyCases();
			case "yacl-only" -> yaclOnlyCases();
			case "jei" -> viewerCases("JEI");
			case "rei" -> viewerCases("REI");
			case "journeymap" -> journeyMapCases();
			case "looks" -> looksCases();
			default -> filter(cases());
		};
		for (ClientCase c : run) {
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
		AutotestReport.write(variant.isEmpty() ? "client" : "client-" + variant, results);
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

	/** 15-3 (part) without Mod Menu and YACL: into a new world, place a stall, buy one and sell one with the screen's buttons. */
	private static void tradeMinimal(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ensureStall(s);
		s.world.getServer().runOnServer(server -> WalletService.give(player(server), 100_000));
		context.waitTicks(3);
		MarketScreen screen = openStall(s);
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		check(snapshot != null, "the trade screen got no snapshot");
		MarketSnapshotS2C.GoodView buy = snapshot.goods().stream()
				.filter(v -> v.buy() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && v.stock() >= 2)
				.findFirst().orElseThrow(() -> new AssertionError("no good to buy"));
		int had = serverCount(s, buy.goods());
		ClientUtil.buyWithButtons(s, screen, buy.goods(), "1");
		check(serverCount(s, buy.goods()) == had + 1, "buying one %s gave %d items", buy.goods(), serverCount(s, buy.goods()) - had);
		screenshot(context, result, "15-3_bought_without_modmenu_yacl");

		MarketSnapshotS2C.GoodView sell = snapshot(context, screen).goods().stream()
				.filter(v -> v.sell() && v.unit() == 1 && v.patch().isEmpty() && !v.special() && !v.goods().equals(buy.goods())
						&& context.computeOnClient(client -> client.player.getInventory().countItem(itemOf(v))) == 0)
				.findFirst().orElseThrow(() -> new AssertionError("no good to sell"));
		net.minecraft.world.item.Item item = itemOf(sell);
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> giveItems(player(server), item, 1));
		check(waitClient(context, client -> client.player.getInventory().countItem(item) == 1, 40), "the item to sell did not reach the client");
		screen = openStall(s);
		putInSellSlot(context, screen, item);
		context.waitTicks(3);
		long money = serverMoney(s);
		context.clickScreenButton("screen.ak_trade_expansion.confirm.sell");
		long after = money;
		for (int i = 0; i < 40 && after == money; i++) {
			context.waitTick();
			after = serverMoney(s);
		}
		check(after > money, "selling one %s paid nothing", sell.goods());
		check(serverCount(s, sell.goods()) == 0, "the sold %s is still in the inventory", sell.goods());
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
	static void checkLayout(ClientGameTestContext context, CaseResult.Builder result, String where, boolean screenshotAlways,
			List<String> problems, int[] box) {
		mouseAway(context);
		// Toasts (advancements) slide in from outside the screen: not part of the screen's layout.
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(4);
		List<TextRecorder.Text> all = TextRecorder.capture(context);
		List<TextRecorder.Text> texts = box == null ? all
				: all.stream().filter(t -> t.afterMark() || !t.inside(box[0], box[1], box[2], box[3])).toList();
		List<String> found = context.computeOnClient(client -> layoutProblems(client, where, texts, panel(client.gui.screen())));
		String size = where.substring(0, where.lastIndexOf(' '));
		if (screenshotAlways || !found.isEmpty() && problems.stream().noneMatch(p -> p.startsWith(size))) {
			screenshot(context, result, "layout_" + where);
		}
		problems.addAll(found);
	}

	static List<String> visibleTabs(Screen screen) {
		List<String> keys = new ArrayList<>();
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().startsWith("screen.ak_trade_expansion.tab.")) {
				keys.add(t.getKey());
			}
		}
		return keys;
	}

	/**
	 * The layout problems of the texts of the screen open now (on the client thread): {@link LayoutCheck#check} and
	 * the texts under a widget ({@link LayoutCheck#underWidgets}).
	 */
	static List<String> layoutProblems(net.minecraft.client.Minecraft client, String where, List<TextRecorder.Text> texts, LayoutCheck.Panel panel) {
		List<String> p = new ArrayList<>(LayoutCheck.check(where, texts,
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), panel));
		if (client.gui.screen() != null) p.addAll(LayoutCheck.underWidgets(where, texts, widgets(client.gui.screen())));
		return p;
	}

	/**
	 * The visible widgets of the screen with their labels. A widget that holds others (a list, a tab bar) is not one:
	 * the widgets in it are. An edit box's label is its value too.
	 */
	static List<LayoutCheck.Widget> widgets(Screen screen) {
		List<LayoutCheck.Widget> widgets = new ArrayList<>();
		addWidgets(screen.children(), widgets);
		return widgets;
	}

	private static void addWidgets(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children, List<LayoutCheck.Widget> widgets) {
		for (var child : children) {
			if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler holder) {
				if (!(child instanceof AbstractWidget w) || w.visible) addWidgets(holder.children(), widgets);
			} else if (child instanceof AbstractWidget w && w.visible && w.getWidth() > 0 && w.getHeight() > 0) {
				String label = w.getMessage().getString();
				if (w instanceof net.minecraft.client.gui.components.EditBox box) label = box.getValue() + " " + label;
				widgets.add(new LayoutCheck.Widget(w.getX(), w.getY(), w.getX() + w.getWidth(), w.getY() + w.getHeight(), label));
			}
		}
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
