# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 概要

Minecraft Java Edition 26.3 向けの Fabric Mod「AK Trade Expansion」（Mod ID `ak_trade_expansion`、パッケージ `com.akf.tradeexpansion`）。コインと屋台で取引する、相場が動く経済を追加する。Java 25、Fabric Loader 0.20.1、Fabric API。Minecraft 26.3 は難読化されていないので Mixin の refmap は無い（`-proc:none`）。

Gradle プロジェクトは `Source/TradeExpansion/` にある。リポジトリ直下には文書だけがある。

**ユーザーへの返答は必ず日本語で書く**（ユーザーは英語が読めない）。

## コマンド（`Source/TradeExpansion/` で実行）

| コマンド | 内容 |
|---|---|
| `build.bat` | 普段のビルド。`gradle.properties` と README.md のバージョンの末尾を上げ（0.1.1 → 0.1.2）、`gradlew build`（テスト＋jar）を実行する。`build.bat nobump` なら上げない。`build.bat gametest` ならサーバーのゲーム内自動テスト（画面なし、約1分）も実行してチェックリストに記録する（`nobump` と併用可） |
| `release.bat` | 配布用。バージョンの真ん中を上げ（0.1.3 → 0.2.0）、jar と `build/distributions/AKTradeExpansion-<version>.zip` を作る。**ユーザーに確認してから実行する** |
| `./gradlew test` | 単体テストだけ実行（ゲームは起動しない） |
| `./gradlew test --tests com.akf.tradeexpansion.core.PriceModelTest` | 1クラスだけ実行 |
| `./gradlew runClient` / `runServer` | 開発用のクライアント／サーバー（ユーザーが操作するもの） |
| `autotest.bat` | ゲーム内の自動テスト（サーバー → クライアント → Mod Menu・YACL なしのクライアント）を実行し、結果を `docs/release/ingame-checklist.md` と `autotest-report.md` に書き込む。`autotest.bat server` / `client` / `minimal` でそれだけ。クライアントはゲームのウィンドウが開く（数分、操作しない）。`-Pautotest.only=2-10+stall` で項目番号かテスト名で絞る |

- Git Bash（Bash ツール）から .bat を実行するときは、フルパスで `cmd` に渡す（`build.bat` とだけ書くと中の `gradlew.bat` が見つからず失敗する）。例：`Source/TradeExpansion` で `cmd //c "$(cygpath -w "$PWD/build.bat") --offline -q"`。`gradlew` を直接使うときも同じ形（`cmd //c "$(cygpath -w "$PWD/gradlew.bat") test --offline -q"`）。
- バージョンの上げ方は `tools/version/bump.ps1`。手で書き換えない。
- OneDrive の同期フォルダで `Failed to clean up stale outputs` が出たら `build` フォルダを消して再実行する。
- README の「商品一覧」は生成物。商品データを変えたら、リポジトリ直下で `node Source/TradeExpansion/tools/docs/goodslist.js .` を実行して作り直す。村の屋台の構造物 NBT は `tools/structures/genstall.js`、進捗の JSON と言語ファイルの訳は `tools/advancements/genadv.js`（表を直して `Source/TradeExpansion` で実行）、Mod のアイコンは `tools/icon/genicon.js`、荷箱のテクスチャは `tools/icon/gencrate.js`、公開ページ用のサムネイルは `tools/thumbnail/genthumb.ps1` で生成する（pwsh で実行する。Windows PowerShell 5 では日本語が化ける。`release.bat` も最後に作り直す）。
- .bat ファイルは CRLF で保存する。バックスラッシュを含むスクリプトをシェルのヒアドキュメントで書くと崩れることがあるので、ファイルに書いてから実行する。

## アーキテクチャ

