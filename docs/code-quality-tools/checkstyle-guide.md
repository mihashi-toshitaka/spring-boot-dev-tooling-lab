# Checkstyle ガイド

## 1. Checkstyle とは

Checkstyle は、Java ソースコードを静的に解析し、コーディング規約への違反を検出するツールです。名前、空白、import、Javadoc、クラス設計、コード量などを、レビュー担当者の目視だけに頼らず継続的に検査できます。

主な用途は次のとおりです。

- チームのコーディング規約を機械的に統一する
- IDE 上で規約違反を早期に発見する
- Gradle の `check` や CI で品質ゲートを設ける
- Javadoc や命名規則を通して公開 API の品質を保つ
- 巨大なクラスや複雑なメソッドなど、保守性低下の兆候を検出する
- 禁止 API、コメント、文字列パターンなど、プロジェクト固有のルールを検査する

Checkstyle は主にソースコードの構文とテキストを検査します。自動整形は Spotless、コンパイル時のバグ検出は Error Prone、バイトコード上のバグ検出は SpotBugs が担当します。各ツールの役割は重なる部分もありますが、Checkstyle だけで実行時の正しさやセキュリティを保証できるわけではありません。

## 2. このプロジェクトでの構成

このプロジェクトでは Gradle 標準の `checkstyle` プラグインを使用しています。

~~~kotlin
plugins {
    id("checkstyle")
}

checkstyle {
    toolVersion = "13.7.0"
    configDirectory = file("config/checkstyle")
}
~~~

主要ファイルとタスクは次のとおりです。

| 対象 | 内容 |
| --- | --- |
| `build.gradle.kts` | プラグイン、Checkstyle のバージョン、設定ディレクトリを定義 |
| `config/checkstyle/checkstyle.xml` | 有効にするルールと各オプションを定義 |
| `checkstyleMain` | `src/main/java` を検査 |
| `checkstyleTest` | `src/test/java` を検査 |
| `check` | テストやほかの品質チェックとともに両 Checkstyle タスクを実行 |

基本的な実行コマンドは次のとおりです。

~~~bash
./gradlew checkstyleMain
./gradlew checkstyleTest
./gradlew checkstyleMain checkstyleTest
./gradlew check
~~~

詳細な実行ログが必要な場合は `--info` または `--debug` を付けます。

~~~bash
./gradlew checkstyleMain --info
~~~

Gradle は通常、`build/reports/checkstyle/` 配下に HTML と XML のレポートを生成します。コンソールのエラーだけでは原因が分かりにくい場合は、`main.html` または `test.html` を確認してください。

## 3. 現在有効なルール

現在の `config/checkstyle/checkstyle.xml` では、次のルールを有効にしています。

| ルール | 現在の設定 | 検査内容 |
| --- | --- | --- |
| `MissingJavadocType` | `scope=public` | public なクラス、インターフェース、enum などに Javadoc があるか |
| `MissingJavadocMethod` | `scope=public` | public なメソッドとコンストラクタに Javadoc があるか |
| `TodoComment` | `format=(TODO)\|(FIXME)` | `TODO` または `FIXME` を含むコメントが残っていないか |
| `ConstantName` | 既定値 | `static final` 定数名が大文字中心の定数命名規則に従うか |

現在の設定は学習用の小さなルールセットです。一般的なスタイルガイドを全面的に適用しているわけではありません。ルールを増やすときは、一度に大量に追加せず、目的と修正方法をチームで合意してから段階的に導入するのが安全です。

## 4. 設定ファイルの構造

Checkstyle の XML 設定はモジュールのツリーです。ルートは必ず `Checker` で、多くの Java 構文ルールは `TreeWalker` の子として配置します。

~~~xml
<module name="Checker">
    <!-- ファイル全体を扱うルールやフィルター -->

    <module name="TreeWalker">
        <!-- Java の構文木を扱うルール -->
        <module name="ConstantName"/>
    </module>
</module>
~~~

### Checker

解析全体のルートです。文字コード、ファイル拡張子、ファイル単位のルール、抑制フィルター、監査イベントの出力などを管理します。

### TreeWalker

Java ファイルを抽象構文木（AST）へ変換し、クラス、メソッド、変数、式などのノードを各ルールへ渡します。命名、修飾子、import、Javadoc、コード構造に関するルールの多くは `TreeWalker` の子です。

### Check

