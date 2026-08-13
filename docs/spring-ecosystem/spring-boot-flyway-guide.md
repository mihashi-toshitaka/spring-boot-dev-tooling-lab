# Spring Boot Flyway ガイド

## 1. Flyway の役割

Flyway は、データベーススキーマの変更をバージョン付きのマイグレーションとして管理し、環境ごとに同じ順序で適用するためのツールです。

このプロジェクトでは、Spring Boot の起動時に Flyway を実行し、PostgreSQL に Spring Security 用の `users` テーブルと `authorities` テーブルを作成します。Flyway がスキーマを準備した後、アプリケーションの `ApplicationRunner` が初期ユーザーを登録します。

## 2. 依存関係

`build.gradle.kts` には、次の依存関係を定義しています。バージョンは Spring Boot の依存関係管理に任せます。

~~~kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
~~~

それぞれの役割は次のとおりです。

| 依存関係 | 役割 |
| --- | --- |
| `spring-boot-starter-flyway` | Flyway と Spring Boot の自動構成を有効にし、起動時にマイグレーションを実行する |
| `spring-boot-starter-jdbc` | `DataSource` と JDBC アクセスの自動構成を提供する |
| `flyway-database-postgresql` | Flyway の PostgreSQL 対応を提供する |
| `postgresql` | PostgreSQL の JDBC ドライバーを提供する |

PostgreSQL のようにデータベース固有の Flyway モジュールが用意されている場合は、Starter に加えてそのモジュールが必要です。

## 3. 起動時の処理

アプリケーションを起動すると、Spring Boot と Flyway は次の順序で処理します。

1. `spring.datasource` または Service Connection から `DataSource` を構成する
2. `classpath:db/migration` からマイグレーションを検索する
3. 適用済みマイグレーションとの整合性を検証する
4. 未適用のマイグレーションをバージョン順に実行する
5. マイグレーションに依存するアプリケーションの Bean を初期化する

Flyway は、既定では `flyway_schema_history` テーブルに適用済みバージョン、スクリプト名、チェックサム、実行結果などを記録します。次の SQL で履歴を確認できます。

~~~sql
SELECT installed_rank, version, description, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
~~~

マイグレーションまたは検証に失敗すると、アプリケーションの起動も失敗します。ログに表示された対象スクリプトと SQL エラーを確認してください。

## 4. このプロジェクトのマイグレーション

マイグレーションは、次のディレクトリに配置します。

~~~text
src/main/resources/db/migration/
└── V1__create_security_tables.sql
~~~

`V1__create_security_tables.sql` は、`JdbcUserDetailsManager` が利用する次のテーブルを作成します。

- `users`: ユーザー名、パスワード、利用可否を保存する
- `authorities`: ユーザーに割り当てた権限を保存する

初期ユーザーはマイグレーションへ固定値として書き込まず、`SecurityConfiguration` の `ApplicationRunner` が設定値を読み取って登録します。これにより、パスワードをマイグレーションファイルや Git の履歴へ残さずに済みます。

## 5. マイグレーションファイルを追加する

バージョン付き SQL マイグレーションは、次の形式で命名します。

~~~text
V<バージョン>__<説明>.sql
~~~

ファイル名の各要素には次の意味があります。

| 要素 | 例 | 説明 |
| --- | --- | --- |
| 接頭辞 | `V` | バージョン付きマイグレーションを表す |
| バージョン | `2` | 既存の最大バージョンより大きい一意な値を指定する |
| 区切り | `__` | バージョンと説明の間にアンダースコアを2つ置く |
| 説明 | `add_last_login_at` | 変更内容を短く表す |
| 拡張子 | `.sql` | SQL マイグレーションを表す |

たとえば、`users` テーブルへ最終ログイン日時を追加する場合は、`src/main/resources/db/migration/V2__add_last_login_at.sql` を作成します。

~~~sql
ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;
~~~

この SQL は追加例であり、現在のプロジェクトには適用していません。複数人で同時にマイグレーションを作成する場合は、バージョンの重複を避けるため、作業開始時だけでなくマージ前にも番号を確認してください。

