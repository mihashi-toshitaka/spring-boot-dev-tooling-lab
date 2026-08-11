# Spring Session + Valkey ガイド

## 1. 構成

このプロジェクトでは、`HttpSession` を Spring Session 経由で Valkey に保存します。Valkey は Redis プロトコルと互換性があるため、Spring Boot の Redis 向け自動構成を使用します。

実行環境ごとの構成は次のとおりです。

| 実行環境 | Valkey の起動・接続方法 |
| --- | --- |
| ローカル開発 | `bootTestRun` が Testcontainers で PostgreSQL と `valkey/valkey:8.1.9-alpine` を起動し、動的に割り当てられた接続先をアプリケーションへ渡す |
| 本番 | 通常のアプリケーション JAR を起動し、`production` プロファイルの `VALKEY_URL` で外部 Valkey へ接続する |

Testcontainers の設定とローカル起動クラスは `src/test` にあります。このため、Testcontainers とその設定は本番 JAR に含まれません。

## 2. 主な設定ファイル

- `build.gradle.kts`: Spring Session Data Redis と Testcontainers の依存関係
- `src/main/resources/application.yaml`: セッションの有効期間と Valkey キーの名前空間
- `src/main/resources/application-production.yaml`: 本番 Valkey の接続 URL
- `src/test/java/com/example/ValkeyTestcontainersConfiguration.java`: ローカル Valkey コンテナ
- `src/test/java/com/example/TestSpringBootDevToolingLabApplication.java`: ローカル起動用アプリケーション

Spring Session は Starter によって自動構成されるため、`@EnableRedisHttpSession` や独自の `RedisConnectionFactory` は定義していません。

## 3. ローカルで起動する

事前に Docker を起動し、次のコマンドを実行します。

~~~bash
./gradlew bootTestRun
~~~

アプリケーションは `http://localhost:8080` で起動します。PostgreSQL と Valkey のホスト側ポートは Testcontainers が動的に割り当て、`@ServiceConnection` が接続情報を Spring Boot へ渡します。固定ポートの設定は不要です。

`Ctrl+C` でアプリケーションを終了すると、PostgreSQL と Valkey のコンテナも終了します。通常の `bootRun` は Testcontainers を起動しないため、ローカル開発では `bootTestRun` を使用してください。

## 4. 本番で起動する

本番用 JAR を作成します。

~~~bash
./gradlew bootJar
~~~

外部 PostgreSQL と Valkey、初期ユーザーの設定を環境変数で渡し、`production` プロファイルを有効にして起動します。

~~~bash
DATABASE_URL='jdbc:postgresql://postgres.example.com:5432/app' \
DATABASE_USERNAME='app' \
DATABASE_PASSWORD='database-secret' \
VALKEY_URL='redis://user:valkey-secret@valkey.example.com:6379/0' \
APP_INITIAL_USER_USERNAME='admin' \
APP_INITIAL_USER_PASSWORD='application-secret' \
  java -jar build/libs/spring-boot-dev-tooling-lab-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=production
~~~

TLS 接続を使う環境では、接続先の仕様に合わせて `rediss://` 形式の URL を指定します。ユーザー名やパスワードに URL の予約文字が含まれる場合は、パーセントエンコーディングが必要です。

`application-production.yaml` の接続情報には既定値を設けていません。`production` プロファイルで接続先が未設定の場合は起動に失敗し、誤ってローカルホストへ接続することを防ぎます。認証設定の詳細は [Spring Security ログインガイド](spring-security-login-guide.md)を参照してください。

## 5. セッション設定

既定値は次のとおりです。

| 環境変数 | 既定値 | 用途 |
| --- | --- | --- |
| `SESSION_TIMEOUT` | `30m` | セッションの有効期間 |
| `SESSION_REDIS_NAMESPACE` | `spring-boot-dev-tooling-lab:session` | 共有 Valkey 上でセッションキーを分離する名前空間 |

たとえば、有効期間を 60 分に変更する場合は次のように指定します。

~~~bash
SESSION_TIMEOUT=60m ./gradlew bootTestRun
~~~

アプリケーションコードでは通常の `HttpSession` API を利用できます。文字列以外のオブジェクトをセッション属性として保存する場合、既定の Java シリアライザーで扱えるよう `Serializable` である必要があります。

## 6. テスト

Docker を起動した状態で、全テストを実行します。

~~~bash
./gradlew test
~~~

`SpringBootDevToolingLabApplicationTests` は次の流れを検証します。

1. HTTP リクエストでセッション属性を設定する
2. Valkey にセッションの Hash と属性が保存されたことを確認する
3. 応答の `SESSION` Cookie だけを次のリクエストへ渡し、属性を復元できることを確認する
4. セッションを削除し、Valkey のキーも削除されたことを確認する

この統合テストは実際の PostgreSQL と Valkey のコンテナを使用するため、Docker を利用できない環境では実行できません。
