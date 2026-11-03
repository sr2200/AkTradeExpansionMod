# Trade Expansion

Minecraft Java Edition 26.3 向けの Fabric Mod です。コインと屋台で取引する、**相場が動く経済**を追加します。

- 売りすぎると値が下がり、買いすぎると値が上がって売り切れる。時間がたつと元に戻る
- 近くの市場ほど相場が連動し、遠くの市場とは価格差が生まれる（安い地域で買い、高い地域で売る遊び）
- 地域・次元・村の規模によって品揃えが変わる
- 財布・銀行・クレジットカードでお金を持ち歩ける・預けられる

| 項目 | 内容 |
|---|---|
| Mod ID | `trade_expansion` |
| 対応 | Minecraft 26.3 / Fabric Loader 0.20.1 以上 / Fabric API / Java 25 |
| 動作環境 | シングルプレイ・マルチプレイ（専用サーバー）の両方 |
| 状態 | 開発中（段階0〜13 実装済み。下の「実装状況」を参照） |

仕様の詳細は設計書を参照してください。

- [設計書（trade-mod-design.md）](../../docs/trade-mod-design.md)
- [商品・品揃え設計書（goods-design.md）](../../docs/goods-design.md)

---

## 遊び方

### コイン

| コイン | 価値（既定） |
|---|---|
| 銅貨 | 1c |
| 鉄貨 | 10c |
| 金貨 | 100c |
| ダイヤ貨 | 1,000c |
| ネザライト貨 | 10,000c（溶岩で燃えない） |

- 価格はすべて銅貨単位（c）。支払いは財布とインベントリのコインから自動で行い、お釣りは財布に（財布が無ければ最小の枚数のコインで）返します。入りきらない分は足元に落ちます。
- コインを持ってスニーク＋右クリックすると、1枚を1段下のコインに崩せます。
- 入手方法：市場に商品を売る、依頼を納める、プレイヤーが倒した敵対モブのドロップ（銅貨0〜2枚、まれに鉄貨）、鋳造（作業台で銅ブロック×9 → 銅貨64枚、鉄ブロック×9 → 鉄貨64枚。素材を売るよりずっと安いので、余った素材の受け皿です）。テスト用に管理コマンド `/tradeex give` もあります。
- 交易画面の「両替」タブで、コインを1段下に崩したり、1段下のコインからまとめたりできます（手数料は既定で無し。設定 `currency.exchangeFee` で変更可）。

### 財布・銀行・クレジットカード

| | 内容 |
|---|---|
| **財布** | コインを金額としてしまうアイテム（革×5＋糸×2、容量 100,000c）。拾ったコインは自動で財布に入り、お釣りや売却代金も財布に入る。コインを財布の上に重ねて（右クリック）入れることもできる。右クリックで取り出し画面。死ぬと財布ごと落とす |
| **銀行** | 市場レベル3（大きな村）以上の交易画面に「銀行」タブが出る。預け入れ・引き出しができ、死んでも失わない。利息は無し |
| **クレジットカード** | 銀行タブで発行（手数料 100c、口座から）。持っていれば、どの市場でも交易画面の「支払い：カード」で銀行残高から払える（売却代金も口座へ）。持ち主専用で、再発行すると古いカードは使えなくなる。残高以上は使えない |

### 交易所（屋台）

1. 交易所をクラフトして置く（レシピ：1段目 カーペット×3／2段目 木材・エメラルド・木材／3段目 木材・樽・木材）。
2. 右クリックで交易画面を開き、「購入」「売却」タブで商品と数量を選んで取引する。

