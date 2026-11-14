# ゲーム内の自動テストの結果

`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。

- サーバー：バージョン 0.2.1、2026-11-14 17:39、14 件中 0 件失敗
- クライアント：バージョン 0.2.1、2026-11-14 17:39、3 件中 0 件失敗
- チェックリスト 321 項目のうち、今回 自動で完了 6・一部を確認 10・失敗 0

## すべての項目

| 項目 | テスト | 側 | 結果 | 確認した範囲 |
|---|---|---|---|---|
| 2-1 | `stall_recipe` | サーバー | ✔ | 全部 |
| 2-3 | `stall_in_village` | サーバー | ✔ | 置けて市場ができることのみ（チャットの文言は見ていない） |
| 2-4 | `stall_outside_village` | サーバー | ✔ | 置けずに交易所が戻ることのみ（チャットの理由は見ていない） |
| 2-5 | `stall_too_close` | サーバー | ✔ | 64〜256ブロックの間に置けないことのみ（理由の文言は見ていない） |
| 2-8 | `stall_opens_menu` | サーバー | ✔ | サーバー側で交易画面が開くことのみ |
| 2-10 | `buy_pays_total` | サーバー | ✔ | 品が手に入り、所持金が合計額どおり減ることのみ（財布へのお釣りは見ていない） |
| 2-11 | `buy_raises_price` | サーバー | ✔ | 買うたびに値段が上がることのみ（売り切れの表示は見ていない） |
| 2-12 | `sell_pays_and_lowers_price` | サーバー | ✔ | 所持金が増えて値段が下がることのみ（買取停止は見ていない） |
| 2-17 | `creative_cannot_trade` | サーバー | ✔ | 取引されないことのみ（メッセージは見ていない） |
| 4-1 | `advance_recovers_half` | サーバー | ✔ | 全部 |
| 7-1 | `wallet_recipe` | サーバー | ✔ | 全部 |
| 8-3 | `minting_copper_not_iron` | サーバー | ✔ | 全部 |
| 8-4 | `emerald_coin_stonecutter` | サーバー | ✔ | 全部 |
| 17-1 | `starting_money` | サーバー | ✔ | 初めて入った時に 240c もらえることのみ（入り直しは見ていない） |
| 1-2 | `startup_with_modmenu_yacl` | クライアント | ✔ | Mod Menu・YACL ありで起動してタイトル画面が出ることのみ（Mod 一覧のアイコンは見ていない） |
| 1-3 | `config_screen_tabs` | クライアント | ✔ | 設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認） |
| 2-8 | `stall_place_and_open` | クライアント | ✔ | 全部 |

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
