# OpenRewrite ガイド

## 1. OpenRewrite とは

OpenRewrite は、ソースコードの意味と構文を保ちながら検索・変換する自動リファクタリング基盤です。Java だけでなく、Gradle、Maven、XML、YAML、properties などを扱える recipe が提供されています。

主な用途は次のとおりです。

- Java や Spring Boot のバージョン移行
- 非推奨 API から後継 API への置換
- コード品質ルールの一括適用
- 依存関係や build script の更新
- 組織独自 API の移行
- コードベースの調査と data table 出力

OpenRewrite は静的解析結果を表示するだけでなく、`rewriteRun` でソースを書き換えます。そのため、適用前の preview と適用後のテストが重要です。

## 2. このプロジェクトでの構成

~~~kotlin
plugins {
    id("org.openrewrite.rewrite") version "7.37.0"
}

rewrite {
    // activeRecipe("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
    activeRecipe("org.openrewrite.java.migrate.UpgradeToJava21")
    activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
}

dependencies {
    rewrite("org.openrewrite.recipe:rewrite-spring:6.34.0")
}
~~~

現在は Java 21 移行 recipe と共通静的解析 recipe が有効です。Spring Boot 4.0 移行 recipe は例としてコメントアウトされています。

## 3. 基本タスク

| タスク | 動作 | ファイル変更 |
| --- | --- | --- |
| `rewriteDiscover` | classpath 上の recipe を一覧表示 | なし |
| `rewriteDryRun` | 変更候補を preview し patch を生成 | なし |
| `rewriteRun` | 有効な recipe を適用 | あり |

~~~bash
./gradlew rewriteDiscover
./gradlew rewriteDryRun
./gradlew rewriteRun
~~~

`rewriteDryRun` の patch は通常 `build/reports/rewrite/rewrite.patch` に生成されます。

## 4. 安全な実行手順

~~~bash
git status --short
./gradlew rewriteDryRun
less build/reports/rewrite/rewrite.patch
./gradlew rewriteRun
git diff
./gradlew spotlessApply
./gradlew check
~~~

実行前から作業ツリーに変更があると、OpenRewrite の変更と区別しにくくなります。可能なら clean なブランチで実行し、一種類の移行ごとにコミットを分けます。

## 5. Recipe と Style

### Recipe

検索または変換の単位です。複数 recipe をまとめた composite recipe もあります。

~~~kotlin
rewrite {
    activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
}
~~~

### Style

インデント、import、命名などのコードスタイルです。recipe が新しいコードを生成するときの判断に利用されます。

~~~kotlin
rewrite {
    activeStyle("org.openrewrite.java.GoogleJavaFormat")
}
~~~

通常は明示的な style を増やす前に、Checkstyle 設定の自動検出と Spotless の最終整形を利用します。複数ツールのスタイルを矛盾させないことが重要です。

## 6. Recipe module の追加

recipe の実装は `rewrite` configuration に追加します。

~~~kotlin
dependencies {
    rewrite("org.openrewrite.recipe:rewrite-spring:<version>")
    rewrite("org.openrewrite.recipe:rewrite-static-analysis:<version>")
    rewrite("org.openrewrite.recipe:rewrite-migrate-java:<version>")
}
~~~

`activeRecipe` の名前が正しくても、実装を含む module が classpath にないと実行できません。`rewriteDiscover` で利用可能か確認します。

## 7. rewrite.yml

複数 recipe をプロジェクト独自の名前で合成できます。

~~~yaml
type: specs.openrewrite.org/v1beta/recipe
name: com.example.ModernizeProject
displayName: Modernize this project
description: Java と Spring の安全な定型移行をまとめて実行します。
recipeList:
  - org.openrewrite.java.migrate.UpgradeToJava21
  - org.openrewrite.staticanalysis.CommonStaticAnalysis
~~~

~~~kotlin
rewrite {
    configFile = file("rewrite.yml")
    activeRecipe("com.example.ModernizeProject")
}
~~~

YAML recipe は recipe の選択とオプション設定に向きます。独自の高度な変換ロジックが必要なら Java で recipe を実装します。

## 8. 主な Gradle plugin オプション