- 新しく生成される村（平原・砂漠・サバンナ・雪原・タイガ）には、交易所の入った屋台の建物が建つことがあります。設定 `worldgen.protectNaturalStalls` を ON にすると、村の交易所をサバイバルで壊せなくなります（既定 OFF）。
- **商人NPC**：交易所を開くと、交易所の前に商人が現れます（いなくなっていれば次に開いた時にまた現れます）。商人を右クリックしても同じ交易画面を開けます。商人は倒せず、その場から動きません。交易所を壊すと一緒に消えます。村の屋台の建物には最初から商人がいます。クリエイティブタブの「商人のスポーンエッグ」や `/summon trade_expansion:trader` で出した商人は、近く（64ブロック以内）の市場で取引できます（商人だけでは新しい市場は作れません）。消す時は `/kill`。
- 左下の「売却枠」に品物を置くと、売却タブでその商品が選ばれます。置いた品も売却の対象になり、画面を閉じるとインベントリに戻ります。
- Shift を押しながら確定すると、できる最大の数量（最大36スタック）でまとめて取引します。
- 所持金（現金・銀行）はインベントリの右に表示します。マウスを乗せると内訳が出ます。表示位置は設定の「表示」タブ（`client.balancePosition`）で「売却枠の下」「右上」にも変えられます。
- 一覧の ▲▼ は画面を開いた時からの値動き、右端のメーターは基準価格からのずれ（右・赤＝高い、左・緑＝安い）です。行にマウスを乗せると直近14日の価格グラフが出ます。
- 設定 `trade.acceptDamaged` を ON にすると（または商品定義で `accept_damaged: true`）、耐久が減った道具・防具も残りの耐久の割合で安く買い取ってもらえます（既定 OFF）。

- 半径64ブロック以内の交易所は同じ「市場」になり、在庫と相場を共有します。
- **市場名**：金床で名前を付けた名札を持って交易所を右クリックすると、その市場に名前を付けられます（名札は1枚使います）。名前を付けた交易所を置いて新しい市場を作った時も、その名前になります。名前を変えられるのは、名付けた人（自分で作った市場なら作った人）と管理者だけです（名前の無い市場は誰でも。設定 `market.renamePermission` で変更可）。名前が無い市場は「平原の村 #12」のように表示されます。
- 既存の市場から 64〜256ブロックの場所には新しい交易所を置けません（相場をリセットする抜け道を防ぐため）。
- クリエイティブモードでは取引できません（無限のアイテムで経済が壊れるのを防ぐため）。試すときはサバイバルモードで行ってください。

### 相場の動き方

- 売るたびに価格が少しずつ下がり、買うたびに上がります。まとめて取引しても1個ずつ取引しても合計は同じです。
- 同じ市場で売ってすぐ買い戻しても儲からないように、買値と売値には差があります。
- ずれた相場はゲーム内2日で半分元に戻ります。在庫も時間で補充されます。
- 売りすぎて在庫が上限に達すると「買取停止」になりますが、市場が在庫を少しずつ使うので、時間がたつとまた買い取ってもらえます。
- 食料・松明・燃料・原木・木材・丸石・砂・ガラスなどの**消耗品**は早く使われます。上限まで売っても1日（現実の20分）で元の在庫に戻り、値段も1日で半分戻ります。消耗品は後から追加・削除できます（管理コマンド `/tradeex consumable add|remove <アイテム>`、設定の `consumables.add`・`consumables.remove`、データパックのアイテムタグ `#trade_expansion:consumables`）。
- 取引の影響は近くの市場にも伝わります（128ブロック以内は100%、1,536ブロックで0%）。在庫は伝わりません。

### 品揃えと市場の規模

- 市場を作った場所によって、扱う商品が変わります。
  - オーバーワールド：平原・砂漠・サバンナ・雪原・タイガの村、ジャングル、沼地、プレイヤーが作った市場
  - ネザー（金・ネザー素材）、エンド（エンダーパール・シュルカーの殻など）
- 市場には「規模（レベル）」があり、レベルが高いほど商品が多く、高級品も並びます。

| レベル | 名前 | ベッド数 | ネザー・エンドの村人数 | 商品数 |
|---|---|---|---|---|
| 1 | 集落 | 0〜4 | 0〜2 | 10 |
| 2 | 村 | 5〜9 | 3〜5 | 13 |
| 3 | 大きな村 | 10〜19 | 6〜11 | 15 |
| 4 | 町 | 20〜39 | 12〜23 | 18 |
| 5 | 都市 | 40〜 | 24〜 | 20 |

