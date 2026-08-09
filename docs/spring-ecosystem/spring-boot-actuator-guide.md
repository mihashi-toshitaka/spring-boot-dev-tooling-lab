# Spring Boot Actuator ガイド

## 1. Actuator とは

Spring Boot Actuator は、稼働中のアプリケーションの状態確認、メトリクス収集、設定確認など、運用に必要な機能を提供します。

このプロジェクトでは、次の依存関係を `build.gradle.kts` に追加しています。バージョンは Spring Boot の依存関係管理に任せます。

~~~kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
~~~

## 2. 既定のエンドポイントを確認する

アプリケーションを起動します。

~~~bash
./gradlew bootRun
~~~

別のターミナルから、Actuator のディスカバリーページとヘルス情報を確認します。

~~~bash
curl http://localhost:8080/actuator
curl http://localhost:8080/actuator/health
~~~

既定のベースパスは `/actuator` です。HTTP で公開されるエンドポイントは、既定では `health` のみに制限されています。

## 3. エンドポイントのアクセスと公開

Actuator では、エンドポイントを利用可能にする設定と、HTTP や JMX に公開する設定は別に管理されます。

- `management.endpoint.<id>.access`: 個別エンドポイントへのアクセス可否を設定する
- `management.endpoints.access.default`: 全エンドポイントの既定アクセスを設定する
- `management.endpoints.access.max-permitted`: アクセス可能な最大範囲を制限する
- `management.endpoints.web.exposure.include`: HTTP で公開するエンドポイントを指定する
- `management.endpoints.web.exposure.exclude`: HTTP で公開しないエンドポイントを指定する

たとえば、`health`、`info`、`metrics` を HTTP で公開する場合は、`application.yaml` に次の設定を追加します。

~~~yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics"
~~~

`exclude` は `include` より優先されます。すべてを指定する `"*"` は便利ですが、機密情報や強力な操作を含むエンドポイントまで公開する可能性があるため、公開環境では必要なものだけを列挙してください。

## 4. 主なエンドポイント

| エンドポイント | 用途 |
| --- | --- |
| `health` | アプリケーションや依存サービスの稼働状態を確認する |
| `info` | アプリケーション情報を確認する |
| `metrics` | JVM、HTTP、プロセスなどのメトリクス名と値を確認する |
| `mappings` | Spring MVC のリクエストマッピングを確認する |
| `conditions` | 自動構成が適用・不適用になった理由を確認する |
| `configprops` | `@ConfigurationProperties` の内容を確認する |
| `env` | Spring Environment のプロパティを確認する |
| `loggers` | 実行中のログレベルを確認・変更する |
| `threaddump` | JVM のスレッドダンプを取得する |

`env`、`configprops`、`loggers`、`heapdump` などは、構成情報の漏えいや運用への影響につながります。認証、認可、ネットワーク制限なしで公開しないでください。

## 5. ヘルス情報

`/actuator/health` は、アプリケーションの総合的な稼働状態を返します。詳細情報は既定では表示されません。

認可された利用者だけに詳細を表示する場合は、次のように設定します。

~~~yaml
management:
  endpoint:
    health:
      show-details: when-authorized
~~~

`show-details` には `never`、`when-authorized`、`always` を指定できます。既定値は `never` です。公開環境で `always` を使う場合は、レスポンスに含まれる情報を確認し、適切なアクセス制御を設定してください。

### 独自のヘルスチェック

ドメイン固有の状態を確認する場合は、`HealthIndicator` を実装できます。Spring Boot 4 系では、ヘルス関連の型は `org.springframework.boot.health.contributor` パッケージにあります。

~~~java
package com.example.demo;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SampleHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up().withDetail("service", "available").build();
    }
}
~~~

ヘルスチェック内で時間のかかる処理を同期実行すると、ヘルスエンドポイント自体が遅くなります。外部サービスの確認には適切なタイムアウトを設定してください。

## 6. メトリクス

Actuator は Micrometer と連携し、JVM、システム、プロセス、HTTP リクエストなどのメトリクスを収集します。外部の監視システムがない場合は、単純なインメモリレジストリが使われます。

`metrics` を公開した後、次のようにメトリクス名と個別の値を確認できます。

~~~bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
~~~

### Prometheus と連携する場合