## 6. 適用済みファイルを変更しない

Flyway は適用した SQL のチェックサムを `flyway_schema_history` に保存し、次回起動時にクラスパス上のファイルと照合します。そのため、共有環境へ一度でも適用したマイグレーションは、次の操作を行わないでください。

- SQL の内容を編集する
- ファイル名やバージョンを変更する
- ファイルを削除する
- 同じバージョンの別ファイルへ置き換える

スキーマを修正する場合は、既存ファイルを履歴として残し、新しいバージョンのマイグレーションを追加します。たとえば、V2 の変更を取り消す必要がある場合も、V2 を編集せず、取り消し内容を V3 として記述します。

ローカルだけで適用したファイルを開発中に変更する場合は、対象データベースが破棄可能であることを確認し、Testcontainers のような一時データベースを作り直してください。共有データベースの履歴を安易に `repair` で書き換えると、実際のスキーマと履歴の不一致を見えなくする可能性があります。

## 7. 接続設定

Flyway 専用の接続設定がなければ、Flyway は `spring.datasource` の接続先を使用します。このプロジェクトの通常の設定は次のとおりです。

~~~yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/spring_boot_dev_tooling_lab}
    username: ${DATABASE_USERNAME:app}
    password: ${DATABASE_PASSWORD:app}
~~~

Flyway の実行ユーザーをアプリケーションの接続ユーザーと分ける場合は、`spring.flyway.url`、`spring.flyway.user`、`spring.flyway.password` を設定できます。パスワードは設定ファイルへ直接記述せず、環境変数や実行環境のシークレット管理機能から渡してください。

起動時にマイグレーションを実行する構成では、Flyway の接続ユーザーに必要な DDL 権限が必要です。アプリケーションの接続ユーザーと権限を分離する場合は、マイグレーション完了後の通常処理が低い権限で実行されることも確認してください。

## 8. 主な設定

このプロジェクトは既定値で動作するため、現在は `spring.flyway` の設定を追加していません。必要に応じて、次のプロパティを使用できます。

| プロパティ | 既定値 | 用途 |
| --- | --- | --- |
| `spring.flyway.enabled` | `true` | Flyway の自動実行を有効または無効にする |
| `spring.flyway.locations` | `classpath:db/migration` | マイグレーションの検索場所を指定する |
| `spring.flyway.table` | `flyway_schema_history` | スキーマ履歴テーブル名を指定する |
| `spring.flyway.validate-on-migrate` | `true` | マイグレーション前に適用済み履歴を検証する |
| `spring.flyway.validate-migration-naming` | `false` | 命名規則に違反したファイルがある場合に失敗させる |
| `spring.flyway.clean-disabled` | `true` | Flyway によるスキーマの削除を無効にする |
| `spring.flyway.baseline-on-migrate` | `false` | 履歴テーブルがない既存スキーマを自動的にベースライン化する |

命名ミスによってファイルが無視されることを早期に検出したい場合は、次の設定を検討できます。

~~~yaml
spring:
  flyway:
    validate-migration-naming: true
~~~

`baseline-on-migrate` は、Flyway を導入する前から存在するデータベースを移行対象にするときに使用する設定です。接続先を間違えても既存スキーマを自動的に受け入れてしまう安全上のリスクがあるため、通常の新規環境では有効にしないでください。

## 9. ローカルで確認する

このプロジェクトでは、Testcontainers を使って PostgreSQL とアプリケーションをまとめて起動できます。事前に Docker を起動し、次のコマンドを実行します。

~~~bash
./gradlew bootTestRun
~~~

`PostgresTestcontainersConfiguration` の `@ServiceConnection` が、動的に割り当てられた JDBC 接続情報を Spring Boot へ渡します。Flyway も同じ接続情報を使い、新しい PostgreSQL コンテナへ V1 からマイグレーションを適用します。

