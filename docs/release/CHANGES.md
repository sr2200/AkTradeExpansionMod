# 次回リリースまでの変更点（リリースノートの下書き）

> 使い方：0.1.0（2026-11-07 公開）より後の変更を、遊ぶ人に見える形でここに書きためます。
> 次に `release.bat` で配布する時、下の「未リリース」をそのままリリースノート（投稿ページの更新内容）に使い、
> 見出しをそのバージョン番号と日付に書き換えてから、新しい「未リリース」の欄を上に作ります。
> 書き方：遊ぶ人向けの言葉で書く（クラス名や内部の話は書かない）。設定を足した時は設定名も書く。

## 未リリース

### 追加
- **輸送依頼**：依頼タブに「○○村へ運ぶ」依頼が出るようになりました。保証金（品物の値段）を払って「荷箱」を受け取り、届け先の市場の依頼タブで「荷箱を届ける」を押すと、報酬と保証金が戻ります。遠い村ほど報酬が増え、依頼を出した村と届け先の村の両方で評判が上がります。期限を過ぎると届けられず、保証金は戻りません（受けた村の評判も下がります）
  - 設定（`quest`）：`transportPerMarket`（市場ごとの輸送依頼の数）、`transportMinDistance`・`transportMaxDistance`（届け先までの距離）、`transportBaseMultiplier`・`transportPerThousandBlocks`・`transportMaxMultiplier`（報酬の倍率）

- **村の発展（その1）**：交易画面に「発展」タブを追加しました。村（市場）ごとに、みんなで貯める「発展度」があります。依頼を納める（報酬の半分。輸送依頼は両方の村）、その市場で売り買いする、「発展」タブでコインを寄付すると貯まります。寄付すると、その村での評判も上がります。タブには発展度を多く入れた人（上位5人）と自分の値が出ます。
  - 設定（`development`、設定画面の「村の発展」タブ）：`enabled`（使うか）、`dimensions`（使える次元。既定はオーバーワールド）、`pointsPerCoin`・`questRewardRatio`・`tradePointsPerCoin`（貯まり方）、`halfLifeDays`（減り方。既定は減らない）、`reputationPerPoint`（寄付で上がる評判）など
  - 管理コマンド：`/aktradeex development`（最寄りの市場の発展度）、`/aktradeex development add <発展度>`（テスト用）
- **村の発展（その2）：村の建物**：発展度を使って、村に「依頼所」「倉庫」「宿屋」「銀行」を建てられるようになりました。「発展」タブで建物を選んで「建てる」を押すと、屋台の周りの空き地に足場が立ち、ゲーム内2日で完成します。「杭をもらう」で受け取る「建設予定地の杭」を置くと、そこに建ちます。建物ごとに効果があり（依頼が増える・在庫が増える・村人が来る・銀行が使えて手数料なし）、2つ完成すると市場の規模が1つ、4つで2つ上がります。建物の資材（木材・丸石など）を寄付しても発展度が貯まります。建物の中の看板を壊すと効果がなくなり、建て直せます。建物の見た目は村の種類（平原・砂漠・サバンナ・雪原・タイガ）ごとに変わります
  - 設定（`development`）：`levelBonusThresholds`（規模が上がる建物の数）、`buildReputationLevel`（建物を選べる評判）、`maxConcurrentBuilds`（同時に建設できる数）、`buildDays`（建設の日数）、`siteRadius`・`siteSearchLimit`（場所探し）、`rebuildCostRatio`（建て直し）、`protectFacilities`（建物を守る）、`materialMultiplier`（資材の寄付）、各建物の効果（`warehouseStockMultiplier`・`warehouseRestockMultiplier`・`questHallExtraQuests`・`questHallExtraTransport`・`innVillagers`・`bankInterestMaxMultiplier`）
  - 管理コマンド：`/aktradeex development complete`（建設中の建物をすぐ完成させる、テスト用）

### 変更
- **掲示板の地図への登録**：市場の一覧の右下に「この市場を登録」（JourneyMap が無い時は「この市場の座標」）を追加し、今いる市場そのものも地図に登録できるようにしました

### 修正

---

## 0.1.0（2026-11-07）

初公開（Crafters Colony：<https://minecraft-mcworld.com/272453/>）