- 市場の中心から半径64ブロック以内のベッドの数で測ります。ネザー・エンドではベッドが使えないので、連れて来た村人の人数で測ります。
- 画面を開いた時にレベルを測り直し（ゲーム内1日に1回まで）、上がっていれば商品が増えます。レベルは下がりません。

### 掲示板・依頼・品不足

| タブ | 内容 |
|---|---|
| **掲示板** | 半径512ブロック以内（同じ次元）の市場の一覧と、方角・距離。市場を選ぶとその市場の商品の買値・売値が見られる（最後に開かれた時点の価格） |
| **依頼** | 市場が「この品を何個」と依頼を出す（1市場に最大3件、ゲーム内3日で期限切れ）。納めると基準価格の1.2〜2.0倍の報酬（★1〜3、高額な依頼ほど倍率が高い） |

- 市場ではゲーム内3日ごとに最大2種類の商品が「品不足」になり、基準価格が1.5〜2.0倍になります（一覧や掲示板でオレンジ色）。

### エメラルド貨

- 石切台で、エメラルドブロック1個からエメラルド貨1枚を作れます（枚数は設定で変更可、0 でレシピ無効）。
- 所持金には数えない補助コインで、**村の市場**でだけ10枚単位で売却できます（平常時 10枚で4c、売るほど値下がり）。
- 村人交易で大量に手に入るエメラルドを、少しずつ経済に取り込むための仕組みです。

---

## コマンド

`/tradeex`（別名 `/trade_expansion`）。`balance` と `bank balance` 以外は OP 権限（既定はレベル2。設定 `command.permissionLevel` で変更可）が必要です。

| コマンド | 内容 |
|---|---|
| `/tradeex balance` | 所持金を表示 |
| `/tradeex give <金額>` | 自分にコインを渡す（テスト用） |
| `/tradeex pay <金額>` | 自分の所持金から支払う（支払い処理のテスト用） |
| `/tradeex market info` | 最寄りの市場の情報（プロファイル・レベル・ベッド数・全商品の価格と在庫） |
| `/tradeex market advance <日数>` | 最寄りの市場の相場を、指定した日数分だけ回復させる（テスト用） |
| `/tradeex market reset [ID]` | 市場の相場と在庫を初期値に戻す（商品構成・レベルはそのまま。ID 省略で最寄り） |
| `/tradeex market reroll [ID]` | 今のデータパックとレベルで品揃えを選び直す |
| `/tradeex market name <ID> <名前>` | 市場名を付ける・変える（`reset` で名前を消す） |
| `/tradeex market delete <ID>` | 市場を削除（交易所は次に使われた時に割り当て直し） |
| `/tradeex market setx <商品> <X>` | 最寄りの市場の相場のずれ X を直接設定する（テスト用） |
| `/tradeex simulate <商品> <buy\|sell> <数>` | 取引せずに、最寄りの市場での価格の推移を表示 |
| `/tradeex consumable list` | 消耗品として扱っている商品と、設定で追加・除外したアイテムを表示 |
| `/tradeex consumable add\|remove <アイテム>` | アイテムを消耗品に追加する／消耗品から外す（設定ファイルに保存され、すぐに反映） |
| `/tradeex consumable reset <アイテム>` | 追加・除外の設定を消して、データパックのタグの指定に戻す |
| `/tradeex audit` | 最寄りの市場で、素材を買ってクラフト（または鋳造）して売ると儲かるレシピを一覧にする（バランス確認用） |
| `/tradeex reload` | 設定ファイルを読み直す |
| `/tradeex bank balance` | 自分の銀行残高（OP 権限不要） |
| `/tradeex bank set\|add <プレイヤー> <金額>` | 銀行残高を設定・増減 |

---

## 設定

設定は `config/trade_expansion.json` に保存されます（初回起動時に全項目が既定値で書き出されます）。

- **Mod Menu ＋ YetAnotherConfigLib（YACL）** を入れていれば、Mod 一覧の Trade Expansion の「設定」ボタンから画面で変更できます。どちらも任意で、入っていなくても Mod は動きます。
- シングルプレイでは保存した時点で反映されます。マルチプレイではサーバーの設定ファイルを編集して `/tradeex reload` を実行します（`client.*` は各プレイヤーが自分で変更できます）。
- 範囲外の値は自動で範囲内に直されます（サーバーログに警告）。書かれていない項目は既定値になります。
- 「**未実装**」の項目は、値を保存できるだけで今はまだ効果がありません。

