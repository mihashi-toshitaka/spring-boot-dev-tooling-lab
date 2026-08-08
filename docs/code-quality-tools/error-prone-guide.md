# Error Prone ガイド

## 1. Error Prone とは

Error Prone は javac に組み込まれて動作し、Java のコンパイル中にバグになりやすいコードを検出する静的解析ツールです。型情報と抽象構文木を利用できるため、単純な文字列検査より意味を理解した指摘ができます。

代表的な検出対象は次のとおりです。

- 間違った型同士の比較や collection 操作
- 生成しただけで throw していない例外
- 戻り値を無視してはいけない API の誤用
- Optional、Stream、Future などの誤用
- equals、hashCode、format string の不整合
- JUnit テストが実行されない定義ミス
- 不要または危険な null 処理

Error Prone はコンパイル処理の一部なので、独立した解析タスクではなく `compileJava` や `compileTestJava` の成否に影響します。

## 2. このプロジェクトでの構成

~~~kotlin
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("net.ltgt.errorprone") version "5.1.0"
}

dependencies {
    compileOnly("com.google.errorprone:error_prone_annotations:2.50.0")
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
    }
}
~~~

本プロジェクトは Java 21 toolchain を使用します。現在の Error Prone は実行 JDK に要件があり、バージョン 2.43 以降は JDK 21 以上が必要です。古い JDK をターゲットにする場合でも、新しい JDK でコンパイラを動かし `options.release` を設定できます。

## 3. 実行方法

~~~bash
./gradlew compileJava
./gradlew compileTestJava
./gradlew clean compileJava compileTestJava
./gradlew check
~~~

Gradle plugin は標準 source set の `JavaCompile` タスクで Error Prone を有効にします。`check` はコンパイルを前提とするため、結果的に Error Prone も実行されます。

出力例:

~~~text
Example.java:20: error: [DeadException] Exception created but not thrown
    new Exception();
    ^
  Did you mean 'throw new Exception();'?
~~~

角括弧内の名前が check name です。リンク先の bug pattern 説明で、危険な理由、例、推奨修正を確認します。

## 4. Severity の設定

Error Prone の severity は `OFF`、`WARN`、`ERROR` です。

~~~kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("DeadException", "CollectionIncompatibleType")
        warn("ReferenceEquality")
        disable("UnusedVariable")
    }
}
~~~

| メソッド | 効果 |
| --- | --- |
| `enable("CheckName")` | check 本来の severity で有効化 |
| `warn("CheckName")` | warning として有効化 |
| `error("CheckName")` | compile error として有効化 |
| `disable("CheckName")` | 無効化 |

特定 check の最後の設定が優先されます。check 名を間違えた場合に黙って無視しないよう、`ignoreUnknownCheckNames` は通常 `false` のままにします。

## 5. 一括ポリシー

~~~kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = false
        disableAllWarnings = false
        allErrorsAsWarnings = false
        allSuggestionsAsWarnings = false
        allDisabledChecksAsWarnings = false
    }
}
~~~

| オプション | 用途 |
| --- | --- |
| `disableAllChecks` | すべて無効にし、allowlist 方式で一部だけ有効化 |
| `disableAllWarnings` | warning check をまとめて無効化 |
| `allErrorsAsWarnings` | error を一時的に warning へ下げる |
| `allSuggestionsAsWarnings` | suggestion check も warning として有効化 |
| `allDisabledChecksAsWarnings` | 既定で無効な check を評価目的で warning 化 |

段階導入時に一括設定は便利ですが、恒久的にすべての error を warning 化すると品質ゲートが弱くなります。個別 check の方針へ移行します。

## 6. main と test でルールを変える

~~~kotlin
tasks.named<JavaCompile>("compileJava") {
    options.errorprone.error("ReturnValueIgnored")
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.warn("ReturnValueIgnored")
}
~~~

テストコードでは production code と異なる API やパターンを使うことがあります。一方で、テストが実行されない、assertion が無効、非同期処理を待っていないなど、テスト固有の重要な check もあります。`compileTestJava` 自体を安易に無効化しません。

