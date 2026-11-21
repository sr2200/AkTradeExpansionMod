# ゲーム内の自動テストの結果

`autotest.bat`（`Source/TradeExpansion`）が作る生成物です。手で直さないでください。結果はチェックリスト（`ingame-checklist.md`）の各項目にも書き込まれます。

- サーバー：バージョン 0.2.41、2026-11-21 10:29、194 件中 0 件失敗
- クライアント：バージョン 0.2.41、2026-11-21 10:43、144 件中 0 件失敗
- クライアント（Mod Menu・YACL なし）：バージョン 0.2.41、2026-11-21 10:29、3 件中 0 件失敗
- クライアント（Mod Menu だけ）：バージョン 0.2.41、2026-11-21 10:29、2 件中 0 件失敗
- クライアント（YACL だけ）：バージョン 0.2.41、2026-11-21 10:43、2 件中 0 件失敗
- クライアント（JEI）：バージョン 0.2.41、2026-11-21 10:30、8 件中 0 件失敗
- クライアント（REI）：バージョン 0.2.41、2026-11-21 10:29、7 件中 0 件失敗
- クライアント（JourneyMap）：バージョン 0.2.41、2026-11-21 10:29、3 件中 0 件失敗
- クライアント（建物の画像）：バージョン 0.2.41、2026-11-21 10:28、2 件中 0 件失敗
- チェックリスト 337 項目のうち、今回 自動で完了 206・一部を確認 119・失敗 0

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
| 21-4 | `transport_taken_by_other_player` | サーバー | ✔ | ほかのプレイヤー（サーバーに足した2人目）が、受け取られた輸送依頼を受け取ろうとすると「受け取り済み」で断られ、保証金も引かれず荷箱も増えず、依頼は最初の人のままであること（本物の2人の画面は見ていない） |
| 21-3 | `transport_deposit_funds_and_card` | サーバー | ✔ | 所持金が保証金より少ないと受け取れず、カード払いなら口座から引かれること |
| 21-8 | `transport_expires` | サーバー | ✔ | 期限を過ぎると届け先で「期限が切れています」になり、出した市場の評判が下がること（荷箱の説明の表示は見ていない） |
| 21-9 | `transport_per_market_zero` | サーバー | ✔ | 全部 |
| 21-10 | `transport_needs_another_market` | サーバー | ✔ | 全部 |
| 26-2 | `staff_needs_reputation` | サーバー | ✔ | 評判「よそ者」では雇えないこと（ボタンと説明の表示は見ていない） |
| 26-5・26-6・26-7・26-14・26-22・26-30 | `carrier_round_trip` | サーバー | ✔ | おまかせを雇い、資金不足 → 所持金を入れると仕入れて出かけ、行き先で売って戻り、歩合が利益の2割になること。出かけている間は解雇できず、休ませると戻ってから止まること。仕入れで相場が1個 0.25 動くこと。帰り荷 OFF では帰り荷を仕入れないこと（画面の表示は見ていない） |
| 26-27・26-29 | `carrier_return_cargo` | サーバー | ✔ | 行きに儲からなくても帰り荷の見込みで空荷で出かけ、行き先で帰り荷を仕入れ、戻って売ると歩合に帰り荷の利益も入ること（ログの文と右パネルは見ていない） |
| 26-30 | `carrier_backhaul_off_no_empty_trip` | サーバー | ✔ | 帰り荷 OFF では、行きに儲からない時に空荷で出かけないこと（見込みの表示は見ていない） |
| 26-11・26-12 | `clerk_sells_and_sold_out` | サーバー | ✔ | 番頭を雇うと品が預けられ、売れて「売り切れ」になり、預けると再開し、引取で品が戻ること（一覧の見込みの表示は見ていない） |
| 26-18 | `clerk_saved` | サーバー | ✔ | 番頭（品を預けて休止中、所持金入り）の所持金・預かった品・様子・ログ・設定が、保存データの書き出しと読み直しで同じこと（ワールドを実際に開き直すのは運び屋だけ見た） |
| 26-20 | `staff_turned_off` | サーバー | ✔ | staff.enabled を OFF にすると、運送中の運び屋が止まり（日が進んでも着かない）、雇う・所持金を入れる・休ませる・品を変えるは断られ、出金・引取・解雇は運送中でもでき、所持金と荷が手元に戻ること。ON に戻すと止まっていた運び屋が続きから着くこと（タブの表示は tabs_turned_off で見る） |
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
| 27-5 | `advancements_requests` | サーバー | ✔ | 全部 |
| 27-6 | `advancements_reputation_and_hiring` | サーバー | ✔ | 全部 |
| 27-7 | `advancements_donation` | サーバー | ✔ | 「発展」タブでコインを寄付すると「村への投資」が付くこと（建物の完成の「槌音」「村おこし」は見ていない） |
| 27-8 | `advancements_money` | サーバー | ✔ | 全部 |
| 27-9 | `advancements_after_joining_again` | サーバー | ✔ | 全部 |
| 27-10 | `advancements_survive_death` | サーバー | ✔ | 全部 |
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
| 22-37b | `site_radius_by_level` | サーバー | ✔ | 全部 |
| 22-37c | `site_search_time` | サーバー | ✔ | 既定の設定（半径32＋レベルごとに8）の市場レベル5で、半径64の中が杭で全部埋まっている時に「建てる場所がありません」になり、その場所探しにかかった時間をログに出すこと（重さの判定はしない。混んだ本物の村では測っていない） |
| 22-39 | `protect_facilities` | サーバー | ✔ | 全部 |
| 22-35 | `site_stakes` | サーバー | ✔ | 「杭をもらう」で杭が1本手に入り、持っている時はもらえない。市場の近くに置くと建設予定地になり、遠くに置くと「市場がありません」が届くこと（パーティクルの枠は見ていない） |
| 22-36 | `build_at_stake` | サーバー | ✔ | 全部 |
| 22-38 | `site_ground_kinds` | サーバー | ✔ | 全部 |
| 28-7 | `fountain_reputation` | サーバー | ✔ | 全部 |
| 28-8 | `park_points` | サーバー | ✔ | 公園がある村で 1,000c 寄付すると発展度が 1,250 増え（結果のパケットの値。チャットの文は見ていない）、評判の増え方は公園が無い時と同じで、噴水広場もあればその分だけ増えること |
| 28-9・27-7 | `eight_kinds` | サーバー | ✔ | 全部 |
| 29-3 | `watchtower_ladder_and_height` | サーバー | ✔ | 見張り塔（高さ21）と時計塔（高さ22）が建ち、見張り塔には床から見張り台までつながったはしごがあること。杭の上の高い所（20ブロック上）にブロックがあると「空間がありません」で断られること（本物のプレイヤーが上れるかは見ていない） |
| 29-4 | `harbor_at_the_shore` | サーバー | ✔ | 陸だけの所の杭では「奥側が水の上にありません」で断られ、岸の杭では手前（6列）が陸・奥が水の上に建ち、桟橋の下が水底まで埋まること（自動の場所探しは harbor_effects で見る。形は見ていない） |
| 29-6 | `clock_tower_recovery` | サーバー | ✔ | 全部 |
| 29-7 | `watchtower_effects` | サーバー | ✔ | 見張り塔の村では、商会の動きの割合（重み）が半分になり、そこを行き来する運び屋の事故の倍率が 0.5 になること（事故の回数・値動きの大きさは数えていない。倍率で確かめる） |
| 29-7 | `watchtower_accident_count` | サーバー | ✔ | staff.accidentBase を 0.5 にして、運び屋の往復の終わりの事故の判定を見張り塔の無い時とある時に 400 回ずつ行うと（運び屋の事故の処理そのものを呼ぶ）、ある時の回数がおよそ半分（確率どおり）になること。商会の動きを同じ品・同じ強さ・同じ時刻で1日分押すと、見張り塔のある市場の相場の押しが、見張り塔の効き目を設定で 1 にした時の半分になること（交易画面の値動きは見ていない） |
| 29-8 | `tavern_board` | サーバー | ✔ | 全部 |
| 29-9 | `market_hall_goods` | サーバー | ✔ | 全部 |
| 29-11 | `theater_trends` | サーバー | ✔ | 野外劇場の村では、人気の分野の倍率が 1+(m−1)×1.5、余り気味の分野が 1−(1−m)÷1.5 になって商品の値段に効き、交易画面にもその倍率が送られること（掲示板の流行の表示は見ていない） |
| 29-13 | `festival_days` | サーバー | ✔ | 祭りの間隔 2日で、祭りの日に交易画面を開くと「今日は〇〇の祭りの日です」が届き、寄付の発展度と評判が2倍（公園があればさらに1.25倍）、発展のタブに「今日は祭り」（0日）、次の日は「祭りまで1日」で普通に戻ること（タブの文は見ていない） |
| 29-4・29-10 | `harbor_effects` | サーバー | ✔ | 自動の場所探しで岸が見つかって港が建つこと。港のある村の新しい輸送依頼の報酬が 1.25 倍になり、そこを行き来する運び屋の移動時間の倍率が 0.75 になり、実際に出かけた運び屋が、距離の時間の 0.75 倍で着くこと（その200ティック前はまだ運送中、200ティック後には着いている） |
| 29-12 | `hot_spring_rest` | サーバー | ✔ | 温泉のある村で雇った運び屋が事故に遭うと、療養の時間が半分（0.25日）になること（辞める確率は見ていない。既定では辞めない） |
| 28-2 | `fountain_water_stays` | サーバー | ✔ | 平原の村で噴水広場を建てると、水盤と柱の上に水（水源と流れ）が張り、水盤の底に明るさ10以上の光るブロックがあり、ランダムティックを速めて600ティック進めても、水が建物の外に出ず、建物のブロックが変わらないこと（ブロックの下の草が土になるのは除く。4本の流れの形・夜の見え方は画像で見る） |
| 28-4 | `park_stays` | サーバー | ✔ | 公園を建てると、生け垣と木の葉・池・サトウキビ・スイレンの葉・柵・ベンチ（階段）があり、ランダムティックを速めて600ティック進めても、葉が消えず、池の水が外に流れ出ず、サトウキビ・スイレンの葉も含めて建物のブロックが変わらないこと（ブロックの下の草が土になる・サトウキビが伸びるのは除く。池の下が低い地面の時は見ていない。見た目は画像で見る） |
| 29-5 | `hot_spring_and_festival_water_stay` | サーバー | ✔ | 温泉の湯船の底にソウルサンドがあって泡（泡の柱）が上がり、温泉と祭りの広場をランダムティックを速めて600ティック進めても、水が建物の外に出ず、建物のブロックが変わらないこと（ブロックの下の草が土になるのは除く。見た目は画像で見る） |
| 28-3 | `snow_does_not_freeze` | サーバー | ✔ | 全部 |
| 29-5 | `snow_hot_spring_does_not_freeze` | サーバー | ✔ | 雪原の村の温泉（雪原の見た目）の水が、ゲームの凍らせる処理（雪の降る時と同じく、どの列にも降水の処理を何度も呼ぶ）でも凍らず、同じ場所の村の外の水は凍ること |
| 28-5 | `snow_park_pond_may_freeze` | サーバー | ✔ | 雪原の村の公園は雪原の見た目（トウヒ）で建ち、池が凍ってもスイレンの葉が残ること（ほかの見た目と材料は画像で見る） |
| 29-2・22-50 | `buildings_stand_every_way` | サーバー | ✔ | 14種類の建物を7種類の見た目（平原・砂漠・サバンナ・雪原・タイガ・ネザー・エンド）と4つの向きで、完成の時と同じく構造物をブロックの更新つきで石の床に置くと、落ちて物になる部品（ボタン・旗・松明・はしご・吊り看板・ろうそく・ケーキなど）が無く、ランダムティックを速めて30ティック進めてもブロックが変わらず（火が付かない。銅の避雷針が錆びる・ブロックの下の草と菌糸が土とネザーラックになる・つる・サトウキビ・竹が伸びるのは除く）、水と溶岩が建物の外に流れ出ないこと（材料と形の見た目は画像で見る。村の家や木の近くでは見ていない） |
| 29-14・22-52・22-53・22-55・22-56・28-6 | `building_parts_by_style` | サーバー | ✔ | 7種類の見た目それぞれに14種類の建物の構造物があり（平原に代わらない）、看板（建物の目印）が1つずつあること。宿屋は村の見た目でベッド4つ・ネザーとエンドはベッドなしでカーペット、煙突に火の付いた焚き火（エンドはエンドロッド）。依頼所と時計塔に鐘、時計塔にボタンと避雷針、見張り塔にはしご・旗・松明、酒場に吊り看板とろうそく、市場にハチの巣（bee_nest）と飾り壺、劇場と祭りの広場に旗、祭りの広場にろうそくのケーキ、銀行に鉄格子、温泉にソウルサンド。噴水広場はネザーで溶岩・ほかは水、公園はネザーで真紅のキノコと溶岩・エンドでコーラスとエンドロッド・村で葉（材料の見た目、鐘を鳴らせる・寝られる・村人が来るは見ていない） |
| 22-51 | `look_by_biome` | サーバー | ✔ | 全部 |
| — | `goods_with_components` | サーバー | ✔ | 全部 |
| 30-7・32-2 | `consumable_goods` | サーバー | ✔ | 真紅の幹・歪んだ幹・ハチミツ入りの瓶・サトウキビ・矢・茶色の卵・青い卵が消耗品で、相場の戻り（半減期）と在庫の減りが消耗品の速さになること（画面の表示は見ていない） |
| 36-1 | `consumables_review` | サーバー | ✔ | 砂利・粘土玉・砂岩・氷・サボテン・スイレンの葉・ネザーウォート・ソウルサンド・ブラックストーン・玄武岩・エンドストーンが消耗品で、トーチフラワー・ウツボカズラ・ウィザーローズ・金のニンジン・腐った肉は消耗品でないこと（コマンドの表示は見ていない） |
| 32-1 | `brown_and_blue_eggs` | サーバー | ✔ | 茶色の卵・青い卵の値段が卵と同じ 6c／4c で、茶色の卵は砂漠・サバンナの村とジャングル、青い卵は雪原・タイガの村の品揃えの候補にあること（実際の村での並び方とアイコンは見ていない） |
| 30-5 | `snowy_ice_prices` | サーバー | ✔ | 雪原の村で氷と氷塊の両方に ×0.7 が掛かり、氷塊1個の買値が氷9個の買値より安いこと（市場ごとのばらつき ±15% の両端でも）（実際の村では見ていない） |
| 30-6 | `savanna_saddle_price` | サーバー | ✔ | サバンナの村の鞍の基準価格が 117c（×0.9）で、革3つと鉄インゴットを買って作って売っても儲からないこと（実際の村では見ていない） |
| 33-1 | `specials_of_a_small_market` | サーバー | ✔ | 全部 |
| 12-9 | `audit_villagers` | サーバー | ✔ | 全部 |
| 13-3 | `config_file_reload` | サーバー | ✔ | 全部 |
| 13-4 | `consumable_and_goods_commands` | サーバー | ✔ | 全部 |
| 13-5 | `audit_simulate_market_info` | サーバー | ✔ | 全部 |
| 13-7 | `added_goods_as_special` | サーバー | ✔ | 全部 |
| 30-2 | `damaged_goods_price` | サーバー | ✔ | 全部 |
| 30-3 | `coast_market` | サーバー | ✔ | 浜辺のバイオーム（/fillbiome で変えた）に交易所を置くと、市場の種類が「海辺の市場」になって生鱈が並ぶこと。村の品揃えの優先度が海辺の市場より高いこと（データ。テストの世界に村の構造物が無いので、浜辺の村そのものは見ていない） |
| 33-2 | `specials_of_a_large_market` | サーバー | ✔ | 全部 |
| 33-3 | `special_settings_next_day` | サーバー | ✔ | 「掘り出し物を市場の規模に合わせる」を OFF にすると、その日の掘り出し物は変わらず、次の日から市場のレベルより高い品も出ること。「高い掘り出し物の出にくさ」を上げると高い品が出にくくなること（設定画面はクライアントのテストで見ている） |
| 5-5・17-4 | `nether_market_goods` | サーバー | ✔ | 全部 |
| 17-2・17-3 | `nether_prices_follow_overworld` | サーバー | ✔ | 全部 |
| 27-4 | `advancements_nether_and_end` | サーバー | ✔ | 全部 |
| 30-4 | `level5_nether_and_end_goods` | サーバー | ✔ | 村人24人（レベル5）のネザー・エンドの市場で、並ぶ商品が候補の一部だけで（すべての候補が並ぶことはない）、market reroll の後も同じ数の候補の一部であること。ネザーの別の場所（400 ブロック離れた所）の村人24人の市場とは品揃えが違うこと（同じ場所で reroll を繰り返すと、場所で決まる乱数なので同じ品揃えになる） |
| 8-2b | `nether_mobs_drop_gold_coins` | サーバー | ✔ | 全部 |
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
| 1-2 | `startup_with_modmenu_yacl` | クライアント | ✔ | 全部 |
| 1-3 | `config_screen_tabs` | クライアント | ✔ | 設定画面が開き、各タブに切り替えられることのみ（表示は画像で確認） |
| 1-3 | `layout_config_screen` | クライアント | ✔ | タイトル画面から開いた設定画面の各タブの文字が重ならず、画面からはみ出さず、翻訳キーがそのまま出ないこと（日本語と英語、854×480 と 1920×1080）。タイトル画面では「追加の商品と消耗品」が押せないこと |
| 2-8 | `stall_place_and_open` | クライアント | ✔ | 全部 |
| 1-4 | `creative_tab` | クライアント | ✔ | 全部 |
| 1-5 | `item_tooltips` | クライアント | ✔ | 全部 |
| 21-2 | `cargo_tooltip` | クライアント | ✔ | 荷箱の説明に中身・届け先・場所（ネザーなら「（ネザー）」）・報酬と保証金・依頼元・残り時間が出ること |
| 21-8 | `cargo_tooltip` | クライアント | ✔ | 期限を過ぎた荷箱の説明が赤い「期限切れ（届けられません）」になり、残り時間が消えること |
| 2-2 | `placement_hint` | クライアント | ✔ | 全部 |
| 3-10 | `buy_with_screen_buttons` | クライアント | ✔ | 全部 |
| 2-9・2-10 | `buy_with_screen_buttons` | クライアント | ✔ | 交易画面のボタン（数量 1・8＋・64、購入する）で、合計が数量で変わり、表示どおりの額で買えること（「最大」・財布へのお釣りは見ていない） |
| 3-1 | `price_arrows` | クライアント | ✔ | 全部 |
| 3-4 | `balance_display` | クライアント | ✔ | 全部 |
| 3-5 | `balance_position_setting` | クライアント | ✔ | 全部 |
| 3-8・3-9 | `row_states` | クライアント | ✔ | 全部 |
| 3-7・23-11 | `row_states` | クライアント | ✔ | 全部 |
| 3-2・3-3 | `row_meter_and_graph` | クライアント | ✔ | 全部 |
| 23-2・24-2 | `row_rival_and_trend_tooltips` | クライアント | ✔ | 全部 |
| 3-11 | `max_with_exact_money` | クライアント | ✔ | 全部 |
| 2-9・2-10 | `max_and_change` | クライアント | ✔ | 全部 |
| 2-14 | `shift_confirm_all` | クライアント | ✔ | 全部 |
| 5-7 | `special_and_emerald_rows` | クライアント | ✔ | 本日の掘り出し物の行の名前に★が付いて水色になり、ツールチップに説明が出ること（入れ替わり・レベルごとの数はサーバーのテストで見ている） |
| 5-2 | `special_and_emerald_rows` | クライアント | ✔ | 売却タブのエメラルド貨の行が「4c/10」のように10枚あたりの値段で、選ぶと数量が「1 (10)」と10枚単位になること（ネザー・エンド・前のバージョンの市場は見ていない） |
| 14-2 | `other_player_buys` | クライアント | ✔ | サーバーに足した2人目のプレイヤー（同じ屋台の画面を開いている）が10個買うと、開いたままの画面の行の値段と在庫がすぐ変わること（本物の2人では見ていない） |
| 16-3 | `regular_customer_prices` | クライアント | ✔ | 全部 |
| 23-10 | `price_changed_message` | クライアント | ✔ | 選んだ品の値段をサーバーで変えてから（日をまたいで商会や流行が動いた時の代わり）表示どおりの額で買うと、赤い「価格が変動しました…」が出て、品もお金も動かず、画面が最新の値段になること |
| 2-13 | `sell_slot_selects_good` | クライアント | ✔ | 全部 |
| 2-19 | `shift_click_sell_slot` | クライアント | ✔ | 全部 |
| 16-1・24-1 | `title_reputation_trends` | クライアント | ✔ | 全部 |
| 24-1b | `trend_position_setting` | クライアント | ✔ | 全部 |
| 16-11 | `debug_tooltip` | クライアント | ✔ | 全部 |
| 23-3・23-4・24-4 | `board_news_and_markets` | クライアント | ✔ | 全部 |
| 9-1 | `board_news_and_markets` | クライアント | ✔ | 市場の行に名前と方角・距離が出て、ニュースから開いた市場の商品の一覧に列の見出し（列とずれていない、マウスで説明）があり、ホイールで最後の商品まで見えること（市場一覧の並び・買値以外の説明は見ていない） |
| 9-1b | `board_deals` | クライアント | ✔ | 全部 |
| 20-5 | `map_coordinates_to_chat` | クライアント | ✔ | JourneyMap なしで「この市場の座標」が掲示板の市場の一覧に出て、押すとこの市場の名前と座標が自分のチャットに出る（クリックでコピー）こと。市場を選ぶと消えること（近くに市場が無い時は見ていない） |
| 20-1 | `map_coordinates_to_chat` | クライアント | ✔ | JourneyMap なしで、選んだ市場の「座標を表示」を押すと、市場名と座標が自分のチャットに出て、クリックで座標がコピーされること（マルチの他のプレイヤーは見ていない） |
| 19-4 | `list_scrollbars` | クライアント | ✔ | 全部 |
| 9-1 | `board_market_list` | クライアント | ✔ | 全部 |
| 10-1 | `board_market_list` | クライアント | ✔ | サーバーで付けた市場の名前（名札と同じ処理）が、ほかの村の掲示板の市場の行と、選んだ市場の題に出ること（名札を使う操作・チャット・名札が減ることはサーバーのテストで見ている） |
| 29-11 | `board_theater_trends` | クライアント | ✔ | 野外劇場の村の流行の倍率（人気は 1+(m−1)×1.5、余り気味は 1−(1−m)÷1.5）が、ほかの村の掲示板の市場の行のツールチップと、その村の交易画面の「流行」のツールチップに出ること（値段への効きはサーバーのテストで見ている） |
| 20-5 | `board_no_markets` | クライアント | ✔ | 近くに市場が無い時（board.radius 150）も「この市場の座標」が出て、押すとこの市場の名前と座標が自分のチャットに出ること（JourneyMap なし） |
| 35-2 | `board_no_markets` | クライアント | ✔ | 1人用の世界で、近くに市場が無い時の「半径 ◯ ブロック以内にほかの市場はありません」の数字が board.radius で、酒場のある村ではサーバーが使った半径（tavernBoardMultiplier 倍）になること（サーバーと自分の設定が違う時は見ていない） |
| 30-1 | `goods_names` | クライアント | ✔ | 交易画面と掲示板に並ぶ品（このワールドの市場の品）の名前が翻訳されていて、耐久値のある品が新品で並ぶこと（アイコンの見た目は画像で見る。JEI／REI は見ていない） |
| 26-2 | `staff_needs_reputation` | クライアント | ✔ | 全部 |
| 18-1・31-2 | `sell_all_box` | クライアント | ✔ | 全部 |
| 18-2 | `sell_all_box` | クライアント | ✔ | 行のクリックで除外が切り替わって合計が変わり、開き直しても残ること（道具・防具が最初から除外なのは、市場が買い取る道具・防具がある時だけ見る） |
| 18-2・18-3 | `sell_all_tools_and_sale` | クライアント | ✔ | 全部 |
| 18-4・18-5 | `sell_all_limits_and_other_player` | クライアント | ✔ | 全部 |
| 31-1 | `goods_order` | クライアント | ✔ | 全部 |
| 22-20・22-21・31-3 | `town_tab` | クライアント | ✔ | 全部 |
| 28-1・29-1・34-1・34-2 | `town_tab` | クライアント | ✔ | 噴水広場・公園を含む14種類の建物が出て、入りきらない時はホイールでスクロールでき、スクロールした後もクリックで正しい建物が選ばれること。行のツールチップに大きさ、選んだ建物の名前の右に大きさが出て、マウスで詳しい大きさが出ること（倉庫で。砂漠と平原の高さの違い・行の見え方は画像で見る） |
| 8-6 | `exchange_tab` | クライアント | ✔ | 全部 |
| 7-4 | `wallet_screen` | クライアント | ✔ | 全部 |
| 7-12 | `wallet_offhand_and_limits` | クライアント | ✔ | 全部 |
| 6-3 | `trader_opens_market` | クライアント | ✔ | 全部 |
| 27-1 | `advancements_screen` | クライアント | ✔ | 全部 |
| 27-11 | `advancements_screen` | クライアント | ✔ | 全部 |
| 13-10 | `clock_advance_updates_open_screen` | クライアント | ✔ | 開いたままの交易画面に新しい時計が届き、依頼の残り時間の表示がすぐ減ること（建設・雇い人の残り時間は clock_advance_staff_and_town で見る） |
| 12-1 | `villager_coin_prices` | クライアント | ✔ | 全部 |
| 12-4 | `villager_coin_prices` | クライアント | ✔ | 支払い欄の下の「代金（所持）」が、所持金が足りない時は赤、足りる時は灰色になること（品物が出ない・取れないはサーバーのテストで見ている） |
| 7-8・7-9 | `bank_card_buttons` | クライアント | ✔ | 全部 |
| 19-1・19-2 | `screen_size_by_gui_scale` | クライアント | ✔ | 全部 |
| 19-5 | `resize_open_screen` | クライアント | ✔ | 全部 |
| 19-6 | `long_names_big_numbers` | クライアント | ✔ | 全部 |
| 3-6・19-1・19-2・19-3・22-1・26-1 | `layout_market_screen_ja` | クライアント | ✔ | 交易画面の各タブ（掲示板で市場を選んだ状態・依頼と輸送依頼を選んだ状態・発展タブで建物を選んだ状態・「まとめて売る」の枠・カードを持っていて支払いのボタンがある状態も）の文字が重ならず、画面と枠からはみ出さず、翻訳キーがそのまま出ないこと（日本語、854×480 と 1920×1080、GUIの大きさ 1〜4）。一覧の行数・スクロールバーは見ていない |
| 26-1 | `layout_market_screen_en` | クライアント | ✔ | 英語でも交易画面の各タブ（日本語と同じ状態）の文字が重ならず、はみ出さず、翻訳キーがそのまま出ないこと |
| 13-1 | `config_save_applies` | クライアント | ✔ | 全部 |
| 33-3 | `special_settings` | クライアント | ✔ | 設定画面の「市場」タブに2つの設定があり、変えて保存するとすぐ設定とファイルに入ること（次の日の掘り出し物は見ていない） |
| 1-3 | `layout_goods_manager` | クライアント | ✔ | 「追加の商品と消耗品」画面（3つのモード）の文字が重ならず、画面からはみ出さず、翻訳キーがそのまま出ないこと（ワールドの中で開く。日本語と英語、854×480 と 1920×1080） |
| 13-6・13-8・13-9 | `goods_manager` | クライアント | ✔ | 全部 |
| 5-4 | `bank_tab_by_level` | クライアント | ✔ | 全部 |
| 22-30 | `bank_tab_by_level` | クライアント | ✔ | 銀行の建物があればレベル1でも銀行タブが出て、そこで預けると利息の行が「200,000c まで・〇〇の銀行に登録」になること（カード払いの手数料は見ていない） |
| 7-7 | `bank_tab` | クライアント | ✔ | 銀行タブのボタン（預け入れ 1,000c・引き出し 100c・全額）で残高と現金が増減し、残高の行が変わること（死んだ時は見ていない） |
| 16-13 | `bank_tab` | クライアント | ✔ | 銀行タブの利息の行「利息：1日 0.03%（100,000c まで）」（利息が付くことは見ていない） |
| 2-18 | `trade_refused_messages` | クライアント | ✔ | 全部 |
| 7-11 | `bank_refused_messages` | クライアント | ✔ | 全部 |
| 8-7 | `exchange_refused_and_fee` | クライアント | ✔ | 全部 |
| 9-2 | `quest_tab` | クライアント | ✔ | 全部 |
| 16-5 | `quest_tab` | クライアント | ✔ | 全部 |
| 16-4 | `quest_locked` | クライアント | ✔ | 全部 |
| 16-7 | `quest_locked` | クライアント | ✔ | 全部 |
| 21-1・21-5・21-7 | `transport_request` | クライアント | ✔ | 全部 |
| 21-2 | `transport_request` | クライアント | ✔ | 「荷箱を受け取る」で保証金が引かれて荷箱が手に入り、ボタンが「受け取り済み」になること（荷箱の説明は見ていない） |
| 21-4 | `transport_taken_by_other` | クライアント | ✔ | ほかの人が受け取った輸送依頼（受けた人をサーバーで足したもの）が一覧で灰色の「運送中」になり、受け取るボタンが出ないこと（本物の2人では見ていない） |
| 22-2・22-3・22-4 | `town_donations` | クライアント | ✔ | 全部 |
| 28-8 | `town_donations` | クライアント | ✔ | 全部 |
| 22-10 | `town_other_player_donates` | クライアント | ✔ | サーバーに足した2人目のプレイヤー（同じ屋台の画面を開いている）が寄付すると、開いたままの発展タブの発展度と、寄付した人の名前の並び（多い順）がすぐ変わること（本物の2人では見ていない） |
| 22-22・22-25 | `town_build_reasons` | クライアント | ✔ | 全部 |
| 22-23b | `town_build_reasons` | クライアント | ✔ | 資材が足りない時に「建てる」のツールチップが「資材があと N 個必要です」になり、development.requireMaterials を OFF にすると押せること（資材の数の倍率は見ていない） |
| 22-23 | `town_materials_and_stake` | クライアント | ✔ | 「資材を寄付（N）」の数が持っている資材の数で、押すと資材が減り、発展度が増え、残りの数が減ること（上限まで寄付した後は見ていない） |
| 22-35 | `town_materials_and_stake` | クライアント | ✔ | 「杭をもらう」で杭が手に入り、持っている時はもらえないこと（置いた時は見ていない） |
| 22-23 | `town_material_cap` | クライアント | ✔ | 全部 |
| 22-23b | `town_material_cap` | クライアント | ✔ | 全部 |
| 22-24・22-26・22-33・29-13 | `town_row_states` | クライアント | ✔ | 発展タブの行の「建設中 約N分」「✔ 完成」「壊れた（建て直しの発展度）」「✔ 今日は祭り／祭りまでN日」（建物はサーバーで直接作る。建設・完成・壊す流れは見ていない） |
| 34-2 | `town_size_by_style` | クライアント | ✔ | 全部 |
| 22-32 | `facility_sign_opens_tab` | クライアント | ✔ | 全部 |
| 22-9・26-20・24-7 | `tabs_turned_off` | クライアント | ✔ | 設定を OFF にすると画面を開き直した時に「発展」タブが消え、雇い人がいない人には「雇い人」タブが無く、いる人には出ること。流行の数を 0 にすると次の期間に「流行」が出ないこと（取引しても発展度が増えないこと・雇い人の操作は見ていない） |
| 26-4 | `staff_hire_carrier` | クライアント | ✔ | 全部 |
| 26-3 | `staff_hire_carrier` | クライアント | ✔ | 品の一覧の先頭が「おまかせ（売れる品を何種類も）」で、各行に「見込み +○c/往復」か「今は利益なし」が出て見込みの大きい順に並び、ツールチップにおすすめの行き先と数が出ること（宝箱のアイコンは画像で見る） |
| 26-6・26-7・26-9・26-14 | `staff_carrier_trip` | クライアント | ✔ | 運び屋の行の「資金不足」「→運送 N分」「←帰路 N分」「休み」「値下がり待ち」、Shift で +1,000c／+10,000c になり10倍入ること、出かけている間は「解雇する」「変更」が押せないこと、「休止」が「再開」になること、往復の後にツールチップのログに往復が出ること、「設定」におすすめが出ること（「利益なし」は見ていない） |
| 26-13 | `staff_clerk` | クライアント | ✔ | 全部 |
| 26-11 | `staff_clerk` | クライアント | ✔ | 番頭の品の一覧（手持ちのある品が先・「1日 約○c・手持ち○」・おすすめのツールチップ）、下限の初期値がおすすめ（売値の85%）、雇うと品が預けられること（少しずつ売れ続けるのは見ていない） |
| 26-15・26-17 | `staff_limit_and_other_market` | クライアント | ✔ | 全部 |
| 26-5 | `staff_limit_and_other_market` | クライアント | ✔ | おまかせを選ぶと設定が「1回に運ぶ数」だけになり、画面から雇えること |
| 26-25 | `staff_layout` | クライアント | ✔ | 全部 |
| 26-23 | `staff_carrier_change` | クライアント | ✔ | 全部 |
| 26-27・26-28・26-16 | `staff_carrier_change` | クライアント | ✔ | 売れ残りと帰り荷を持って戻った運び屋の右パネルが「荷 ×○個・帰り荷 ×○個」になり、ツールチップのログに帰り荷の仕入れが出ること。「変更」でおまかせに変えると、設定の画面に「今持っている荷 ×○個は手元に戻ります」が出て、売れ残りと帰り荷が手元に戻ること。画面の「解雇する」で、所持金と、売れ残りと2種類の帰り荷（サーバーで持たせる）が種類ごとに数どおり戻り、一覧から消えること（帰り荷の売れた文・「引取」での帰り荷は見ていない） |
| 26-24・26-12 | `staff_clerk_change` | クライアント | ✔ | 全部 |
| 26-16 | `staff_clerk_change` | クライアント | ✔ | 番頭を画面の「解雇する」で解雇すると所持金が戻り、一覧から消えること（運び屋の売れ残りは staff_carrier_change で見る） |
| 26-8・26-27 | `staff_auto_carrier` | クライアント | ✔ | 全部 |
| 26-28 | `staff_auto_carrier` | クライアント | ✔ | 売れ残りと帰り荷を持って戻った運び屋の「引取」で、荷と帰り荷の品が種類ごとに数どおり手元に戻ること（「変更」「解雇する」は staff_carrier_change で見る） |
| 35-3 | `staff_harbor_watchtower` | クライアント | ✔ | 港のある村では行き先のツールチップと設定の画面の「片道 約◯分」が harborTravelMultiplier の分だけ短く、雇った運び屋の出発時に決まる着く時刻もその時間になること。見張り塔のある村では行き先のツールチップの「事故の確率」が watchtowerAccidentMultiplier 倍になること（建物はサーバーで直接作る。着いたところまでは見ていない） |
| 13-10 | `clock_advance_staff_and_town` | クライアント | ✔ | 交易画面を開いたまま clock advance すると、雇い人タブの運び屋の「→運送 N分」と発展タブの「建設中 約N分」が開き直さずにすぐ減ること（建物はサーバーで直接作る） |
| 26-26 | `staff_backhaul_hint` | クライアント | ✔ | 全部 |
| 26-30 | `staff_backhaul_hint` | クライアント | ✔ | staff.carrierBackhaul を OFF にすると、雇う画面の行き先の見込みに帰り荷の分が入らず、ツールチップと設定の画面に帰り荷の説明が出ないこと（帰り荷を仕入れない・空荷で出かけないはサーバーのテストで見ている） |
| 26-29 | `staff_empty_trip` | クライアント | ✔ | 全部 |
| 26-22 | `staff_carrier_moves_price` | クライアント | ✔ | 全部 |
| 26-10 | `staff_accident` | クライアント | ✔ | 全部 |
| 26-6 | `staff_accident` | クライアント | ✔ | 行き先で高く売れない時に運び屋の行が「利益なし」になり、仕入れずに出かけないこと |
| 2-20 | `market_gone_while_open` | クライアント | ✔ | 全部 |
| 4-3 | `reopen_world_keeps_market` | クライアント | ✔ | 全部 |
| 22-11・22-40・23-8・24-8 | `reopen_world_keeps_town_and_staff` | クライアント | ✔ | 全部 |
| 26-18 | `reopen_world_keeps_town_and_staff` | クライアント | ✔ | 運び屋（運送中）と、おまかせの運び屋（2種類の荷と帰り荷、休止中）の所持金・荷（種類ごと）・様子・ログが保存データでも雇い人タブでも同じで、運送中の運び屋が続きから行き先に着くこと（番頭は見ていない） |
| 11-2 | `where_show_lines` | クライアント | ✔ | where show で、黄（同じ市場の範囲）・赤（新しい市場を作れない範囲）・青（数える範囲）の点がそれぞれの円の上に、地面（いちばん上のブロック＋0.3）に届き、where hide と30秒で止まること（緑の村の範囲は、テストの世界に村の構造物が無いので見ていない。見やすさは画像で確認） |
| 22-24b | `construction_effects` | クライアント | ✔ | 建設中の場所（サーバーで直接作った 7×7 の建物）に、床の高さのオレンジの点が枠の上に、木くず（オークの板）が範囲の中に、工事の音が範囲の中で届き、音の字幕が「工事の音」であること。48 ブロックより離れた時・constructionEffects を OFF にして reload した時・完成した後は届かないこと（見え方と、字幕が画面に出るところは見ていない） |
| 2-6 | `stall_facing_and_shape` | クライアント | ✔ | 使うキーでプレイヤーの東西南北に置いた交易所が、それぞれプレイヤーの方を向くこと（見た目は画像で確認） |
| 2-7 | `stall_facing_and_shape` | クライアント | ✔ | 交易所が隣のブロックの面を隠さず（透けたり黒くなったりしない）、当たり判定が1ブロックいっぱいでも空でもないこと（形どおりかは画像で確認） |
| 6-10b | `trader_robe_colours` | クライアント | ✔ | /summon で出した24人の商人の服の色がばらばら（8色のうち6色以上）で、クライアントにも同じ色が届き、遠くへ行ってチャンクごと保存・読み込みし直しても、リソースの再読み込み（F3+T）の後も色が変わらず、色を変えたテクスチャが残ること（スポーンエッグ・ワールドの入り直し・肌と縁取りの色は見ていない。見た目は画像で確認） |
| 6-10 | `trader_name_tag_and_eggs` | クライアント | ✔ | 名札を使うと商人に名前が付き、クライアントにも名前が見えること（村人の形と行商人の服は画像で見る） |
| 6-10b | `trader_name_tag_and_eggs` | クライアント | ✔ | スポーンエッグを地面に使って出した12人の商人の服の色がばらばら（4色以上）で、クライアントにも同じ色が届き、ワールドを閉じて開き直しても色と名札の名前が変わらないこと（肌と縁取りの色は画像で見る） |
| 7-2 | `wallet_picks_up_coins` | クライアント | ✔ | 全部 |
| 22-36b | `stake_outlines` | クライアント | ✔ | 杭を右クリックすると（サーバーでプレイヤーの右クリックを呼ぶ）、チャットに全部の建物が大きさつきで並び、大きさごとに1つ、その大きさの枠（四隅の柱）が、建てられる大きさは緑・建てられない大きさは赤で、杭を正面の真ん中にして届き、30秒で止まること（市場の建物は一時的に外す。杭を置いた時・もう1本置いた時・完成した建物の枠が出ないことは見ていない） |
| 28-10 | `stake_outlines` | クライアント | ✔ | 杭を右クリックすると、噴水広場（11×11）・公園（13×13）もチャットに並び、その枠も緑／赤で届くこと |
| 22-36b | `stake_placed` | クライアント | ✔ | 杭を置くと（使うキーと同じくサーバーで杭を使う。setPlacedBy）、置いた場所の文と建物の一覧がチャットに出て、すぐ枠が届き、完成した建物（依頼所）は一覧に出ないこと。もう1本置くと「先に置いた杭（先の杭の座標）がある間は、そちらに建ちます」と出ること（杭の右クリックと枠の形・色・30秒は stake_outlines で見る） |
| 2-11 | `sold_out_and_not_buying` | クライアント | ✔ | 全部 |
| 2-12 | `sold_out_and_not_buying` | クライアント | ✔ | 全部 |
| 22-27 | `debug_tooltip_warehouse_and_consumables` | クライアント | ✔ | 全部 |
| 30-7・32-2 | `debug_tooltip_warehouse_and_consumables` | クライアント | ✔ | 全部 |
| 7-6 | `died_with_wallet` | クライアント | ✔ | 全部 |
| 7-7 | `died_with_wallet` | クライアント | ✔ | 財布を持って死んで生き返った後も、銀行タブの残高が変わらないこと（預け入れ・引き出しは bank_tab で見る） |
| 2-16 | `screen_closes_when_away` | クライアント | ✔ | 全部 |
| 14-1・14-5 | `dedicated_server_one_player` | クライアント | ✔ | 専用サーバー（このプロセスの中で起動）に1人で接続し、村に交易所を置いて交易画面を開け、買う・売るが1往復できること。サーバーで clock advance と設定（同じプロセスなので設定は共有）を変えた後に切断すると、設定が自分の設定ファイルの値に、市場の時計のずれが 0 に戻ること（2人と、切断の後に1人用の世界に入るところは見ていない） |
| 13-2 | `dedicated_server_one_player` | クライアント | ✔ | 全部 |
| 20-4・25-1 | `log_has_no_mod_errors` | クライアント | ✔ | 全部 |
| 1-1 | `startup_without_modmenu_yacl` | クライアント（Mod Menu・YACL なし） | ✔ | 全部 |
| 15-3 | `trade_without_modmenu_yacl` | クライアント（Mod Menu・YACL なし） | ✔ | Mod Menu・YACL を外した開発用の起動構成（テスト用の Mod は入っている）で、新しいワールドに入って村に交易所を置き、交易画面で1つ買い1つ売れることのみ（配布 jar だけの起動構成では見ていない） |
| 1-1 | `log_has_no_mod_errors` | クライアント（Mod Menu・YACL なし） | ✔ | 全部 |
| 1-6 | `startup_modmenu_without_yacl` | クライアント（Mod Menu だけ） | ✔ | 全部 |
| 1-6 | `log_has_no_mod_errors` | クライアント（Mod Menu だけ） | ✔ | 全部 |
| 1-7 | `startup_yacl_without_modmenu` | クライアント（YACL だけ） | ✔ | 全部 |
| 1-7 | `log_has_no_mod_errors` | クライアント（YACL だけ） | ✔ | 全部 |
| 25-2 | `viewer_keys` | クライアント（JEI） | ✔ | 全部 |
| 25-6 | `viewer_keys` | クライアント（JEI） | ✔ | 全部 |
| 25-3 | `viewer_page` | クライアント（JEI） | ✔ | 近い順に5つの市場の名前・方角と距離・買値・売値・在庫、値段が屋台の画面と同じ（評判 300 点の割引）、品不足の市場の名前がオレンジ、行のツールチップに市場名と品不足の注意が出ること（売り切れ・買い取りの上限・何個ずつの取引かの注意は見ていない） |
| 25-4 | `viewer_page` | クライアント（JEI） | ✔ | ページを開いたまま、屋台の値段をサーバーで動かすと（相場の押しを変える。実際の売買ではない）、数秒で新しい値段になること |
| 25-5 | `viewer_page_none` | クライアント（JEI） | ✔ | board.radius を 16 にして市場から 40 ブロック離れた所で開くと「近くにこの品を扱う市場はありません…」と出て、ヘッダーの半径が 16 になること（512 ブロックの既定では見ていない） |
| 25-8 | `viewer_reload` | クライアント（JEI） | ✔ | 全部 |
| 30-1 | `viewer_new_goods` | クライアント（JEI） | ✔ | JEI で、新しい商品（エリトラ・トライデント・ダイヤのツルハシとチェストプレート）に「市場の相場」のページがあり、ページの品が耐久値の減っていない新品であること（アイコンの見た目は画像で見る） |
| 25-2 | `log_has_no_mod_errors` | クライアント（JEI） | ✔ | JEI を入れたクライアントのログに本 Mod のエラー・警告が出ないことのみ |
| 25-7 | `viewer_keys` | クライアント（REI） | ✔ | REI で、R と U のどちらでも「市場の相場」のページが出て、岩盤には出ないこと（25-2 の分） |
| 25-7 | `viewer_page` | クライアント（REI） | ✔ | REI で、25-3 のページの中身（近い順に5つ、屋台と同じ値段、品不足のオレンジとツールチップ）が出ること |
| 25-7 | `viewer_page` | クライアント（REI） | ✔ | REI で、ページを開いたまま値段が変わると数秒で新しくなること（25-4 の分。サーバーで相場の押しを変える） |
| 25-7 | `viewer_page_none` | クライアント（REI） | ✔ | REI で、近くに市場が無い時に「近くにこの品を扱う市場はありません…」が出ること（25-5 の分。board.radius を 16 にした） |
| 25-7 | `viewer_reload` | クライアント（REI） | ✔ | REI で、/aktradeex goods で品を外す・戻す、設定ファイルで品を足して /aktradeex reload すると、ページが消える・出ること（25-8 の分） |
| 30-1 | `viewer_new_goods` | クライアント（REI） | ✔ | REI で、新しい商品（エリトラ・トライデント・ダイヤのツルハシとチェストプレート）に「市場の相場」のページがあり、ページの品が耐久値の減っていない新品であること（アイコンの見た目は画像で見る） |
| 25-7 | `log_has_no_mod_errors` | クライアント（REI） | ✔ | REI を入れたクライアントのログに本 Mod のエラー・警告が出ないことのみ |
| 20-3 | `journeymap_waypoints` | クライアント（JourneyMap） | ✔ | 全部 |
| 20-5 | `journeymap_waypoints` | クライアント（JourneyMap） | ✔ | JourneyMap がある時、掲示板の市場の一覧の「この市場を登録」で今いる市場の金色の地点が JourneyMap に追加され（チャットには出ない）、もう一度押しても増えないこと（近くに市場が無い時は JourneyMap なしでだけ見た） |
| 20-3 | `log_has_no_mod_errors` | クライアント（JourneyMap） | ✔ | JourneyMap を入れたクライアントのログに本 Mod のエラー・警告が出ないことのみ |
| 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 | `building_looks` | クライアント（建物の画像） | ✔ | 14種類の建物を7種類の見た目で、完成の時と同じく構造物を置いて、正面の斜め上から撮った画像（98枚、look_<見た目>_<建物>.png）があり、どの建物にも看板があること（材料と形の見た目・扉や窓や屋根の向き・水の張り方は画像で見る。向きは1つ。鐘を鳴らせる・寝られる・村人が来るは見ていない） |
| 29-2 | `log_has_no_mod_errors` | クライアント（建物の画像） | ✔ | 建物の画像を撮ったクライアントのログに本 Mod のエラー・警告が出ないことのみ |