Prometheus 形式で取得する場合は、レジストリを追加します。

~~~kotlin
runtimeOnly("io.micrometer:micrometer-registry-prometheus")
~~~

さらに `prometheus` エンドポイントを明示的に公開します。

~~~yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,prometheus"
~~~

取得先は `/actuator/prometheus` です。Prometheus などの監視基盤を導入しない段階では、この追加依存関係は不要です。

## 7. 管理用ポートを分ける

業務 API と管理エンドポイントを別ポートに分ける場合は、次のように設定します。

~~~yaml
management:
  server:
    port: 8081
    address: "127.0.0.1"
~~~

この例では、管理エンドポイントをローカルホストの `8081` 番ポートだけで待ち受けます。`management.server.address` は、管理用ポートをメインポートと分けた場合に設定できます。

コンテナや別ホストの監視システムから接続する場合、`127.0.0.1` では到達できません。実行環境のネットワーク構成に合わせて、待受アドレス、ファイアウォール、認証を設計してください。

## 8. セキュリティ上の注意

既定で HTTP 公開されるのは `health` のみですが、エンドポイントを追加公開する場合は次を確認します。

- インターネットへ直接公開せず、管理ネットワークやプロキシで接続元を制限する
- Spring Security を導入して認証・認可を設定する
- 独自の `SecurityFilterChain` を定義した場合は、Actuator 用のルールも明示する
- `env` や `configprops` の値がマスクされても、アクセス制御の代わりにはならないと考える
- 管理操作が可能なエンドポイントは、必要性を確認してから公開する

Spring Security がクラスパスにあり、独自の `SecurityFilterChain` がない場合、Spring Boot は `/health` 以外の Actuator エンドポイントを保護します。独自のチェーンを定義すると自動構成は後退するため、アプリケーション側で保護範囲を管理します。

## 9. Kubernetes のプローブ

Kubernetes 環境では、次のヘルスグループを利用できます。

- `/actuator/health/liveness`: プロセスを再起動すべき状態かを確認する
- `/actuator/health/readiness`: トラフィックを受け付けられる状態かを確認する

メインポートにも `/livez` と `/readyz` を追加する場合は、次を設定します。

~~~yaml
management:
  endpoint:
    health:
      probes:
        add-additional-paths: true
~~~

データベースなどの外部依存を liveness に含めると、一時的な外部障害によって全インスタンスが再起動を繰り返す可能性があります。外部依存は readiness に含めるかを個別に判断してください。

## 10. AWS ECS で生死監視する例

Amazon ECS では、再起動・置換の判断と、リクエストの振り分け判断を分けて構成します。

| 監視元 | Actuator エンドポイント | 目的 |
| --- | --- | --- |
| ECS コンテナヘルスチェック | `/actuator/health/liveness` | プロセスが回復不能になった場合に、コンテナやタスクを置き換える |
| Application Load Balancer（ALB） | `/actuator/health/readiness` | リクエストを受け付けられないタスクをターゲットから一時的に外す |

### Actuator の設定

ECS 上でもプローブを明示的に有効化しておくと、実行環境によらず意図が明確になります。

~~~yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
~~~

この例では、Actuator を業務 API と同じ `8080` 番ポートで公開します。管理用ポートを分ける場合、管理用Webサーバーだけが正常で業務用Webサーバーが応答できない状態を見逃す可能性があります。その場合は `management.endpoint.health.probes.add-additional-paths=true` を設定し、メインポートの `/livez` と `/readyz` を監視してください。

### ECS タスク定義のコンテナヘルスチェック

タスク定義の対象コンテナに、次のような `healthCheck` を設定します。

~~~json
{
  "healthCheck": {
    "command": [
      "CMD-SHELL",
      "curl -fsS http://localhost:8080/actuator/health/liveness || exit 1"
    ],
    "interval": 30,
    "timeout": 5,
    "retries": 3,
    "startPeriod": 60
  }
}
~~~

このコマンドはコンテナ内で実行されるため、コンテナイメージに `curl` が必要です。`startPeriod` はアプリケーションの起動時間に合わせ、起動処理中の失敗が置換判定に数えられないように調整します。対象コンテナをタスク定義で `essential: true` にすると、そのコンテナのヘルス状態がタスク全体のヘルス判定に使われます。