個々の検査ルールです。例えば `ConstantName`、`MethodLength`、`AvoidStarImport` が該当します。同じ Check を複数回定義し、`tokens` や `id` を変えて用途別のポリシーにすることもできます。

### Filter

検出された違反を条件付きで除外します。ファイル、ルール、行、列、ID、AST の XPath などで対象を限定できます。フィルターは例外運用のための仕組みであり、広すぎる抑制はルール自体を無効化するのと同じなので注意が必要です。

## 5. 共通オプション

多くの Check で利用できる代表的なオプションは次のとおりです。実際に使用できるプロパティと既定値は Check ごとに異なるため、追加前に公式の Checks リファレンスを確認してください。

| オプション | 用途 | 例 |
| --- | --- | --- |
| `severity` | 違反の重大度を `error`、`warning`、`info`、`ignore` から指定 | `<property name="severity" value="warning"/>` |
| `id` | 同じ Check を複数設定するときの識別子 | `<property name="id" value="constructorLength"/>` |
| `tokens` | Check を適用する AST ノードの種類を限定 | `METHOD_DEF`、`CTOR_DEF` |
| `fileExtensions` | 対象とする拡張子を限定 | `java` |
| `tabWidth` | タブを何文字として位置計算するか | `4` |
| `message` | 違反メッセージをプロジェクト向けに上書き | `<message key="name.invalidPattern" value="..."/>` |

重大度を `warning` に変えただけでは、Gradle が警告を許容するとは限りません。Checkstyle XML の `severity` と Gradle 側の `maxWarnings` を組み合わせてビルド失敗条件を決めます。

## 6. ユースケース別のルール例

以下は選択肢の例です。そのまますべて導入するのではなく、プロジェクトの規模、既存コード、利用するフォーマッターとの役割分担を考えて選択してください。

### 6.1 命名規則を統一する

~~~xml
<module name="TreeWalker">
    <module name="TypeName">
        <property name="format" value="^[A-Z][a-zA-Z0-9]*$"/>
    </module>
    <module name="MethodName">
        <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
    </module>
    <module name="MemberName"/>
    <module name="ParameterName"/>
    <module name="LocalVariableName"/>
    <module name="ConstantName"/>
</module>
~~~

主な選択肢は次のとおりです。

- `format`: Java の正規表現で許可する名前を指定する
- `applyToPublic`、`applyToProtected`、`applyToPackage`、`applyToPrivate`: 可視性ごとに適用対象を調整するルールがある
- `tokens`: 通常変数、catch パラメーター、ラムダパラメーターなどを限定する

フレームワークが特定の名前を要求する場合は、ルール全体を緩めるより、対象ファイルや明確な ID に対する抑制を検討します。

### 6.2 import を整理する

~~~xml
<module name="TreeWalker">
    <module name="AvoidStarImport"/>
    <module name="UnusedImports"/>
    <module name="RedundantImport"/>
    <module name="CustomImportOrder">
        <property name="customImportOrderRules"
                  value="STATIC###STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE"/>
        <property name="sortImportsInGroupAlphabetically" value="true"/>
        <property name="separateLineBetweenGroups" value="true"/>
    </module>
</module>
~~~

自動整形ツールも import 順を変更する場合は、Checkstyle と Spotless で同じ方針にします。異なる規則を設定すると、整形と検査が互いに打ち消し合います。

### 6.3 空白、改行、波括弧を検査する

~~~xml
<module name="Checker">
    <module name="LineLength">
        <property name="max" value="120"/>
        <property name="ignorePattern" value="^package.*|^import.*|https?://"/>
    </module>

    <module name="TreeWalker">
        <module name="NeedBraces"/>
        <module name="WhitespaceAfter"/>
        <module name="WhitespaceAround"/>
        <module name="EmptyLineSeparator"/>
        <module name="OperatorWrap"/>
    </module>
</module>
~~~

フォーマットに関するルールは Spotless と重複しやすい領域です。自動修正できる内容は Spotless に任せ、Checkstyle では自動整形だけでは表現しにくい制約を検査する運用も有効です。

### 6.4 Javadoc とコメントを検査する

~~~xml
<module name="TreeWalker">
    <module name="MissingJavadocType">
        <property name="scope" value="public"/>
    </module>
    <module name="MissingJavadocMethod">
        <property name="scope" value="public"/>
        <property name="allowMissingPropertyJavadoc" value="true"/>
    </module>
    <module name="JavadocMethod"/>
    <module name="JavadocType"/>
    <module name="SummaryJavadoc"/>
    <module name="TodoComment">
        <property name="format" value="(TODO)|(FIXME)"/>
    </module>
