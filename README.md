# Gerbil｜Pixel11シリーズ特化の汎用DATビューワー
最新のGooglePixelに最適化したMaterial3デザインの汎用DATビューワーです（Pixel11Proで開発）

### インストール
#### Playストア
<a href="https://play.google.com/store/apps/details?id=jp.miyavi.android.gerbil"><img src="docs/store/play-card.png" width="560" alt="Gerbil｜ローカルAIを使った専ブラ（Google Play）"></a>

#### Github
[最新リリースをダウンロード](https://github.com/gloria0021/gerbil/releases/latest/download/gerbil-release.apk)


### 動作環境

- Android13以上
- AI機能は端末でGeminiNano（AICore）が使える場合だけ表示されます

### 対応形式

| 形式 | 読む | 書く |
|---|---|---|
| 2ch 互換（bbsmenu・subject.txt・dat・bbs.cgi） | ○ | ○ |
| したらば形式（subject.txt・rawmode.cgi・write.cgi） | ○ | ○ |

## スレ表示・閲覧・書き込み

<img src="docs/screenshots/thread-url.png" width="220" alt="スレのURLを入力する画面"> <img src="docs/screenshots/thread-view.png" width="220" alt="レス本文と返信ボタンを表示したスレ画面">

<img src="docs/screenshots/reading-list.png" width="220" alt="閲覧中のスレ一覧"> <img src="docs/screenshots/board-list.png" width="220" alt="カテゴリごとに3列で並ぶ板一覧">


## 設定

<img src="docs/screenshots/display-settings.png" width="220" alt="外観・フォント・透過効果などの表示設定"> <img src="docs/screenshots/ai-settings.png" width="220" alt="自動要約やミュート条件などのAI設定">

## AI機能

### AIモデルの取得
AIモデルはローカルモデルを使用します。AICoreアプリ経由でインストールできます。

AI機能の利用は対応している端末のみ。

<img src="docs/screenshots/ai-model-prompt.png" width="220" alt="ローカルAIモデルの取得案内"> <img src="docs/screenshots/ai-model-download.png" width="220" alt="ローカルAIモデルのダウンロード進捗">

### ジャービルに質問
アプリの使い方に対する質問はこのチャットでしてください。

<img src="docs/screenshots/gerbil-chat01.png" width="220" alt="アプリ内チャットでジャービルに質問した例"> <img src="docs/screenshots/gerbil-chat02.png" width="220" alt="ジャービルが掲示板の追加方法を案内し、掲示板管理を開くボタンを表示した例">

### AI要約・質問
スレを分割で要約します。分割要約にしているのはローカルAIの性能の問題。

<img src="docs/screenshots/thread-summary.png" width="220" alt="スレのレスを範囲ごとに分割した要約"> <img src="docs/screenshots/thread-question.png" width="220" alt="スレについて質問し、参照レス付きで回答を得た例">

### AIミュート
指定したルールでレスをミュートします。ローカルAIの性能の問題で精度が低いため実験中。

<img src="docs/screenshots/ai-mute.png" width="220" alt="AIミュートで対象のレス本文がぼかされた画面">

## 今後の予定
### クラウドAI対応
要約やAIミュートはGPT6.1Luna辺りの格安フロンティアモデルを使えば低コストで求めている精度の機能が得られます。QOL上がるのでこの機能は入れたい


## プライバシーポリシー
[プライバシーポリシー](docs/privacy-policy.md)（日本語 / English）

## ライセンス
アプリが使っているライブラリ・アイコン・フォントのライセンスは、アプリの「設定 → オープンソースライセンス」で確認できます。