liveness にはデータベースや外部 API の状態を含めないでください。共有する外部サービスの障害時に全タスクが同時に置き換えられ、障害を拡大する可能性があります。

### ALB ターゲットグループのヘルスチェック

ALB のターゲットグループには、次の値を設定します。数値は構成例であり、起動時間、復旧時間、必要な検知速度に合わせて調整してください。

~~~yaml
HealthCheckProtocol: HTTP
HealthCheckPort: traffic-port
HealthCheckPath: /actuator/health/readiness
Matcher:
  HttpCode: "200"
HealthCheckIntervalSeconds: 30
HealthCheckTimeoutSeconds: 5
HealthyThresholdCount: 2
UnhealthyThresholdCount: 3
~~~

readiness が `DOWN` または `OUT_OF_SERVICE` になると、Actuator は既定で HTTP `503` を返すため、成功コードを `200` にした ALB はそのタスクへの振り分けを停止できます。ALB のセキュリティグループからアプリケーションのポートへ到達できること、および Spring Security がヘルスチェックを拒否しないことも確認してください。

データベースなどを readiness の判定に含める場合は、必要な `HealthIndicator` だけを明示的に追加します。Spring Boot は既定では他のヘルスインジケーターを readiness グループへ追加しません。

~~~yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: "readinessState,db"
~~~

共有データベースの一時障害で全タスクを一斉にターゲットから外すことが適切か、フォールバック可能な機能まで停止扱いにしないかを検討してから追加してください。

### 起動直後の置換を防ぐ

Spring Boot の起動に時間がかかる場合は、ECSサービスの `healthCheckGracePeriodSeconds` も設定します。

~~~yaml
HealthCheckGracePeriodSeconds: 60
~~~

この猶予期間中、ECSサービススケジューラーはALBとコンテナの異常判定を無視します。値は実測した起動時間より少し長くし、タスクが準備完了になる前に停止されることを防ぎます。タスク定義の `startPeriod` とECSサービスの `healthCheckGracePeriodSeconds` は別の設定です。

### ECS 上での確認項目

- ECSサービスイベントに、ヘルスチェック失敗によるタスク置換が繰り返し記録されていないか
- ALBターゲットの状態と理由コードが `healthy` になっているか
- CloudWatch Logsでアプリケーションの起動完了時刻とヘルスチェック開始時刻を比較したか
- ALBのターゲット登録解除遅延とアプリケーションのシャットダウン猶予が整合しているか
- 必要なタスク数を維持したまま、1タスクの停止・置換を行えるか

## 11. VS Code での利用

Spring Tools for Visual Studio Code は、実行中の Spring Boot アプリケーションからライブ情報を取得できます。Actuator を追加したうえで、必要に応じて起動時の VM 引数に次を指定します。

~~~text
-Dspring.jmx.enabled=true
~~~

JMX のリモート公開は行わず、ローカル開発用途に限定してください。利用できる表示項目は、Spring Tools のバージョンとアプリケーション側の公開設定によって異なります。

## 12. 動作確認

依存関係とテストを確認します。

~~~bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew check
~~~

起動後は、まず既定のヘルスエンドポイントを確認します。

~~~bash
curl --fail http://localhost:8080/actuator/health
~~~

### よくある問題

- `404 Not Found`: 対象エンドポイントが HTTP に公開されているか、ベースパスとポートが正しいかを確認する
- `401 Unauthorized` / `403 Forbidden`: Spring Security の Actuator 向け認可ルールを確認する
- ヘルスの詳細が表示されない: `management.endpoint.health.show-details` の既定値は `never`
- `metrics` が表示されない: `management.endpoints.web.exposure.include` に `metrics` を追加する
- 管理ポートに接続できない: `management.server.port` と `management.server.address`、コンテナのポート公開を確認する

## 13. 公式ドキュメント

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)
- [Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Monitoring and Management over HTTP](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
- [Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Actuator REST API](https://docs.spring.io/spring-boot/api/rest/actuator/index.html)
- [Amazon ECS のコンテナヘルスチェック](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/healthcheck.html)
- [Application Load Balancer のターゲットヘルスチェック](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html)
- [Amazon ECS サービス定義のヘルスチェック猶予期間](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html)