</module>
~~~

`MissingJavadoc...` はコメントの有無を、`Javadoc...` 系はタグや内容の妥当性を検査します。内部実装まで一律に Javadoc を要求すると、価値の低いコメントが増える場合があります。ライブラリなら public API、アプリケーションなら controller や設定クラスなど、保守上重要な境界へ絞る方法もあります。

### 6.5 クラス設計と可視性を検査する

~~~xml
<module name="TreeWalker">
    <module name="VisibilityModifier">
        <property name="protectedAllowed" value="false"/>
        <property name="packageAllowed" value="true"/>
    </module>
    <module name="FinalClass"/>
    <module name="HideUtilityClassConstructor"/>
    <module name="DesignForExtension"/>
    <module name="OneTopLevelClass"/>
</module>
~~~

- `VisibilityModifier`: フィールドのカプセル化を促す
- `FinalClass`: 継承されないクラスを `final` にできるか検査する
- `HideUtilityClassConstructor`: static メソッドだけのユーティリティクラスが生成されないようにする
- `DesignForExtension`: 継承を前提とする設計が明示されているか検査する
- `OneTopLevelClass`: 1 ファイルに複数のトップレベル型を置かないようにする

Spring のプロキシ、ORM、シリアライザーなどはコンストラクタや継承可能性に制約を持つ場合があります。フレームワークの要件を確認してから導入してください。

### 6.6 コード量と複雑度に上限を設ける

~~~xml
<module name="Checker">
    <module name="FileLength">
        <property name="max" value="1000"/>
    </module>

    <module name="TreeWalker">
        <module name="MethodLength">
            <property name="max" value="80"/>
            <property name="tokens" value="METHOD_DEF"/>
        </module>
        <module name="ParameterNumber">
            <property name="max" value="7"/>
        </module>
        <module name="ReturnCount">
            <property name="max" value="4"/>
        </module>
        <module name="CyclomaticComplexity">
            <property name="max" value="10"/>
        </module>
        <module name="NPathComplexity">
            <property name="max" value="200"/>
        </module>
    </module>
</module>
~~~

閾値は絶対的な品質指標ではなく、レビュー対象を見つけるためのシグナルです。テスト、DTO、設定コードなどは性質が異なるため、必要なら `id` と抑制を使って別ポリシーにします。

### 6.7 禁止パターンやプロジェクト固有ルールを検査する

~~~xml
<module name="TreeWalker">
    <module name="IllegalImport">
        <property name="illegalPkgs" value="java.util.logging,sun"/>
    </module>
    <module name="IllegalThrows">
        <property name="illegalClassNames"
                  value="java.lang.Error,java.lang.RuntimeException,java.lang.Throwable"/>
    </module>
    <module name="IllegalCatch">
        <property name="illegalClassNames"
                  value="java.lang.Exception,java.lang.Throwable"/>
    </module>
    <module name="RegexpSinglelineJava">
        <property name="format" value="System\.(out|err)\.print"/>
        <property name="message" value="標準出力ではなくロガーを使用してください。"/>
        <property name="ignoreComments" value="true"/>
    </module>
</module>
~~~

禁止ルールには必ず代替手段を用意します。例えば `System.out` を禁止するなら、このプロジェクトで使用するロガーとテスト時の出力方法も文書化します。

### 6.8 main と test でポリシーを変える

Gradle タスク側で対象ファイルを調整できます。

~~~kotlin
import org.gradle.api.plugins.quality.Checkstyle

tasks.named<Checkstyle>("checkstyleMain") {
    exclude("**/generated/**")
}

tasks.named<Checkstyle>("checkstyleTest") {
    // テスト生成物など、根拠のある対象だけを除外する
    exclude("**/generated/**")
}
~~~

ルールセット自体を完全に分けることもできますが、差分が見えにくくなります。まず共通ルールを使い、テスト特有の例外だけを ID やファイルパターンで抑制する方が管理しやすい場合が多いです。

## 7. 抑制と除外

### 7.1 SuppressionFilter

プロジェクト固有の例外は、メイン設定へ直接正規表現を増やし続けるより `suppressions.xml` に分離できます。

`checkstyle.xml`:

