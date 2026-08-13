# Flyway 主要機能ガイド

## 1. Flyway とは

Flyway は、データベースの変更をマイグレーションファイルとしてバージョン管理し、開発、テスト、本番などの各環境へ同じ順序で適用するためのツールです。

基本的な流れは次のとおりです。

1. スキーマやデータの変更をマイグレーションとして作成する
2. マイグレーションをアプリケーションコードと一緒にバージョン管理する
3. Flyway が対象データベースの適用履歴とファイルを比較する
4. 未適用のマイグレーションを順番に実行する
5. 実行結果とチェックサムをスキーマ履歴テーブルへ記録する

Flyway は CLI、Gradle／Maven プラグイン、Java API、Spring Boot などから利用できます。このプロジェクトでは Spring Boot との統合を使用しており、Flyway CLI と Flyway Gradle プラグインは導入していません。

## 2. マイグレーションの種類

主なマイグレーションは次のとおりです。

| 種類 | 既定の命名例 | 実行タイミング | 主な用途 |
| --- | --- | --- | --- |
| バージョン付き | `V2__add_email.sql` | バージョンごとに一度だけ | テーブル、列、制約、インデックス、データの段階的な変更 |
| リピータブル | `R__customer_summary_view.sql` | ファイルのチェックサムが変わったとき | View、関数、プロシージャなどの定義を最新状態に保つ |
| ベースライン | `B10__current_state.sql` | 新しい環境を構築するとき | 長くなった履歴を特定バージョン時点のスキーマへ集約する |
| Java ベース | `V3__NormalizeUsernames.java` | バージョン付きなど、実装した種類に従う | SQL だけでは表現しにくい複雑なデータ変換 |

### バージョン付きマイグレーション

バージョン付きマイグレーションは、バージョン番号の順に一度だけ実行されます。

~~~text
V1__create_customer.sql
V2__add_customer_email.sql
V3__create_customer_email_index.sql
~~~

ファイル名は、既定では `V<バージョン>__<説明>.sql` の形式です。各バージョンは一意でなければなりません。バージョンには整数、ドット区切り、アンダースコア区切りを使用できますが、プロジェクト内では一貫した方式を選びます。

~~~sql
-- V2__add_customer_email.sql
ALTER TABLE customer
    ADD COLUMN email VARCHAR(254);
~~~

恒久的な環境へ適用したファイルは編集せず、修正が必要な場合は新しいバージョンを追加してロールフォワードします。

### リピータブルマイグレーション

リピータブルマイグレーションにはバージョンがなく、チェックサムが変わると再実行されます。すべての未適用バージョン付きマイグレーションが完了した後、説明の名前順に実行されます。

~~~sql
-- R__customer_summary_view.sql
CREATE OR REPLACE VIEW customer_summary AS
SELECT id, email
FROM customer;
~~~

再実行できるよう、`CREATE OR REPLACE` などを使って冪等な内容にします。破壊的な DDL や一度だけ行うデータ更新には、バージョン付きマイグレーションを使用します。

### ベースラインマイグレーション

ベースラインマイグレーションは、多数のバージョン付きマイグレーションを適用した結果を、1つのスクリプトへまとめる機能です。

たとえば `B10__current_state.sql` がある場合、新しい環境は B10 を開始点として使用し、その後に V11 以降を適用できます。すでにマイグレーション履歴がある環境ではベースラインマイグレーションは無視されるため、既存環境の履歴を書き換えません。

ベースラインマイグレーションと、後述する `baseline` コマンドは別の機能です。

## 3. スキーマ履歴とチェックサム

Flyway は、既定では `flyway_schema_history` テーブルで次の情報を管理します。

- 適用したバージョンと説明
- マイグレーションの種類とファイル名
- 適用日時と実行ユーザー
- 実行時間と成功可否
- ファイル内容から計算したチェックサム

主な状態には次のものがあります。

| 状態 | 意味 |
| --- | --- |
| `Pending` | ファイルは存在するが、まだ適用されていない |
| `Success` | 正常に適用された |
| `Failed` | 適用に失敗した履歴がある |
| `Missing` | 適用済みのファイルが現在の検索場所に存在しない |
| `Outdated` | リピータブルマイグレーションが変更され、再適用を待っている |
| `Out of Order` | 通常のバージョン順とは異なる順序で適用された |

適用済みファイルの内容、名前、種類が変わると、保存された履歴との不一致を `validate` で検出できます。スキーマ履歴テーブルは監査と整合性確認の基準であり、通常の運用で直接編集しません。

## 4. 主要コマンド

CLI、Gradle、Maven では呼び出し方が異なりますが、中心となる操作は共通です。

