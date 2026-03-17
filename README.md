# 中文ピンイン変換アプリ

中国語の画像を撮影・選択すると、テキストを認識してピンイン（声調記号付き）に変換し、クリップボードに自動コピーするAndroidアプリです。

## 機能

| 機能 | 説明 |
|---|---|
| 画像選択 | ギャラリーから画像を選択 |
| カメラ撮影 | カメラで直接撮影 |
| 範囲クロップ | タッチ操作で認識範囲を自由に選択 |
| 中国語OCR | Google ML Kit（中国語専用モデル）でテキスト認識 |
| ピンイン変換 | 声調記号付きピンイン（ā á ǎ à）に変換 |
| ルビ表示 | 漢字の上にピンインをルビとして表示 |
| 自動コピー | 認識完了と同時にピンインをクリップボードへ自動コピー |

## クロップ操作

| 操作 | 動作 |
|---|---|
| 枠の内側をドラッグ | クロップ枠を移動 |
| 四隅の赤いハンドルをドラッグ | 枠を斜め方向にリサイズ |
| 上下左右の白いハンドルをドラッグ | 枠を縦・横方向にリサイズ |
| 「リセット」ボタン | クロップ枠を画像全体に戻す |
| 「この範囲で認識」ボタン | 選択範囲でOCR認識を開始 |

## 技術スタック

- **言語:** Kotlin
- **OCR:** Google ML Kit Text Recognition（中国語）
- **ピンイン変換:** pinyin4j
- **UI:** ViewBinding + CardView + MaterialComponents
- **最小SDK:** 26 (Android 8.0)
- **ターゲットSDK:** 34 (Android 14)

## ビルド方法

```bash
# Android SDK と Java 17 が必要
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleDebug
```

APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

## 権限

- `CAMERA` — カメラ撮影
- `READ_EXTERNAL_STORAGE` — ギャラリーアクセス（Android 12以下）
- `READ_MEDIA_IMAGES` — ギャラリーアクセス（Android 13以上）