~~~kotlin
rewrite {
    activeRecipe("com.example.ModernizeProject")
    activeStyle("com.example.ProjectStyle")
    configFile = file("rewrite.yml")
    exportDatatables = true
    failOnDryRunResults = false
    sizeThresholdMb = 20
}
~~~

| オプション | 説明 |
| --- | --- |
| `activeRecipe` / `activeRecipes` | 実行する recipe |
| `activeStyle` / `activeStyles` | 適用する style |
| `configFile` | YAML 設定ファイル。既定は `rewrite.yml` |
| `exportDatatables` | recipe が生成した data table を CSV などで出力 |
| `failOnDryRunResults` | dry run で変更候補があれば失敗させる |
| `checkstyleConfigFile` | style 判定に使用する Checkstyle 設定 |
| `enableExperimentalGradleBuildScriptParsing` | Gradle build script も解析対象にするか |
| `sizeThresholdMb` | 大きすぎるファイルを解析対象外にするしきい値 |

本プロジェクトでは Checkstyle Gradle plugin が存在するため、OpenRewrite がその設定を style 判断へ利用できる場合があります。

## 9. コマンドラインで一時的に Recipe を選ぶ

build script を恒久変更せず調査したい場合、システムプロパティで指定できます。

~~~bash
./gradlew rewriteDryRun \
  -Drewrite.activeRecipe=org.openrewrite.java.search.FindMethods
~~~

再現可能なチーム運用では、最終的に `build.gradle.kts` または `rewrite.yml` へ固定します。

## 10. Data table

一部 recipe は、検索結果や変更内容を表形式で出力します。

~~~bash
./gradlew rewriteRun -Drewrite.exportDatatables=true
~~~

通常は `build/reports/rewrite/datatables/` 配下に出力されます。依存関係の利用状況や脆弱な依存候補などを調査するときに有効です。data table の出力内容は recipe ごとに異なります。

## 11. CI での使い方

`rewriteRun` を通常の CI で自動実行すると、CI 上だけファイルが変更されます。品質ゲートにする場合は `rewriteDryRun` と `failOnDryRunResults = true` を組み合わせます。

~~~kotlin
rewrite {
    failOnDryRunResults = true
}

tasks.named("check") {
    dependsOn("rewriteDryRun")
}
~~~

本プロジェクトでは `rewriteDryRun` を `check` に接続していません。OpenRewrite は明示的に実行する変更ツールとして扱います。

## 12. Recipe 選定の注意

- recipe の表示名だけでなく、変更対象とオプションを確認する
- composite recipe に含まれる子 recipe を確認する
- recipe module と plugin の互換性を確認する
- 大規模な version migration は migration 専用ブランチで行う
- `CommonStaticAnalysis` のような集合 recipe は更新で内容が増える可能性がある
- 適用結果をコンパイル、テスト、静的解析で検証する

## 13. トラブルシューティング

### Recipe が見つからない

`./gradlew rewriteDiscover` を実行し、必要な recipe module が `rewrite(...)` dependency にあるか確認します。

### dryRun と run の結果が異なる

dry run 後にソースや依存関係が変更されていないか確認します。複数 recipe が順に適用されると、前段の変更によって後段が新たに適用可能になる場合もあります。

### 変更後に formatter 差分が大量に出る

OpenRewrite 適用後に `spotlessApply` を実行します。移行差分と純粋な formatter 更新は可能なら分離します。

### 実行が遅い、またはメモリ不足になる

- 対象 source set と巨大ファイルを確認する
- 必要な recipe だけを有効にする
- Gradle daemon のヒープを確認する
- data table が不要なら export を無効にする

### 意図しない変更がある

`git diff` で recipe 単位に確認し、まず `rewriteDryRun` へ戻って対象を絞ります。生成物や vendor コードは source set または plugin 設定で除外します。

## 14. 参考資料

- [OpenRewrite documentation](https://docs.openrewrite.org/)
- [Gradle plugin configuration](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
- [Recipe catalog](https://docs.openrewrite.org/recipes)
- [Running recipes](https://docs.openrewrite.org/running-recipes)
- [Authoring recipes](https://docs.openrewrite.org/authoring-recipes)