| 操作 | CLI | Gradle plugin | 用途 |
| --- | --- | --- | --- |
| 状態確認 | `flyway info` | `./gradlew flywayInfo` | 適用済み、未適用、失敗などの状態を確認する |
| 検証 | `flyway validate` | `./gradlew flywayValidate` | ファイルと適用履歴の不一致を検出する |
| 適用 | `flyway migrate` | `./gradlew flywayMigrate` | 未適用マイグレーションを実行する |
| 履歴修復 | `flyway repair` | `./gradlew flywayRepair` | 原因を解消した後に履歴の失敗行やチェックサムを修復する |
| 既存 DB の登録 | `flyway baseline` | `./gradlew flywayBaseline` | Flyway 導入前から存在するスキーマへ履歴の開始点を設定する |
| スキーマ消去 | `flyway clean` | `./gradlew flywayClean` | 構成したスキーマ内のオブジェクトを削除する |

Gradle のタスクは Flyway Gradle プラグインを追加したプロジェクトでのみ利用できます。このリポジトリには同プラグインがないため、現在の `./gradlew tasks` には表示されません。

### 通常の実行順

接続先とマイグレーションの検索場所を設定した後、次の順で確認します。

~~~bash
flyway info
flyway validate
flyway migrate
flyway info
~~~

`migrate` も適用前に検証を行います。CI で `validate` を独立して実行すると、デプロイ前の段階でチェックサム不一致や削除されたファイルを明示的に検出できます。

### `repair` の使い方

`repair` はデータベースオブジェクトを元へ戻すコマンドではありません。主に次の処理をスキーマ履歴テーブルへ行います。

- 失敗したマイグレーションの履歴を削除する
- 適用済みマイグレーションのチェックサム、説明、種類を現在のファイルへ合わせる
- 見つからなくなったマイグレーションを削除済みとして記録する

失敗途中のテーブルやデータは自動修復されない場合があります。原因と実際のデータベース状態を調査し、不完全な変更を手動で解消してから、`migrate` と同じ検索場所を指定して `repair` を実行します。単に検証エラーを消す目的では使用しません。

### `clean` の使い方

`clean` は構成対象のスキーマからオブジェクトを削除する破壊的な操作です。個人用の一時データベースや CI の使い捨てデータベース以外では実行しないでください。

- 本番環境では無効にする
- 実行前に接続 URL、データベース名、スキーマ名を確認する
- 共有開発データベースを対象にしない
- Testcontainers など、破棄して再作成できる環境を優先する

## 5. CLI の基本設定

Flyway CLI は、設定ファイル、環境変数、コマンドライン引数などから設定を読み取れます。現在の CLI では、複数の接続先を TOML の environment として分けて管理できます。

~~~toml
[flyway]
locations = ["filesystem:sql"]
validateMigrationNaming = true
cleanDisabled = true

[environments.local]
url = "jdbc:postgresql://localhost:5432/app"
user = "app"
password = "local-development-only"
~~~

設定した `local` 環境を選択して実行します。

~~~bash
flyway -environment=local info
flyway -environment=local migrate
~~~

このパスワードはローカル構成の記述例です。実際の認証情報を Git 管理対象の設定ファイルやコマンド履歴へ残さず、環境変数、Flyway の resolver、CI/CD のシークレット管理機能などから渡してください。

CLI を使わず、コマンドライン引数で一時的に接続先を指定することもできます。

~~~bash
flyway \
  -url="jdbc:postgresql://localhost:5432/app" \
  -user="app" \
  -locations="filesystem:sql" \
  info
~~~

パスワードをコマンドライン引数へ書くと、シェル履歴やプロセス情報に残る可能性があるため避けてください。

## 6. プレースホルダー

プレースホルダーを使うと、マイグレーション内の値を実行時の設定で置換できます。既定の形式は `${name}` です。

~~~sql
CREATE TABLE ${appSchema}.audit_log (
    id BIGINT PRIMARY KEY,
    message VARCHAR(500) NOT NULL
);
~~~

CLI では、次のように値を渡せます。

~~~bash
flyway -placeholders.appSchema=app migrate
~~~

プレースホルダーはバージョン付き、リピータブル、SQL コールバックで利用できます。値の変更はリピータブルマイグレーションのチェックサムにも影響し、再実行の対象になります。

プレースホルダーは SQL のバインドパラメーターではなく、実行前の文字列置換です。テーブル名やスキーマ名に使う値は信頼できる設定から渡し、外部入力をそのまま埋め込まないでください。

## 7. コールバック

コールバックは、`migrate`、`validate`、`clean` などの処理前後にスクリプトを実行する仕組みです。たとえば、マイグレーション完了後の処理は `afterMigrate.sql` に記述します。

~~~sql
-- afterMigrate.sql
REFRESH MATERIALIZED VIEW customer_summary;
~~~

主な利用例は次のとおりです。

- プロシージャの再コンパイル
- マテリアライズド View の更新
- PostgreSQL の `VACUUM` などの保守処理
- 実行結果の監査や通知に必要な処理

スキーマの中核となる変更は、実行順と履歴が明確なバージョン付きマイグレーションへ記述します。コールバックは、ライフサイクルへ付随する処理に限定してください。また、`info` のような読み取り目的の操作に書き込みコールバックを追加しないでください。

## 8. Java ベースのマイグレーション

SQL だけでは扱いにくい BLOB／CLOB の変換や複雑なデータ再計算には、`JavaMigration` を実装できます。通常は `BaseJavaMigration` を継承します。