### 設定ファイルの書き方

JSON の中は、下の表の「キー」の `.` の前がカテゴリ、後ろが項目名です。変えたい項目だけ書けば、残りは既定値になります。

```json
{
  "currency": { "exchangeFee": 0.02 },
  "price": { "globalMultiplier": 1.5 },
  "recovery": { "halfLifeDays": 1.0 },
  "consumables": { "add": ["minecraft:sandstone"], "remove": [] },
  "trade": { "allowCreative": true, "maxPerTransaction": 128 },
  "worldgen": { "protectNaturalStalls": true },
  "client": { "balancePosition": "INVENTORY_RIGHT" }
}
```

### 設定値一覧

「設定例」は、そのカテゴリの `{ }` の中に書く形です（例：`"currency": { "exchangeFee": 0.02 }`）。

#### 通貨（currency）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `currency.ratios` | `[10, 10, 10, 10]` | 各 2〜100 | 銅→鉄、鉄→金、金→ダイヤ、ダイヤ→ネザライトの比率。ワールドを作る時に決める前提（変えると手持ちのコインの価値が変わる） | `"ratios": [10, 10, 10, 10]` |
| `currency.ratioLocked` | `true` | true / false | ON の時、ワールド作成後に比率を変えても、そのワールドが作られた時の比率を使い続ける | `"ratioLocked": false`（比率の変更をワールドに反映する） |
| `currency.allowMinting` | `true` | true / false | 銅ブロック×9 → 銅貨、鉄ブロック×9 → 鉄貨の鋳造レシピ。反映は再起動か `/reload` の後 | `"allowMinting": false`（鋳造を禁止） |
| `currency.exchangeFee` | `0.0` | 0〜0.5 | 両替タブの手数料の割合 | `"exchangeFee": 0.02`（2%） |
| `currency.startingMoney` | `0` | 0〜1,000,000,000 | 初めてログインした時に渡す金額（c）。**未実装** | `"startingMoney": 500` |
| `currency.mobDropCoins` | `true` | true / false | プレイヤーが倒した敵対モブがコインを落とす（銅貨0〜2枚、まれに鉄貨） | `"mobDropCoins": false` |
| `currency.sneakUseExchange` | `true` | true / false | コインを持ってスニーク＋右クリックで、1枚を下位のコインに崩す | `"sneakUseExchange": false` |
| `currency.emeraldCoinsPerBlock` | `1` | 0〜64 | エメラルドブロック1個から石切台で作れるエメラルド貨の枚数。0 でレシピ無効。反映は再起動か `/reload` の後 | `"emeraldCoinsPerBlock": 2` |

#### 価格（price）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `price.defaultSpread` | `0.4` | 0.05〜0.9 | 買値と売値の差の割合（商品定義に無い時の既定）。0.4 なら買値＝基準×1.2、売値＝基準×0.8 | `"defaultSpread": 0.3` |
| `price.defaultElasticity` | `32` | 3〜10000 | 大きいほど売買しても値崩れしにくい（商品定義に無い時の既定） | `"defaultElasticity": 64` |
| `price.minMultiplier` | `0.25` | 0.01〜1 | 売られすぎても、基準価格のこの倍率より下がらない | `"minMultiplier": 0.5` |
| `price.maxMultiplier` | `3.0` | 1〜100 | 買われすぎても、基準価格のこの倍率より上がらない | `"maxMultiplier": 2.0` |
| `price.jitter` | `0.15` | 0〜0.5 | 新しい市場の基準価格のばらつき（±15%）。既存の市場は変わらない | `"jitter": 0.3`（地域差を大きく） |
| `price.defaultMaxStockMultiplier` | `2.0` | 1〜10 | 商品定義に `max_stock` が無い時の在庫上限＝目標在庫×この値 | `"defaultMaxStockMultiplier": 4.0` |
| `price.globalMultiplier` | `1.0` | 0.01〜100 | すべての価格に掛ける倍率。既存の市場の価格もすぐ変わる | `"globalMultiplier": 2.0`（全部2倍） |

