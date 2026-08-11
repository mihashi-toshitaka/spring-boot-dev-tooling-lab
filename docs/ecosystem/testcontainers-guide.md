# Testcontainers ガイド

## 1. Testcontainers の役割

Testcontainers は、テストやローカル開発で必要なデータベース、メッセージブローカーなどをコンテナとして起動するためのライブラリです。実際のミドルウェアを使って、アプリケーションの接続設定やデータの読み書きを確認できます。

このプロジェクトでは、認証ユーザーを保存する PostgreSQL と、Spring Session の保存先となる Valkey を Testcontainers で起動します。コンテナのホスト側ポートは実行時に動的に割り当て、Spring Boot の Service Connection が接続情報をアプリケーションへ渡します。そのため、テスト用の固定ポートや接続 URL を設定する必要はありません。

Testcontainers のクラスと設定は `src/test` に置き、依存関係も `testImplementation` に限定しています。これにより、Testcontainers は本番用のアプリケーション JAR に含まれません。

## 2. 前提条件

実行前に、Docker Engine または Docker Desktop など、Testcontainers が利用できるコンテナ実行環境を起動してください。WSL2 から Docker Desktop を使う場合は、対象の WSL ディストリビューションとの連携も有効にします。

次のコマンドが成功すれば、Docker に接続できます。

~~~bash
docker version
docker run --rm hello-world
~~~

初回実行時はコンテナイメージを取得するため、インターネット接続が必要です。

## 3. 依存関係

このプロジェクトでは、`build.gradle.kts` に次の依存関係を定義しています。

~~~kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
~~~

それぞれの役割は次のとおりです。

| 依存関係 | 役割 |
| --- | --- |
| `spring-boot-testcontainers` | `@ServiceConnection` など、Spring Boot と Testcontainers の連携機能を提供する |
| `testcontainers` | `GenericContainer` など、コンテナの定義と制御に必要な API を提供する |
| `testcontainers-postgresql` | PostgreSQL コンテナとJDBC接続情報を提供する |

バージョンは Spring Boot の依存関係管理に任せています。個別にバージョンを指定する場合は、Spring Boot が管理するバージョンとの互換性を確認してください。

## 4. コンテナを定義する

Valkey コンテナは `src/test/java/com/example/ValkeyTestcontainersConfiguration.java`、PostgreSQL コンテナは `src/test/java/com/example/PostgresTestcontainersConfiguration.java` に定義しています。

~~~java
@TestConfiguration(proxyBeanMethods = false)
class ValkeyTestcontainersConfiguration {

    private static final DockerImageName VALKEY_IMAGE =
            DockerImageName.parse("valkey/valkey:8.1.9-alpine");
    private static final int VALKEY_PORT = 6379;

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> valkeyContainer() {
        return new GenericContainer<>(VALKEY_IMAGE).withExposedPorts(VALKEY_PORT);
    }
}
~~~

各要素には次の役割があります。

- `@TestConfiguration`: テストや開発時だけ読み込む Spring の構成であることを示す
- `@Bean`: コンテナの起動と停止を Spring Boot のライフサイクルに連携させる
- `@ServiceConnection(name = "redis")`: 動的に決まったホスト名とポートから Redis 用の接続情報を生成する
- `withExposedPorts(6379)`: コンテナ内の Valkey ポートを、動的に割り当てられたホスト側ポートへ公開する

`GenericContainer` だけでは接続先サービスの種類を判別できないため、`@ServiceConnection` の `name` に `redis` を指定しています。Spring Boot はこの情報を使い、Redis 接続用のプロパティより Service Connection の接続情報を優先して自動構成します。

PostgreSQL は専用の `PostgreSQLContainer` を使用するため、名前の指定は不要です。`@ServiceConnection` から DataSource と Flyway に必要なJDBC接続情報が自動構成されます。

コンテナイメージには `latest` ではなく明示的なタグを指定します。これにより、開発者や CI の間で同じバージョンを使い、イメージ更新による予期しないテスト結果の変化を防げます。

## 5. 統合テストで使用する

テストクラスから構成を読み込むには、`@Import` を使用します。

