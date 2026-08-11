# Spring Security ログインガイド

## 1. 構成

このプロジェクトの認証は、用途ごとに次のように分けています。

| 対象 | 認証方式 | 状態管理 |
| --- | --- | --- |
| `/greeting` などの画面 | Spring Security のデフォルトフォームログイン | `HttpSession` を Valkey に保存 |
| `GET /api/private` | HTTP Basic | ステートレス |
| `GET /api/public` | 認証不要 | ステートレス |

ログインユーザーは PostgreSQL の `users`、`authorities` テーブルに保存します。テーブルは起動時に Flyway が作成し、パスワードは平文ではなく `{bcrypt}` 形式で保存します。

## 2. ローカルで起動する

Docker を起動してから、次のコマンドを実行します。

~~~bash
./gradlew bootTestRun
~~~

Testcontainers が PostgreSQL と Valkey を起動し、アプリケーションへ接続情報を渡します。ブラウザーで `http://localhost:8080/greeting` を開くと、Spring Security のデフォルトログイン画面へ移動します。

ローカル確認用の初期ユーザーは次のとおりです。

| 項目 | 既定値 |
| --- | --- |
| ユーザー名 | `user` |
| パスワード | `password` |

初期ユーザーは PostgreSQL に存在しない場合だけ作成します。値を変更する場合は、最初の起動前に環境変数を指定してください。

~~~bash
APP_INITIAL_USER_USERNAME=developer \
APP_INITIAL_USER_PASSWORD='local-secret' \
  ./gradlew bootTestRun
~~~

## 3. APIを確認する

公開APIは認証なしでアクセスできます。

~~~bash
curl --fail-with-body http://localhost:8080/api/public
~~~

保護APIは認証情報なしでは `401 Unauthorized` と `WWW-Authenticate` ヘッダーを返します。

~~~bash
curl --include http://localhost:8080/api/private
curl --fail-with-body --user user:password http://localhost:8080/api/private
~~~

HTTP Basic は資格情報を暗号化しないため、本番環境では必ず HTTPS 経由で使用してください。現在のサンプルAPIは読み取り専用の `GET` であり、CSRF保護も有効なままです。

## 4. 本番設定

本番用JARを作成し、PostgreSQL、Valkey、初期ユーザーの値を環境変数で渡します。

~~~bash
./gradlew bootJar

DATABASE_URL='jdbc:postgresql://postgres.example.com:5432/app' \
DATABASE_USERNAME='app' \
DATABASE_PASSWORD='database-secret' \
VALKEY_URL='rediss://user:valkey-secret@valkey.example.com:6379/0' \
APP_INITIAL_USER_USERNAME='admin' \
APP_INITIAL_USER_PASSWORD='application-secret' \
  java -jar build/libs/spring-boot-dev-tooling-lab-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=production
~~~

`production` プロファイルでは、上記の環境変数に既定値を設けていません。初期ユーザーのパスワードを後から環境変数だけで変更しても、既存ユーザーは更新されません。運用時のユーザー追加やパスワード変更は、管理機能または適切に管理されたSQLで行ってください。

## 5. 主な実装ファイル

- `src/main/java/com/example/security/SecurityConfiguration.java`: フォーム認証、Basic認証、PostgreSQLユーザーの設定
- `src/main/java/com/example/api/controller/AuthenticationApiController.java`: 公開APIと保護API
- `src/main/resources/db/migration/V1__create_security_tables.sql`: 認証テーブルのFlyway migration
- `src/main/resources/application.yaml`: ローカル既定値とValkeyセッション設定
- `src/main/resources/application-production.yaml`: 本番接続設定
- `src/test/java/com/example/PostgresTestcontainersConfiguration.java`: ローカル・テスト用PostgreSQL
- `src/test/java/com/example/ValkeyTestcontainersConfiguration.java`: ローカル・テスト用Valkey

## 6. テスト

Docker を起動した状態で実行します。

~~~bash
./gradlew test
~~~

テストでは、フォームログインとValkeyへのSecurity Context保存、公開API、Basic認証必須API、PostgreSQL上のユーザー認証を確認します。