#### 回復（recovery）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `recovery.halfLifeDays` | `2.0` | 0.05〜100 | 相場のずれがゲーム内何日で半分元に戻るか | `"halfLifeDays": 1.0` |
| `recovery.restockMultiplier` | `1.0` | 0〜100 | 在庫の補充・消費の速さの全体倍率 | `"restockMultiplier": 2.0` |
| `recovery.clock` | `"GAME_TIME"` | "GAME_TIME" / "DAY_TIME" | GAME_TIME：寝ても回復は進まない／DAY_TIME：寝て飛ばした時間も数える | `"clock": "DAY_TIME"` |
| `recovery.consumableMultiplier` | `1.0` | 0〜10 | 消耗品（食料・松明・原木など）が在庫過多の時に1日に使われる量＝目標在庫×この値。1.0 なら1日で目標に戻る | `"consumableMultiplier": 0.5`（2日で戻る） |
| `recovery.consumableHalfLifeDays` | `1.0` | 0.05〜100 | 消耗品の相場の半減期（日） | `"consumableHalfLifeDays": 0.5` |

#### 消耗品（consumables）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `consumables.add` | `[]` | アイテムIDの一覧 | タグ `#trade_expansion:consumables` に無くても消耗品として扱うアイテム。`/tradeex consumable add` でも追加できる | `"add": ["minecraft:sandstone", "minecraft:white_wool"]` |
| `consumables.remove` | `[]` | アイテムIDの一覧 | タグや `add` にあっても消耗品として扱わないアイテム。`/tradeex consumable remove` でも指定できる | `"remove": ["minecraft:cobblestone"]` |

#### 距離（distance）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `distance.mergeRadius` | `64` | 8〜512 | この距離（ブロック）以内の交易所は同じ市場になる | `"mergeRadius": 96` |
| `distance.nearRadius` | `128` | mergeRadius〜100000 | この距離以内の市場は相場が100%連動する | `"nearRadius": 256` |
| `distance.farRadius` | `1536` | nearRadius+1〜100000 | この距離より遠い市場には相場の変化が伝わらない | `"farRadius": 3000` |
| `distance.falloffExponent` | `2.0` | 0.5〜5 | 距離による影響の減り方。1＝直線、2＝近場を出た直後に大きく下がる | `"falloffExponent": 1.0` |
| `distance.linkNetherToOverworld` | `false` | true / false | ネザー座標×8 をオーバーワールド座標とみなして相場を連動させる。**未実装** | `"linkNetherToOverworld": true` |
| `distance.minNewMarketDistance` | `256` | 0〜10000 | クラフトした交易所で新しい市場を作れるのは、既存の市場からこの距離以上離れた場所だけ | `"minNewMarketDistance": 128` |

#### 市場（market）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `market.minGoods` | `10` | 1〜100 | レベル1の市場が扱う商品数 | `"minGoods": 8` |
| `market.maxGoods` | `20` | minGoods〜100 | レベル5の市場が扱う商品数 | `"maxGoods": 30` |
| `market.levelThresholds` | `[5, 10, 20, 40]` | 昇順の整数4つ | レベル2〜5になるベッドの数（オーバーワールド） | `"levelThresholds": [3, 8, 15, 30]` |
| `market.villagerThresholds` | `[3, 6, 12, 24]` | 昇順の整数4つ | レベル2〜5になる村人の人数（ネザー・エンド） | `"villagerThresholds": [2, 4, 8, 16]` |
| `market.levelRadius` | `64` | 16〜256 | ベッド・村人を数える水平半径 | `"levelRadius": 96` |
| `market.levelCheckIntervalDays` | `1.0` | 0〜100 | 画面を開いた時、前回からこの日数以上たっていればレベルを測り直す | `"levelCheckIntervalDays": 0.5` |
| `market.renamePermission` | `"NAMER_OR_OP"` | "ANYONE" / "NAMER_OR_OP" / "OP_ONLY" | 市場名を変えられる人。NAMER_OR_OP＝名付けた人と管理者（名前の無い市場は誰でも） | `"renamePermission": "OP_ONLY"` |