通常の `bootRun` は Testcontainers を起動しません。`./gradlew bootRun` を使う場合は、`application.yaml` の接続設定に対応する PostgreSQL を別途準備してください。

このプロジェクトには Flyway の Gradle プラグインを追加していないため、`flywayMigrate` や `flywayInfo` などの Gradle タスクはありません。マイグレーションは Spring Boot アプリケーションまたはテストの起動時に実行されます。

## 10. テスト

Docker を起動した状態で、次のコマンドを実行します。

~~~bash
./gradlew test
~~~

統合テストの起動時には、新しい PostgreSQL コンテナへ Flyway のマイグレーションが適用されます。その後、初期ユーザーが作成され、フォームログインと HTTP Basic 認証のテストが実行されます。テーブルを作成できない場合や初期ユーザーを保存できない場合は、アプリケーションコンテキストの起動または認証テストが失敗します。

マイグレーションを追加した場合は、少なくとも次を確認してください。

- 空のデータベースへ V1 からすべて適用できる
- 直前のバージョンから新しいバージョンへ更新できる
- アプリケーションが新旧スキーマの切り替わりを考慮してデプロイできる
- 制約、インデックス、データ更新が想定どおりである

Testcontainers によるテストは空のデータベースへの適用確認に向いています。既存データを変更するマイグレーションでは、更新前の状態と代表的なデータを用意したテストも追加してください。

## 11. 本番運用の注意点

本番では、`production` プロファイルを有効にし、次の環境変数で PostgreSQL の接続先を渡します。

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

アプリケーションの各インスタンスが起動時にマイグレーションを試みるため、複数インスタンスを同時にデプロイする場合は次を確認します。

- マイグレーション中のロックや長時間のテーブル更新が、既存トラフィックへ与える影響
- 旧バージョンと新バージョンのアプリケーションが一時的に共存できるスキーマ変更か
- マイグレーション失敗時に新しいインスタンスが起動しないことをデプロイ基盤が検知できるか
- 実行前にバックアップを取得し、復旧手順を確認しているか

列やテーブルの削除、型変更、巨大テーブルの一括更新は、アプリケーションのデプロイと一度に行わず、追加、データ移行、参照先の切り替え、削除を複数回のリリースへ分けることを検討してください。

## 12. トラブルシューティング

### PostgreSQL に対応していないというエラーになる

`org.flywaydb:flyway-database-postgresql` が `runtimeOnly` に含まれていることを確認します。JDBC ドライバーだけでは、Flyway の PostgreSQL 対応モジュールを代替できません。

### マイグレーションファイルが実行されない

ファイルが `src/main/resources/db/migration` にあり、`V2__description.sql` のような命名規則に従っていることを確認します。命名ミスを起動時エラーにする場合は、`spring.flyway.validate-migration-naming=true` を設定します。

### チェックサム不一致で起動できない

適用済みマイグレーションが編集されていないか、Git の差分とデータベースの `flyway_schema_history` を確認します。原則として適用済みファイルを元に戻し、必要な修正は新しいマイグレーションとして追加します。

### 空ではない既存データベースへ導入できない

既存スキーマの内容と、Flyway が管理を開始する基準バージョンを確認してからベースライン化します。`baseline-on-migrate` を常時有効にして回避せず、対象データベース、バックアップ、既存オブジェクト、履歴の開始点を明示した導入手順を作成してください。

### テストでデータベースへ接続できない

Docker が起動していることと、テストが `PostgresTestcontainersConfiguration` を読み込んでいることを確認します。Testcontainers の PostgreSQL はホスト側ポートを動的に割り当てるため、テスト用に `localhost:5432` を固定指定しません。

## 13. 関連資料

- [Flyway 主要機能ガイド](../ecosystem/flyway-guide.md)
- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Boot Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.data-migration)
- [Flyway Migrations](https://documentation.red-gate.com/fd/migrations-271585107.html)
- [Testcontainers ガイド](../ecosystem/testcontainers-guide.md)
- [Spring Security ガイド](spring-security-guide.md)