~~~xml
<module name="Checker">
    <module name="SuppressionFilter">
        <property name="file" value="${config_loc}/suppressions.xml"/>
    </module>

    <module name="TreeWalker">
        <module name="MagicNumber"/>
    </module>
</module>
~~~

`suppressions.xml`:

~~~xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
          "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
          "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<suppressions>
    <suppress checks="MagicNumber"
              files="[/\\]LegacyProtocol\.java$"
              lines="40-55"/>
</suppressions>
~~~

`files` と `checks` は正規表現です。`lines` や `columns` はコード変更ですぐずれるため、安定したファイル名・Check の `id`・XPath を優先します。`message` による抑制は実行ロケールで文言が変わる可能性があるため避けます。

### 7.2 ID で同じ Check を区別する

~~~xml
<module name="TreeWalker">
    <module name="MethodLength">
        <property name="id" value="methodLength"/>
        <property name="tokens" value="METHOD_DEF"/>
        <property name="max" value="80"/>
    </module>
    <module name="MethodLength">
        <property name="id" value="constructorLength"/>
        <property name="tokens" value="CTOR_DEF"/>
        <property name="max" value="40"/>
    </module>
</module>
~~~

~~~xml
<suppressions>
    <suppress id="constructorLength" files="[/\\]GeneratedModel\.java$"/>
</suppressions>
~~~

### 7.3 コメントによる局所抑制

`SuppressionCommentFilter` や `SuppressWithNearbyCommentFilter` を使うと、ソースコメントで局所的に違反を抑制できます。例外理由をコードの近くに残せる一方、開発者が簡単に検査を無効化できるため、利用を許可するルールとレビュー基準を決めてください。

### 7.4 XPath による抑制

`SuppressionXpathFilter` は AST ノードを XPath で指定します。行番号よりリファクタリングに強く、特定アノテーションを持つクラスの特定要素だけを除外するといった用途に向きます。ただし、ファイル全体を扱う Check など XPath 抑制に対応しないルールもあります。

### 7.5 生成コードを解析対象から外す

生成コードは原則として Gradle の `exclude` でタスク入力から外します。Checkstyle が解釈できない Java ファイルを設定側で除外する場合は、`Checker` の子に `BeforeExecutionExclusionFileFilter` を設定できます。

~~~xml
<module name="BeforeExecutionExclusionFileFilter">
    <property name="fileNamePattern" value="[/\\]generated[/\\]"/>
</module>
~~~

手書きコードまで広く除外しないよう、生成先ディレクトリを明確に分離してください。

## 8. Gradle 側の主なオプション

### Checkstyle 拡張

~~~kotlin
checkstyle {
    toolVersion = "13.7.0"
    configDirectory = file("config/checkstyle")
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
    configProperties["projectName"] = project.name
}
~~~

| オプション | 説明 |
| --- | --- |
| `toolVersion` | 使用する Checkstyle 本体のバージョンを固定する |
| `configDirectory` | 設定ディレクトリ。XML 内では `${config_loc}` として参照できる |
| `configFile` / `config` | メイン設定を既定の `checkstyle.xml` 以外へ変更する |
| `configProperties` | XML 内の `${propertyName}` に渡す値を定義する |
| `isIgnoreFailures` | 違反があっても Gradle ビルドを継続するか |
| `maxErrors` | 許容する error 件数 |
| `maxWarnings` | 許容する warning 件数 |
| `showViolations` | コンソールへ個々の違反を表示するか |

品質ゲートとして利用する場合は `isIgnoreFailures = false` を維持します。段階導入では warning と `maxWarnings` を使えますが、期限なく警告を許容し続けないよう、削減方針を決めてください。

### レポート

~~~kotlin
import org.gradle.api.plugins.quality.Checkstyle

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required = true
        html.required = true
        sarif.required = false
    }
}
~~~

| 形式 | 主な用途 |
| --- | --- |
| HTML | 開発者がブラウザーで確認する |
| XML | CI、集計ツール、独自変換で利用する |
| SARIF | SARIF 対応のコードスキャン UI へ取り込む。Checkstyle 10.3.3 以降が必要 |

### 大規模プロジェクトでのメモリ調整

~~~kotlin
tasks.withType<Checkstyle>().configureEach {
    minHeapSize = "256m"
    maxHeapSize = "1g"
}
~~~

Checkstyle は別プロセスで解析されます。大量のソースでメモリ不足になる場合だけ調整し、必要以上に大きくしないでください。

### 別の Java Toolchain で実行する

