# コード品質ツールガイド

このページは、本プロジェクトで利用するコード品質・開発支援ツールの索引です。各ツールの詳細な機能、設定例、ユースケース、運用方法は個別ガイドを参照してください。

## ツール一覧

| ツール | 主な入力 | 主な役割 | 自動修正 | 通常の品質ゲート |
| --- | --- | --- | --- | --- |
| [Checkstyle](checkstyle-guide.md) | Java ソース | コーディング規約、命名、Javadoc、構造 | なし | `checkstyleMain` / `checkstyleTest` |
| [SpotBugs](spotbugs-guide.md) | Java bytecode | 潜在的な bug pattern | なし | `spotbugsMain` / `spotbugsTest` |
| [Spotless](spotless-guide.md) | source / text | 決定的な自動整形 | あり | `spotlessCheck` |
| [OpenRewrite](openrewrite-guide.md) | source / build files | semantic search、自動 migration | あり | 現在は明示実行のみ |
| [Error Prone](error-prone-guide.md) | javac AST / 型情報 | compile 時の bug pattern | 候補・patch あり | `compileJava` / `compileTestJava` |
| [ArchUnit](archunit-guide.md) | compiled classes | architecture rule | なし | `test` |
| [SonarQube for IDE](sonarqube-for-ide-guide.md) | IDE 上の source | 編集中の幅広い早期 feedback | Quick Fix あり | 現在は対象外 |

## 推奨ワークフロー

### 通常のコード変更

~~~bash
./gradlew spotlessApply
git diff
./gradlew check
~~~

`spotlessApply` はファイルを書き換えます。差分を確認してから、Checkstyle、Spotless、SpotBugs、Error Prone、ArchUnit、JUnit を含む `check` を実行します。

### OpenRewrite を使う変更

~~~bash
./gradlew rewriteDryRun
./gradlew rewriteRun
./gradlew spotlessApply
git diff
./gradlew clean check
~~~

OpenRewrite と Spotless は変更を加えるツールです。clean な作業ツリーまたは専用ブランチで実行し、機械的変更と機能変更を分離します。

### 問題の発見から対応まで

1. IDE の SonarQube for IDE、Checkstyle、Spotless 診断で早期に発見する
2. コンパイル時に Error Prone の指摘を確認する
3. test で挙動と ArchUnit rule を検証する
4. SpotBugs で bytecode 上の bug pattern を検査する
5. `check` で repository 共通の品質ゲートを通す
6. 指摘を抑制する場合は、最小範囲に理由を残す

## 役割が重なる場合

複数ツールが同じコードを指摘することがあります。単純に一方を無効化せず、次の基準で担当を選びます。

- 自動整形できる書式: Spotless
- チーム独自のソース規約: Checkstyle
- javac の型情報が必要な API 誤用: Error Prone
- bytecode と data flow で分かる bug: SpotBugs
- package、layer、依存方向: ArchUnit
- IDE での幅広い早期 feedback: SonarQube for IDE
- 大規模な定型移行: OpenRewrite

最終的な基準は再現可能な Gradle Wrapper のタスクです。IDE のローカル設定だけに依存するルールは、repository の品質ゲートとは区別します。

## バージョン更新時の原則

- plugin、解析本体、formatter、recipe module のどのバージョンを更新するか明確にする
- 公式 release note と Java / Gradle compatibility を確認する
- 更新だけの変更として差分を確認する
- 新しい rule、severity、formatter 出力、recipe 内容の変化を確認する
- `./gradlew clean check` を実行する
- OpenRewrite または Spotless の更新で大量変更が出る場合、機能変更と別コミットにする