~~~java
package db.migration;

import java.sql.PreparedStatement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V3__NormalizeUsernames extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (PreparedStatement statement = context.getConnection()
                .prepareStatement("UPDATE users SET username = LOWER(username)")) {
            statement.executeUpdate();
        }
    }
}
~~~

Flyway から渡された Connection は閉じません。上の例は `PreparedStatement` だけを try-with-resources で閉じています。

Java ベースのマイグレーションは、既定ではチェックサムを持ちません。変更検出が必要な場合は `getChecksum()` を実装します。データベース固有の処理を Java に隠しすぎるとレビューや手動検証が難しくなるため、単純な DDL／DML には SQL を優先します。

## 9. 既存データベースへの導入

すでにテーブルやデータがあるデータベースへ初めて Flyway を導入する場合は、`baseline` コマンドで履歴の開始点を登録できます。

~~~bash
flyway -baselineVersion=10 baseline
~~~

この操作は V1 から V10 の SQL を実行するのではなく、対象スキーマがバージョン 10 相当であることを履歴へ記録します。その後の `migrate` では V11 以降が適用対象になります。

導入前に次を確認します。

1. 対象環境間のスキーマ差分を解消する
2. 現在のスキーマがどのバージョンに対応するか決める
3. バックアップと復旧手順を用意する
4. 本番と同等のコピーで `baseline` と後続の `migrate` を検証する
5. 対象 URL、スキーマ、開始バージョンをレビューする

`baselineOnMigrate` を有効にすると、履歴テーブルがない空ではないスキーマを `migrate` 時に自動でベースライン化できます。しかし、接続先の間違いを検出する安全策が弱くなるため、恒常的な有効化は避け、明示的な導入手順を優先します。

## 10. トランザクションと同時実行

Flyway は、データベースのロック機構を利用して複数プロセスによる同時マイグレーションを調整します。ただし、ロックがあることは、無停止で安全に変更できることを意味しません。大きなテーブルの変更や長時間のデータ更新は、アプリケーションのクエリを待たせる可能性があります。

各マイグレーションの失敗時にロールバックできる範囲は、対象データベースが DDL トランザクションをどこまでサポートするかによって異なります。失敗途中のオブジェクトが残るデータベースでは、状態を確認して手動で修復してから `repair` と再実行を行います。

互換性が必要なデプロイでは、次のように変更を複数段階へ分けます。

1. 新しい列やテーブルを追加する
2. 新旧のアプリケーションが共存できる状態でデータを移行する
3. アプリケーションの参照先を切り替える
4. 利用されなくなった列やテーブルを後のリリースで削除する

## 11. 開発から本番までの使い方

### 開発時

1. 最新のマイグレーションをローカル DB へ適用する
2. 一意な次バージョンのファイルを追加する
3. SQL とファイル名をレビューする
4. 空の DB と更新前バージョンの DB の両方で適用する
5. アプリケーションテストを実行する

### CI

- 命名規則と適用済み履歴を `validate` で検証する
- 使い捨て DB を作成し、先頭から `migrate` できることを確認する
- 更新前スキーマと代表データを用意し、アップグレードを確認する
- 適用後に制約、インデックス、データ変換をテストする

### 本番デプロイ

- 接続先と実行ユーザーの権限を確認する
- 適用予定のマイグレーションを `info` で確認する
- バックアップと復旧手順を確認する
- `migrate` の失敗を検知してデプロイを停止できるようにする
- 適用後のアプリケーション動作とデータベースメトリクスを監視する

## 12. エディションによる機能差

`migrate`、`info`、`validate`、`repair`、`baseline` などの基本的なマイグレーション管理は Community で利用できます。一方、Undo migration、Dry Run、差分検出、スキーマモデルからの生成などには、有償エディションが必要な機能があります。

エディションやバージョンによって利用条件が変わる可能性があるため、導入前に公式のコマンド一覧とライセンスを確認してください。有償の `undo` に依存しない場合は、新しいバージョン付きマイグレーションで変更を戻すロールフォワードと、バックアップからの復旧を基本にします。

## 13. このプロジェクトでの利用

このプロジェクトでは、`spring-boot-starter-flyway` によってアプリケーション起動時にマイグレーションを実行します。

~~~text
src/main/resources/db/migration/
└── V1__create_security_tables.sql
~~~

ローカル開発とテストでは Testcontainers の PostgreSQL を使用し、本番では環境変数で指定した PostgreSQL を使用します。依存関係、Spring Boot の設定、起動方法については、[Spring Boot Flyway ガイド](../spring-ecosystem/spring-boot-flyway-guide.md)を参照してください。

## 14. 関連資料

- [Flyway Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
- [Flyway Commands](https://documentation.red-gate.com/flyway/reference/commands)
- [Flyway Schema History Table](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table)
- [Flyway Configuration](https://documentation.red-gate.com/flyway/reference/configuration)
- [Spring Boot Flyway ガイド](../spring-ecosystem/spring-boot-flyway-guide.md)
- [Testcontainers ガイド](testcontainers-guide.md)