## 7. 生成コードと対象パス

本プロジェクトの `disableWarningsInGeneratedCode = true` は、`@Generated` が付いたクラス内の warning を抑制します。error は対象外になり得る点と、annotation のない生成コードには効かない点に注意します。

パスで完全に除外する場合:

~~~kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone.excludedPaths = ".*/build/generated/.*"
}
~~~

`excludedPaths` はソースファイルパスに一致させる正規表現です。手書きコードまで除外しないようにします。

## 8. コード上の抑制

多くの check は `@SuppressWarnings` で局所的に抑制できます。

~~~java
@SuppressWarnings("ReferenceEquality")
boolean isSentinel(Object value) {
    // 同一インスタンスであること自体が仕様
    return value == SENTINEL;
}
~~~

優先順位:

1. 指摘どおりコードを修正する
2. API の意図を明確にする annotation や構造へ直す
3. 正当な例外だけを最小の要素へ `@SuppressWarnings` する
4. プロジェクト全体で不適切な check だけ Gradle 設定で無効化する

抑制理由はコメントまたは設計資料で説明できるようにします。

## 9. Check option と追加 checker

追加 checker は `errorprone` configuration に加えます。例えば NullAway は null safety を強化します。

~~~kotlin
dependencies {
    errorprone("com.uber.nullaway:nullaway:<version>")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.example")
    }
}
~~~

| API | 用途 |
| --- | --- |
| `option("Name")` | boolean option を true で渡す |
| `option("Name", "Value")` | checker 固有オプションを渡す |
| `errorproneArgs` | DSL にない追加引数を直接渡す |
| `argumentFiles` | 複数 build tool で共有する引数ファイルを使う |

追加 checker は annotation 方針、解析時間、既存違反への移行計画まで含めて評価します。本プロジェクトには導入していません。

## 10. Suggested fix と patch

多くの指摘はコンパイラ出力に修正候補を示します。Error Prone の patching 機能を使えば一部を一括修正できますが、ソースを書き換える操作です。

主な compiler flag:

- `-XepPatchChecks:<CheckName>`: 自動修正対象を選ぶ
- `-XepPatchLocation:IN_PLACE`: 対象ファイルへ適用する

専用ブランチで実行し、`git diff`、formatter、test、`check` で検証します。

## 11. 運用とバージョン更新

- plugin と `error_prone_core` の両方を固定バージョンにする
- JDK 更新時は Error Prone の実行 JDK 対応を確認する
- Error Prone 更新時は新規・severity 変更 check を確認する
- 更新差分と機能変更を分離する
- CI では Gradle Wrapper と同じ Java toolchain を利用する

動的バージョンを使うと、新しい check の追加だけで突然コンパイルが失敗するため避けます。

## 12. トラブルシューティング

### コンパイラ起動時に module access error が出る

JDK、Error Prone、Gradle plugin の互換性を確認します。Gradle plugin は通常 JDK 16 以降で必要な compiler JVM 引数を自動設定するため、手動の `--add-exports` を追加する前に toolchain と plugin 適用状態を確認します。

### check が見つからない

check 名、Error Prone のバージョン、追加 checker dependency を確認します。`ignoreUnknownCheckNames = true` で隠さず、設定ミスを直します。

### annotation processor と競合する

Error Prone とほかの annotation processor が同じ processor path に正しく入っているか確認します。独自 `JavaCompile` タスクでは plugin による自動配線が行われない場合があります。

### ローカルと CI で結果が違う

JDK vendor/version、Gradle Wrapper、Error Prone 本体、生成コード、compiler option を比較します。`clean compileJava compileTestJava` で再現性を確認します。

## 13. 参考資料

- [Error Prone](https://errorprone.info/)
- [Bug patterns](https://errorprone.info/bugpatterns)
- [Command-line flags](https://errorprone.info/docs/flags)
- [Installation](https://errorprone.info/docs/installation)
- [Gradle Error Prone Plugin](https://github.com/tbroyer/gradle-errorprone-plugin)