#### 財布（wallet）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `wallet.capacity` | `100000` | 1000〜1,000,000,000 | 財布1つに入る金額（c） | `"capacity": 1000000` |
| `wallet.autoCollect` | `true` | true / false | 財布を持っている間、拾ったコインを自動で財布に入れる | `"autoCollect": false` |

#### 銀行（bank）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `bank.minMarketLevel` | `3` | 1〜5 | このレベル以上の市場の交易画面に銀行タブが出る | `"minMarketLevel": 1`（どの市場でも銀行） |
| `bank.interestRatePerDay` | `0.0` | 0〜0.01 | 預金の利率（ゲーム内1日あたり）。**未実装** | `"interestRatePerDay": 0.001` |
| `bank.cardFee` | `100` | 0〜1,000,000 | カード発行・再発行の手数料（c、口座から） | `"cardFee": 0` |
| `bank.cardTransactionFee` | `0.0` | 0〜0.1 | カードで買う時、1回ごとに代金に上乗せする手数料の割合 | `"cardTransactionFee": 0.01`（1%） |

#### 取引（trade）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `trade.maxDistance` | `8` | 2〜32 | 交易所（商人）からこの距離以内でないと取引できない | `"maxDistance": 12` |
| `trade.acceptDamaged` | `false` | true / false | 耐久が減った品を残り耐久の割合で安く買い取る（商品定義の `accept_damaged` が優先） | `"acceptDamaged": true` |
| `trade.priceTolerance` | `0.0` | 0〜0.2 | 確定時の価格がプレビューからこの割合以上ずれたら中止する。0 なら1cでも違えば中止 | `"priceTolerance": 0.05` |
| `trade.maxPerTransaction` | `64` | 1〜2304 | 1回の取引で選べる最大の数量 | `"maxPerTransaction": 128` |
| `trade.allowShiftAll` | `true` | true / false | Shift＋確定で、できる最大の数量（最大2,304）までまとめて取引する | `"allowShiftAll": false` |
| `trade.allowCreative` | `false` | true / false | クリエイティブモードでの取引・銀行・両替を許可する | `"allowCreative": true`（テスト用） |

#### 生成・商人（worldgen / trader）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `worldgen.spawnStallsInVillages` | `true` | true / false | 新しく生成される村に屋台の建物を建てる。反映はサーバー（ワールド）の再起動後 | `"spawnStallsInVillages": false` |
| `worldgen.protectNaturalStalls` | `false` | true / false | 村の交易所をサバイバルで壊せなくする | `"protectNaturalStalls": true` |
| `worldgen.spawnTraderNpc` | `true` | true / false | 村の屋台の建物に商人NPCを置く | `"spawnTraderNpc": false` |
| `trader.spawnOnOpen` | `true` | true / false | 交易所を開いた時、紐付いた商人がいなければ交易所の前に出す | `"spawnOnOpen": false` |

#### 掲示板・依頼・品不足（board / quest / demand）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `board.radius` | `512` | 64〜4096 | 掲示板に載せる市場の水平距離 | `"radius": 1024` |
| `board.maxMarkets` | `20` | 1〜100 | 掲示板に載せる市場の最大数（近い順） | `"maxMarkets": 10` |
| `quest.maxPerMarket` | `3` | 0〜10 | 1つの市場に同時に出る依頼の最大数。0 で依頼なし | `"maxPerMarket": 5` |
| `quest.refreshDays` | `1.0` | 0.1〜30 | この日数ごとに空いた枠へ新しい依頼が出る | `"refreshDays": 0.5` |
| `quest.durationDays` | `3.0` | 0.5〜30 | 依頼の期限（ゲーム内日） | `"durationDays": 5.0` |
| `quest.rewardMultipliers` | `[1.2, 1.5, 2.0]` | 各 0.1〜10（3つ） | ★1〜★3 の報酬倍率（報酬＝基準価格×数量×倍率） | `"rewardMultipliers": [1.1, 1.3, 1.6]` |
| `quest.difficultyThresholds` | `[200, 1000]` | 昇順の整数2つ | ★2・★3 になる依頼の金額（c） | `"difficultyThresholds": [300, 1500]` |
| `demand.maxShortages` | `2` | 0〜10 | 1つの市場で同時に品不足になる商品の最大数。0 で無効 | `"maxShortages": 0`（品不足なし） |
| `demand.durationDays` | `3.0` | 0.5〜30 | 品不足の商品を選び直す間隔（ゲーム内日） | `"durationDays": 7.0` |
| `demand.minMultiplier` | `1.5` | 1〜5 | 品不足の商品の基準価格に掛ける倍率の下限 | `"minMultiplier": 1.2` |
| `demand.maxMultiplier` | `2.0` | minMultiplier〜5 | 品不足の商品の基準価格に掛ける倍率の上限 | `"maxMultiplier": 1.5` |

