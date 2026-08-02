# SpotBugs ガイド

## 1. SpotBugs とは

SpotBugs は、コンパイル済みの Java バイトコードを解析して潜在的なバグパターンを検出する静的解析ツールです。ソースの表記を検査する Checkstyle と異なり、クラスファイル上の制御フロー、データフロー、フィールドアクセス、API の使い方などを解析します。

代表的な検出対象は次のとおりです。

- null 参照や常に成立・不成立になる条件
- equals/hashCode、compareTo、clone の誤実装
- リソースの閉じ忘れや例外処理の不備
- 同期、ロック、volatile に関する並行処理上の問題
- 読み書きされないフィールドや無効な代入
- 不変オブジェクトの内部状態を外部へ公開するコード
- パフォーマンス上の明らかな問題

SpotBugs の指摘はバグの可能性を示すものであり、すべてが実際の不具合とは限りません。まずコードの意図と実行経路を確認し、修正、限定的な抑制、設定変更の順で対応します。

## 2. このプロジェクトでの構成

~~~kotlin
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    id("com.github.spotbugs") version "6.5.9"
}

spotbugs {
    ignoreFailures = false
    effort = Effort.DEFAULT
    reportLevel = Confidence.DEFAULT
}

tasks.withType<SpotBugsTask>().configureEach {
    val spotBugsTaskName = name
    reports.create("html") {
        required = true
        outputLocation = layout.buildDirectory.file(
            "reports/spotbugs/$spotBugsTaskName.html"
        )
    }
}
~~~

| タスク | 対象 | レポート |
| --- | --- | --- |
| `spotbugsMain` | `src/main/java` のコンパイル結果 | `build/reports/spotbugs/spotbugsMain.html` |
| `spotbugsTest` | `src/test/java` のコンパイル結果 | `build/reports/spotbugs/spotbugsTest.html` |
| `check` | 上記を含む全品質チェック | 各タスクのレポート |

~~~bash
./gradlew spotbugsMain spotbugsTest
./gradlew check
~~~

SpotBugs はクラスファイルを入力にするため、対象ソースのコンパイル後に実行されます。

## 3. 主要オプション

### effort

解析に使う計算量と精度のバランスです。

| 値 | 用途 |
| --- | --- |
| `MIN` / `LESS` | 大規模プロジェクトで速度を優先する場合 |
| `DEFAULT` | 通常のローカル開発と CI。本プロジェクトの設定 |
| `MORE` / `MAX` | より深い解析を行う。実行時間とメモリ消費が増える |

`MAX` にしても誤検知がなくなるわけではありません。まず `DEFAULT` の指摘を安定運用してから変更します。

### reportLevel

レポートに含める信頼度のしきい値です。

| 値 | 対象 |
| --- | --- |
| `HIGH` | 高信頼度のみ |
| `DEFAULT` / `MEDIUM` | 高・中信頼度。本プロジェクトの設定 |
| `LOW` | 低信頼度を含む、検出された全候補 |

`LOW` は調査用途には有効ですが、品質ゲートにするとノイズが増える可能性があります。

### そのほかの拡張オプション

~~~kotlin
spotbugs {
    ignoreFailures = false
    showProgress = true
    showStackTraces = true
    maxHeapSize = "1g"
    onlyAnalyze = listOf("com.example.*")
    excludeFilter = file("config/spotbugs/exclude.xml")
    baselineFile = file("config/spotbugs/baseline.xml")
}
~~~

| オプション | 説明 |
| --- | --- |
| `ignoreFailures` | 指摘があってもビルドを成功扱いにするか |
| `showProgress` | 解析の進捗を表示するか |
| `showStackTraces` | SpotBugs 自体のエラーでスタックトレースを表示するか |
| `maxHeapSize` | 解析 JVM の最大ヒープ |
| `onlyAnalyze` | 解析するクラス・パッケージを限定 |
| `visitors` | 実行する detector を限定 |
| `omitVisitors` | 特定 detector を無効化 |
| `includeFilter` | レポート対象を include filter で限定 |
| `excludeFilter` | 指定した指摘をレポートから除外 |
| `baselineFile` | 既知の指摘をベースラインとして扱う |

