# ゲーム内の自動テストの結果

`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。

- サーバー：バージョン 0.2.6、2026-11-14 20:55、63 件中 0 件失敗
- クライアント：バージョン 0.2.6、2026-11-14 20:57、23 件中 0 件失敗
- クライアント（Mod Menu・YACL なし）：バージョン 0.2.6、2026-11-14 20:54、2 件中 0 件失敗
- チェックリスト 322 項目のうち、今回 自動で完了 40・一部を確認 48・失敗 0

## すべての項目

| 項目 | テスト | 側 | 結果 | 確認した範囲 |
|---|---|---|---|---|
| 2-1 | `stall_recipe` | サーバー | ✔ | 全部 |
| 2-3 | `stall_in_village` | サーバー | ✔ | 全部 |
| 2-4 | `stall_outside_village` | サーバー | ✔ | 全部 |
| 2-5 | `stall_too_close` | サーバー | ✔ | 全部 |
| 2-8 | `stall_opens_menu` | サーバー | ✔ | サーバー側で交易画面が開くことのみ |
| 2-10 | `buy_pays_total` | サーバー | ✔ | 品が手に入り、所持金が合計額どおり減ることのみ（財布へのお釣りは見ていない） |
| 2-11 | `buy_raises_price_until_sold_out` | サーバー | ✔ | 買うたびに値段が上がり、在庫が0になると買えないこと（「売り切れ」「入荷まで」の表示は見ていない） |
| 2-12 | `sell_pays_and_lowers_price_until_full` | サーバー | ✔ | 所持金が増えて値段が下がり、在庫が上限で売れないこと（「買取停止」の表示は見ていない） |
| 2-14 | `shift_all_beyond_limit` | サーバー | ✔ | 上限（64）を超える数量の Shift 取引をサーバーが受け付けることのみ（最大数は画面側で計算） |
| 2-15 | `change_drops_when_full` | サーバー | ✔ | 全部 |
| 2-17 | `creative_cannot_trade` | サーバー | ✔ | 全部 |
| — | `buy_without_money` | サーバー | ✔ | 全部 |
| — | `sell_without_items` | サーバー | ✔ | 全部 |
| — | `price_changed_is_refused` | サーバー | ✔ | 全部 |
| — | `card_without_card` | サーバー | ✔ | 全部 |
| — | `unknown_or_missing_good` | サーバー | ✔ | 全部 |
| 3-10 | `snapshot_totals_match` | サーバー | ✔ | 画面と同じ計算（スナップショットの値）で 1・16・64 個とも確定できること（割引の表示は見ていない） |
| 16-3 | `reputation_prices_match` | サーバー | ✔ | 評判「お得意様」で買値が下がり売値が上がり、表示どおりに確定できること（評判の名前の表示は見ていない） |
| 4-1 | `advance_recovers_half` | サーバー | ✔ | 全部 |
| 7-1 | `wallet_recipe` | サーバー | ✔ | 全部 |
| 8-3 | `minting_copper_not_iron` | サーバー | ✔ | 全部 |
| 8-4 | `emerald_coin_stonecutter` | サーバー | ✔ | 全部 |
| 17-1 | `starting_money` | サーバー | ✔ | 初めて入った時に 240c もらえることのみ（入り直しは見ていない） |
| 4-2 | `propagation_by_distance` | サーバー | ✔ | 全部 |
| 4-4 | `consumables_come_back` | サーバー | ✔ | 全部 |
| 4-5 | `goods_set_base_price` | サーバー | ✔ | 全部 |
| 7-2 | `wallet_picks_up_coins` | サーバー | ✔ | 財布を持って拾ったコインが財布に入り、インベントリに出ないこと（拾う音・表示は見ていない） |
| 7-5 | `wallet_capacity` | サーバー | ✔ | 全部 |
| 7-6・7-7 | `bank_deposit_withdraw_death` | サーバー | ✔ | 預け入れ・引き出しで残高が増減し、死ぬと財布ごと落ちて銀行の残高は減らないこと（画面の表示は見ていない） |
| 7-8・7-9 | `card_issue_and_pay` | サーバー | ✔ | 全部 |
| 7-10 | `card_reissue_and_other_player` | サーバー | ✔ | 全部 |
| 18-3 | `sell_all_leaves_hotbar` | サーバー | ✔ | 表示どおりの合計を受け取り、ホットバーの品は残ること（確認の枠・除外の操作は見ていない） |
| 18-4 | `sell_all_stock_limit` | サーバー | ✔ | 上限を超える数では全体が中止され、売れる分だけなら売れること（「40個（64個中）」の表示は見ていない） |
| 18-5 | `sell_all_price_changed` | サーバー | ✔ | ほかのプレイヤーが先に売ると「価格が変わった」で中止され、新しい相場が届くこと（画面の更新は見ていない） |
| 9-2 | `quest_deliver` | サーバー | ✔ | 品を持って納品すると品が減り、報酬が入ること（一覧の表示は見ていない） |
| 9-3 | `quests_expire_and_refill` | サーバー | ✔ | 全部 |
| 9-4 | `shortages_pay_more_and_rotate` | サーバー | ✔ | 全部 |
| 21-1・21-2・21-5・21-6・21-7 | `transport_take_and_deliver` | サーバー | ✔ | 輸送依頼が1件あり、受け取ると保証金が引かれ荷箱が手に入り、出した市場では届けられず、届け先で報酬＋保証金を受け取って両方の評判が上がること（一覧・荷箱の説明の表示は見ていない） |
| 21-3 | `transport_deposit_funds_and_card` | サーバー | ✔ | 所持金が保証金より少ないと受け取れず、カード払いなら口座から引かれること |
| 21-8 | `transport_expires` | サーバー | ✔ | 期限を過ぎると届け先で「期限が切れています」になり、出した市場の評判が下がること（荷箱の説明の表示は見ていない） |
| 21-9 | `transport_per_market_zero` | サーバー | ✔ | 全部 |
| 21-10 | `transport_needs_another_market` | サーバー | ✔ | 全部 |
| 26-2 | `staff_needs_reputation` | サーバー | ✔ | 評判「よそ者」では雇えないこと（ボタンと説明の表示は見ていない） |
| 26-5・26-6・26-7・26-14・26-22・26-30 | `carrier_round_trip` | サーバー | ✔ | おまかせを雇い、資金不足 → 所持金を入れると仕入れて出かけ、行き先で売って戻り、歩合が利益の2割になること。出かけている間は解雇できず、休ませると戻ってから止まること。仕入れで相場が1個 0.25 動くこと。帰り荷 OFF では帰り荷を仕入れないこと（画面の表示は見ていない） |
| 26-27・26-29 | `carrier_return_cargo` | サーバー | ✔ | 行きに儲からなくても帰り荷の見込みで空荷で出かけ、行き先で帰り荷を仕入れ、戻って売ると歩合に帰り荷の利益も入ること（ログの文と右パネルは見ていない） |
| 26-30 | `carrier_backhaul_off_no_empty_trip` | サーバー | ✔ | 帰り荷 OFF では、行きに儲からない時に空荷で出かけないこと（見込みの表示は見ていない） |
| 26-11・26-12 | `clerk_sells_and_sold_out` | サーバー | ✔ | 番頭を雇うと品が預けられ、売れて「売り切れ」になり、預けると再開し、引取で品が戻ること（一覧の見込みの表示は見ていない） |
| 26-16・26-17 | `staff_dismiss_and_limit` | サーバー | ✔ | 解雇で所持金と品が戻って一覧から消えること、3人まで雇えて4人目は断られること |
| 26-19 | `staff_pay_by_card` | サーバー | ✔ | カード払いで雇う・所持金を入れると口座から引かれ（手数料つき）、カードで出金すると口座に入ること |
| 26-21 | `staff_home_market_deleted` | サーバー | ✔ | 全部 |
| 13-10 | `clock_advance_moves_everything` | サーバー | ✔ | 市場の時計だけが進み、依頼の入れ替え・預金の利息・相場の回復がまとめて進むこと（画面の表示の更新は見ていない） |
| — | `request_trade` | サーバー | ✔ | 全部 |
| — | `request_sell_all` | サーバー | ✔ | 全部 |
| — | `request_bank` | サーバー | ✔ | 全部 |
| — | `request_exchange` | サーバー | ✔ | 全部 |
| — | `request_quest_accept` | サーバー | ✔ | 全部 |
| — | `request_quest_deliver` | サーバー | ✔ | 全部 |
| — | `request_cargo_deliver` | サーバー | ✔ | 全部 |
| — | `request_development` | サーバー | ✔ | 全部 |
| — | `request_staff` | サーバー | ✔ | 全部 |
| — | `request_board` | サーバー | ✔ | 全部 |
| — | `request_wallet_withdraw` | サーバー | ✔ | 全部 |
| 14-4 | `log_has_no_mod_errors` | サーバー | ✔ | テスト用サーバー（専用サーバーではない）のログに本 Mod のエラーが出ないことのみ |
| 1-2 | `startup_with_modmenu_yacl` | クライアント | ✔ | Mod Menu・YACL ありで起動してタイトル画面が出ることのみ（Mod 一覧のアイコンは見ていない） |
| 1-3 | `config_screen_tabs` | クライアント | ✔ | 設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認） |
| 1-3 | `layout_config_screen` | クライアント | ✔ | 設定画面の各タブの文字が重ならず、画面からはみ出さないこと（日本語、854×480） |
| 2-8 | `stall_place_and_open` | クライアント | ✔ | 全部 |
| 2-2 | `placement_hint` | クライアント | ✔ | 全部 |
| 3-10 | `buy_with_screen_buttons` | クライアント | ✔ | 全部 |
| 2-9・2-10 | `buy_with_screen_buttons` | クライアント | ✔ | 交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない） |
| 3-1 | `price_arrows` | クライアント | ✔ | 画面を開いたまま買った商品の行に ▲ が出ることのみ（▼ は見ていない） |
| 3-4 | `balance_display` | クライアント | ✔ | 全部 |
| 3-8・3-9 | `row_states` | クライアント | ✔ | 全部 |
| 3-7 | `row_states` | クライアント | ✔ | 品不足の商品の名前がオレンジ色になることのみ（ツールチップは見ていない） |
| 3-11 | `max_with_exact_money` | クライアント | ✔ | 全部 |
| 2-13 | `sell_slot_selects_good` | クライアント | ✔ | 全部 |
| 13-10 | `clock_advance_updates_open_screen` | クライアント | ✔ | 開いたままの交易画面に新しい時計が届き、依頼の残り時間の表示がすぐ減ること（建設・雇い人の残り時間は見ていない） |
| 3-6・19-1・19-2・19-3・22-1・26-1 | `layout_market_screen_ja` | クライアント | ✔ | 交易画面の各タブ（掲示板で市場を選んだ状態・依頼を選んだ状態・「まとめて売る」の枠も）の文字が重ならず、画面と枠からはみ出さないこと（日本語、854×480 と 1920×1080、GUIの大きさ 1〜4）。一覧の行数・スクロールバーは見ていない |
| 26-1 | `layout_market_screen_en` | クライアント | ✔ | 英語でも交易画面の各タブの文字が重ならず、はみ出さないこと |
| 13-1 | `config_save_applies` | クライアント | ✔ | 全部 |
| 13-6・13-8・13-9 | `goods_manager` | クライアント | ✔ | 全部 |
| 4-3 | `reopen_world_keeps_market` | クライアント | ✔ | 全部 |
| 2-16 | `screen_closes_when_away` | クライアント | ✔ | 全部 |
| 14-1 | `dedicated_server_one_player` | クライアント | ✔ | 専用サーバー（このプロセスの中で起動）に1人で接続し、村に交易所を置いて交易画面を開けることのみ（2人は見ていない） |
| 13-2 | `dedicated_server_one_player` | クライアント | ✔ | 全部 |
| 20-4・25-1 | `log_has_no_mod_errors` | クライアント | ✔ | 全部 |
| 1-1 | `startup_without_modmenu_yacl` | クライアント（Mod Menu・YACL なし） | ✔ | 全部 |
| 1-1 | `log_has_no_mod_errors` | クライアント（Mod Menu・YACL なし） | ✔ | 全部 |

## 見て確かめる画像

見た目（文字の重なり・はみ出し・色）は人が判断します。画像は `Source/TradeExpansion/build/autotest/screenshots/` にあります（リポジトリには入れない）。

- 1-2 `startup_with_modmenu_yacl`：[1-2_title.png](../../Source/TradeExpansion/build/autotest/screenshots/1-2_title.png)
- 1-3 `config_screen_tabs`：[1-3_config_01.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_01.png)
- 1-3 `config_screen_tabs`：[1-3_config_02.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_02.png)
- 1-3 `config_screen_tabs`：[1-3_config_03.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_03.png)
- 1-3 `config_screen_tabs`：[1-3_config_04.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_04.png)
- 1-3 `config_screen_tabs`：[1-3_config_05.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_05.png)
- 1-3 `config_screen_tabs`：[1-3_config_06.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_06.png)
- 1-3 `config_screen_tabs`：[1-3_config_07.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_07.png)
- 1-3 `config_screen_tabs`：[1-3_config_08.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_08.png)
- 1-3 `config_screen_tabs`：[1-3_config_09.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_09.png)
- 1-3 `config_screen_tabs`：[1-3_config_10.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_10.png)
- 1-3 `config_screen_tabs`：[1-3_config_11.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_11.png)
- 1-3 `config_screen_tabs`：[1-3_config_12.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_12.png)
- 1-3 `config_screen_tabs`：[1-3_config_13.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_13.png)
- 1-3 `config_screen_tabs`：[1-3_config_14.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_14.png)
- 1-3 `config_screen_tabs`：[1-3_config_15.png](../../Source/TradeExpansion/build/autotest/screenshots/1-3_config_15.png)
- 2-8 `stall_place_and_open`：[2-8_trade_screen.png](../../Source/TradeExpansion/build/autotest/screenshots/2-8_trade_screen.png)
- 2-2 `placement_hint`：[2-2_hint_join.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_join.png)
- 2-2 `placement_hint`：[2-2_hint_refused.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_refused.png)
- 2-2 `placement_hint`：[2-2_hint_new.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_new.png)
- 3-10 `buy_with_screen_buttons`：[3-10_bulk_64.png](../../Source/TradeExpansion/build/autotest/screenshots/3-10_bulk_64.png)
- 3-1 `price_arrows`：[3-1_price_up.png](../../Source/TradeExpansion/build/autotest/screenshots/3-1_price_up.png)
- 3-4 `balance_display`：[3-4_balance_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-4_balance_tooltip.png)
- 3-8・3-9 `row_states`：[3-7_shortage_orange.png](../../Source/TradeExpansion/build/autotest/screenshots/3-7_shortage_orange.png)
- 3-8・3-9 `row_states`：[3-8_sold_out_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-8_sold_out_tooltip.png)
- 3-8・3-9 `row_states`：[3-8_not_buying_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-8_not_buying_tooltip.png)
- 3-8・3-9 `row_states`：[3-9_not_buying_sell_slot.png](../../Source/TradeExpansion/build/autotest/screenshots/3-9_not_buying_sell_slot.png)
- 3-11 `max_with_exact_money`：[3-11_max_exact_money.png](../../Source/TradeExpansion/build/autotest/screenshots/3-11_max_exact_money.png)
- 2-13 `sell_slot_selects_good`：[2-13_sell_slot.png](../../Source/TradeExpansion/build/autotest/screenshots/2-13_sell_slot.png)
- 13-10 `clock_advance_updates_open_screen`：[13-10_quest_after_clock_advance.png](../../Source/TradeExpansion/build/autotest/screenshots/13-10_quest_after_clock_advance.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_buy.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_buy.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_sell.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_sell.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_sell_sell_all_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_sell_sell_all_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_bank.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_board.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_board.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_board_market_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_board_market_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest_request_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest_request_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_exchange.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_exchange.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_town.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_town.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_staff.png)
- 13-1 `config_save_applies`：[13-1_max_per_transaction_48.png](../../Source/TradeExpansion/build/autotest/screenshots/13-1_max_per_transaction_48.png)
- 13-6・13-8・13-9 `goods_manager`：[13-6_goods_added.png](../../Source/TradeExpansion/build/autotest/screenshots/13-6_goods_added.png)
- 13-6・13-8・13-9 `goods_manager`：[13-9_already_goods.png](../../Source/TradeExpansion/build/autotest/screenshots/13-9_already_goods.png)
- 13-6・13-8・13-9 `goods_manager`：[13-8_consumable_remove.png](../../Source/TradeExpansion/build/autotest/screenshots/13-8_consumable_remove.png)
- 4-3 `reopen_world_keeps_market`：[4-3_reopened.png](../../Source/TradeExpansion/build/autotest/screenshots/4-3_reopened.png)
- 14-1 `dedicated_server_one_player`：[14-1_dedicated_trade_screen.png](../../Source/TradeExpansion/build/autotest/screenshots/14-1_dedicated_trade_screen.png)
- 13-2 `dedicated_server_one_player`：[13-2_settings_on_server.png](../../Source/TradeExpansion/build/autotest/screenshots/13-2_settings_on_server.png)
- 1-1 `startup_without_modmenu_yacl`：[1-1_title_without_modmenu_yacl.png](../../Source/TradeExpansion/build/autotest/screenshots/1-1_title_without_modmenu_yacl.png)