プロジェクトのターゲット Java と Checkstyle の実行要件が異なる場合、`javaLauncher` へ Checkstyle 用 Toolchain を指定できます。このプロジェクトは Java 21 を使っているため、通常は追加設定不要です。

## 9. VS Code との連携

このプロジェクトでは Checkstyle for VS Code を利用できます。`.vscode/settings.json` またはワークスペース設定で、Gradle と同じバージョンと設定ファイルを指定します。

~~~json
{
  "java.checkstyle.version": "13.7.0",
  "java.checkstyle.configuration": "${workspaceFolder}/config/checkstyle/checkstyle.xml"
}
~~~

IDE と Gradle の結果が異なる場合は、次の順番で確認します。

1. Checkstyle のバージョンが一致しているか
2. 同じ `checkstyle.xml` を参照しているか
3. `suppressions.xml` などの参照先を IDE が解決できるか
4. IDE と Gradle で対象ソースセットや除外パターンが異なっていないか
5. Gradle の `configProperties` に依存する設定が IDE にも渡っているか

最終的な品質ゲートは、環境差を減らせる Gradle Wrapper の `./gradlew check` とします。

## 10. 導入・変更時の進め方

### 新しいルールを追加する場合

1. ルールで防ぎたい問題と、違反時の修正方法を明確にする
2. 公式ドキュメントで親モジュール、プロパティ、既定値、対応トークンを確認する
3. `checkstyle.xml` に最小限の設定を追加する
4. `./gradlew checkstyleMain checkstyleTest` を実行して既存違反数を把握する
5. 実際の問題は修正し、正当な例外だけを狭い条件で抑制する
6. IDE と Gradle の両方で同じ結果になることを確認する
7. ルールの目的や例外方針をレビューで共有する

### 既存プロジェクトへ段階導入する場合

- まず新規・変更コードで守りたい少数のルールから始める
- 自動修正可能なフォーマットは先に Spotless で揃える
- 既存違反を無差別に行単位で抑制しない
- 一時的に warning とする場合は、error 化する条件と期限を決める
- モジュール単位で導入する場合も最終的な共通ルールセットを意識する

### ルールを無効化・緩和する場合

次の内容をレビューで説明できるようにします。

- どのユースケースで誤検知または過剰制約になるか
- コード修正では解決できない理由
- ルール全体の変更が必要か、局所抑制で十分か
- 品質上のリスクを別のテストやツールで補えるか

## 11. トラブルシューティング

### 設定 XML を読み込めない

- XML の階層と DTD 宣言を確認する
- Check が `Checker` と `TreeWalker` のどちらの子か確認する
- `${config_loc}` から参照するファイルが存在するか確認する
- Checkstyle バージョンがその Check やプロパティに対応しているか確認する

### Java の構文解析に失敗する

使用している Java 構文に対応した Checkstyle へ更新します。一時回避として `TreeWalker` の `skipFileOnJavaParseException` もありますが、解析されないファイルが生じるため、恒久対応にはしません。

### IDE では成功するが Gradle では失敗する

IDE の設定だけでなく、必ず次を実行して Gradle 側の対象とレポートを確認します。

~~~bash
./gradlew clean checkstyleMain checkstyleTest --info
~~~

### 抑制が効かない

- `SuppressionFilter` の配置場所を確認する
- Windows と Linux の両方を考慮したパス正規表現か確認する
- Check 名ではなく `id` を指定している場合、両者が完全一致しているか確認する
- XPath 非対応の Check ではないか確認する
- メッセージ文字列に依存していないか確認する

### 解析が遅い、またはメモリ不足になる

- 生成コードや解析不要なソースが入力に含まれていないか確認する
- 高コストなルールを必要以上に重複定義していないか確認する
- Gradle の build cache と up-to-date 判定が利用できているか確認する
- 必要な場合だけ `minHeapSize` と `maxHeapSize` を調整する

## 12. 参考資料

- [Checkstyle 公式サイト](https://checkstyle.org/)
- [Checkstyle の全 Checks](https://checkstyle.org/checks.html)
- [Checkstyle 設定リファレンス](https://checkstyle.org/config.html)
- [Checkstyle Filters](https://checkstyle.org/filters/)
- [Checkstyle XPath サポート](https://checkstyle.org/xpath.html)
- [Gradle Checkstyle Plugin](https://docs.gradle.org/current/userguide/checkstyle_plugin.html)