~~~java
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, ValkeyTestcontainersConfiguration.class})
class SpringBootDevToolingLabApplicationTests {
    // テストコード
}
~~~

テストを実行すると、次の順序で処理されます。

1. Spring Boot が PostgreSQL と Valkey のコンテナを起動する
2. `@ServiceConnection` が両サービスの接続情報を Spring Boot へ渡す
3. アプリケーションコンテキストとテストが実行される
4. テスト終了時にコンテナが停止する

Docker を起動した状態で、次のコマンドを実行します。

~~~bash
./gradlew test
~~~

現在の `SpringBootDevToolingLabApplicationTests` は、PostgreSQLユーザーによる認証と、HTTP セッションが実際に Valkey へ保存され次のリクエストで復元されることを検証します。単に Spring のコンテキストが起動することだけでなく、アプリケーションと外部サービス間の接続およびデータ操作まで確認する統合テストです。

## 6. ローカル開発で使用する

`src/test/java/com/example/TestSpringBootDevToolingLabApplication.java` は、本番用アプリケーションへ Testcontainers の構成を追加する開発用エントリーポイントです。

~~~java
public static void main(String[] args) {
    SpringApplication.from(SpringBootDevToolingLabApplication::main)
            .with(
                    PostgresTestcontainersConfiguration.class,
                    ValkeyTestcontainersConfiguration.class)
            .run(args);
}
~~~

次のコマンドで、PostgreSQL、Valkey、アプリケーションをまとめて起動できます。

~~~bash
./gradlew bootTestRun
~~~

アプリケーションを終了すると、コンテナも停止します。通常の `bootRun` は `src/test` の構成を読み込まないため、Testcontainers を使うローカル開発では `bootTestRun` を使用してください。

## 7. ポートとコンテナの確認

ホスト側ポートは実行ごとに変わるため、`localhost:6379` への接続を前提にしないでください。アプリケーションからの接続には `@ServiceConnection` が提供する情報を使います。

起動中のコンテナは、別のターミナルから確認できます。

~~~bash
docker ps
~~~

コンテナのログを確認する場合は、`docker ps` で得られたコンテナ ID を指定します。

~~~bash
docker logs <container-id>
~~~

通常は Testcontainers がコンテナを自動的に削除します。プロセスが強制終了されてコンテナが残った場合は、対象がこのプロジェクトのテスト用コンテナであることを確認してから停止、削除してください。

## 8. CI で実行する際の注意点

CI でも、テストプロセスから Docker 互換 API に接続できる必要があります。利用する CI の実行方式に合わせて、Docker デーモン、権限、イメージ取得用のネットワークを準備してください。

コンテナを利用できないことを理由に統合テストを常にスキップすると、実際の接続設定やミドルウェアとの互換性を検証できません。通常の CI ではコンテナ実行環境を用意し、ローカルと同じ `./gradlew test` を実行する構成を推奨します。

## 9. トラブルシューティング

### Docker に接続できない

`docker version` を実行し、Client だけでなく Server の情報も表示されることを確認します。Docker Desktop を利用している場合は、Docker Desktop の起動状態と WSL Integration の設定を確認してください。

### コンテナイメージを取得できない

ネットワーク、プロキシ、コンテナレジストリへの認証を確認します。原因を切り分けるには、ガイドで指定しているイメージを `docker pull` で直接取得できるか確認します。

~~~bash
docker pull valkey/valkey:8.1.9-alpine
~~~

### Valkey の接続先が固定ポートになっている

テスト構成に `@ServiceConnection(name = "redis")` が付いていることと、テストクラスが `ValkeyTestcontainersConfiguration` を読み込んでいることを確認します。Testcontainers ではホスト側ポートを動的に割り当てるため、テスト用に `spring.data.redis.port=6379` を固定する必要はありません。

### テスト終了後もコンテナが見える

テストがまだ実行中であれば正常です。テストプロセスが終了しても残る場合は、Gradle と Docker のログを確認し、テストが強制終了されていないかを調べます。

## 10. 関連ガイド

Valkey を Spring Session の保存先として利用する設定や、本番環境との切り替えについては、[Spring Session + Valkey ガイド](../spring-ecosystem/spring-session-valkey-guide.md)を参照してください。
