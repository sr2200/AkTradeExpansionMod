# ゲーム内の自動テストの結果

`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。

- サーバー：バージョン 0.2.14、2026-11-15 11:03、150 件中 0 件失敗
- クライアント：バージョン 0.2.12、2026-11-15 10:43、36 件中 0 件失敗
- クライアント（Mod Menu・YACL なし）：バージョン 0.2.12、2026-11-15 10:32、2 件中 0 件失敗
- チェックリスト 323 項目のうち、今回 自動で完了 94・一部を確認 126・失敗 0

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
| 17-1 | `starting_money` | サーバー | ✔ | 全部 |
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
| 5-1 | `profiles_differ_by_region` | サーバー | ✔ | 同梱の市場プロファイルで、砂漠の村に砂・サボテン、沼地にヒスイランがあり、平原の村には無いこと（実際の村の地形での抽選は見ていない） |
| 5-1b | `raw_meat_and_fish` | サーバー | ✔ | 同梱の市場プロファイルに生の肉（牛・豚・鶏）と生の魚（鱈・鮭）が並び、焼いた肉・魚の商品が無いこと（並んだ画面は見ていない） |
| 5-2 | `emerald_coin_always_sells` | サーバー | ✔ | 村の市場の売却タブにエメラルド貨があり、10枚で 4c のまま70回続けて売れること（ネザー・エンド・前のバージョンの市場は見ていない） |
| 5-3・5-3b・5-4・5-6 | `market_grows_and_shrinks` | サーバー | ✔ | ベッドだけ増やすと村人の数で止まり、村人も増やして1日たつと「成長しました」と出て商品が増え（レベル3以上で銀行が使える。レベル2では使えない）、ベッドを減らすと「縮小しました」と出て減り、戻すと同じ商品が同じ状態で戻ること（銀行タブの表示は見ていない） |
| 5-7 | `specials_rotate_daily` | サーバー | ✔ | レベル1の市場に本日の掘り出し物が1品あり、日が変わると入れ替わること（レベル3以上で2品は market_grows_and_shrinks で確かめる。★と水色の表示は見ていない） |
| 5-8 | `diamond_gear_levels` | サーバー | ✔ | 同梱のプロファイルでダイヤの道具はレベル4以上・防具はレベル5でだけ並び、エンチャント付きのダイヤのツルハシは売れず、無しなら売れること（並んだ画面は見ていない） |
| 5-9 | `plains_logs` | サーバー | ✔ | 同梱の平原の村・開拓地（自分で置いた交易所）のプロファイルにシラカバ・ダークオーク・サクラ・ペールオーク・ポプラの原木があること（並んだ画面は見ていない） |
| 6-4 | `natural_stall_protected` | サーバー | ✔ | 全部 |
| 6-5・6-6・6-7 | `trader_spawns_once_and_stays` | サーバー | ✔ | 開くと正面に商人が1体だけ出て、攻撃・溶岩で死なず、押されず、遠くでも消えない扱いで、交易所を壊すと消えること（時間がたつ様子は見ていない） |
| 6-8・6-11 | `summoned_trader_opens_market` | サーバー | ✔ | 全部 |
| 6-9 | `trader_without_market` | サーバー | ✔ | 全部 |
| 7-3 | `coins_stacked_onto_wallet` | サーバー | ✔ | 全部 |
| 8-1・8-2 | `mobs_drop_coins_to_players` | サーバー | ✔ | プレイヤーが倒したゾンビは銅貨を落とし（ドロップ増加 III で増える）、プレイヤー以外に倒されたゾンビは落とさず、オーバーワールドでは金貨を落とさないこと（鉄貨のまれな確率は見ていない） |
| 8-3b | `iron_minting_switch` | サーバー | ✔ | 全部 |
| 8-5 | `sneak_use_breaks_coin` | サーバー | ✔ | 全部 |
| 10-1・10-3・10-4 | `name_tag_names_market` | サーバー | ✔ | 名付け親が名札を使うと名前が変わって名札が1枚減り、画面の題に名前が入り、ほかの人は変えられず（理由が出て名札は減らない）、コマンドで自動の名前に戻ること（掲示板の表示は見ていない） |
| 10-2 | `named_stall_names_new_market` | サーバー | ✔ | 全部 |
| 11-0 | `enough_beds_allow_more_villagers` | サーバー | ✔ | 全部 |
| 11-1 | `where_describes_spot` | サーバー | ✔ | 村の範囲・一番近い市場・ベッドと村人の数・ここに作った場合の規模・置いた時の結果の行が出て、数と結果が正しいこと（チャットの見た目は見ていない） |
| 11-3 | `new_market_requires_modes` | サーバー | ✔ | 全部 |
| 12-2・12-3・12-4・12-5 | `villager_coin_trades` | サーバー | ✔ | 取引を選ぶと支払い欄が空のまま結果欄に品が出て、取ると所持金が減り、Shift でお金が尽きるまで買え、足りないと品が出ず、支払い欄のエメラルドは使われないこと（「代金（所持）」の赤い表示は見ていない） |
| 12-6・12-7 | `villager_other_costs` | サーバー | ✔ | 全部 |
| 12-8 | `villager_bread_above_market` | サーバー | ✔ | パン6個の代金が、市場の売値と基準価格×1.4 より安くないこと（農民の本物の取引表は見ていない） |
| 12-10・12-11 | `coin_trade_switches` | サーバー | ✔ | 全部 |
| 12-12 | `villager_discount_lowers_coins` | サーバー | ✔ | 村の英雄の効果で村人が値引きすると、コインの代金も安くなること（評判を上げる・ゾンビ村人を治すは見ていない） |
| 16-1b | `reputation_decays` | サーバー | ✔ | 全部 |
| 16-2 | `reputation_from_trades` | サーバー | ✔ | 全部 |
| 16-4 | `hard_request_needs_reputation` | サーバー | ✔ | 評判が足りないと★3の依頼が「納品不可」で送られ、納品が断られ、レベル3で納品できること（灰色の★と右の欄の文は見ていない） |
| 16-5 | `accepted_request_more_points` | サーバー | ✔ | 依頼を受けると受付済みとして送られ、受けた依頼の納品で受けていない依頼の倍率分だけ多く評判が増えること（一覧の ✔ は見ていない） |
| 16-6 | `expired_request_costs_points` | サーバー | ✔ | 全部 |
| 16-7 | `reward_grows_with_reputation` | サーバー | ✔ | 交易画面に送る依頼の報酬が、レベル0とレベル3で報酬の倍率どおりに違うこと（画面の表示は見ていない） |
| 16-8 | `villagers_and_reputation` | サーバー | ✔ | 全部 |
| 16-9・16-10 | `reputation_per_player` | サーバー | ✔ | 評判レベル4の人とレベル0の人に、それぞれの評判で交易画面のデータ（追加の掘り出し物・値段・評判）が送られること（本物の2人のクライアントでの表示は見ていない） |
| 16-12 | `change_owner_only` | サーバー | ✔ | 全部 |
| 16-13 | `bank_interest_over_days` | サーバー | ✔ | 10日で 10,000c が約 10,030c になり、market advance では利息が付かないこと（銀行タブの「利息」の表示は見ていない） |
| 23-1 | `rivals_start_command` | サーバー | ✔ | rivals start で中心の市場に動きが1件始まり、始まった知らせと rivals の一覧の行がチャットに出ること（文の中身は見ていない） |
| 23-2 | `rivals_move_prices` | サーバー | ✔ | 買い集めで値段が上がり、売りさばきで下がり、交易画面に送る商品に商会の動き（向き・あと何日）が付くこと（ツールチップとグラフは見ていない） |
| 23-5 | `rivals_reach` | サーバー | ✔ | 全部 |
| 23-6 | `rivals_grow_then_fade` | サーバー | ✔ | 全部 |
| 23-7 | `rivals_start_by_themselves` | サーバー | ✔ | 全部 |
| 23-8・24-8 | `rivals_and_trends_saved` | サーバー | ✔ | 保存データ（markets）を書き出して読み直すと、商会の動き・値段のずれ・流行が同じであること（実際にワールドを開き直してはいない） |
| 23-9 | `rivals_off` | サーバー | ✔ | 全部 |
| 23-10 | `price_changed_across_day` | サーバー | ✔ | 開いた時の合計額で日をまたいでから買うと PRICE_CHANGED が返り、品もお金も動かないこと（メッセージの文は見ていない） |
| 23-11 | `shortage_multiplier_sent` | サーバー | ✔ | 品不足の商品に、市場の品不足の倍率と、その倍率を掛けた基準価格が送られること（ツールチップの文は見ていない） |
| 24-2 | `trend_raises_base_price` | サーバー | ✔ | 人気の分野の商品に流行と倍率が送られ、基準価格がその倍率だけ上がっていること（ツールチップと market info の表示は見ていない） |
| 24-3 | `no_trend_on_specials` | サーバー | ✔ | 全部 |
| 24-5 | `trends_change_with_period` | サーバー | ✔ | 全部 |
| 24-6 | `reroll_rerolls_trends` | サーバー | ✔ | 全部 |
| 24-7 | `trends_off` | サーバー | ✔ | 流行の数を両方 0 にすると次の期間から流行が無くなり、交易画面に送る流行も空になること（設定はファイルの再読み込みでなくその場で変えた。画面の表示は見ていない） |
| 27-2 | `advancements_first_trades` | サーバー | ✔ | 全部 |
| 27-3 | `advancements_selling` | サーバー | ✔ | 全部 |
| 27-4 | `advancements_three_markets` | サーバー | ✔ | 3つの市場で取引すると「行商」が付くこと（ネザー・エンドの市場は見ていない） |
| 27-5 | `advancements_requests` | サーバー | ✔ | 依頼を納めると「頼れる人」が付くこと（★3 の「腕利き」と輸送依頼の「長い旅路」は見ていない） |
| 27-6 | `advancements_reputation_and_hiring` | サーバー | ✔ | 評判を 50・400・2500 にして交易画面を開くと「顔見知り」「お得意様」「名士」、運び屋を雇うと「人を使う身」が付くこと（運び屋の往復の「留守を任せて」は見ていない） |
| 27-7 | `advancements_donation` | サーバー | ✔ | 「発展」タブでコインを寄付すると「村への投資」が付くこと（建物の完成の「槌音」「村おこし」は見ていない） |
| 27-8 | `advancements_money` | サーバー | ✔ | 全部 |
| 27-9 | `advancements_after_joining_again` | サーバー | ✔ | 評判が高い人が入り直すと、ログインした時に評判の進捗が付くこと（建物・所持金は見ていない） |
| 27-10 | `advancements_survive_death` | サーバー | ✔ | 2つの市場で取引してから死んで生き返っても、取引した市場の記録（「行商」の数）が残っていること（生き返った後の取引は見ていない） |
| 22-2 | `development_tab_view` | サーバー | ✔ | 交易画面を開くと、発展度 0・寄付した人なし・自分の値 0 と建物の一覧が送られること（画面の表示は見ていない） |
| 22-3 | `donate_coins` | サーバー | ✔ | 100c を寄付すると所持金が 100c 減り、発展度が 100 増え、寄付した人に自分の名前が出て、評判のポイントが 3 増えること（画面の表示は見ていない） |
| 22-4 | `donate_without_money` | サーバー | ✔ | 所持金より多い額の寄付は「所持金が足りない」で断られ、何も変わらないこと（画面の文は見ていない） |
| 22-5 | `donate_by_card` | サーバー | ✔ | 全部 |
| 22-6 | `development_from_requests` | サーバー | ✔ | 全部 |
| 22-7 | `development_from_trades` | サーバー | ✔ | 全部 |
| 22-8 | `development_command` | サーバー | ✔ | 全部 |
| 22-9 | `development_off` | サーバー | ✔ | 設定 development.enabled を OFF にすると、発展のタブが送られず、取引しても発展度が増えず、寄付も断られること（/aktradeex reload での読み直しは見ていない） |
| 22-10 | `development_two_players` | サーバー | ✔ | 仮のプレイヤー2人で寄付すると、多い順に2人の名前が並び、画面を開いているもう1人にも新しい発展度が送られること（画面の表示は見ていない） |
| 22-11・22-40 | `development_saved` | サーバー | ✔ | 保存データ（markets）を書き出して読み直すと、発展度・寄付した人・建設中／完成／壊れた建物と寄付した資材が同じで、読み直した後も建設中の建物は期日が来ると完成すること（実際にワールドを開き直してはいない） |
| 22-22 | `build_needs_reputation` | サーバー | ✔ | 評判が足りないと「選べない」として送られ、建てる要求が「評判が足りない」で断られること（ボタンとツールチップは見ていない） |
| 22-23 | `donate_materials` | サーバー | ✔ | 丸石を寄付すると丸石が減り（512個まで）、発展度と評判が増え、寄付した数が送られ、512個の後や資材を持っていない時は「寄付できる物がない」で断られること（ボタンの状態は見ていない） |
| 22-23b | `materials_required` | サーバー | ✔ | 資材が足りないと「資材が足りない」で建てられず、資材の数は4倍で、全部寄付すると建てられること。requireMaterials を OFF にすると資材なしで建てられ、materialCountMultiplier を 2 にすると必要数が半分になること（ボタンとツールチップは見ていない） |
| 22-24・22-25・22-26・22-27 | `build_warehouse` | サーバー | ✔ | 建てると発展度が減り、四隅に足場・正面に看板が立ち、看板で「建設中」が出る。建設中はほかの建物を建てられない。development complete で足場が消えて建物が建ち、完成の知らせが届き、在庫の上限と回復の速さが上がること（画面の一覧と土台は見ていない） |
| 22-28 | `quest_hall_more_requests` | サーバー | ✔ | 依頼所が完成すると依頼が5件まで出ること（輸送依頼の2件は見ていない） |
| 22-29 | `inn_brings_villagers` | サーバー | ✔ | 宿屋が完成すると村人が2人来て、市場の周りのベッドが4つ増えること（market info の表示は見ていない） |
| 22-30 | `bank_building` | サーバー | ✔ | 銀行が完成すると、レベル1の市場でも銀行が使え、カード払いの手数料がなく、預け入れでその銀行に登録されて利息の上限が2倍になること（銀行タブの表示は見ていない） |
| 22-31・22-32 | `facilities_raise_level` | サーバー | ✔ | 建物2つで市場の規模が1つ上がって「市場が成長しました」が届き、4つで2つ上がること。完成した建物の看板を右クリックすると、その建物のタブで交易画面が開くこと（画面の表示は見ていない） |
| 22-33・22-34 | `facility_destroyed_and_rebuilt` | サーバー | ✔ | 看板以外を壊しても効果は消えず、看板を壊すと「壊れました」が届いて効果が消え、半分の発展度で別の場所に建て直せて、宿屋は建て直しても村人が来ないこと（一覧の表示は見ていない） |
| 22-37 | `no_site` | サーバー | ✔ | 全部 |
| 22-39 | `protect_facilities` | サーバー | ✔ | 全部 |
| 22-35 | `site_stakes` | サーバー | ✔ | 「杭をもらう」で杭が1本手に入り、持っている時はもらえない。市場の近くに置くと建設予定地になり、遠くに置くと「市場がありません」が届くこと（パーティクルの枠は見ていない） |
| 22-36 | `build_at_stake` | サーバー | ✔ | 全部 |
| 22-38 | `site_ground_kinds` | サーバー | ✔ | 全部 |
| 30-7・32-2 | `consumable_goods` | サーバー | ✔ | 真紅の幹・歪んだ幹・ハチミツ入りの瓶・サトウキビ・矢・茶色の卵・青い卵が消耗品で、相場の戻り（半減期）と在庫の減りが消耗品の速さになること（画面の表示は見ていない） |
| 32-1 | `brown_and_blue_eggs` | サーバー | ✔ | 茶色の卵・青い卵の値段が卵と同じ 6c／4c で、茶色の卵は砂漠・サバンナの村とジャングル、青い卵は雪原・タイガの村の品揃えの候補にあること（実際の村での並び方とアイコンは見ていない） |
| 30-5 | `snowy_ice_prices` | サーバー | ✔ | 雪原の村で氷と氷塊の両方に ×0.7 が掛かり、氷塊1個の買値が氷9個の買値より安いこと（市場ごとのばらつき ±15% の両端でも）（実際の村では見ていない） |
| 30-6 | `savanna_saddle_price` | サーバー | ✔ | サバンナの村の鞍の基準価格が 117c（×0.9）で、革3つと鉄インゴットを買って作って売っても儲からないこと（実際の村では見ていない） |
| 33-1 | `specials_of_a_small_market` | サーバー | ✔ | 全部 |
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
| 16-1・24-1 | `title_reputation_trends` | クライアント | ✔ | 全部 |
| 24-1b | `trend_position_setting` | クライアント | ✔ | 全部 |
| 16-11 | `debug_tooltip` | クライアント | ✔ | 基準価格・値段・在庫の行が出て、OFF にすると出ないこと（倍率・戻るまでの時間・価格グラフの位置は見ていない） |
| 23-3・23-4・24-4 | `board_news_and_markets` | クライアント | ✔ | 全部 |
| 9-1 | `board_news_and_markets` | クライアント | ✔ | 市場の行に名前と方角・距離が出て、ニュースから開いた市場の商品の一覧に列の見出し（列とずれていない、マウスで説明）があり、ホイールで最後の商品まで見えること（市場一覧の並び・買値以外の説明は見ていない） |
| 18-1・31-2 | `sell_all_box` | クライアント | ✔ | 全部 |
| 18-2 | `sell_all_box` | クライアント | ✔ | 行のクリックで除外が切り替わって合計が変わり、開き直しても残ること（道具・防具が最初から除外なのは、市場が買い取る道具・防具がある時だけ見る） |
| 31-1 | `goods_order` | クライアント | ✔ | 全部 |
| 22-20・22-21・31-3 | `town_tab` | クライアント | ✔ | 全部 |
| 28-1・29-1・34-1・34-2 | `town_tab` | クライアント | ✔ | 噴水広場・公園を含む14種類の建物が出て、入りきらない時はホイールでスクロールでき、スクロールした後もクリックで正しい建物が選ばれること。行のツールチップに大きさ、選んだ建物の名前の右に大きさが出て、マウスで詳しい大きさが出ること（倉庫で。砂漠と平原の高さの違い・行の見え方は画像で見る） |
| 8-6 | `exchange_tab` | クライアント | ✔ | 全部 |
| 7-4 | `wallet_screen` | クライアント | ✔ | 全部 |
| 6-3 | `trader_opens_market` | クライアント | ✔ | 全部 |
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
- 16-1・24-1 `title_reputation_trends`：[24-1_title_row.png](../../Source/TradeExpansion/build/autotest/screenshots/24-1_title_row.png)
- 16-1・24-1 `title_reputation_trends`：[16-1_reputation_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/16-1_reputation_tooltip.png)
- 16-1・24-1 `title_reputation_trends`：[24-1_trends_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/24-1_trends_tooltip.png)
- 16-1・24-1 `title_reputation_trends`：[16-1_long_name.png](../../Source/TradeExpansion/build/autotest/screenshots/16-1_long_name.png)
- 24-1b `trend_position_setting`：[24-1b_title_right.png](../../Source/TradeExpansion/build/autotest/screenshots/24-1b_title_right.png)
- 24-1b `trend_position_setting`：[24-1b_status_row.png](../../Source/TradeExpansion/build/autotest/screenshots/24-1b_status_row.png)
- 16-11 `debug_tooltip`：[16-11_debug_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/16-11_debug_tooltip.png)
- 23-3・23-4・24-4 `board_news_and_markets`：[23-3_board_news.png](../../Source/TradeExpansion/build/autotest/screenshots/23-3_board_news.png)
- 23-3・23-4・24-4 `board_news_and_markets`：[24-4_board_market_trends.png](../../Source/TradeExpansion/build/autotest/screenshots/24-4_board_market_trends.png)
- 23-3・23-4・24-4 `board_news_and_markets`：[23-4_news_opens_market.png](../../Source/TradeExpansion/build/autotest/screenshots/23-4_news_opens_market.png)
- 23-3・23-4・24-4 `board_news_and_markets`：[9-1_board_market_scrolled.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1_board_market_scrolled.png)
- 18-1・31-2 `sell_all_box`：[18-1_sell_all.png](../../Source/TradeExpansion/build/autotest/screenshots/18-1_sell_all.png)
- 18-1・31-2 `sell_all_box`：[18-2_excluded.png](../../Source/TradeExpansion/build/autotest/screenshots/18-2_excluded.png)
- 31-1 `goods_order`：[31-1_sell_order.png](../../Source/TradeExpansion/build/autotest/screenshots/31-1_sell_order.png)
- 22-20・22-21・31-3 `town_tab`：[22-20_town_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/22-20_town_tab.png)
- 22-20・22-21・31-3 `town_tab`：[29-1_town_scrolled.png](../../Source/TradeExpansion/build/autotest/screenshots/29-1_town_scrolled.png)
- 22-20・22-21・31-3 `town_tab`：[22-21_warehouse_selected.png](../../Source/TradeExpansion/build/autotest/screenshots/22-21_warehouse_selected.png)
- 8-6 `exchange_tab`：[8-6_exchange_down.png](../../Source/TradeExpansion/build/autotest/screenshots/8-6_exchange_down.png)
- 7-4 `wallet_screen`：[7-4_wallet_screen.png](../../Source/TradeExpansion/build/autotest/screenshots/7-4_wallet_screen.png)
- 6-3 `trader_opens_market`：[6-3_trader_opens.png](../../Source/TradeExpansion/build/autotest/screenshots/6-3_trader_opens.png)
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
