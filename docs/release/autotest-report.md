# ゲーム内の自動テストの結果

`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。

- サーバー：バージョン 0.2.2、2026-11-14 19:22、35 件中 0 件失敗
- クライアント：バージョン 0.2.2、2026-11-14 19:23、8 件中 0 件失敗
- チェックリスト 321 項目のうち、今回 自動で完了 14・一部を確認 16・失敗 0

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
| 2-8 | `stall_place_and_open` | クライアント | ✔ | 全部 |
| 3-10 | `buy_with_screen_buttons` | クライアント | ✔ | 全部 |
| 2-9・2-10 | `buy_with_screen_buttons` | クライアント | ✔ | 交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない） |
| 3-6・19-1・19-2・19-3・22-1・26-1 | `layout_market_screen_ja` | クライアント | ✔ | 交易画面の各タブの文字が重ならず、画面と枠からはみ出さないこと（日本語、854×480 と 1920×1080、GUIの大きさ 1〜4）。一覧の行数・スクロールバー・「まとめて売る」の枠は見ていない |
| 26-1 | `layout_market_screen_en` | クライアント | ✔ | 英語でも交易画面の各タブの文字が重ならず、はみ出さないこと |
| 20-4・25-1 | `log_has_no_mod_errors` | クライアント | ✔ | 全部 |

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
- 3-10 `buy_with_screen_buttons`：[3-10_bulk_64.png](../../Source/TradeExpansion/build/autotest/screenshots/3-10_bulk_64.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_buy.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_buy.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_sell.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_sell.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_bank.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_board.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_board.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_exchange.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_exchange.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_town.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_town.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_staff.png)