#### 村人（villager）※段階14で使う予定

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `villager.coinTrades` | `true` | true / false | 村人から買う時の代金をコインにする。**未実装** | `"coinTrades": false` |
| `villager.emeraldPrice` | `30` | 1〜10000 | 村人の代金をコインにする時、エメラルド1個を何cとするか。**未実装** | `"emeraldPrice": 50` |

#### その他（log / command / vanilla）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `log.trades` | `true` | true / false | 誰が何をいくらで売買したか（銀行の出し入れも）をサーバーログに出す | `"trades": false` |
| `command.permissionLevel` | `2` | 0〜4 | `/tradeex` の管理用コマンドに必要な権限レベル（2＝OP） | `"permissionLevel": 3` |
| `vanilla.disableVillagerTrades` | `false` | true / false | バニラの村人との交易をできなくする | `"disableVillagerTrades": true` |

#### 表示（client）※各プレイヤーのクライアント側の設定

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `client.showDebugTooltip` | `false` | true / false | 交易画面のツールチップに基準価格などを出す。**未実装** | `"showDebugTooltip": true` |
| `client.balancePosition` | `"INVENTORY_RIGHT"` | "INVENTORY_RIGHT" / "BELOW_SELL_SLOT" / "TOP_RIGHT" | 交易画面の所持金の表示位置（インベントリの右／売却枠の下／右上） | `"balancePosition": "TOP_RIGHT"` |

詳しい計算方法や各項目の背景は、設計書（`docs/trade-mod-design.md`）の G章を参照してください。

---

## データパックでのカスタマイズ

商品と品揃えはデータパックで変更・追加できます。`/reload` で再読み込みされ、既存の市場にも価格の変更が反映されます（商品の構成は変わりません）。

| 種類 | 置き場所 |
|---|---|
| 商品定義 | `data/<namespace>/trade_expansion/goods/<名前>.json` |
| 市場プロファイル | `data/<namespace>/trade_expansion/market_profile/<名前>.json` |

商品定義の例：

```json
{
  "item": "minecraft:diamond_sword",
  "base_price": 900,
  "elasticity": 16,
  "target_stock": 8,
  "max_stock": 24,
  "restock_per_day": 4,
  "buy": true,
  "sell": true
}
```

主な項目：`base_price`（基準価格）、`elasticity`（大きいほど値崩れしにくい）、`target_stock`（目標在庫）、`max_stock`（在庫上限。これ以上は買い取らない）、`unit`（取引単位）、`auto_add`（条件に合う市場に自動で追加）、`link`（近くの市場への相場の伝わりやすさ、既定 1.0）、`consume_per_day`（在庫が目標より多い時に1日に減る数）、`half_life_days`（相場の半減期）、`components`（エンチャントなど付加情報付きの品。例：`{"minecraft:enchantments": {"minecraft:sharpness": 1}}`）。

市場プロファイルでは、条件（`dimension` / `biome` / `structure` / `villager_type` / `origin` / `distance_from_spawn` / `all_of` / `any_of` / `not`）、抽選プール（重み・`min_level`）、必ず扱う商品（`always`）、価格倍率（`price_modifiers`）を指定します。書式の詳細は設計書 D-2 を参照してください。同梱データ（商品60種＋エメラルド貨、プロファイル12種）は `src/main/resources/data/trade_expansion/trade_expansion/` にあります。