- **ソースセットの分割**（`splitEnvironmentSourceSets`）：`src/main` はサーバーとクライアントの共通コード、`src/client` はクライアント専用コード（画面、描画、JourneyMap／JEI／REI／Mod Menu／YACL の連携）。`src/main` からクライアントのクラスを参照してはいけない。YACL・Mod Menu・JourneyMap・JEI・REI は compileOnly の任意依存。連携先の型は `client/compat/<mod>/` だけで使い、その Mod の entrypoint（`fabric.mod.json`）から読み込ませる（入っていない時はクラスが読み込まれない）。
- **`core/` は Minecraft に依存しない純粋な計算**（価格モデル、距離による減衰、コイン、利息、評判、依頼、市場レベル、商品の抽選）。単体テストはほぼここに対するもの（`src/test`）。新しい計算式はまずここに置いてテストを書く。
- **サーバーが正、クライアントはプレビュー**：取引の成否と状態の変更はすべてサーバーで行う。`MarketSnapshotS2C` は価格そのものではなく計算に使うパラメータ（B・τ・X・S など）を送り、クライアントは同じ `PriceModel` で価格と時間による回復を計算して表示する。確定時にサーバーが計算し直し、期待額と違えば `PRICE_CHANGED` を返す。そのため価格の式を変えるときは、サーバーとクライアントの両方の挙動に影響する。スナップショットは見ている人ごとに作る（評判の効果が入るため）。
- **パケット**は `network/` にまとめ、`ModPayloads` で登録する（C2S は要求、S2C は結果・スナップショット）。受け取った処理で、画面の市場 ID、屋台までの距離、数量の範囲を必ず検証する。一覧は設計書 F-2。
- **保存**：`SavedData` は2つ。`ak_trade_expansion:markets`（`MarketRegistry`：全次元の市場、商品の状態、依頼、品不足、市場名）と `ak_trade_expansion:bank`（口座）。Codec の保存項目名は snake_case。新しく足す項目は省略可能にし、無いときは既定値にして互換を保つ（`schema_version` は簡単には上げない）。財布の残高はアイテムのデータコンポーネントに持つ。
- **相場は遅延評価**：時間による回復は、市場に触れたときに経過時間からまとめて計算する。市場の時計は設定 `recovery.clock`（`MarketClock`）による。
- **市場**：半径64ブロック以内の交易所は1つの市場になる。新しい市場の作成条件は `SettlementRule` と `MarketFactory`、相場の波及は `Propagation`。
- **データ駆動**：商品の定義と市場プロファイル（条件、抽選プール、価格倍率）は `src/main/resources/data/ak_trade_expansion/ak_trade_expansion/{goods,market_profile}/` のデータパック。読み込みは `data/`。同梱データの読み込みは `BundledDataTest` で検証する。
- **設定**：`config/ak_trade_expansion.json`（`config/`）。表示に必要な値は `ConfigSyncS2C` でクライアントに同期する。設定を足したら `TradeExpansionConfig`（Codec）と `ConfigValidator`（範囲の補正）に加えて、README の設定値一覧、設計書 G章、YACL の画面（`src/client/.../config`）、言語ファイルも合わせて直す。
- **言語ファイル**：`assets/ak_trade_expansion/lang/` の `ja_jp.json` と `en_us.json` の両方に同じキーを足す。
- **ゲーム内の自動テスト**（`src/gametest`、別の Mod `ak_trade_expansion_test`。配布 jar には入らない）：Loom の `fabricApi.configureTests` で `runGameTest`（サーバー、`server/ServerCases.java`）と `runClientGameTest`（クライアント、`client/ClientChecklistTests.java`。ケースは `ClientTradeCases`・`ClientConfigCases` にも）を作る。`runClientGameTestMinimal` は Mod Menu・YACL を外したクライアント（`build.gradle` で追加、チェックリスト 1-1）で、YACL を使うケースは `ClientConfigCases` にまとめてある（YACL なしでも `ClientChecklistTests` を読み込めるように）。各テストは `ingame-checklist.md` の項目番号と、一部だけ確かめる時はその範囲（日本語）を持ち、結果は `build/autotest/*-results.json` → `tools/checklist/record.js` でチェックリストに書き込む。サーバーの項目は1つのゲームテストの中で順に実行する（市場がワールド全体で共有されるため）。Minecraft 標準の仮プレイヤーはクリエイティブ固定なので `ServerCtx.player` を使う。クライアントのテストは日本語で実行し、画面の文字の重なり・はみ出しを自動で判定する（`TextRecorder`・`LayoutCheck`）。サーバーのテストは仮のプレイヤーに届いたパケットで結果コードやチャットを判定でき、不正な要求のテスト（`ServerRequestCases`）もある。方針・章ごとの対応表・書く時の注意（26.3 の左クリックは SDL の 1 など）は `docs/release/autotest-plan.md`。`runGameTest` は Loom の既定では `check` に入るが、`build.gradle` で外している

## 文書と運用ルール

- 仕様の正本は `docs/trade-mod-design.md`（章 A〜R、`v0.xx` の版表記は設計書の版で、Mod のバージョンとは別）と `docs/goods-design.md`。仕様を変えたら設計書も更新する（先頭の版と、付録B の改版履歴も）。README は遊ぶ人向けの説明で、ソースと一致させる。
- TODO は `docs/TODO`（Todo+ 書式：`☐` と `✔ ... @done(yy-mm-dd HH:MM)`）。README や設計書には書かない。
- 0.1.0 より後に入れた、遊ぶ人に見える変更は `docs/release/CHANGES.md` の「未リリース」に、遊ぶ人向けの言葉で書く（追加、変更、修正。設定を足したら設定名も書く。公開前の機能を直した時は、その機能の「追加」の説明を書き直す）。短い版 `docs/release/CHANGES-short.md`（投稿ページの更新履歴用、1つの変更を1行）も合わせて直す。`release.bat` を実行したら、両方の「未リリース」の見出しをそのバージョンと日付に変え、新しい「未リリース」を上に作る。
- ゲーム内で確認が必要な項目は `docs/release/ingame-checklist.md` に足す（番号は重ねない。記録スクリプトが番号で行を探す）。自動で確かめられるものはテストも足し、`autotest-plan.md` の表を直す。チェックリストの〔自動 ✔〕〔一部自動 ✔〕〔自動 ✘〕の注記は `record.js` が書くので手で直さない。`autotest-report.md` は生成物
- 作業の終わりは `build.bat` → コミット（push はしない）。`release.bat` は聞いてから実行する。
- ビルドとコミットのたびに新しいセッションで続ける運用なので、やり残したことや次の作業は会話ではなく `docs/TODO` に残す。
- 文書とコミットメッセージは日本語で書く。コードのコメントは英語で書く（既存のコードに合わせる）。
