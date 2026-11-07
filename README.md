# AK Trade Expansion

Minecraft Java Edition 26.3 向けの Fabric Mod です。コインと屋台で取引する、**相場が動く経済**を追加します。

- 売りすぎると値が下がり、買いすぎると値が上がって売り切れる。時間がたつと元に戻る
- 近くの市場ほど相場が連動し、遠くの市場とは価格差が生まれる（安い地域で買い、高い地域で売る遊び）
- 地域・次元・村の規模によって品揃えが変わる
- 財布・銀行・クレジットカードでお金を持ち歩ける・預けられる
- 村には屋台と商人が現れ、掲示板で近くの市場の相場を調べたり、依頼をこなしたり、荷箱をほかの村へ運んだりできる
- 値段や消耗品の扱いなどは、設定ファイルと管理コマンドで後から調整できる

| 項目 | 内容 |
|---|---|
| Mod ID | `ak_trade_expansion`（表示名は「AK Trade Expansion」。0.0.10 までは「Trade Expansion」） |
| 対応 | Minecraft 26.3 / Fabric Loader 0.20.1 以上 / Fabric API / Java 25 |
| 動作環境 | シングルプレイ・マルチプレイ（専用サーバー）の両方 |
| 配布 | [Crafters Colony（マイクラコミュニティ）の投稿ページ](https://minecraft-mcworld.com/272453/)（0.1.0 から公開） |
| 状態 | 開発中（段階0〜14 実装済み。下の「実装状況」を参照） |
| バージョン | 0.1.10（`Source/TradeExpansion/gradle.properties` の `version`。`build.bat` でビルドするたびに末尾を上げ（0.0.1 → 0.0.2）、`release.bat` で配布用に作る時は真ん中を上げて末尾を0に戻す（0.0.12 → 0.1.0）） |

ソースコード（Gradle プロジェクト）は [Source/TradeExpansion](Source/TradeExpansion) にあります。仕様の詳細は設計書を参照してください。

- [設計書（trade-mod-design.md）](docs/trade-mod-design.md)
- [商品・品揃え設計書（goods-design.md）](docs/goods-design.md)
- [TODO（docs/TODO）](docs/TODO)：未解決の項目・今後の予定（VS Code 拡張機能「Todo+」の書式）

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
- 入手方法：初めてワールドに入った時に 240c（パン10個分）、市場に商品を売る、依頼を納める、プレイヤーが倒した敵対モブのドロップ（銅貨0〜2枚、まれに鉄貨）、鋳造（作業台で銅ブロック×9 → 銅貨64枚、鉄ブロック×9 → 鉄貨64枚。素材を売るよりずっと安いので、余った素材の受け皿です）。テスト用に管理コマンド `/aktradeex give` もあります。
- 交易画面の「両替」タブで、コインを1段下に崩したり、1段下のコインからまとめたりできます（手数料は既定で無し。設定 `currency.exchangeFee` で変更可）。

### 財布・銀行・クレジットカード

| | 内容 |
|---|---|
| **財布** | コインを金額としてしまうアイテム（革×5＋糸×2、容量 20,000c）。拾ったコインは自動で財布に入り、お釣りや売却代金も財布に入る。コインを財布の上に重ねて（右クリック）入れることもできる。右クリックで取り出し画面。死ぬと財布ごと落とす |
| **銀行** | 市場レベル3（大きな村）以上の交易画面に「銀行」タブが出る。預け入れ・引き出しができ、死んでも失わない。利息は無し。預金には**利息**が付きます（ゲーム内1日 0.03%、10日で約 0.3%、毎日複利。利息が付くのは 100,000c まで） |
| **クレジットカード** | 銀行タブで発行（手数料 100c、口座から）。持っていれば、どの市場でも交易画面の「支払い：カード」で銀行残高から払える（売却代金も口座へ）。持ち主専用で、再発行すると古いカードは使えなくなる。残高以上は使えない |

### 交易所（屋台）と商人

1. 交易所をクラフトして置く（レシピ：1段目 カーペット×3／2段目 木材・エメラルド・木材／3段目 木材・樽・木材）。
2. 右クリックで交易画面を開き、「購入」「売却」タブで商品と数量を選んで取引する。

- 半径64ブロック以内の交易所は同じ「市場」になり、在庫と相場を共有します。
- 既存の市場から 64〜256ブロックの場所には新しい交易所を置けません（相場をリセットする抜け道を防ぐため）。
- 256ブロック以上離れた場所に新しい市場を作るには、周り（半径64ブロック）に**村人が3人以上いて、ベッドが3個以上かつ村人の数以上**あることが必要です（既定）。ただし**ベッドが5個以上あれば、村人の方が多くても作れます**（設定 `market.newMarketEnoughBeds`）。条件は設定 `market.newMarketRequires` で「村の中だけ」「ベッドの数」「村人の数」に変えられます。ネザー・エンドでは村人の数だけで判定します。
- クリエイティブモードでは取引できません（無限のアイテムで経済が壊れるのを防ぐため）。試すときはサバイバルモードで行ってください（設定 `trade.allowCreative` で許可もできます）。
- **置き場所の確認**：交易所を手に持つと、画面下に「置くと◯◯に加わる／新しい市場ができる／置けない理由」が出ます。`/aktradeex where` で今いる場所の情報（村の範囲内か・近くの市場・ベッドと村人の数・置いた時の結果）、`/aktradeex where show` で範囲を30秒間パーティクルで表示できます（緑：村、黄：同じ市場になる範囲、赤：新しい市場を作れない範囲、青：ベッド・村人を数える範囲）。
- **村の屋台**：新しく生成される村（平原・砂漠・サバンナ・雪原・タイガ）には、交易所の入った屋台の建物が建つことがあります。設定 `worldgen.protectNaturalStalls` を ON にすると、村の交易所をサバイバルで壊せなくなります（既定 OFF）。
- **商人NPC**：交易所を開くと、交易所の前に商人が現れます（いなくなっていれば次に開いた時にまた現れます）。商人を右クリックしても同じ交易画面を開けます。商人は倒せず、その場から動きません。交易所を壊すと一緒に消えます。村の屋台の建物には最初から商人がいます。クリエイティブタブの「商人のスポーンエッグ」や `/summon ak_trade_expansion:trader` で出した商人は、近く（64ブロック以内）の市場で取引できます（商人だけでは新しい市場は作れません）。消す時は `/kill`。
- **市場名**：金床で名前を付けた名札を持って交易所を右クリックすると、その市場に名前を付けられます（名札は1枚使います）。名前を付けた交易所を置いて新しい市場を作った時も、その名前になります。名前を変えられるのは、名付けた人（自分で作った市場なら作った人）と管理者だけです（名前の無い市場は誰でも。設定 `market.renamePermission` で変更可）。名前が無い市場は「平原の村 #12」のように表示されます。

### 交易画面

| タブ | 内容 |
|---|---|
| 購入・売却 | 商品と数量を選んで取引する |
| 銀行 | 市場レベル3以上の市場だけ。預け入れ・引き出し・カード発行 |
| 掲示板・依頼 | 下の「掲示板・依頼・品不足」 |
| 両替 | コインを1段下に崩す・1段下からまとめる |
| 発展 | 村の発展度・発展度を多く入れた人・コインの寄付（「村の発展」を参照。オーバーワールドの市場だけ） |

- 一覧の右端のスクロールバーは、つまみをドラッグしたり溝をクリックしたりして動かせます（マウスホイールも使えます）。
- 交易画面は、ウィンドウに余裕があると開いた時に大きくなります（商品一覧は最大10行、名前の欄も広くなる）。マイクラの「GUIの大きさ」を小さくすると、より大きく表示されます。
- 左下の「売却枠」に品物を置くと、売却タブでその商品が選ばれます。置いた品も売却の対象になり、画面を閉じるとインベントリに戻ります。
- Shift を押しながら確定すると、できる最大の数量（最大36スタック）でまとめて取引します。
- 売却タブの「**まとめて売る**」で、この市場が買い取る物をまとめて売れます。ホットバー（手元の9枠）の品は売りません。確認の枠に品と金額の一覧が出て、行をクリックすると「除外」を切り替えられます（道具・防具は最初から除外。選んだ除外は次からも覚えています）。在庫の上限にかかる品は売れる分だけ売ります。
- 所持金（現金・銀行）はインベントリの右に表示します。マウスを乗せると内訳が出ます。表示位置は設定の「表示」タブ（`client.balancePosition`）で「売却枠の下」「右上」にも変えられます。
- 一覧の ▲▼ は画面を開いた時からの値動き、右端のメーターは基準価格からのずれ（右・赤＝高い、左・緑＝安い）です。行にマウスを乗せると直近14日の価格グラフが出ます。品不足の商品はオレンジ色、**本日の掘り出し物**は★付きの水色です。
- 売り切れ・買取停止の商品は、行が赤みがかった灰色になります。マウスを乗せると理由と「入荷まで約○分」「買取再開まで約○分」が出ます。売却枠に置いた品が買取停止のときは、売却枠が赤い枠で囲まれます。
- **まとめ買い割引**：1回に16個以上買うと5%引き、64個以上で10%引きになります（売却には付きません。設定 `trade.bulkDiscounts`）。割引が効く数量では、合計金額の下に「まとめ買い −10%」のように出ます。値段の安い商品やスプレッドの小さい商品では、売り戻して儲からないよう割引率が小さくなることがあります。
- 設定 `trade.acceptDamaged` を ON にすると（または商品定義で `accept_damaged: true`）、耐久が減った道具・防具も残りの耐久の割合で安く買い取ってもらえます（既定 OFF）。

### 相場の動き方

- 売るたびに価格が少しずつ下がり、買うたびに上がります。まとめて取引しても1個ずつ取引しても合計は同じです（購入はまとめ買い割引の分だけ、まとめて買う方が安くなります）。
- 同じ市場で売ってすぐ買い戻しても儲からないように、買値と売値には差があります。
- ずれた相場はゲーム内2日で半分元に戻ります。在庫も時間で補充されます。
- 売りすぎて在庫が上限に達すると「買取停止」になりますが、市場が在庫を少しずつ使うので、時間がたつとまた買い取ってもらえます。
- 食料・松明・燃料・原木・木材・丸石・砂・ガラス・花などの**消耗品**は早く使われます。上限まで売っても1日（現実の20分）で元の在庫に戻り、値段も1日で半分戻ります。消耗品は後から追加・削除できます（設定画面の「商品」タブ →「追加の商品と消耗品」でアイコンから選ぶ、管理コマンド `/aktradeex consumable add|remove <アイテム>`、設定の `consumables.add`・`consumables.remove`、データパックのアイテムタグ `#ak_trade_expansion:consumables`）。
- 取引の影響は近くの市場にも伝わります（128ブロック以内は100%、1,536ブロックで0%）。在庫は伝わりません。ネザーの市場は、座標を8倍してオーバーワールドの市場とも連動します（設定 `distance.linkNetherToOverworld`）。

### 品揃えと市場の規模

- 市場を作った場所によって、扱う商品が変わります。
  - オーバーワールド：平原・砂漠・サバンナ・雪原・タイガの村、ジャングル、沼地、プレイヤーが作った市場
  - ネザー（金・ネザー素材）、エンド（エンダーパール・シュルカーの殻など）
- 商品は全部で95種（食料（生の肉と魚を含む）・オーバーワールドの原木すべて・建材・鉱物・モブの素材・道具とダイヤの装備・花20種・ネザーとエンドの品）と、補助コインのエメラルド貨です。1つの市場が扱うのはそのうち10〜20種です。
- 設定画面の「商品」タブ →「追加の商品と消耗品」で、アイコンからアイテムを選び、基準価格を入れて**商品を追加**できます。追加した商品は、下の「本日の掘り出し物」として市場に並ぶことがあります。
- 市場には「規模（レベル）」があり、レベルが高いほど商品が多く、高級品も並びます。

| レベル | 名前 | ベッド数 | ネザー・エンドの村人数 | 商品数 |
|---|---|---|---|---|
| 1 | 集落 | 0〜4 | 0〜2 | 10 |
| 2 | 村 | 5〜9 | 3〜5 | 13 |
| 3 | 大きな村 | 10〜19 | 6〜11 | 15 |
| 4 | 町 | 20〜39 | 12〜23 | 18 |
| 5 | 都市 | 40〜 | 24〜 | 20 |

- 市場の中心から半径64ブロック以内の**ベッドの数と村人の人数**で測り、低い方のレベルになります（例：ベッド8個＝レベル2、村人13人＝レベル4 → レベル2。村人の基準は下の表の「ネザー・エンドの村人数」と同じ）。ベッドだけで測りたい時は設定 `market.levelUsesVillagers` を OFF にします。ネザー・エンドではベッドが使えないので、連れて来た村人の人数だけで測ります。
- 村人が家の中や畑に出ていて範囲の外にいると数えられないので、日によってレベルが上下することがあります。
- 画面を開いた時にレベルを測り直し（ゲーム内1日に1回まで）、上がっていれば商品が増えます。
- ベッドや村人が減ってレベルが下がると、最後に並んだ商品から外れて品揃えが減ります。外れた商品の値段と在庫は覚えていて、レベルが戻ると同じ商品が戻ります（設定 `market.shrinkOnLevelDown` を OFF にすると、レベルは下がらず品揃えも減りません）。
- **本日の掘り出し物**：毎日（ゲーム内1日ごと）、その市場がふだん扱わない商品が並びます。レベル1〜2の市場で1品、3以上で2品。価格は2割高く、在庫は少なめで、次の日には入れ替わります（設定 `market.specialGoods` など）。

### 掲示板・依頼・品不足

| タブ | 内容 |
|---|---|
| **掲示板** | 半径512ブロック以内（同じ次元）の市場の一覧と、方角・距離。市場を選ぶとその市場の全商品の買値・売値が見られる（一覧の上に「商品／買値／売値」の見出し。買値は買う時に払う値段、売値は売る時にもらえる値段）（タブを開いた時点の最新の価格。離れた市場とは取引できない）。**お買い得情報**として、今いる市場より安く買える品（水色「安n」）・高く売れる品（桃色「高n」）の数を出し、市場にマウスを乗せると一番お得な品とその差（例：-25%）が出る。市場を選んで「**地図に登録**」を押すと、JourneyMap が入っていればその市場を地図の地点として追加します。入っていない時は、座標をチャットに出します（クリックでコピー）。市場の一覧の右下の「**この市場を登録**」（JourneyMap が無い時は「この市場の座標」）で、今いる市場そのものも同じように登録できます |
| **依頼** | 市場が「この品を何個」と依頼を出す（1市場に最大3件、ゲーム内3日で期限切れ）。納めると基準価格の1.2〜2.0倍の報酬（★1〜3、高額な依頼ほど倍率が高い）。「依頼を受ける」を押すと受付済み（✔）になり、納めた時の評判が増えます（受けたまま期限が切れると評判が下がります）。★★★の依頼は評判「お得意様」以上で納品できます |
| **輸送依頼** | 市場が「この品をほかの市場へ運んでほしい」と依頼を出す（1市場に1件、届け先は 300〜2000 ブロック先）。「荷箱を受け取る」で保証金（品の値段）を払って荷箱をもらい、届け先の市場の依頼タブで「荷箱を届ける」を押すと、報酬と保証金を受け取れます。遠いほど報酬が多く（1000 ブロックで品の値段と同じくらい、最大2倍）、依頼元と届け先の両方で評判が上がります。期限を過ぎると届けられず、保証金は戻りません |

- 市場ではゲーム内3日ごとに最大2種類の商品が「品不足」になり、基準価格が1.5〜2.0倍になります（一覧や掲示板でオレンジ色）。

### 評判

村（市場）ごとに、あなたの**評判**があります。交易画面のタイトル（市場名）のすぐ後ろに「│ 評判：常連」のように出て、マウスを乗せると効果と次のレベルまでのポイントが見られます。

| レベル | 名前 | 必要なポイント |
|---|---|---|
| 0 | よそ者 | 0 |
| 1 | 顔見知り | 50 |
| 2 | 常連 | 150 |
| 3 | お得意様 | 400 |
| 4 | 信頼の厚い客 | 1,000 |
| 5 | 名士 | 2,500 |

- **上がる**：その市場で買う・売る（100c で1ポイント。品不足の商品を売ると2倍）、依頼を納める（★1＝10・★2＝25・★3＝50。受けた依頼なら1.5倍）、市場の近くの村人と取引する（1回1ポイント）
- **下がる**：時間がたつ（ゲーム内30日で半分）、市場の近くの村人や商人を攻撃する（1回 −20、倒すと −100）、受けた依頼の期限が切れる（★1つあたり −10）
- **効果**：レベル1つごとに買値が1%安く・売値が1%高くなる（レベル5で5%。売り戻して儲からないよう、商品によっては小さくなります）、依頼の報酬が4%増える、レベル3から★★★の依頼を納品できる、レベル4から「評判の掘り出し物」が1品見える
- 数値はすべて設定の「評判」タブ（`reputation.*`）で変えられます。

### 村の発展

村（市場）ごとに、みんなで貯める**発展度**があります。発展度を使って村に**建物**を建てると、建物ごとの効果が付き、市場の規模も上がります。交易画面の「**発展**」タブで、発展度・建物・発展度を多く入れた人（マウスを乗せると上位5人）と自分の値が見られます。

- **貯まる**：依頼を納める（報酬の半分。輸送依頼は依頼元と届け先の両方）、その市場で売り買いする（100c で 2）、「発展」タブでコインを寄付する（1c で 1。100c／1,000c／10,000c。カード払いなら口座から、手数料なし）、建物の**資材**を寄付する（基準価格の1.5倍。建物を選んで「資材を寄付」。建物ごとに必要な数まで）
- 寄付すると、その村での自分の評判も上がります（寄付した発展度 1 につき 0.03）

| 建物 | 大きさ | 発展度 | ほしい資材 | 効果 |
|---|---|---|---|---|
| 依頼所 | 5×5 | 1,500 | 板材256・紙128・本32 | 依頼の数 +2、輸送依頼の数 +1 |
| 倉庫 | 9×9 | 2,500 | 原木256・丸石512・チェスト32 | 全商品の在庫の上限と補充の速さが1.5倍 |
| 宿屋 | 9×9 | 2,500 | 原木192・羊毛96・ガラス128 | ベッド4つ（市場の規模のベッドに数える）。最初に完成した時に村人が2人来る |
| 銀行 | 7×7 | 5,000 | 石レンガ512・金インゴット64・ガラス64 | 市場のレベルに関係なく銀行タブが出る。その市場ではカード払いの手数料なし。ここで預け入れ・引き出しをすると口座がこの銀行に登録され、利息が付く上限が2倍になる（建物がある間） |

- **建てる条件**：建物ごとの**発展度**と、**ほしい資材を全部寄付してあること**の両方が必要です（資材の数は設定 `development.materialCountMultiplier` で変えられます。既定は建物のデータの4倍。`development.requireMaterials` を OFF にすると資材は条件にならず、発展度を稼ぐ手段だけになります）
- **建て方**：「発展」タブで建物を選んで「**建てる**」を押します（その村での評判が「常連」以上の人だけ。同時に建設できるのは1つ）。発展度を使い、屋台から32ブロック以内の平らな空き地に足場と看板が立ち、ゲーム内2日（現実の約40分）で完成します（その場所が読み込まれている時）。建設中は、範囲の床にオレンジの点線の枠が出て、木くずが舞い、ときどき作業の音がします（設定 `development.constructionEffects` で切れます）。空気・草・花・雪の層しか無い場所にだけ建てるので、ほかの建物は壊しません
- **場所を決めたい時**：「杭をもらう」で**建設予定地の杭**を受け取り、建てたい場所（建物の正面の真ん中）に置きます。置いた時に向いていた方が建物の正面になります。杭があればそこに建て、建てられない時は理由が出ます。杭を置いた時と、杭を右クリックした時に、まだ建っていない建物が建つ場所の枠を30秒間パーティクルで表示します（緑：今建てられる／赤：建てられない。チャットに建物ごとの結果と理由）
- **市場の規模**：完成した建物が2つで +1、4つで +2（家と村人で決まる規模に足す。最大は「都市」）
- **壊れた時**：建物の中の**看板**を壊すと、その建物の効果がなくなります（ほかのブロックを壊しても効果は消えません）。「発展」タブで半分の発展度で建て直せます（別の場所に建ちます）。設定 `development.protectFacilities` を ON にすると、サバイバルでは壊せず、爆発でも壊れません
- 建物の見た目は村の種類（平原・砂漠・サバンナ・雪原・タイガ）ごとに変わります（ジャングル・沼地などは平原の見た目）。設定 `development.dimensions` でネザー・エンドを足すと、それぞれ専用の見た目になり、宿屋にはベッドを置きません（ベッドが爆発するため。効果は村人が来ることだけ）
- 完成した建物の看板を右クリックすると、交易画面がその建物のタブ（依頼所＝依頼、銀行＝銀行、ほか＝発展）で開きます。建設中の看板は、あと何分で完成するかを出します
- 発展度は時間では減りません（設定 `development.halfLifeDays` で減らせます）
- 使えるのはオーバーワールドの市場です（設定 `development.dimensions` でほかの次元も足せます）
- 数値はすべて設定の「村の発展」タブ（`development.*`）で変えられます。

### 村人との取引

- 村人から品物を**買う**時の代金は、エメラルドではなく**コイン**になります（設定 `villager.coinTrades`）。エメラルド1個＝30c で換算し、財布・コインから自動で払います。支払い欄にエメラルドを置く必要はありません（置いても使えません）。
- 取引一覧のエメラルドの上にコインでの代金が表示され、支払い欄の下に「代金 ◯c（所持 ◯c）」が出ます。
- 村人が市場の商品と同じ品を売る時は、代金が市場の買値や「基準価格の1.4倍」より安くなりません（村人から安く買って市場で売る抜け道を防ぐため。設定 `villager.minGoodsPriceMultiplier`）。
- 村人に品物を**売る**取引は今までどおりエメラルドがもらえます。需要による値上げもバニラと同じく効きます。
- **値引き**：村人ゾンビを治療した時や、村の英雄・評判による値引きは、コインの代金にも同じ割合で効きます（例：エメラルド10個→4個に値引きされた取引は 300c → 120c）。ただし、もともとエメラルド1個の取引はそれ以上安くなりません（エメラルドの交易と同じ）。また、パンや原木など市場の商品と同じ品は、上の「市場の買値・基準価格の1.4倍より安くならない」決まりが優先されるため、値引きされないことがあります。
- 行商人から買う時も同じくコインです（設定 `villager.wanderingTraderCoins` でエメラルドに戻せます）。探検家の地図やエンチャントの本など、エメラルドと別の品が要る取引は、エメラルドの分だけコインで払い、別の品は支払い欄に置きます。

### エメラルド貨

- 石切台で、エメラルドブロック1個からエメラルド貨1枚を作れます（枚数は設定で変更可、0 でレシピ無効）。
- 所持金には数えない補助コインで、**どの市場でも**10枚単位で売却できます（10枚で4c。値段は変わらず、いくらでも売れます。設定 `currency.emeraldCoinFixedPrice`・`currency.emeraldCoinUnlimited` を OFF にすると、売るほど値下がり・上限640枚になります）。
- 村人交易で大量に手に入るエメラルドを、少しずつ経済に取り込むための仕組みです。

---

## 商品一覧

同梱の商品は95種と、補助コインのエメラルド貨です。1つの市場が扱うのはこのうち10〜20種で、どれが並ぶかは市場の種類（地域・次元）と規模（レベル）で抽選されます。肉と魚は生のものだけを扱います（焼いた物は扱いません）。ほかに毎日1〜2品、その市場がふだん扱わない商品が「本日の掘り出し物」として並びます。

- **買値／売値** は平常時（相場のずれが0）の目安です。実際の価格は市場ごとのばらつき（±15%）、地域の価格倍率、品不足、売買による相場の動きで変わります。
- **在庫** は平常時の在庫（目標在庫）です。上限はその2倍（ダイヤの剣・エメラルド貨は個別に設定）。
- **消耗品** は在庫と相場が早く戻る商品です（「相場の動き方」を参照）。
- **並ぶ市場** の「オーバーワールドの全市場」は、どの地域の市場でも抽選の候補になる日用品・高級品です。「（レベルN〜）」はその規模以上の市場だけで並ぶことを表します。地元の特産として、ほかより低いレベルから並ぶ地域は個別に書いています。
- 値は設定の `goods` や `/aktradeex goods set` で変えられます。ここに載っているのは既定値です。

### 食料（12種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 小麦 | `minecraft:wheat` | 7c | 9c / 5c | 64 | ○ | 平原・サバンナ・既定 |
| パン | `minecraft:bread` | 20c | 24c / 16c | 16 | ○ | オーバーワールドの全市場・エンド |
| ニンジン | `minecraft:carrot` | 5c | 6c / 4c | 64 | ○ | オーバーワールドの全市場 |
| ジャガイモ | `minecraft:potato` | 5c | 6c / 4c | 64 | ○ | オーバーワールドの全市場 |
| リンゴ | `minecraft:apple` | 12c | 15c / 9c | 32 | ○ | オーバーワールドの全市場 |
| 生の牛肉 | `minecraft:beef` | 16c | 20c / 12c | 32 | ○ | オーバーワールドの全市場・エンド |
| 生の豚肉 | `minecraft:porkchop` | 16c | 20c / 12c | 32 | ○ | オーバーワールドの全市場・ネザー |
| 生の鶏肉 | `minecraft:chicken` | 10c | 12c / 8c | 32 | ○ | オーバーワールドの全市場 |
| 生鱈 | `minecraft:cod` | 8c | 10c / 6c | 32 | ○ | オーバーワールドの全市場 |
| 生鮭 | `minecraft:salmon` | 10c | 12c / 8c | 32 | ○ | オーバーワールドの全市場 |
| 金のニンジン | `minecraft:golden_carrot` | 70c | 84c / 56c | 16 |  | オーバーワールドの全市場（レベル3〜）・ネザー（レベル3〜） |
| 金のリンゴ | `minecraft:golden_apple` | 450c | 540c / 360c | 4 |  | オーバーワールドの全市場（レベル4〜）・ネザー（レベル4〜）・エンド（レベル4〜） |

### 植物・地域の特産（5種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| サボテン | `minecraft:cactus` | 4c | 5c / 3c | 64 |  | 砂漠 |
| スイートベリー | `minecraft:sweet_berries` | 6c | 8c / 4c | 32 | ○ | 雪原・タイガ |
| カカオ豆 | `minecraft:cocoa_beans` | 8c | 10c / 6c | 32 | ○ | ジャングル |
| スイレンの葉 | `minecraft:lily_pad` | 5c | 6c / 4c | 32 |  | 沼地 |
| 氷 | `minecraft:ice` | 6c | 8c / 4c | 32 |  | 雪原 |

### 木材（10種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| オークの原木 | `minecraft:oak_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |
| トウヒの原木 | `minecraft:spruce_log` | 8c | 10c / 6c | 64 | ○ | 雪原・タイガ |
| アカシアの原木 | `minecraft:acacia_log` | 8c | 10c / 6c | 64 | ○ | サバンナ |
| ジャングルの原木 | `minecraft:jungle_log` | 8c | 10c / 6c | 64 | ○ | ジャングル |
| マングローブの原木 | `minecraft:mangrove_log` | 8c | 10c / 6c | 64 | ○ | 沼地 |
| シラカバの原木 | `minecraft:birch_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |
| ダークオークの原木 | `minecraft:dark_oak_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |
| サクラの原木 | `minecraft:cherry_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |
| ペールオークの原木 | `minecraft:pale_oak_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |
| ポプラの原木 | `minecraft:poplar_log` | 8c | 10c / 6c | 64 | ○ | 平原・開拓地・既定 |

### 建材（4種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 丸石 | `minecraft:cobblestone` | 2c | 3c / 1c | 128 | ○ | オーバーワールドの全市場 |
| 砂 | `minecraft:sand` | 4c | 5c / 3c | 64 | ○ | 砂漠 |
| 砂岩 | `minecraft:sandstone` | 16c | 20c / 12c | 32 |  | 砂漠 |
| ガラス | `minecraft:glass` | 4c | 5c / 3c | 64 | ○ | 砂漠・開拓地 |

### 鉱物（8種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 石炭 | `minecraft:coal` | 8c | 10c / 6c | 64 | ○ | オーバーワールドの全市場・ネザー・エンド |
| 銅インゴット | `minecraft:copper_ingot` | 8c | 10c / 6c | 64 |  | オーバーワールドの全市場 |
| 鉄インゴット | `minecraft:iron_ingot` | 60c | 72c / 48c | 16 |  | オーバーワールドの全市場（レベル2〜）・開拓地・エンド |
| 金インゴット | `minecraft:gold_ingot` | 80c | 96c / 64c | 16 |  | オーバーワールドの全市場（レベル2〜）・ネザー・エンド |
| ラピスラズリ | `minecraft:lapis_lazuli` | 15c | 18c / 12c | 32 |  | オーバーワールドの全市場（レベル3〜）・エンド（レベル3〜） |
| レッドストーンダスト | `minecraft:redstone` | 10c | 12c / 8c | 32 |  | オーバーワールドの全市場（レベル2〜）・エンド（レベル2〜） |
| ダイヤモンド | `minecraft:diamond` | 420c | 504c / 336c | 4 |  | オーバーワールドの全市場（レベル4〜）・エンド（レベル4〜） |
| 黒曜石 | `minecraft:obsidian` | 30c | 36c / 24c | 16 |  | オーバーワールドの全市場（レベル3〜）・ネザー・エンド |

### モブの素材（7種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 革 | `minecraft:leather` | 20c | 24c / 16c | 32 |  | オーバーワールドの全市場・ネザー |
| 白色の羊毛 | `minecraft:white_wool` | 12c | 15c / 9c | 32 |  | オーバーワールドの全市場 |
| 糸 | `minecraft:string` | 4c | 5c / 3c | 64 |  | オーバーワールドの全市場・ネザー・エンド |
| 骨 | `minecraft:bone` | 3c | 4c / 2c | 64 |  | オーバーワールドの全市場・ネザー・エンド |
| 火薬 | `minecraft:gunpowder` | 15c | 18c / 12c | 32 |  | オーバーワールドの全市場・ネザー |
| スライムボール | `minecraft:slime_ball` | 25c | 30c / 20c | 16 |  | 沼地 |
| 腐った肉 | `minecraft:rotten_flesh` | 2c | 買えない / 1c | 64 |  | オーバーワールドの全市場・ネザー・エンド |

### 道具・装備・日用品（16種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 松明 | `minecraft:torch` | 2c | 3c / 1c | 128 | ○ | オーバーワールドの全市場・エンド |
| 本 | `minecraft:book` | 32c | 39c / 25c | 16 |  | オーバーワールドの全市場（レベル2〜） |
| 弓 | `minecraft:bow` | 14c | 17c / 11c | 8 |  | オーバーワールドの全市場（レベル2〜） |
| 盾 | `minecraft:shield` | 78c | 94c / 62c | 8 |  | オーバーワールドの全市場（レベル2〜） |
| 鉄の剣 | `minecraft:iron_sword` | 130c | 156c / 104c | 4 |  | オーバーワールドの全市場（レベル3〜） |
| ダイヤの剣 | `minecraft:diamond_sword` | 900c | 1,080c / 720c | 8 |  | オーバーワールドの全市場（レベル5〜） |
| ダイヤのヘルメット | `minecraft:diamond_helmet` | 2,250c | 2,700c / 1,800c | 4 |  | オーバーワールドの全市場（レベル5〜） |
| ダイヤのチェストプレート | `minecraft:diamond_chestplate` | 3,600c | 4,320c / 2,880c | 4 |  | オーバーワールドの全市場（レベル5〜） |
| ダイヤのレギンス | `minecraft:diamond_leggings` | 3,150c | 3,780c / 2,520c | 4 |  | オーバーワールドの全市場（レベル5〜） |
| ダイヤのブーツ | `minecraft:diamond_boots` | 1,800c | 2,160c / 1,440c | 4 |  | オーバーワールドの全市場（レベル5〜） |
| ダイヤのツルハシ | `minecraft:diamond_pickaxe` | 1,350c | 1,620c / 1,080c | 8 |  | オーバーワールドの全市場（レベル4〜） |
| ダイヤの斧 | `minecraft:diamond_axe` | 1,350c | 1,620c / 1,080c | 8 |  | オーバーワールドの全市場（レベル4〜） |
| ダイヤのクワ | `minecraft:diamond_hoe` | 900c | 1,080c / 720c | 8 |  | オーバーワールドの全市場（レベル4〜） |
| ダイヤのシャベル | `minecraft:diamond_shovel` | 450c | 540c / 360c | 8 |  | オーバーワールドの全市場（レベル4〜） |
| 鞍 | `minecraft:saddle` | 130c | 156c / 104c | 2 |  | オーバーワールドの全市場（レベル3〜） |
| 白色のベッド | `minecraft:white_bed` | 44c | 53c / 35c | 8 |  | オーバーワールドの全市場 |

### 特殊（1種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| 経験値ボトル | `minecraft:experience_bottle` | 40c | 48c / 32c | 16 |  | オーバーワールドの全市場（レベル3〜）・エンド（レベル3〜） |

### ネザー（8種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| ネザークォーツ | `minecraft:quartz` | 12c | 15c / 9c | 32 |  | ネザー |
| グロウストーンダスト | `minecraft:glowstone_dust` | 10c | 12c / 8c | 32 |  | ネザー |
| ネザーウォート | `minecraft:nether_wart` | 8c | 10c / 6c | 32 |  | ネザー |
| マグマクリーム | `minecraft:magma_cream` | 25c | 30c / 20c | 16 |  | ネザー（レベル2〜） |
| ブレイズロッド | `minecraft:blaze_rod` | 60c | 72c / 48c | 8 |  | ネザー（レベル2〜）・エンド（レベル2〜） |
| ガストの涙 | `minecraft:ghast_tear` | 150c | 180c / 120c | 4 |  | ネザー（レベル3〜） |
| 金塊 | `minecraft:gold_nugget` | 9c | 11c / 7c | 32 |  | ネザー |
| ネザライトの欠片 | `minecraft:netherite_scrap` | 1,600c | 1,920c / 1,280c | 2 |  | ネザー（レベル5〜） |

### エンド（4種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| エンダーパール | `minecraft:ender_pearl` | 60c | 72c / 48c | 8 |  | オーバーワールドの全市場（レベル4〜）・ネザー（レベル3〜）・エンド |
| シュルカーの殻 | `minecraft:shulker_shell` | 400c | 480c / 320c | 4 |  | エンド（レベル3〜） |
| コーラスフルーツ | `minecraft:chorus_fruit` | 5c | 6c / 4c | 32 | ○ | エンド |
| エンダーアイ | `minecraft:ender_eye` | 80c | 96c / 64c | 8 |  | エンド（レベル2〜） |

### 花（20種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| タンポポ | `minecraft:dandelion` | 3c | 4c / 2c | 64 | ○ | 平原・既定 |
| ポピー | `minecraft:poppy` | 3c | 4c / 2c | 64 | ○ | 平原・既定 |
| フランスギク | `minecraft:oxeye_daisy` | 4c | 5c / 3c | 64 | ○ | 平原 |
| ヒナソウ | `minecraft:azure_bluet` | 4c | 5c / 3c | 64 | ○ | 平原 |
| ヤグルマギク | `minecraft:cornflower` | 4c | 5c / 3c | 64 | ○ | 平原 |
| 赤いチューリップ | `minecraft:red_tulip` | 4c | 5c / 3c | 64 | ○ | 平原・タイガ |
| 橙色のチューリップ | `minecraft:orange_tulip` | 4c | 5c / 3c | 64 | ○ | 平原・タイガ |
| 白いチューリップ | `minecraft:white_tulip` | 4c | 5c / 3c | 64 | ○ | 平原・タイガ |
| ピンクのチューリップ | `minecraft:pink_tulip` | 4c | 5c / 3c | 64 | ○ | 平原・タイガ |
| スズラン | `minecraft:lily_of_the_valley` | 5c | 6c / 4c | 32 | ○ | 雪原・タイガ |
| ヒスイラン | `minecraft:blue_orchid` | 6c | 8c / 4c | 32 | ○ | 沼地 |
| アリウム | `minecraft:allium` | 6c | 8c / 4c | 32 | ○ | 平原 |
| サボテンの花 | `minecraft:cactus_flower` | 5c | 6c / 4c | 32 | ○ | 砂漠・サバンナ |
| ヒマワリ | `minecraft:sunflower` | 6c | 8c / 4c | 32 | ○ | 平原（レベル2〜）・サバンナ（レベル2〜） |
| ライラック | `minecraft:lilac` | 6c | 8c / 4c | 32 | ○ | 平原（レベル2〜）・サバンナ（レベル2〜） |
| バラの低木 | `minecraft:rose_bush` | 6c | 8c / 4c | 32 | ○ | 平原（レベル2〜）・サバンナ（レベル2〜） |
| ボタン | `minecraft:peony` | 6c | 8c / 4c | 32 | ○ | 平原（レベル2〜）・サバンナ（レベル2〜） |
| トーチフラワー | `minecraft:torchflower` | 40c | 48c / 32c | 8 | ○ | オーバーワールドの全市場（レベル3〜） |
| ウツボカズラ | `minecraft:pitcher_plant` | 40c | 48c / 32c | 8 | ○ | オーバーワールドの全市場（レベル3〜） |
| ウィザーローズ | `minecraft:wither_rose` | 60c | 72c / 48c | 4 | ○ | オーバーワールドの全市場（レベル4〜）・ネザー（レベル2〜） |

### 補助コイン（1種）

| 名前 | ID | 基準 | 買値 / 売値 | 在庫 | 消耗品 | 並ぶ市場 |
|---|---|---|---|---|---|---|
| エメラルド貨 | `ak_trade_expansion:emerald_coin` | 5c（10個あたり） | 買えない / 4c | 0 |  | すべての市場（商品数とは別枠） |

---

### 他の Mod との連携

- **JourneyMap**（任意）：入っていると、掲示板の「地図に登録」「この市場を登録」で市場を JourneyMap の地点として追加できます。入っていなくても、この Mod は普通に動きます（座標をチャットに出します）。

## コマンド

`/aktradeex`（別名 `/ak_trade_expansion`）。`balance`・`bank balance`・`where` 以外は OP 権限（既定はレベル2。設定 `command.permissionLevel` で変更可）が必要です。

| コマンド | 内容 |
|---|---|
| `/aktradeex balance` | 所持金を表示 |
| `/aktradeex where` | 今いる場所の情報（村の範囲内か・一番近い市場・ベッドと村人の数・ここに市場を作った場合の規模・交易所を置いた時の結果）。OP 権限不要 |
| `/aktradeex reputation` | 最寄りの市場での自分の評判（レベル・ポイント）。OP 権限不要 |
| `/aktradeex reputation set <ポイント>` | 最寄りの市場での自分の評判ポイントを設定する（テスト用） |
| `/aktradeex development` | 最寄りの市場の発展度と、発展度を多く入れた人。OP 権限不要 |
| `/aktradeex development add <発展度>` | 最寄りの市場の発展度を増やす（マイナスで減らす。テスト用） |
| `/aktradeex development complete` | 最寄りの市場の建設中の建物をすぐ完成させる（テスト用。その場所が読み込まれている時） |
| `/aktradeex where show` / `hide` | 範囲を30秒間パーティクルで表示する／表示を止める。OP 権限不要 |
| `/aktradeex give <金額>` | 自分にコインを渡す（テスト用） |
| `/aktradeex pay <金額>` | 自分の所持金から支払う（支払い処理のテスト用） |
| `/aktradeex market info` | 最寄りの市場の情報（プロファイル・レベル・ベッドと村人の数・今測った場合のレベル・全商品の価格と在庫） |
| `/aktradeex market advance <日数>` | 最寄りの市場の相場を、指定した日数分だけ回復させる（テスト用） |
| `/aktradeex market reset [ID]` | 市場の相場と在庫を初期値に戻す（商品構成・レベルはそのまま。ID 省略で最寄り） |
| `/aktradeex market reroll [ID]` | 今のデータパックとレベルで品揃えを選び直す |
| `/aktradeex market name <ID> <名前>` | 市場名を付ける・変える（`reset` で名前を消す） |
| `/aktradeex market delete <ID>` | 市場を削除（交易所は次に使われた時に割り当て直し） |
| `/aktradeex market setx <商品> <X>` | 最寄りの市場の相場のずれ X を直接設定する（テスト用） |
| `/aktradeex simulate <商品> <buy\|sell> <数>` | 取引せずに、最寄りの市場での価格の推移を表示 |
| `/aktradeex consumable list` | 消耗品として扱っている商品と、設定で追加・除外したアイテムを表示 |
| `/aktradeex consumable add\|remove <アイテム>` | アイテムを消耗品に追加する／消耗品から外す（設定ファイルに保存され、すぐに反映） |
| `/aktradeex consumable reset <アイテム>` | 追加・除外の設定を消して、データパックのタグの指定に戻す |
| `/aktradeex goods info <商品>` | 商品の値（データパックの値と設定ファイルの値）を表示 |
| `/aktradeex goods set <商品> <項目> <値>` | 商品の値を設定ファイルで上書きする（例：`/aktradeex goods set dandelion base_price 5`。すぐ反映） |
| `/aktradeex goods reset <商品> [項目]` | 上書きを消してデータパックの値に戻す |
| `/aktradeex audit villagers` | 周りの村人・行商人の取引のうち、買って市場で売ると儲かるものを一覧にする（換算額の調整用） |
| `/aktradeex audit` | 最寄りの市場で、素材を買ってクラフト（または鋳造）して売ると儲かるレシピを一覧にする（バランス確認用） |
| `/aktradeex reload` | 設定ファイルを読み直す |
| `/aktradeex bank balance` | 自分の銀行残高（OP 権限不要） |
| `/aktradeex bank set\|add <プレイヤー> <金額>` | 銀行残高を設定・増減 |

### 既存のワールドの品揃えを更新する

MOD を新しいバージョンに入れ替えた時や、データパックで商品を変えた時、**すでにある市場の品揃えは自動では変わりません**（新しく作られる市場から反映されます）。すでにある市場にも反映したい時は、次のコマンドで品揃えを選び直します。

1. OP 権限を用意する（シングルプレイなら、ワールドの「チートの許可」を ON にするか、LAN に公開してチートを ON にする）
2. 選び直したい市場の交易所の近くで、次を実行する
   - `/aktradeex market reroll` … 一番近い市場を選び直す
   - `/aktradeex market reroll <ID>` … ID を指定して選び直す（離れた市場や、まとめて直したい時）
3. 「市場 #12 の品揃えを選び直しました（…、15 商品）」と出れば完了。交易画面を開き直すと新しい品揃えになっている

- **市場の ID の調べ方**：交易画面のタイトルや `/aktradeex where` に出る「平原の村 #12」の **#の後ろの数字**が ID です。`/aktradeex market info` でも表示されます。市場の ID は 1 から順に付いているので、全部の市場を直す時は `/aktradeex market reroll 1`、`/aktradeex market reroll 2`… と順に実行します。
- **選び直すと変わるもの**：扱う商品の組み合わせ（その時の MOD とデータパックの内容で選び直す）、各商品の相場と在庫（初期値に戻る）、品不足と依頼（選び直す）
- **そのまま残るもの**：市場の名前、市場のレベル（下がらない）、交易所と商人、銀行の残高、プレイヤーの持ち物
- 品揃えは場所ごとに決まった乱数で選ぶので、MOD やデータパックの内容が変わっていなければ、選び直しても同じ品揃えになります。
- 例：0.0.11 以前のワールドの市場で、焼いた肉の代わりに生の肉・魚を並べたい時は、この方法で選び直してください。

---

## 設定

設定は `config/ak_trade_expansion.json` に保存されます（初回起動時に全項目が既定値で書き出されます）。

- **Mod Menu ＋ YetAnotherConfigLib（YACL）** を入れていれば、Mod 一覧の AK Trade Expansion の「設定」ボタンから画面で変更できます。どちらも任意で、入っていなくても Mod は動きます。
- シングルプレイでは保存した時点で反映されます。マルチプレイではサーバーの設定ファイルを編集して `/aktradeex reload` を実行します（`client.*` は各プレイヤーが自分で変更できます）。
- 範囲外の値は自動で範囲内に直されます（サーバーログに警告）。書かれていない項目は既定値になります。

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
| `currency.allowMinting` | `true` | true / false | 銅ブロック×9 → 銅貨の鋳造レシピ（鉄ブロックは下の `allowIronMinting` も必要）。反映は再起動か `/reload` の後 | `"allowMinting": false`（鋳造を禁止） |
| `currency.allowIronMinting` | `false` | true / false | 鉄ブロック×9 → 鉄貨の鋳造レシピ。アイアンゴーレムトラップで鉄がいくらでも手に入り、お金が無限に増えるため既定は OFF。`allowMinting` も ON の時だけ有効。反映は再起動か `/reload` の後 | `"allowIronMinting": true`（鉄の鋳造を許可） |
| `currency.changeOwnerOnly` | `false` | true / false | インベントリに入りきらずに足元に落ちたお釣りを、取引した本人だけが拾えるようにする。OFF なら誰でも拾える | `"changeOwnerOnly": true` |
| `currency.emeraldCoinFixedPrice` | `true` | true / false | エメラルド貨の値段を固定する（売っても値下がりせず、どの市場でも10枚で4c）。OFF で相場によって動く | `"emeraldCoinFixedPrice": false` |
| `currency.emeraldCoinUnlimited` | `true` | true / false | 市場がエメラルド貨をいくらでも買い取る（買取停止にならない）。OFF で在庫上限（640枚）まで | `"emeraldCoinUnlimited": false` |
| `currency.exchangeFee` | `0.0` | 0〜0.5 | 両替タブの手数料の割合 | `"exchangeFee": 0.02`（2%） |
| `currency.startingMoney` | `240` | 0〜1,000,000,000 | ワールドに初めて入った時に一度だけ渡す金額（c）。既定はパン10個分。財布があれば財布に、無ければコインで渡す。0 で渡さない | `"startingMoney": 1000` |
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
| `consumables.add` | `[]` | アイテムIDの一覧 | タグ `#ak_trade_expansion:consumables` に無くても消耗品として扱うアイテム。`/aktradeex consumable add` や設定画面の「追加の商品と消耗品」でも追加できる | `"add": ["minecraft:sandstone", "minecraft:white_wool"]` |
| `consumables.remove` | `[]` | アイテムIDの一覧 | タグや `add` にあっても消耗品として扱わないアイテム。`/aktradeex consumable remove` でも指定できる | `"remove": ["minecraft:cobblestone"]` |

#### 商品ごとの値（goods）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `goods` | `{}` | 商品ID → 値 | 商品定義（データパック）の値を商品ごとに上書きする。書ける項目は `base_price`（基準価格）・`elasticity`（弾力性）・`target_stock`（目標在庫）・`max_stock`（在庫上限）・`restock_per_day`（補充速度）・`consume_per_day`（消費量）・`half_life_days`（相場の半減期）・`spread`（スプレッド）・`buy`・`sell`。`buy` と `sell` を両方 `false` にするとその商品を扱わなくなる。名前空間を省くと `ak_trade_expansion:`。`/aktradeex goods set` でも変更できる。データパックに無い商品IDに `item`（アイテムID）と `base_price` を書くと**商品を追加**する（設定画面の「追加の商品と消耗品」でアイコンから追加できる） | `"goods": { "dandelion": { "base_price": 5 }, "melon": { "item": "minecraft:melon", "base_price": 12 } }` |

#### 距離（distance）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `distance.mergeRadius` | `64` | 8〜512 | この距離（ブロック）以内の交易所は同じ市場になる | `"mergeRadius": 96` |
| `distance.nearRadius` | `128` | mergeRadius〜100000 | この距離以内の市場は相場が100%連動する | `"nearRadius": 256` |
| `distance.farRadius` | `1536` | nearRadius+1〜100000 | この距離より遠い市場には相場の変化が伝わらない | `"farRadius": 3000` |
| `distance.falloffExponent` | `2.0` | 0.5〜5 | 距離による影響の減り方。1＝直線、2＝近場を出た直後に大きく下がる | `"falloffExponent": 1.0` |
| `distance.linkNetherToOverworld` | `true` | true / false | ネザー座標×8 をオーバーワールド座標とみなして、近くの市場どうしの相場を連動させる（両方で扱う商品だけ）。OFF でネザーは独立 | `"linkNetherToOverworld": false` |
| `distance.minNewMarketDistance` | `256` | 0〜10000 | クラフトした交易所で新しい市場を作れるのは、既存の市場からこの距離以上離れた場所だけ | `"minNewMarketDistance": 128` |

#### 市場（market）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `market.minGoods` | `10` | 1〜100 | レベル1の市場が扱う商品数 | `"minGoods": 8` |
| `market.maxGoods` | `20` | minGoods〜100 | レベル5の市場が扱う商品数 | `"maxGoods": 30` |
| `market.levelThresholds` | `[5, 10, 20, 40]` | 昇順の整数4つ | レベル2〜5になるベッドの数（オーバーワールド） | `"levelThresholds": [3, 8, 15, 30]` |
| `market.villagerThresholds` | `[3, 6, 12, 24]` | 昇順の整数4つ | レベル2〜5になる村人の人数（ネザー・エンド。`levelUsesVillagers` が ON ならオーバーワールドでも） | `"villagerThresholds": [2, 4, 8, 16]` |
| `market.levelUsesVillagers` | `true` | true / false | オーバーワールドのレベルを、ベッドで測ったレベルと村人で測ったレベルの低い方にする。OFF にするとベッドの数だけで測る | `"levelUsesVillagers": false` |
| `market.levelRadius` | `64` | 16〜256 | ベッド・村人を数える水平半径 | `"levelRadius": 96` |
| `market.levelCheckIntervalDays` | `1.0` | 0〜100 | 画面を開いた時、前回からこの日数以上たっていればレベルを測り直す | `"levelCheckIntervalDays": 0.5` |
| `market.shrinkOnLevelDown` | `true` | true / false | レベルが下がった時に品揃えを減らす（外した商品はレベルが戻ると元の値段・在庫で戻る）。OFF にするとレベルは最高到達値のまま | `"shrinkOnLevelDown": false` |
| `market.specialGoods` | `[1, 1, 2, 2, 2]` | 0〜10 の整数5つ | レベル1〜5の市場に毎日並ぶ「本日の掘り出し物」の数。0 で並ばない | `"specialGoods": [0, 1, 1, 2, 3]` |
| `market.specialPriceMultiplier` | `1.2` | 0.5〜5 | 掘り出し物の基準価格に掛ける倍率 | `"specialPriceMultiplier": 1.5` |
| `market.specialStockRatio` | `0.5` | 0.01〜1 | 掘り出し物の最初の在庫（目標在庫に対する割合、最低1個） | `"specialStockRatio": 0.25` |
| `market.newMarketRequires` | `"BEDS_AND_VILLAGERS"` | `"VILLAGE"` / `"BEDS"` / `"VILLAGERS"` / `"BEDS_AND_VILLAGERS"` | クラフトした交易所で新しい市場を作れる場所。1 VILLAGE＝村の構造物の中だけ、2 BEDS＝ベッドが一定数以上、3 VILLAGERS＝村人が一定数以上、4 BEDS_AND_VILLAGERS＝村人が一定数以上かつベッドが一定数以上で、ベッド≧村人（ベッドが `newMarketEnoughBeds` 個以上ならベッド＜村人でもよい）。既存の市場に加わる時と村の屋台には関係しない。ネザー・エンドではベッドの条件を外す | `"newMarketRequires": "VILLAGE"` |
| `market.newMarketMinBeds` | `3` | 0〜1000 | 新しい市場に必要なベッドの数（規模を測る半径の中） | `"newMarketMinBeds": 5` |
| `market.newMarketMinVillagers` | `3` | 0〜1000 | 新しい市場に必要な村人の数（規模を測る半径の中） | `"newMarketMinVillagers": 1` |
| `market.newMarketEnoughBeds` | `5` | 0〜1000 | 条件が `BEDS_AND_VILLAGERS` の時、ベッドがこの数以上あれば村人の方が多くても新しい市場を作れる。0 にすると常に「ベッド≧村人」が必要 | `"newMarketEnoughBeds": 8` |
| `market.renamePermission` | `"NAMER_OR_OP"` | "ANYONE" / "NAMER_OR_OP" / "OP_ONLY" | 市場名を変えられる人。NAMER_OR_OP＝名付けた人と管理者（名前の無い市場は誰でも） | `"renamePermission": "OP_ONLY"` |

#### 財布（wallet）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `wallet.capacity` | `20000` | 1000〜1,000,000,000 | 財布1つに入る金額（c） | `"capacity": 50000` |
| `wallet.autoCollect` | `true` | true / false | 財布を持っている間、拾ったコインを自動で財布に入れる | `"autoCollect": false` |

#### 銀行（bank）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `bank.minMarketLevel` | `3` | 1〜5 | このレベル以上の市場の交易画面に銀行タブが出る | `"minMarketLevel": 1`（どの市場でも銀行） |
| `bank.interestRatePerDay` | `0.0003` | 0〜0.01 | 預金の利率（ゲーム内1日あたり、毎日複利。0.0003 で 0.03%、10日で約 0.3%）。0 で利息なし | `"interestRatePerDay": 0.001` |
| `bank.interestMaxBalance` | `100000` | 0〜1,000,000,000,000 | 利息が付く残高の上限（超えた分には付かない）。0 で上限なし | `"interestMaxBalance": 1000000` |
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
| `trade.showPlacementHint` | `true` | true / false | 交易所を手に持っている間、置いた時の結果（市場に加わる／新しい市場ができる／置けない理由）を画面下に出す | `"showPlacementHint": false` |
| `trade.bulkDiscounts` | 16個以上 5%、64個以上 10% | 個数 2〜2304、割引率 0〜0.5 | まとめ買い割引の段階（購入だけ）。売り戻して儲からないよう、商品ごとに割引率の上限が自動で付く。`[]` で割引なし | `"bulkDiscounts": [{"minQuantity": 32, "rate": 0.05}, {"minQuantity": 128, "rate": 0.15}]` |
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
| `board.dealThreshold` | `0.1` | 0.01〜1 | 掲示板で、今いる市場よりこの割合以上安く買える・高く売れる品をお買い得として表示する（0.1＝10%） | `"dealThreshold": 0.2` |
| `board.maxMarkets` | `20` | 1〜100 | 掲示板に載せる市場の最大数（近い順） | `"maxMarkets": 10` |
| `quest.maxPerMarket` | `3` | 0〜10 | 1つの市場に同時に出る依頼の最大数。0 で依頼なし | `"maxPerMarket": 5` |
| `quest.refreshDays` | `1.0` | 0.1〜30 | この日数ごとに空いた枠へ新しい依頼が出る | `"refreshDays": 0.5` |
| `quest.durationDays` | `3.0` | 0.5〜30 | 依頼の期限（ゲーム内日） | `"durationDays": 5.0` |
| `quest.rewardMultipliers` | `[1.2, 1.5, 2.0]` | 各 0.1〜10（3つ） | ★1〜★3 の報酬倍率（報酬＝基準価格×数量×倍率） | `"rewardMultipliers": [1.1, 1.3, 1.6]` |
| `quest.difficultyThresholds` | `[200, 1000]` | 昇順の整数2つ | ★2・★3 になる依頼の金額（c） | `"difficultyThresholds": [300, 1500]` |
| `quest.transportPerMarket` | `1` | 0〜5 | 1つの市場に出る輸送依頼の最大数（普通の依頼とは別）。0 で輸送依頼なし | `"transportPerMarket": 2` |
| `quest.transportMinDistance` | `300` | 0〜100000 | 届け先の市場までの最短距離（ブロック） | `"transportMinDistance": 500` |
| `quest.transportMaxDistance` | `2000` | 0〜100000 | 届け先の市場までの最長距離（ブロック。ネザーは座標×8） | `"transportMaxDistance": 4000` |
| `quest.transportBaseMultiplier` | `0.2` | 0〜10 | 輸送依頼の報酬倍率の基本（報酬＝品の値段×倍率。保証金は別に戻る） | `"transportBaseMultiplier": 0.5` |
| `quest.transportPerThousandBlocks` | `0.8` | 0〜10 | 距離1000ブロックごとに報酬倍率へ足す値 | `"transportPerThousandBlocks": 1.0` |
| `quest.transportMaxMultiplier` | `2.0` | 0〜10 | 輸送依頼の報酬倍率の上限 | `"transportMaxMultiplier": 3.0` |
| `reputation.enabled` | `true` | true / false | 評判を使う。OFF にすると全員レベル0で、評判は上下しない | `"enabled": false` |
| `reputation.levelThresholds` | `[50, 150, 400, 1000, 2500]` | 昇順の整数5つ | レベル1〜5に必要なポイント | `"levelThresholds": [30, 100, 300, 800, 2000]` |
| `reputation.halfLifeDays` | `30.0` | 0〜1000 | ポイントが半分になる日数（ゲーム内）。0 で減らない | `"halfLifeDays": 0` |
| `reputation.pointsPerCoin` | `0.01` | 0〜10 | 取引1cあたりのポイント（0.01 で 100c＝1ポイント） | `"pointsPerCoin": 0.02` |
| `reputation.shortageMultiplier` | `2.0` | 1〜10 | 品不足の商品を売った時の倍率 | `"shortageMultiplier": 3.0` |
| `reputation.questPoints` | `[10, 25, 50]` | 整数3つ | ★1〜3の依頼を納めた時のポイント | `"questPoints": [5, 20, 60]` |
| `reputation.acceptedQuestMultiplier` | `1.5` | 1〜10 | 自分が受けた依頼を納めた時の倍率 | `"acceptedQuestMultiplier": 2.0` |
| `reputation.villagerTradePoints` | `1.0` | 0〜1000 | 市場の近くの村人と1回取引するごとのポイント | `"villagerTradePoints": 0.5` |
| `reputation.attackPenalty` | `20.0` | 0〜100000 | 市場の近くの村人・商人に1回ダメージを与えると減るポイント | `"attackPenalty": 50` |
| `reputation.killPenalty` | `100.0` | 0〜100000 | 市場の近くの村人・商人を倒すと減るポイント | `"killPenalty": 500` |
| `reputation.questFailPenalty` | `10.0` | 0〜100000 | 受けた依頼が期限切れになった時、★1つあたりに減るポイント | `"questFailPenalty": 0` |
| `reputation.priceBonusPerLevel` | `0.01` | 0〜0.05 | レベルごとに買値を安く・売値を高くする割合（商品ごとに上限あり） | `"priceBonusPerLevel": 0.005` |
| `reputation.rewardBonusPerLevel` | `0.04` | 0〜1 | レベルごとの依頼の報酬アップ | `"rewardBonusPerLevel": 0.1` |
| `reputation.hardQuestLevel` | `3` | 0〜5 | ★★★の依頼を納めるのに必要なレベル（0 で誰でも） | `"hardQuestLevel": 0` |
| `reputation.specialBonusLevel` | `4` | 0〜5 | 評判の掘り出し物が見えるレベル | `"specialBonusLevel": 3` |
| `reputation.specialBonusCount` | `1` | 0〜5 | 評判の高い人だけに見える掘り出し物の数 | `"specialBonusCount": 2` |
| `development.enabled` | `true` | true / false | 村の発展を使う。OFF にすると「発展」タブが消え、発展度も貯まらない（建っている建物の効果は残る） | `"enabled": false` |
| `development.dimensions` | `["minecraft:overworld"]` | 次元 ID の一覧 | 村の発展を使える次元 | `"dimensions": ["minecraft:overworld", "minecraft:the_nether"]` |
| `development.pointsPerCoin` | `1.0` | 0〜100 | コインの寄付 1c あたりの発展度 | `"pointsPerCoin": 0.5` |
| `development.requireMaterials` | `true` | true / false | ほしい資材を全部寄付するまで建物を建てられないようにする | `"requireMaterials": false` |
| `development.materialCountMultiplier` | `4.0` | 0.1〜100 | 建物のデータの資材の数に掛ける倍率（必要な数＝データの数×倍率、切り上げ） | `"materialCountMultiplier": 2.0` |
| `development.materialMultiplier` | `1.5` | 0〜10 | 資材の寄付の発展度（基準価格×個数に掛ける） | `"materialMultiplier": 2.0` |
| `development.questRewardRatio` | `0.5` | 0〜10 | 依頼の報酬のうち発展度になる割合 | `"questRewardRatio": 1.0` |
| `development.tradePointsPerCoin` | `0.02` | 0〜1 | 取引額 1c あたりの発展度 | `"tradePointsPerCoin": 0` |
| `development.halfLifeDays` | `0` | 0〜1000 | 発展度が半分になる日数（ゲーム内）。0 で減らない | `"halfLifeDays": 60` |
| `development.reputationPerPoint` | `0.03` | 0〜10 | 寄付した発展度 1 あたりに上がる評判のポイント | `"reputationPerPoint": 0` |
| `development.levelBonusThresholds` | `[2, 4]` | 昇順の整数（1〜4、4つまで） | レベルのボーナス +1、+2 … になる完成した建物の数 | `"levelBonusThresholds": [1, 2, 3, 4]` |
| `development.buildReputationLevel` | `2` | 0〜5 | 建物を選べる評判のレベル（0 で誰でも） | `"buildReputationLevel": 0` |
| `development.maxConcurrentBuilds` | `1` | 1〜4 | 1つの市場で同時に建設中にできる数 | `"maxConcurrentBuilds": 2` |
| `development.buildDays` | `2.0` | 0〜100 | 建物が完成するまでの日数（ゲーム内） | `"buildDays": 0.5` |
| `development.siteRadius` | `32` | 8〜64 | 建てる場所と杭を探す、市場の中心からの水平距離 | `"siteRadius": 48` |
| `development.siteSearchLimit` | `256` | 16〜4096 | 1回の場所探しで調べる地点の数 | `"siteSearchLimit": 1024` |
| `development.rebuildCostRatio` | `0.5` | 0〜1 | 壊れた建物を建て直す時の発展度の割合 | `"rebuildCostRatio": 1.0` |
| `development.constructionEffects` | `true` | true / false | 建設中の場所に点線の枠・木くず・作業の音を出す | `"constructionEffects": false` |
| `development.protectFacilities` | `false` | true / false | 完成した建物をサバイバルで壊せない・爆発で壊れないようにする | `"protectFacilities": true` |
| `development.warehouseStockMultiplier` | `1.5` | 1〜10 | 倉庫：在庫上限の倍率 | `"warehouseStockMultiplier": 2.0` |
| `development.warehouseRestockMultiplier` | `1.5` | 1〜10 | 倉庫：補充の速さの倍率 | `"warehouseRestockMultiplier": 2.0` |
| `development.questHallExtraQuests` | `2` | 0〜10 | 依頼所：増える依頼の枠 | `"questHallExtraQuests": 3` |
| `development.questHallExtraTransport` | `1` | 0〜5 | 依頼所：増える輸送依頼の枠 | `"questHallExtraTransport": 2` |
| `development.innVillagers` | `2` | 0〜10 | 宿屋：最初の完成で来る村人の数 | `"innVillagers": 4` |
| `development.bankInterestMaxMultiplier` | `2.0` | 1〜100 | 銀行：登録した口座の利息の上限の倍率 | `"bankInterestMaxMultiplier": 3.0` |
| `demand.maxShortages` | `2` | 0〜10 | 1つの市場で同時に品不足になる商品の最大数。0 で無効 | `"maxShortages": 0`（品不足なし） |
| `demand.durationDays` | `3.0` | 0.5〜30 | 品不足の商品を選び直す間隔（ゲーム内日） | `"durationDays": 7.0` |
| `demand.minMultiplier` | `1.5` | 1〜5 | 品不足の商品の基準価格に掛ける倍率の下限 | `"minMultiplier": 1.2` |
| `demand.maxMultiplier` | `2.0` | minMultiplier〜5 | 品不足の商品の基準価格に掛ける倍率の上限 | `"maxMultiplier": 1.5` |

#### 村人（villager）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `villager.coinTrades` | `true` | true / false | 村人から買う時の代金をコインにする（財布・コインから自動で払う。エメラルドでは払えなくなる）。行商人は下の項目で切り替え | `"coinTrades": false` |
| `villager.wanderingTraderCoins` | `true` | true / false | `coinTrades` が ON の時、行商人から買う代金もコインにする | `"wanderingTraderCoins": false` |
| `villager.minGoodsPriceMultiplier` | `1.4` | 0〜10 | 村人・行商人が市場の商品と同じ品を売る時の代金の下限＝基準価格 × この値 × 個数（近くに市場が無くても効く）。0 で無効 | `"minGoodsPriceMultiplier": 1.2` |
| `villager.emeraldPrice` | `30` | 1〜10000 | 村人の代金をコインにする時、エメラルド1個を何cとするか。近くの市場がその品を売っていれば、市場の買値より安くはならない | `"emeraldPrice": 50` |

#### その他（log / command / vanilla）

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `log.trades` | `true` | true / false | 誰が何をいくらで売買したか（銀行の出し入れも）をサーバーログに出す | `"trades": false` |
| `command.permissionLevel` | `2` | 0〜4 | `/aktradeex` の管理用コマンドに必要な権限レベル（2＝OP） | `"permissionLevel": 3` |
| `vanilla.disableVillagerTrades` | `false` | true / false | バニラの村人との交易をできなくする | `"disableVillagerTrades": true` |

#### 表示（client）※各プレイヤーのクライアント側の設定

| キー | 既定値 | 範囲 | 説明 | 設定例 |
|---|---|---|---|---|
| `client.showDebugTooltip` | `false` | true / false | 交易画面で商品の行にマウスを乗せると、基準価格・今の倍率・在庫・値段と在庫が戻るまでの時間を出す（設定画面では「相場の詳しい情報を表示」） | `"showDebugTooltip": true` |
| `client.balancePosition` | `"INVENTORY_RIGHT"` | "INVENTORY_RIGHT" / "BELOW_SELL_SLOT" / "TOP_RIGHT" | 交易画面の所持金の表示位置（インベントリの右／売却枠の下／右上） | `"balancePosition": "TOP_RIGHT"` |
| `client.sellAllExclude` | `[]` | アイテムIDの一覧 | 「まとめて売る」で売らないアイテム（確認の枠で行をクリックすると追加・削除される） | `"sellAllExclude": ["minecraft:diamond"]` |
| `client.sellAllInclude` | `[]` | アイテムIDの一覧 | 道具・防具（最初から除外）のうち、「まとめて売る」で売るアイテム | `"sellAllInclude": ["minecraft:bow"]` |

詳しい計算方法や各項目の背景は、設計書（`docs/trade-mod-design.md`）の G章を参照してください。

---

## データパックでのカスタマイズ

商品と品揃えはデータパックで変更・追加できます。`/reload` で再読み込みされ、既存の市場にも価格の変更が反映されます（商品の構成は変わりません）。

値段や在庫だけを変えたい時は、データパックを作らなくても、設定ファイルの `goods` か `/aktradeex goods set` で商品ごとに上書きできます（上の「設定」を参照）。

| 種類 | 置き場所 |
|---|---|
| 商品定義 | `data/<namespace>/ak_trade_expansion/goods/<名前>.json` |
| 市場プロファイル | `data/<namespace>/ak_trade_expansion/market_profile/<名前>.json` |
| 村の建物 | `data/<namespace>/ak_trade_expansion/facility/<名前>.json`（建物の形は `data/<namespace>/structure/facility/<見た目>/<structure>.nbt`） |

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

村の建物の定義では、種類（`kind`：`quest_hall`／`warehouse`／`inn`／`bank`。種類ごとに1つ）、並び順（`order`）、タブのアイコン（`icon`）、必要な発展度（`cost`）、建物の形の名前（`structure`）、ほしい資材（`wanted`：`item` か `tag`、`count`、商品でない品の値段 `value`）を指定します。建物の形は北向き（`z = 0` の面が正面、扉は幅の真ん中）で、中に「施設の看板」を1つ置きます。見た目は村の種類（`plains` など）ごとのフォルダから選び、無ければ `plains` を使います。

市場プロファイルでは、条件（`dimension` / `biome` / `structure` / `villager_type` / `origin` / `distance_from_spawn` / `all_of` / `any_of` / `not`）、抽選プール（重み・`min_level`）、必ず扱う商品（`always`）、価格倍率（`price_modifiers`）を指定します。書式の詳細は設計書 D-2 を参照してください。同梱データ（商品95種（花20種を含む）＋エメラルド貨、プロファイル12種）は `Source/TradeExpansion/src/main/resources/data/ak_trade_expansion/ak_trade_expansion/` にあります。

---

## 開発

### 必要なもの

- JDK 25 以上（JDK 26 でも `release 25` 指定でビルドできることを確認済み）
- IntelliJ IDEA など（Fabric の Loom を使う Gradle プロジェクト）

### よく使うコマンド

`Source/TradeExpansion` に移動してから実行します（下の `build/`・`run/` もこのフォルダの中です）。

| コマンド | 内容 |
|---|---|
| `build.bat` | **普段のビルド**。`gradle.properties` のバージョンの末尾（とこの README の表記）を1つ上げてから（0.1.2 → 0.1.3）`gradlew build` を実行し、テストと Mod の jar（`build/libs/ak_trade_expansion-<version>.jar`）を作る。配布用 zip は作らない。`build.bat nobump` で上げずにビルド、残りの引数（`--offline` など）は Gradle に渡す |
| `release.bat` | **配布用のビルド**。`gradle.properties` のバージョンの真ん中を1つ上げて末尾を0に戻してから（0.1.3 → 0.2.0。この README の表記も）`gradlew build releaseZip` を実行し、jar と**配布用 zip**（`build/distributions/AKTradeExpansion-<version>.zip`：jar・導入方法 README.txt・LICENSE）を作る。`release.bat nobump` で上げずにビルド、残りの引数（`--offline` など）は Gradle に渡す |
| `./gradlew build` | バージョンを上げずにビルドする（`build.bat nobump` と同じ）。配布用 zip は作らない |
| `./gradlew releaseZip` | 今のバージョンのまま配布用 zip だけを作る |
| `./gradlew test` | 単体テスト（ゲームを起動せずに実行できる） |
| `./gradlew runClient` | 開発用クライアントを起動（Mod Menu と YACL も開発用に読み込まれる） |
| `./gradlew runServer` | 開発用サーバーを起動 |

- `runServer` を初めて動かす時は、`run/eula.txt` を `eula=true` にする必要があります（[Minecraft EULA](https://aka.ms/MinecraftEULA) に同意したことになります）。
- 開発用クライアントからサーバーに接続するには、`run/server.properties` で `online-mode=false`、`white-list=false` にします。
- プロジェクトを OneDrive の同期フォルダに置くと、Gradle が `Failed to clean up stale outputs` で失敗することがあります。その場合は `build` フォルダを削除して再実行してください。

### 構成

場所はすべて `Source/TradeExpansion/` からの相対パスです（設計書は直下の `docs/`）。

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
| `src/main/java/.../entity/` | 商人NPC |
| `src/main/java/.../command/` | 管理コマンド（`/aktradeex`） |
| `src/main/resources/data/ak_trade_expansion/structure/` | 村の屋台の建物（構造物NBT。`tools/structures/genstall.js` で生成）と村の発展の建物（`facility/<見た目>/`、`tools/structures/genfacility.js` で生成） |
| `src/client/java/...` | 交易画面などクライアント専用のコード |
| `tools/structures/genstall.js`・`tools/structures/genfacility.js`・`tools/docs/goodslist.js`・`tools/icon/genicon.js` | 村の屋台の建物の生成、村の発展の建物の生成（`node tools/structures/genfacility.js src/main/resources/data/ak_trade_expansion/structure/facility`）、この README の「商品一覧」の生成（リポジトリ直下で `node Source/TradeExpansion/tools/docs/goodslist.js .`）、Mod のアイコンの生成 |
| `build.bat`・`release.bat`・`tools/version/bump.ps1` | 普段のビルド（末尾を上げる）・配布用のビルド（真ん中を上げる）と、バージョンを上げる処理（下の「よく使うコマンド」） |
| `src/dist/README.txt` | 配布用 zip に入れる導入方法（`@VERSION@` がバージョンに置き換わる） |
| `src/test/java/...` | 単体テスト（価格・まとめ買い割引・取引単位・距離・コイン・鋳造の枚数・財布・市場レベル・商品の抽選・依頼の計算・評判・預金の利息・市場名・交易画面の大きさ・設定ファイル・同梱データの読み込み）。87件 |

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
| 14 | 村人交易のコイン化（14a 村人・14b 行商人と特殊な取引・14c 抜け道対策） | ✅（ゲーム内の確認は進行中） |

---

## ライセンス

**GNU Lesser General Public License v2.1 のみ（LGPL-2.1-only）** です。全文はリポジトリ直下の [LICENSE](LICENSE) を参照してください。