本プロジェクトは初期状態でフィルターやベースラインを使用しません。新しい指摘を確実に品質ゲートへ反映するためです。

## 4. 指摘の読み方

HTML レポートでは、次の情報を確認します。

1. Bug pattern ID（例: `NP_NULL_ON_SOME_PATH`）
2. Category（Correctness、Bad practice、Performance など）
3. Confidence / Priority
4. 対象クラス、メソッド、フィールド、行番号
5. 詳細説明と発生条件

同じ行に指摘されても、原因はその前の分岐や別メソッドにある場合があります。行だけで判断せず、データがどこから来てどこへ流れるかを確認します。

## 5. 抑制とフィルター

### コード上の抑制

SpotBugs annotation を `compileOnly` で導入すると、限定的な抑制や意図の表明ができます。

~~~kotlin
dependencies {
    compileOnly(
        "com.github.spotbugs:spotbugs-annotations:${spotbugs.toolVersion.get()}"
    )
}
~~~

~~~java
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "返却値は不変の値オブジェクトであるため")
public Example value() {
    return example;
}
~~~

抑制には Bug pattern ID と具体的な理由を必ず記載します。クラス全体への広い抑制は避けます。

### exclude filter

~~~xml
<FindBugsFilter>
    <Match>
        <Bug pattern="EI_EXPOSE_REP"/>
        <Class name="com.example.framework.GeneratedAdapter"/>
    </Match>
</FindBugsFilter>
~~~

フィルターでは package、class、method、field、annotation、bug pattern、confidence などを組み合わせられます。フレームワーク生成物など、コード上に annotation を置けない場合に利用します。

### ベースライン

大量の既存違反があるプロジェクトへ導入するとき、baseline を使えば新規指摘だけを失敗対象にできます。ただし、既存指摘が見えなくなるため、削減計画と更新手順を同時に決めます。

## 6. detector plugin

SpotBugs は detector plugin でルールを拡張できます。代表例は Find Security Bugs です。

~~~kotlin
dependencies {
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:<version>")
}
~~~

追加 plugin は検出範囲、実行時間、誤検知、更新頻度が変わるため、本体とは別にバージョンと運用方針を決めます。本プロジェクトには導入していません。

## 7. 運用パターン

### ローカル開発

変更範囲が小さい場合も、コミット前に `spotbugsMain` と必要に応じて `spotbugsTest` を実行します。HTML レポートは生成物なのでコミットしません。

### CI の品質ゲート

`ignoreFailures = false` のまま `./gradlew check` を実行します。レポートを CI artifact として保存すると、失敗原因を確認しやすくなります。

### 大規模コードへの段階導入

1. `DEFAULT` で全体を解析する
2. 実害のある指摘を修正する
3. 誤検知だけを狭く抑制する
4. 必要なら期限付き baseline を作る
5. 安定後に `LOW` や追加 detector を評価する

## 8. トラブルシューティング

### UnsupportedClassVersionError

解析対象の Java バイトコードを理解できる SpotBugs バージョンか確認します。Java または Gradle plugin を更新したときは、SpotBugs 本体の対応状況も確認します。

### Missing classes が表示される

対象クラスの compile classpath が解析へ渡っているか確認します。独自 source set や生成コードでは `auxClassPaths` の調整が必要な場合があります。

### メモリ不足または解析が遅い

- `maxHeapSize` を段階的に増やす
- 意図せず巨大な生成コードを解析していないか確認する
- `effort` を一時的に下げて原因を切り分ける
- detector plugin の影響を確認する

### ローカルと CI で結果が違う

JDK、Gradle Wrapper、plugin、本体バージョン、対象 classpath を揃えます。キャッシュを疑う場合は `./gradlew clean spotbugsMain spotbugsTest` で再確認します。

## 9. 参考資料

- [SpotBugs Manual](https://spotbugs.readthedocs.io/en/stable/)
- [SpotBugs Gradle Plugin](https://spotbugs.readthedocs.io/en/stable/gradle.html)
- [Bug descriptions](https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html)
- [Filter file](https://spotbugs.readthedocs.io/en/stable/filter.html)
- [SpotBugs annotations](https://spotbugs.readthedocs.io/en/stable/annotations.html)