## 見て確かめる画像

見た目（文字の重なり・はみ出し・色）は人が判断します。画像は `Source/TradeExpansion/build/autotest/screenshots/` にあります（リポジトリには入れない）。

- 1-2 `startup_with_modmenu_yacl`：[1-2_title.png](../../Source/TradeExpansion/build/autotest/screenshots/1-2_title.png)
- 1-2 `startup_with_modmenu_yacl`：[1-2_mod_list.png](../../Source/TradeExpansion/build/autotest/screenshots/1-2_mod_list.png)
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
- 1-4 `creative_tab`：[1-4_creative_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/1-4_creative_tab.png)
- 1-5 `item_tooltips`：[1-5_copper_coin.png](../../Source/TradeExpansion/build/autotest/screenshots/1-5_copper_coin.png)
- 1-5 `item_tooltips`：[1-5_gold_coins.png](../../Source/TradeExpansion/build/autotest/screenshots/1-5_gold_coins.png)
- 1-5 `item_tooltips`：[1-5_wallet.png](../../Source/TradeExpansion/build/autotest/screenshots/1-5_wallet.png)
- 1-5 `item_tooltips`：[1-5_card.png](../../Source/TradeExpansion/build/autotest/screenshots/1-5_card.png)
- 21-2 `cargo_tooltip`：[21-2_cargo_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/21-2_cargo_tooltip.png)
- 21-8 `cargo_tooltip`：[21-8_cargo_expired.png](../../Source/TradeExpansion/build/autotest/screenshots/21-8_cargo_expired.png)
- 2-2 `placement_hint`：[2-2_hint_join.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_join.png)
- 2-2 `placement_hint`：[2-2_hint_refused.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_refused.png)
- 2-2 `placement_hint`：[2-2_hint_new.png](../../Source/TradeExpansion/build/autotest/screenshots/2-2_hint_new.png)
- 3-10 `buy_with_screen_buttons`：[3-10_bulk_64.png](../../Source/TradeExpansion/build/autotest/screenshots/3-10_bulk_64.png)
- 3-1 `price_arrows`：[3-1_price_up.png](../../Source/TradeExpansion/build/autotest/screenshots/3-1_price_up.png)
- 3-1 `price_arrows`：[3-1_price_down.png](../../Source/TradeExpansion/build/autotest/screenshots/3-1_price_down.png)
- 3-4 `balance_display`：[3-4_balance_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-4_balance_tooltip.png)
- 3-5 `balance_position_setting`：[3-5_below_sell_slot.png](../../Source/TradeExpansion/build/autotest/screenshots/3-5_below_sell_slot.png)
- 3-5 `balance_position_setting`：[3-5_top_right.png](../../Source/TradeExpansion/build/autotest/screenshots/3-5_top_right.png)
- 3-5 `balance_position_setting`：[3-5_top_right_long_name.png](../../Source/TradeExpansion/build/autotest/screenshots/3-5_top_right_long_name.png)
- 3-8・3-9 `row_states`：[3-7_shortage_orange.png](../../Source/TradeExpansion/build/autotest/screenshots/3-7_shortage_orange.png)
- 3-8・3-9 `row_states`：[3-7_shortage_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-7_shortage_tooltip.png)
- 3-8・3-9 `row_states`：[3-8_sold_out_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-8_sold_out_tooltip.png)
- 3-8・3-9 `row_states`：[3-8_not_buying_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/3-8_not_buying_tooltip.png)
- 3-8・3-9 `row_states`：[3-9_not_buying_sell_slot.png](../../Source/TradeExpansion/build/autotest/screenshots/3-9_not_buying_sell_slot.png)
- 3-2・3-3 `row_meter_and_graph`：[3-2_meters.png](../../Source/TradeExpansion/build/autotest/screenshots/3-2_meters.png)
- 3-2・3-3 `row_meter_and_graph`：[3-3_price_graph_line.png](../../Source/TradeExpansion/build/autotest/screenshots/3-3_price_graph_line.png)
- 23-2・24-2 `row_rival_and_trend_tooltips`：[23-2_rival_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/23-2_rival_tooltip.png)
- 23-2・24-2 `row_rival_and_trend_tooltips`：[24-2_trend_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/24-2_trend_tooltip.png)
- 3-11 `max_with_exact_money`：[3-11_max_exact_money.png](../../Source/TradeExpansion/build/autotest/screenshots/3-11_max_exact_money.png)
- 2-9・2-10 `max_and_change`：[2-9_max.png](../../Source/TradeExpansion/build/autotest/screenshots/2-9_max.png)
- 2-14 `shift_confirm_all`：[2-14_shift.png](../../Source/TradeExpansion/build/autotest/screenshots/2-14_shift.png)
- 5-7 `special_and_emerald_rows`：[5-7_special.png](../../Source/TradeExpansion/build/autotest/screenshots/5-7_special.png)
- 5-2 `special_and_emerald_rows`：[5-2_emerald_coin.png](../../Source/TradeExpansion/build/autotest/screenshots/5-2_emerald_coin.png)
- 14-2 `other_player_buys`：[14-2_other_player_bought.png](../../Source/TradeExpansion/build/autotest/screenshots/14-2_other_player_bought.png)
- 16-3 `regular_customer_prices`：[16-3_regular_customer.png](../../Source/TradeExpansion/build/autotest/screenshots/16-3_regular_customer.png)
- 23-10 `price_changed_message`：[23-10_price_changed.png](../../Source/TradeExpansion/build/autotest/screenshots/23-10_price_changed.png)
- 2-13 `sell_slot_selects_good`：[2-13_sell_slot.png](../../Source/TradeExpansion/build/autotest/screenshots/2-13_sell_slot.png)
- 2-19 `shift_click_sell_slot`：[2-19_shift_click_in.png](../../Source/TradeExpansion/build/autotest/screenshots/2-19_shift_click_in.png)
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
- 9-1b `board_deals`：[9-1b_board_deals.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1b_board_deals.png)
- 9-1b `board_deals`：[9-1b_board_cheap.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1b_board_cheap.png)
- 9-1b `board_deals`：[9-1b_board_dear.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1b_board_dear.png)
- 20-5 `map_coordinates_to_chat`：[20-5_here_coords.png](../../Source/TradeExpansion/build/autotest/screenshots/20-5_here_coords.png)
- 20-5 `map_coordinates_to_chat`：[20-1_board_coords.png](../../Source/TradeExpansion/build/autotest/screenshots/20-1_board_coords.png)
- 20-5 `map_coordinates_to_chat`：[20-1_chat.png](../../Source/TradeExpansion/build/autotest/screenshots/20-1_chat.png)
- 19-4 `list_scrollbars`：[19-4_buy_list.png](../../Source/TradeExpansion/build/autotest/screenshots/19-4_buy_list.png)
- 19-4 `list_scrollbars`：[19-4_board_market.png](../../Source/TradeExpansion/build/autotest/screenshots/19-4_board_market.png)
- 19-4 `list_scrollbars`：[19-4_sell_all.png](../../Source/TradeExpansion/build/autotest/screenshots/19-4_sell_all.png)
- 9-1 `board_market_list`：[9-1_board_nearest_first.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1_board_nearest_first.png)
- 9-1 `board_market_list`：[9-1_board_selected_scrolled.png](../../Source/TradeExpansion/build/autotest/screenshots/9-1_board_selected_scrolled.png)
- 10-1 `board_market_list`：[10-1_board_renamed.png](../../Source/TradeExpansion/build/autotest/screenshots/10-1_board_renamed.png)
- 29-11 `board_theater_trends`：[29-11_board_theater_trends.png](../../Source/TradeExpansion/build/autotest/screenshots/29-11_board_theater_trends.png)
- 29-11 `board_theater_trends`：[29-11_screen_theater_trends.png](../../Source/TradeExpansion/build/autotest/screenshots/29-11_screen_theater_trends.png)
- 20-5 `board_no_markets`：[35-2_board_no_markets.png](../../Source/TradeExpansion/build/autotest/screenshots/35-2_board_no_markets.png)
- 20-5 `board_no_markets`：[20-5_no_markets_coords.png](../../Source/TradeExpansion/build/autotest/screenshots/20-5_no_markets_coords.png)
- 35-2 `board_no_markets`：[35-2_board_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/35-2_board_tavern.png)
- 30-1 `goods_names`：[30-1_goods.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_goods.png)
- 26-2 `staff_needs_reputation`：[26-2_staff_needs_reputation.png](../../Source/TradeExpansion/build/autotest/screenshots/26-2_staff_needs_reputation.png)
- 18-1・31-2 `sell_all_box`：[18-1_sell_all.png](../../Source/TradeExpansion/build/autotest/screenshots/18-1_sell_all.png)
- 18-1・31-2 `sell_all_box`：[18-2_excluded.png](../../Source/TradeExpansion/build/autotest/screenshots/18-2_excluded.png)
- 18-2・18-3 `sell_all_tools_and_sale`：[18-2_tool_excluded.png](../../Source/TradeExpansion/build/autotest/screenshots/18-2_tool_excluded.png)
- 18-2・18-3 `sell_all_tools_and_sale`：[18-3_sold_all.png](../../Source/TradeExpansion/build/autotest/screenshots/18-3_sold_all.png)
- 18-4・18-5 `sell_all_limits_and_other_player`：[18-4_partial_and_not_buying.png](../../Source/TradeExpansion/build/autotest/screenshots/18-4_partial_and_not_buying.png)
- 18-4・18-5 `sell_all_limits_and_other_player`：[18-5_box_updated.png](../../Source/TradeExpansion/build/autotest/screenshots/18-5_box_updated.png)
- 18-4・18-5 `sell_all_limits_and_other_player`：[18-5_price_changed.png](../../Source/TradeExpansion/build/autotest/screenshots/18-5_price_changed.png)
- 31-1 `goods_order`：[31-1_sell_order.png](../../Source/TradeExpansion/build/autotest/screenshots/31-1_sell_order.png)
- 22-20・22-21・31-3 `town_tab`：[22-20_town_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/22-20_town_tab.png)
- 22-20・22-21・31-3 `town_tab`：[29-1_town_scrolled.png](../../Source/TradeExpansion/build/autotest/screenshots/29-1_town_scrolled.png)
- 22-20・22-21・31-3 `town_tab`：[22-21_warehouse_selected.png](../../Source/TradeExpansion/build/autotest/screenshots/22-21_warehouse_selected.png)
- 8-6 `exchange_tab`：[8-6_exchange_down.png](../../Source/TradeExpansion/build/autotest/screenshots/8-6_exchange_down.png)
- 7-4 `wallet_screen`：[7-4_wallet_screen.png](../../Source/TradeExpansion/build/autotest/screenshots/7-4_wallet_screen.png)
- 7-12 `wallet_offhand_and_limits`：[7-12_wallet_offhand.png](../../Source/TradeExpansion/build/autotest/screenshots/7-12_wallet_offhand.png)
- 6-3 `trader_opens_market`：[6-3_trader_opens.png](../../Source/TradeExpansion/build/autotest/screenshots/6-3_trader_opens.png)
- 27-1 `advancements_screen`：[27-1_advancements_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/27-1_advancements_tab.png)
- 27-11 `advancements_screen`：[27-11_advancements_en.png](../../Source/TradeExpansion/build/autotest/screenshots/27-11_advancements_en.png)
- 13-10 `clock_advance_updates_open_screen`：[13-10_quest_after_clock_advance.png](../../Source/TradeExpansion/build/autotest/screenshots/13-10_quest_after_clock_advance.png)
- 12-1 `villager_coin_prices`：[12-1_villager_coin_prices.png](../../Source/TradeExpansion/build/autotest/screenshots/12-1_villager_coin_prices.png)
- 12-4 `villager_coin_prices`：[12-4_villager_price_short.png](../../Source/TradeExpansion/build/autotest/screenshots/12-4_villager_price_short.png)
- 7-8・7-9 `bank_card_buttons`：[7-8_card_issued.png](../../Source/TradeExpansion/build/autotest/screenshots/7-8_card_issued.png)
- 7-8・7-9 `bank_card_buttons`：[7-9_pay_by_card.png](../../Source/TradeExpansion/build/autotest/screenshots/7-9_pay_by_card.png)
- 7-8・7-9 `bank_card_buttons`：[7-9_pay_by_cash.png](../../Source/TradeExpansion/build/autotest/screenshots/7-9_pay_by_cash.png)
- 19-1・19-2 `screen_size_by_gui_scale`：[19-1_gui_1920x1080_scale_1.png](../../Source/TradeExpansion/build/autotest/screenshots/19-1_gui_1920x1080_scale_1.png)
- 19-1・19-2 `screen_size_by_gui_scale`：[19-1_gui_1920x1080_scale_2.png](../../Source/TradeExpansion/build/autotest/screenshots/19-1_gui_1920x1080_scale_2.png)
- 19-1・19-2 `screen_size_by_gui_scale`：[19-1_gui_1920x1080_scale_3.png](../../Source/TradeExpansion/build/autotest/screenshots/19-1_gui_1920x1080_scale_3.png)
- 19-1・19-2 `screen_size_by_gui_scale`：[19-1_gui_1920x1080_scale_4.png](../../Source/TradeExpansion/build/autotest/screenshots/19-1_gui_1920x1080_scale_4.png)
- 19-1・19-2 `screen_size_by_gui_scale`：[19-1_gui_854x480_scale_2.png](../../Source/TradeExpansion/build/autotest/screenshots/19-1_gui_854x480_scale_2.png)
- 19-5 `resize_open_screen`：[19-5_back_854x480.png](../../Source/TradeExpansion/build/autotest/screenshots/19-5_back_854x480.png)
- 19-5 `resize_open_screen`：[19-5_back_1920x1080.png](../../Source/TradeExpansion/build/autotest/screenshots/19-5_back_1920x1080.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_buy.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_buy.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_sell.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_sell.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_bank.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_board.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_board.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_board_market_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_board_market_.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_quest.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_quest.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_exchange.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_exchange.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_town.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_town.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_town_building_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_town_building_.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_staff.png)
- 19-6 `long_names_big_numbers`：[layout_long_ja_jp_854x480_GUI_auto_wallet.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_ja_jp_854x480_GUI_auto_wallet.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_buy.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_buy.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_sell.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_sell.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_bank.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_board.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_board.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_board_market_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_board_market_.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_quest.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_quest.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_exchange.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_exchange.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_town.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_town.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_town_building_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_town_building_.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_staff.png)
- 19-6 `long_names_big_numbers`：[layout_long_en_us_854x480_GUI_auto_wallet.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_long_en_us_854x480_GUI_auto_wallet.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_buy.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_buy.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_sell.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_sell.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_sell_sell_all_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_sell_sell_all_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_bank.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_board.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_board.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_board_market_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_board_market_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest_request_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest_request_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_quest_transport_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_quest_transport_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_exchange.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_exchange.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_town.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_town.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_town_building_.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_town_building_.png)
- 3-6・19-1・19-2・19-3・22-1・26-1 `layout_market_screen_ja`：[layout_ja_jp_854x480_GUI_auto_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/layout_ja_jp_854x480_GUI_auto_staff.png)
- 13-1 `config_save_applies`：[13-1_max_per_transaction_48.png](../../Source/TradeExpansion/build/autotest/screenshots/13-1_max_per_transaction_48.png)
- 33-3 `special_settings`：[33-3_market_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/33-3_market_tab.png)
- 13-6・13-8・13-9 `goods_manager`：[13-6_goods_added.png](../../Source/TradeExpansion/build/autotest/screenshots/13-6_goods_added.png)
- 13-6・13-8・13-9 `goods_manager`：[13-9_already_goods.png](../../Source/TradeExpansion/build/autotest/screenshots/13-9_already_goods.png)
- 13-6・13-8・13-9 `goods_manager`：[13-8_consumable_remove.png](../../Source/TradeExpansion/build/autotest/screenshots/13-8_consumable_remove.png)
- 5-4 `bank_tab_by_level`：[5-4_level2_no_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/5-4_level2_no_bank.png)
- 5-4 `bank_tab_by_level`：[5-4_level3_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/5-4_level3_bank.png)
- 22-30 `bank_tab_by_level`：[22-30_bank_building.png](../../Source/TradeExpansion/build/autotest/screenshots/22-30_bank_building.png)
- 7-7 `bank_tab`：[7-7_bank_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/7-7_bank_tab.png)
- 7-7 `bank_tab`：[7-7_bank_after.png](../../Source/TradeExpansion/build/autotest/screenshots/7-7_bank_after.png)
- 2-18 `trade_refused_messages`：[2-18_insufficient_funds.png](../../Source/TradeExpansion/build/autotest/screenshots/2-18_insufficient_funds.png)
- 2-18 `trade_refused_messages`：[2-18_stock_full.png](../../Source/TradeExpansion/build/autotest/screenshots/2-18_stock_full.png)
- 7-11 `bank_refused_messages`：[7-11_withdraw_too_much.png](../../Source/TradeExpansion/build/autotest/screenshots/7-11_withdraw_too_much.png)
- 7-11 `bank_refused_messages`：[7-11_deposit_too_much.png](../../Source/TradeExpansion/build/autotest/screenshots/7-11_deposit_too_much.png)
- 8-7 `exchange_refused_and_fee`：[8-7_exchange_refused.png](../../Source/TradeExpansion/build/autotest/screenshots/8-7_exchange_refused.png)
- 8-7 `exchange_refused_and_fee`：[8-7_exchange_fee.png](../../Source/TradeExpansion/build/autotest/screenshots/8-7_exchange_fee.png)
- 9-2 `quest_tab`：[9-2_request_selected.png](../../Source/TradeExpansion/build/autotest/screenshots/9-2_request_selected.png)
- 9-2 `quest_tab`：[9-2_delivered.png](../../Source/TradeExpansion/build/autotest/screenshots/9-2_delivered.png)
- 16-5 `quest_tab`：[16-5_accepted.png](../../Source/TradeExpansion/build/autotest/screenshots/16-5_accepted.png)
- 16-5 `quest_tab`：[16-5_reputation_after_delivery.png](../../Source/TradeExpansion/build/autotest/screenshots/16-5_reputation_after_delivery.png)
- 16-4 `quest_locked`：[16-4_locked.png](../../Source/TradeExpansion/build/autotest/screenshots/16-4_locked.png)
- 16-7 `quest_locked`：[16-7_reward_level3.png](../../Source/TradeExpansion/build/autotest/screenshots/16-7_reward_level3.png)
- 21-1・21-5・21-7 `transport_request`：[21-1_transport_selected.png](../../Source/TradeExpansion/build/autotest/screenshots/21-1_transport_selected.png)
- 21-1・21-5・21-7 `transport_request`：[21-5_deliver_button.png](../../Source/TradeExpansion/build/autotest/screenshots/21-5_deliver_button.png)
- 21-2 `transport_request`：[21-2_crate_taken.png](../../Source/TradeExpansion/build/autotest/screenshots/21-2_crate_taken.png)
- 21-4 `transport_taken_by_other`：[21-4_taken_by_other.png](../../Source/TradeExpansion/build/autotest/screenshots/21-4_taken_by_other.png)
- 22-2・22-3・22-4 `town_donations`：[22-2_town_donations.png](../../Source/TradeExpansion/build/autotest/screenshots/22-2_town_donations.png)
- 22-2・22-3・22-4 `town_donations`：[22-3_donated.png](../../Source/TradeExpansion/build/autotest/screenshots/22-3_donated.png)
- 22-2・22-3・22-4 `town_donations`：[22-4_not_enough.png](../../Source/TradeExpansion/build/autotest/screenshots/22-4_not_enough.png)
- 28-8 `town_donations`：[28-8_park_donation.png](../../Source/TradeExpansion/build/autotest/screenshots/28-8_park_donation.png)
- 22-10 `town_other_player_donates`：[22-10_other_player_donated.png](../../Source/TradeExpansion/build/autotest/screenshots/22-10_other_player_donated.png)
- 22-22・22-25 `town_build_reasons`：[22-22_reputation.png](../../Source/TradeExpansion/build/autotest/screenshots/22-22_reputation.png)
- 22-22・22-25 `town_build_reasons`：[22-23b_materials.png](../../Source/TradeExpansion/build/autotest/screenshots/22-23b_materials.png)
- 22-22・22-25 `town_build_reasons`：[22-25_limit.png](../../Source/TradeExpansion/build/autotest/screenshots/22-25_limit.png)
- 22-23 `town_materials_and_stake`：[22-23_materials_donated.png](../../Source/TradeExpansion/build/autotest/screenshots/22-23_materials_donated.png)
- 22-35 `town_materials_and_stake`：[22-35_stake.png](../../Source/TradeExpansion/build/autotest/screenshots/22-35_stake.png)
- 22-23 `town_material_cap`：[22-23_material_cap.png](../../Source/TradeExpansion/build/autotest/screenshots/22-23_material_cap.png)
- 22-23b `town_material_cap`：[22-23b_multiplier_2.png](../../Source/TradeExpansion/build/autotest/screenshots/22-23b_multiplier_2.png)
- 22-23b `town_material_cap`：[22-23b_all_donated.png](../../Source/TradeExpansion/build/autotest/screenshots/22-23b_all_donated.png)
- 22-24・22-26・22-33・29-13 `town_row_states`：[22-24_rows.png](../../Source/TradeExpansion/build/autotest/screenshots/22-24_rows.png)
- 34-2 `town_size_by_style`：[34-2_desert_sizes.png](../../Source/TradeExpansion/build/autotest/screenshots/34-2_desert_sizes.png)
- 22-32 `facility_sign_opens_tab`：[22-32_sign_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/22-32_sign_quest_hall.png)
- 22-32 `facility_sign_opens_tab`：[22-32_sign_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/22-32_sign_bank.png)
- 22-32 `facility_sign_opens_tab`：[22-32_sign_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/22-32_sign_warehouse.png)
- 22-32 `facility_sign_opens_tab`：[22-32_sign_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/22-32_sign_inn.png)
- 22-9・26-20・24-7 `tabs_turned_off`：[22-9_no_town_tab.png](../../Source/TradeExpansion/build/autotest/screenshots/22-9_no_town_tab.png)
- 22-9・26-20・24-7 `tabs_turned_off`：[26-20_staff_tab_kept.png](../../Source/TradeExpansion/build/autotest/screenshots/26-20_staff_tab_kept.png)
- 22-9・26-20・24-7 `tabs_turned_off`：[24-7_no_trends.png](../../Source/TradeExpansion/build/autotest/screenshots/24-7_no_trends.png)
- 26-4 `staff_hire_carrier`：[26-3_carrier_goods.png](../../Source/TradeExpansion/build/autotest/screenshots/26-3_carrier_goods.png)
- 26-4 `staff_hire_carrier`：[26-4_destinations.png](../../Source/TradeExpansion/build/autotest/screenshots/26-4_destinations.png)
- 26-4 `staff_hire_carrier`：[26-4_setup.png](../../Source/TradeExpansion/build/autotest/screenshots/26-4_setup.png)
- 26-4 `staff_hire_carrier`：[26-4_hired.png](../../Source/TradeExpansion/build/autotest/screenshots/26-4_hired.png)
- 26-6・26-7・26-9・26-14 `staff_carrier_trip`：[26-6_wait_funds.png](../../Source/TradeExpansion/build/autotest/screenshots/26-6_wait_funds.png)
- 26-6・26-7・26-9・26-14 `staff_carrier_trip`：[26-6_outbound.png](../../Source/TradeExpansion/build/autotest/screenshots/26-6_outbound.png)
- 26-6・26-7・26-9・26-14 `staff_carrier_trip`：[26-7_returning.png](../../Source/TradeExpansion/build/autotest/screenshots/26-7_returning.png)
- 26-6・26-7・26-9・26-14 `staff_carrier_trip`：[26-7_back_paused.png](../../Source/TradeExpansion/build/autotest/screenshots/26-7_back_paused.png)
- 26-6・26-7・26-9・26-14 `staff_carrier_trip`：[26-9_wait_price.png](../../Source/TradeExpansion/build/autotest/screenshots/26-9_wait_price.png)
- 26-13 `staff_clerk`：[26-11_clerk_goods.png](../../Source/TradeExpansion/build/autotest/screenshots/26-11_clerk_goods.png)
- 26-13 `staff_clerk`：[26-13_unpaid.png](../../Source/TradeExpansion/build/autotest/screenshots/26-13_unpaid.png)
- 26-15・26-17 `staff_limit_and_other_market`：[26-5_auto_setup.png](../../Source/TradeExpansion/build/autotest/screenshots/26-5_auto_setup.png)
- 26-15・26-17 `staff_limit_and_other_market`：[26-17_limit.png](../../Source/TradeExpansion/build/autotest/screenshots/26-17_limit.png)
- 26-15・26-17 `staff_limit_and_other_market`：[26-15_other_market.png](../../Source/TradeExpansion/build/autotest/screenshots/26-15_other_market.png)
- 26-25 `staff_layout`：[26-25_ja_jp_854x480_GUI_auto_staff__-1.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_ja_jp_854x480_GUI_auto_staff__-1.png)
- 26-25 `staff_layout`：[26-25_ja_jp_854x480_GUI_auto_staff__2.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_ja_jp_854x480_GUI_auto_staff__2.png)
- 26-25 `staff_layout`：[26-25_ja_jp_854x480_GUI_auto_staff__3.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_ja_jp_854x480_GUI_auto_staff__3.png)
- 26-25 `staff_layout`：[26-25_ja_jp_854x480_GUI_auto_staff__4.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_ja_jp_854x480_GUI_auto_staff__4.png)
- 26-25 `staff_layout`：[26-25_en_us_854x480_GUI_auto_staff__-1.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_en_us_854x480_GUI_auto_staff__-1.png)
- 26-25 `staff_layout`：[26-25_en_us_854x480_GUI_auto_staff__2.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_en_us_854x480_GUI_auto_staff__2.png)
- 26-25 `staff_layout`：[26-25_en_us_854x480_GUI_auto_staff__3.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_en_us_854x480_GUI_auto_staff__3.png)
- 26-25 `staff_layout`：[26-25_en_us_854x480_GUI_auto_staff__4.png](../../Source/TradeExpansion/build/autotest/screenshots/26-25_en_us_854x480_GUI_auto_staff__4.png)
- 26-23 `staff_carrier_change`：[26-27_return_cargo.png](../../Source/TradeExpansion/build/autotest/screenshots/26-27_return_cargo.png)
- 26-23 `staff_carrier_change`：[26-23_change_setup.png](../../Source/TradeExpansion/build/autotest/screenshots/26-23_change_setup.png)
- 26-23 `staff_carrier_change`：[26-23_changed.png](../../Source/TradeExpansion/build/autotest/screenshots/26-23_changed.png)
- 26-24・26-12 `staff_clerk_change`：[26-24_change_setup.png](../../Source/TradeExpansion/build/autotest/screenshots/26-24_change_setup.png)
- 26-24・26-12 `staff_clerk_change`：[26-12_sold_out.png](../../Source/TradeExpansion/build/autotest/screenshots/26-12_sold_out.png)
- 26-8・26-27 `staff_auto_carrier`：[26-8_auto_outbound.png](../../Source/TradeExpansion/build/autotest/screenshots/26-8_auto_outbound.png)
- 26-8・26-27 `staff_auto_carrier`：[26-27_auto_back.png](../../Source/TradeExpansion/build/autotest/screenshots/26-27_auto_back.png)
- 26-8・26-27 `staff_auto_carrier`：[26-28_before_withdraw.png](../../Source/TradeExpansion/build/autotest/screenshots/26-28_before_withdraw.png)
- 26-8・26-27 `staff_auto_carrier`：[26-12_withdrawn.png](../../Source/TradeExpansion/build/autotest/screenshots/26-12_withdrawn.png)
- 35-3 `staff_harbor_watchtower`：[35-3_harbor_destination.png](../../Source/TradeExpansion/build/autotest/screenshots/35-3_harbor_destination.png)
- 35-3 `staff_harbor_watchtower`：[35-3_watchtower_destination.png](../../Source/TradeExpansion/build/autotest/screenshots/35-3_watchtower_destination.png)
- 13-10 `clock_advance_staff_and_town`：[13-10_staff_after_clock_advance.png](../../Source/TradeExpansion/build/autotest/screenshots/13-10_staff_after_clock_advance.png)
- 13-10 `clock_advance_staff_and_town`：[13-10_town_after_clock_advance.png](../../Source/TradeExpansion/build/autotest/screenshots/13-10_town_after_clock_advance.png)
- 26-26 `staff_backhaul_hint`：[26-26_return_cargo_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/26-26_return_cargo_tooltip.png)
- 26-26 `staff_backhaul_hint`：[26-26_return_cargo_setup.png](../../Source/TradeExpansion/build/autotest/screenshots/26-26_return_cargo_setup.png)
- 26-29 `staff_empty_trip`：[26-29_empty_trip_return_cargo.png](../../Source/TradeExpansion/build/autotest/screenshots/26-29_empty_trip_return_cargo.png)
- 26-22 `staff_carrier_moves_price`：[26-22_carrier_moved_price.png](../../Source/TradeExpansion/build/autotest/screenshots/26-22_carrier_moved_price.png)
- 26-10 `staff_accident`：[26-6_no_margin.png](../../Source/TradeExpansion/build/autotest/screenshots/26-6_no_margin.png)
- 26-10 `staff_accident`：[26-10_resting.png](../../Source/TradeExpansion/build/autotest/screenshots/26-10_resting.png)
- 26-10 `staff_accident`：[26-10_quit.png](../../Source/TradeExpansion/build/autotest/screenshots/26-10_quit.png)
- 2-20 `market_gone_while_open`：[2-20_good_left.png](../../Source/TradeExpansion/build/autotest/screenshots/2-20_good_left.png)
- 2-20 `market_gone_while_open`：[2-20_market_deleted.png](../../Source/TradeExpansion/build/autotest/screenshots/2-20_market_deleted.png)
- 4-3 `reopen_world_keeps_market`：[4-3_reopened.png](../../Source/TradeExpansion/build/autotest/screenshots/4-3_reopened.png)
- 22-11・22-40・23-8・24-8 `reopen_world_keeps_town_and_staff`：[22-40_reopened_town.png](../../Source/TradeExpansion/build/autotest/screenshots/22-40_reopened_town.png)
- 22-11・22-40・23-8・24-8 `reopen_world_keeps_town_and_staff`：[26-18_reopened_staff.png](../../Source/TradeExpansion/build/autotest/screenshots/26-18_reopened_staff.png)
- 22-11・22-40・23-8・24-8 `reopen_world_keeps_town_and_staff`：[22-40_completed_after_reopen.png](../../Source/TradeExpansion/build/autotest/screenshots/22-40_completed_after_reopen.png)
- 11-2 `where_show_lines`：[11-2_where_show_at_stall.png](../../Source/TradeExpansion/build/autotest/screenshots/11-2_where_show_at_stall.png)
- 11-2 `where_show_lines`：[11-2_where_show_red.png](../../Source/TradeExpansion/build/autotest/screenshots/11-2_where_show_red.png)
- 22-24b `construction_effects`：[22-24b_site_under_construction.png](../../Source/TradeExpansion/build/autotest/screenshots/22-24b_site_under_construction.png)
- 2-6 `stall_facing_and_shape`：[2-6_stall_facing_north.png](../../Source/TradeExpansion/build/autotest/screenshots/2-6_stall_facing_north.png)
- 2-6 `stall_facing_and_shape`：[2-6_stall_facing_east.png](../../Source/TradeExpansion/build/autotest/screenshots/2-6_stall_facing_east.png)
- 2-6 `stall_facing_and_shape`：[2-6_stall_facing_south.png](../../Source/TradeExpansion/build/autotest/screenshots/2-6_stall_facing_south.png)
- 2-6 `stall_facing_and_shape`：[2-6_stall_facing_west.png](../../Source/TradeExpansion/build/autotest/screenshots/2-6_stall_facing_west.png)
- 6-10b `trader_robe_colours`：[6-10b_robe_colours.png](../../Source/TradeExpansion/build/autotest/screenshots/6-10b_robe_colours.png)
- 6-10b `trader_robe_colours`：[6-10b_robe_colours_after_reload.png](../../Source/TradeExpansion/build/autotest/screenshots/6-10b_robe_colours_after_reload.png)
- 6-10 `trader_name_tag_and_eggs`：[6-10_spawn_eggs_and_name.png](../../Source/TradeExpansion/build/autotest/screenshots/6-10_spawn_eggs_and_name.png)
- 22-36b `stake_outlines`：[22-36b_stake_outlines.png](../../Source/TradeExpansion/build/autotest/screenshots/22-36b_stake_outlines.png)
- 22-36b `stake_placed`：[22-36b_stake_placed.png](../../Source/TradeExpansion/build/autotest/screenshots/22-36b_stake_placed.png)
- 2-11 `sold_out_and_not_buying`：[2-11_price_rose.png](../../Source/TradeExpansion/build/autotest/screenshots/2-11_price_rose.png)
- 2-11 `sold_out_and_not_buying`：[2-11_sold_out.png](../../Source/TradeExpansion/build/autotest/screenshots/2-11_sold_out.png)
- 2-12 `sold_out_and_not_buying`：[2-12_price_fell.png](../../Source/TradeExpansion/build/autotest/screenshots/2-12_price_fell.png)
- 2-12 `sold_out_and_not_buying`：[2-12_not_buying.png](../../Source/TradeExpansion/build/autotest/screenshots/2-12_not_buying.png)
- 22-27 `debug_tooltip_warehouse_and_consumables`：[22-27_warehouse_stock_limit.png](../../Source/TradeExpansion/build/autotest/screenshots/22-27_warehouse_stock_limit.png)
- 30-7・32-2 `debug_tooltip_warehouse_and_consumables`：[30-7_consumable_debug_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/30-7_consumable_debug_tooltip.png)
- 7-6 `died_with_wallet`：[7-6_cash_before_dying.png](../../Source/TradeExpansion/build/autotest/screenshots/7-6_cash_before_dying.png)
- 7-6 `died_with_wallet`：[7-6_cash_after_dying.png](../../Source/TradeExpansion/build/autotest/screenshots/7-6_cash_after_dying.png)
- 7-7 `died_with_wallet`：[7-7_bank_after_dying.png](../../Source/TradeExpansion/build/autotest/screenshots/7-7_bank_after_dying.png)
- 14-1・14-5 `dedicated_server_one_player`：[14-1_dedicated_trade_screen.png](../../Source/TradeExpansion/build/autotest/screenshots/14-1_dedicated_trade_screen.png)
- 13-2 `dedicated_server_one_player`：[13-2_settings_on_server.png](../../Source/TradeExpansion/build/autotest/screenshots/13-2_settings_on_server.png)
- 1-1 `startup_without_modmenu_yacl`：[1-1_title_without_modmenu_yacl.png](../../Source/TradeExpansion/build/autotest/screenshots/1-1_title_without_modmenu_yacl.png)
- 15-3 `trade_without_modmenu_yacl`：[15-3_bought_without_modmenu_yacl.png](../../Source/TradeExpansion/build/autotest/screenshots/15-3_bought_without_modmenu_yacl.png)
- 1-6 `startup_modmenu_without_yacl`：[1-2_mod_list.png](../../Source/TradeExpansion/build/autotest/screenshots/1-2_mod_list.png)
- 1-6 `startup_modmenu_without_yacl`：[1-6_mod_list_without_yacl.png](../../Source/TradeExpansion/build/autotest/screenshots/1-6_mod_list_without_yacl.png)
- 1-7 `startup_yacl_without_modmenu`：[1-7_title_without_modmenu.png](../../Source/TradeExpansion/build/autotest/screenshots/1-7_title_without_modmenu.png)
- 1-7 `startup_yacl_without_modmenu`：[1-7_settings_without_modmenu.png](../../Source/TradeExpansion/build/autotest/screenshots/1-7_settings_without_modmenu.png)
- 25-2 `viewer_keys`：[25-2_JEI_R.png](../../Source/TradeExpansion/build/autotest/screenshots/25-2_JEI_R.png)
- 25-2 `viewer_keys`：[25-2_JEI_U.png](../../Source/TradeExpansion/build/autotest/screenshots/25-2_JEI_U.png)
- 25-6 `viewer_keys`：[25-6_JEI_stall_uses.png](../../Source/TradeExpansion/build/autotest/screenshots/25-6_JEI_stall_uses.png)
- 25-3 `viewer_page`：[25-3_JEI_page.png](../../Source/TradeExpansion/build/autotest/screenshots/25-3_JEI_page.png)
- 25-3 `viewer_page`：[25-3_JEI_shortage_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/25-3_JEI_shortage_tooltip.png)
- 25-4 `viewer_page`：[25-4_JEI_new_price.png](../../Source/TradeExpansion/build/autotest/screenshots/25-4_JEI_new_price.png)
- 25-5 `viewer_page_none`：[25-5_JEI_none.png](../../Source/TradeExpansion/build/autotest/screenshots/25-5_JEI_none.png)
- 25-8 `viewer_reload`：[25-8_JEI_added_good.png](../../Source/TradeExpansion/build/autotest/screenshots/25-8_JEI_added_good.png)
- 30-1 `viewer_new_goods`：[30-1_JEI_elytra.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_JEI_elytra.png)
- 30-1 `viewer_new_goods`：[30-1_JEI_trident.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_JEI_trident.png)
- 30-1 `viewer_new_goods`：[30-1_JEI_diamond_pickaxe.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_JEI_diamond_pickaxe.png)
- 30-1 `viewer_new_goods`：[30-1_JEI_diamond_chestplate.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_JEI_diamond_chestplate.png)
- 25-7 `viewer_keys`：[25-2_REI_R.png](../../Source/TradeExpansion/build/autotest/screenshots/25-2_REI_R.png)
- 25-7 `viewer_keys`：[25-2_REI_U.png](../../Source/TradeExpansion/build/autotest/screenshots/25-2_REI_U.png)
- 25-7 `viewer_page`：[25-3_REI_page.png](../../Source/TradeExpansion/build/autotest/screenshots/25-3_REI_page.png)
- 25-7 `viewer_page`：[25-3_REI_shortage_tooltip.png](../../Source/TradeExpansion/build/autotest/screenshots/25-3_REI_shortage_tooltip.png)
- 25-7 `viewer_page`：[25-4_REI_new_price.png](../../Source/TradeExpansion/build/autotest/screenshots/25-4_REI_new_price.png)
- 25-7 `viewer_page_none`：[25-5_REI_none.png](../../Source/TradeExpansion/build/autotest/screenshots/25-5_REI_none.png)
- 25-7 `viewer_reload`：[25-8_REI_added_good.png](../../Source/TradeExpansion/build/autotest/screenshots/25-8_REI_added_good.png)
- 30-1 `viewer_new_goods`：[30-1_REI_elytra.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_REI_elytra.png)
- 30-1 `viewer_new_goods`：[30-1_REI_trident.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_REI_trident.png)
- 30-1 `viewer_new_goods`：[30-1_REI_diamond_pickaxe.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_REI_diamond_pickaxe.png)
- 30-1 `viewer_new_goods`：[30-1_REI_diamond_chestplate.png](../../Source/TradeExpansion/build/autotest/screenshots/30-1_REI_diamond_chestplate.png)
- 20-3 `journeymap_waypoints`：[20-3_journeymap_board.png](../../Source/TradeExpansion/build/autotest/screenshots/20-3_journeymap_board.png)
- 20-5 `journeymap_waypoints`：[20-5_journeymap_here.png](../../Source/TradeExpansion/build/autotest/screenshots/20-5_journeymap_here.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_plains_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_plains_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_desert_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_desert_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_savanna_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_savanna_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_snowy_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_snowy_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_taiga_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_taiga_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_nether_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_nether_festival.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_quest_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_quest_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_warehouse.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_warehouse.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_inn.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_inn.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_bank.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_bank.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_fountain.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_fountain.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_park.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_park.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_clock_tower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_clock_tower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_watchtower.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_watchtower.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_tavern.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_tavern.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_market_hall.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_market_hall.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_harbor.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_harbor.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_theater.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_theater.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_hot_spring.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_hot_spring.png)
- 22-50・22-52・22-53・22-54・22-55・22-56・28-2・28-3・28-4・28-5・28-6・29-2・29-14 `building_looks`：[look_end_festival.png](../../Source/TradeExpansion/build/autotest/screenshots/look_end_festival.png)
