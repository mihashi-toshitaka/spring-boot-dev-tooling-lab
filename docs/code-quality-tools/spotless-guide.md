# Spotless ガイド

## 1. Spotless とは

Spotless は、複数のフォーマッターやテキスト変換を Gradle タスクとして統一するツールです。`spotlessCheck` で差分の有無を検査し、`spotlessApply` で安全に自動整形します。

Checkstyle が規約違反を報告するのに対し、Spotless は主に機械的に決定できる書式を自動修正します。レビューでは書式ではなく設計や挙動へ集中できるようになります。

## 2. このプロジェクトでの構成

~~~kotlin
plugins {
    id("com.diffplug.spotless") version "8.8.0"
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        ktlint()
    }
}
~~~

| 対象 | Formatter step | タスク例 |
| --- | --- | --- |
| Java ソース | Palantir Java Format | `spotlessJavaCheck`、`spotlessJavaApply` |
| Gradle Kotlin DSL | ktlint | `spotlessKotlinGradleCheck`、`spotlessKotlinGradleApply` |
| 全対象 | 上記すべて | `spotlessCheck`、`spotlessApply` |

~~~bash
./gradlew spotlessCheck
./gradlew spotlessApply
~~~

`spotlessCheck` は `check` に含まれます。`spotlessApply` はファイルを書き換えるため、実行後に必ず `git diff` を確認します。

## 3. Spotless の基本モデル

Spotless は「対象ファイル」と、順番に適用する `FormatterStep` の組み合わせです。

~~~kotlin
spotless {
    format("misc") {
        target("*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
~~~

各 step は文字列を受け取り、整形後の文字列を返します。順序によって結果が変わるため、意味のある順に並べます。Spotless は複数回適用しても結果が変わらない冪等性を重視しています。

## 4. 対象と除外

~~~kotlin
java {
    target("src/**/*.java")
    targetExclude("**/generated/**", "**/build/**")
    palantirJavaFormat()
}
~~~

- `target`: 対象を glob で指定する
- `targetExclude`: 生成コードや外部から取り込んだコードを除外する
- 対象は必要なソースへ限定し、`build/` や vendor コードを誤って書き換えない
- 除外理由が分かるように生成先ディレクトリを明確にする

## 5. Java formatter の選択

Spotless は formatter 本体ではなく、各 formatter を統一して呼び出す基盤です。

### Palantir Java Format

本プロジェクトで使用しています。チームが細かな空白ルールを個別設定するのではなく、formatter の決定的な出力へ統一します。

~~~kotlin
java {
    palantirJavaFormat("<version>")
}
~~~

バージョンを明示すると更新時の差分を管理しやすくなります。formatter の更新は広範囲の差分を生むことがあるため、機能変更とは別のコミットで行います。

### Google Java Format

~~~kotlin
java {
    googleJavaFormat("<version>")
}
~~~

Google Java Style に近い決定的フォーマットが必要な場合の選択肢です。

### Eclipse formatter

~~~kotlin
java {
    eclipse("<version>").configFile("config/eclipse-formatter.xml")
}
~~~

改行幅などを組織独自に細かく設定したい場合に向きます。その分、設定ファイルの保守が必要です。

プロジェクト内で複数の Java formatter を同じ対象へ重ねないでください。

## 6. よく使う FormatterStep

~~~kotlin
format("misc") {
    target("*.md", "*.yml", "*.yaml", ".gitignore")
    trimTrailingWhitespace()
    leadingTabsToSpaces(4)
    endWithNewline()
}
~~~

| Step | 用途 |
| --- | --- |
| `trimTrailingWhitespace()` | 行末空白を削除 |
| `endWithNewline()` | ファイル末尾の改行を保証 |
| `leadingTabsToSpaces(n)` | 行頭タブを空白へ変換 |
| `replace()` | 固定文字列を置換 |
| `replaceRegex()` | 正規表現で置換 |
| `licenseHeader()` | ライセンスヘッダーを挿入・検査 |
| `custom()` | 独自の文字列変換を定義 |

独自 step は強力ですが、構文を理解せず文字列置換するため、コードを壊さない範囲に限定します。

## 7. ライセンスヘッダー

~~~kotlin
java {
    palantirJavaFormat()
    licenseHeaderFile("config/license-header.txt")
}
~~~

年を自動更新する必要がある場合は `licenseHeaderFile` の delimiter や年の正規表現を検討します。生成コード、package-info、module-info の扱いも事前に確認します。

## 8. Ratchet による段階導入

既存コード全体の一括整形を避けたい場合、指定した Git ref との差分だけを対象にできます。

~~~kotlin
spotless {
    ratchetFrom("origin/main")
}
~~~

注意点:

- CI で基準 ref が取得できる clone depth にする
- 開発者環境にも同じ ref が存在するようにする
- ratchet は恒久的に未整形コードを放置する仕組みではなく、段階移行として管理する
- formatter 更新時の全面差分には別途計画が必要

## 9. VS Code 連携

このプロジェクトは Spotless Gradle 拡張を推奨し、Java の既定 formatter を無効にしています。

~~~json
{
  "java.format.enabled": false,
  "[java]": {
    "editor.formatOnSave": true,
    "spotlessGradle.diagnostics.enable": true,
    "spotlessGradle.format.enable": true,
    "editor.defaultFormatter": "richardwillis.vscode-spotless-gradle",
    "editor.codeActionsOnSave": {
      "source.fixAll.spotlessGradle": "explicit"
    }
  }
}
~~~

VS Code、Java Extension、Spotless で複数の formatter が同時に動くと結果が競合します。保存時整形の担当を Spotless に一本化します。

## 10. 運用方法

### 通常の変更

~~~bash
./gradlew spotlessApply
git diff
./gradlew spotlessCheck
~~~

### formatter のバージョン更新

1. 作業ツリーをクリーンにする
2. formatter のリリースノートを確認する
3. バージョンだけを更新して `spotlessApply` を実行する
4. 整形差分だけのコミットにする
5. 機能変更を rebase して競合を解消する

### CI

CI ではファイルを書き換える `spotlessApply` ではなく、`spotlessCheck` または `check` を実行します。

## 11. トラブルシューティング

### spotlessCheck が失敗する

`./gradlew spotlessApply` を実行し、差分を確認します。意図しないファイルが変わる場合は `target` と `targetExclude` を見直します。

### 保存するたびに書式が戻る

VS Code の既定 formatter、Java formatter、Spotless のうち複数が有効になっていないか確認します。

### CI とローカルで改行差分が出る

`.gitattributes`、Git の line ending 設定、Spotless の line ending 方針を確認します。Spotless は通常 Git 属性から改行を推測します。

### formatter が非冪等と報告される

複数 step の順序や相互作用を確認します。独自 step を一つずつ外し、どの組み合わせで二回目の結果が変わるか切り分けます。

## 12. 参考資料

- [Spotless](https://github.com/diffplug/spotless)
- [Spotless Gradle Plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [Palantir Java Format](https://github.com/palantir/palantir-java-format)
- [ktlint](https://pinterest.github.io/ktlint/)