---

## 開発

### 必要なもの

- JDK 25 以上（JDK 26 でも `release 25` 指定でビルドできることを確認済み）
- IntelliJ IDEA など（Fabric の Loom を使う Gradle プロジェクト）

### よく使うコマンド

`Source/TradeExpansion` で実行します。

| コマンド | 内容 |
|---|---|
| `./gradlew build` | ビルド（`build/libs/trade_expansion-<version>.jar`） |
| `./gradlew test` | 単体テスト（ゲームを起動せずに実行できる） |
| `./gradlew runClient` | 開発用クライアントを起動（Mod Menu と YACL も開発用に読み込まれる） |
| `./gradlew runServer` | 開発用サーバーを起動 |

- `runServer` を初めて動かす時は、`run/eula.txt` を `eula=true` にする必要があります（[Minecraft EULA](https://aka.ms/MinecraftEULA) に同意したことになります）。
- 開発用クライアントからサーバーに接続するには、`run/server.properties` で `online-mode=false`、`white-list=false` にします。
- プロジェクトを OneDrive の同期フォルダに置くと、Gradle が `Failed to clean up stale outputs` で失敗することがあります。その場合は `build` フォルダを削除して再実行してください。

### 構成

| 場所 | 内容 |
|---|---|
| `src/main/java/.../core/` | Minecraft に依存しない計算（価格・距離・コイン・市場レベル・商品の抽選）。JUnit でテスト |
| `src/main/java/.../market/` | 市場・保存データ・新規市場の作成・相場の波及 |
| `src/main/java/.../data/` | データパック（商品定義・市場プロファイル・条件）の読み込み |
| `src/main/java/.../trade/` | 購入・売却の処理 |
| `src/main/java/.../bank/`・`currency/` | 銀行口座・財布・支払い |
| `src/main/java/.../config/` | 設定ファイルの読み書き・同期 |
| `src/main/java/.../quest/`・`board/` | 依頼・品不足・掲示板 |
| `src/main/java/.../worldgen/`・`mixin/` | 村への屋台の建物の追加・財布の自動収納 |
| `src/main/resources/data/trade_expansion/structure/` | 村の屋台の建物（構造物NBT。`tools/structures/genstall.js` で生成） |
| `src/client/java/...` | 交易画面などクライアント専用のコード |
| `src/test/java/...` | 単体テスト（価格・距離・コイン・財布・市場レベル・商品の抽選・依頼の計算・設定ファイル・同梱データの読み込み） |

---

## 実装状況

| 段階 | 内容 | 状態 |
|---|---|---|
| 0〜2 | 環境・コイン・支払いとお釣り | ✅ |
| 3〜4 | 交易所・交易画面・価格モデル・保存 | ✅ |
| 5〜6 | 市場の統合・新規市場の制限・相場の波及 | ✅ |
| 7 | 地域ごとの品揃え・市場レベル・エメラルド貨 | ✅ |
| 8 | 設定ファイル・Mod Menu＋YACL の設定画面・管理コマンド（`market reset` / `reroll` / `delete`、`simulate`、`reload`） | ✅（ゲーム内の確認は進行中） |
| 9 | 村への交易所（屋台の建物）の自然生成 | ✅（実際の村での見た目はゲーム内で確認中） |
| 10 | 商人NPC | ✅（ゲーム内の確認は進行中） |
| 11 | 両替タブ・敵モブのコインドロップ・銅貨と鉄貨の鋳造・減額買取・付加情報付きの商品・売却枠・Shift で全数・相場グラフ・屋台の形・`audit` など | ✅（ゲーム内の確認は進行中） |
| 12 | 財布・銀行・クレジットカード | ✅（ゲーム内の確認は進行中） |
| 13 | 掲示板・依頼・品不足 | ✅（ゲーム内の確認は進行中） |
| 14 | 村人交易のコイン化 | 未着手 |

---

## ライセンス

現在はテンプレートのまま CC0-1.0 です（公開前に見直す予定）。
