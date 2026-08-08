# logback-spring.xml ガイド

このガイドは、本プロジェクトの [logback-spring.xml](../../src/main/resources/logback-spring.xml) を題材に、基本的な読み方からクラウド運用、セキュリティ上の注意までを説明します。設定例は、現在の [build.gradle.kts](../../build.gradle.kts) で使用している Spring Boot 4.1.0 を前提としています。Logback のバージョンは Spring Boot の依存関係管理に任せています。

## このガイドの読み方

- 1〜7 章では、現在の設定、ログパターン、SLF4J、ログレベル、Spring プロファイルを説明します
- 8 章では、ファイル出力、フィルター、非同期出力、MDC、構造化ログなどの代表的な設定を説明します
- 9 章では、クラウドサービスへログ管理を任せる構成を説明します
- 10〜11 章では、安全なログ出力と設定変更時の確認事項を説明します

## 1. logback-spring.xml とは

`logback-spring.xml` は、Spring Boot アプリケーションで利用する Logback の設定ファイルです。ログの出力先、出力形式、ログレベルなどを XML で定義できます。

Spring Boot はクラスパス直下にある `logback-spring.xml` を自動的に読み込みます。このファイル名を使用すると、Spring の環境情報を利用する `springProperty` や、プロファイルごとの設定を切り替える `springProfile` などの Spring Boot 拡張を使えます。

このプロジェクトでは、設定ファイルを次に配置しています。

~~~text
src/main/resources/logback-spring.xml
~~~

`spring-boot-starter-webmvc` に含まれる Spring Boot の標準ロギングによって Logback が利用できるため、Logback の依存関係を個別に追加する必要はありません。

`logback-spring.xml` は必須ではありません。配置しない場合は Spring Boot の標準設定が使われ、配置した場合はこのファイルで Logback の構成をカスタマイズします。標準設定だけを使う選択については 9.6 章で説明します。

## 2. 現在の設定

現在の設定は、アプリケーション名を含むログを UTF-8 のコンソールへ出力します。

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty name="applicationName" source="spring.application.name" defaultValue="application"/>

    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] ${applicationName} %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <charset>UTF-8</charset>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
~~~

設定の対応関係は次のとおりです。

| 設定 | 内容 |
| --- | --- |
| `springProperty` | Spring の環境から `spring.application.name` を取得する。値がない場合は `application` を使用する |
| `CONSOLE_LOG_PATTERN` | コンソールへ出力するログの形式を定義する |
| `ConsoleAppender` | 標準出力へログを出力する |
| `charset` | ログの文字コードを UTF-8 にする |
| `root level="INFO"` | アプリケーション全体の既定ログレベルを INFO にする |
| `appender-ref` | ルートロガーから `CONSOLE` アペンダーへ出力する |

## 3. ログ出力形式

`CONSOLE_LOG_PATTERN` では、次の項目を順番に出力しています。

~~~text
2026-08-08T17:18:36.374+09:00 INFO  [main] spring-boot-dev-tooling-lab c.e.SpringBootDevToolingLabApplication - Started application
~~~

| パターン | 内容 |
| --- | --- |
| `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}` | 日付、時刻、ミリ秒、タイムゾーンのオフセット |
| `%-5level` | ログレベル。幅 5 で左寄せする |
| `[%thread]` | ログを出力したスレッド名 |
| `${applicationName}` | `spring.application.name` の値 |
| `%logger{36}` | ロガー名。長い場合は 36 文字程度に省略する |
| `%msg` | ログメッセージ |
| `%n` | 改行 |

## 4. アプリケーションからログを出力する

Java コードからは、Logback のクラスを直接使用せず SLF4J の API を使用します。実際のロギング実装を Logback に隠蔽できるため、設定や実装を変更しやすくなります。

~~~java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleService.class);

    public void service(String operationId) {
        LOGGER.info("サービスを開始します。operationId={}", operationId);
    }
}
~~~

メッセージへ値を埋め込むときは、文字列連結ではなく `{}` のプレースホルダーを使用します。不要な文字列生成を避けられ、ログレベルが無効な場合にも効率的です。

例外を記録するときは、例外オブジェクトを最後の引数に渡します。

~~~java
logger.error("データの取得に失敗しました。operationId={}", operationId, exception);
~~~

パスワード、アクセストークン、個人情報などの機密情報はログへ出力しません。

## 5. ログを確認する

アプリケーションを起動すると、Spring Boot の起動ログがコンソールへ出力されます。

~~~bash
./gradlew bootRun
~~~

別のターミナルからエンドポイントの応答を確認できます。

~~~bash
curl http://localhost:8080/test
~~~

現在の `/test` の実装自体はアプリケーションログを出力しません。4 章のようなログ出力を実装した場合は、エンドポイントへアクセスしたターミナルではなく、`bootRun` を実行しているターミナルでログを確認します。

テスト実行時にも `logback-spring.xml` が読み込まれます。ログは Gradle によって捕捉され、実行条件によってコンソールまたはテストレポートへ出力されます。

~~~bash
./gradlew test
~~~

## 6. ログレベルを変更する

ログレベルには、一般的に次の順で詳細度があります。

~~~text
TRACE < DEBUG < INFO < WARN < ERROR
~~~

`root` のレベルを変更すると、アプリケーション全体の既定値を変更できます。

~~~xml
<root level="DEBUG">
    <appender-ref ref="CONSOLE"/>
</root>
~~~

特定のパッケージだけを詳細にする場合は、`logger` を追加します。

~~~xml
<logger name="com.example" level="DEBUG"/>
~~~

ログレベルだけを環境ごとに変更する場合は、XML を編集せず `application.yaml` で管理する方法もあります。

~~~yaml
logging:
  level:
    root: INFO
    com.example: DEBUG
~~~

環境変数では、例えば `LOGGING_LEVEL_ROOT=WARN` や `LOGGING_LEVEL_COM_EXAMPLE=DEBUG` のように上書きできます。クラス単位の設定は環境変数名へ正確に変換できない場合があるため、環境変数ではパッケージ単位の指定を基本にします。

通常は `INFO` または `WARN` を使用し、調査が必要な期間だけ `DEBUG` や `TRACE` に変更します。本番環境で詳細なログを常時出力すると、ログ量の増加や機密情報の出力につながるため注意してください。

## 7. Spring プロファイルごとに設定を分ける

`logback-spring.xml` では、Spring のプロファイルに応じて設定を切り替えられます。

~~~xml
<springProfile name="development">
    <logger name="com.example" level="DEBUG"/>
</springProfile>

<springProfile name="production">
    <logger name="com.example" level="INFO"/>
</springProfile>
~~~

プロファイルは、例えば次のように指定して起動します。

~~~bash
./gradlew bootRun --args='--spring.profiles.active=development'
~~~

プロファイルを利用する場合も、共通のアペンダーやログ形式は重複させず、差分となるログレベルだけをプロファイル側へ記述すると管理しやすくなります。

ログレベルだけを切り替える場合は、`application-development.yaml` などのプロファイル別設定で `logging.level` を変更する方が簡潔です。`springProfile` は、プロファイルごとにアペンダーや出力形式まで切り替える場合に使用します。

## 8. よく使われる代表的な設定

現在の設定に加えて、実際のアプリケーションでは次の設定がよく使われます。すべてを同時に追加する必要はありません。ログの収集方法、実行環境、必要な保持期間に応じて選択します。

この章で XML 宣言と `<configuration>` を省略している例は、現在の `logback-spring.xml` の `<configuration>` 内へ追加・置換する設定断片です。参照しているプロパティやアペンダーが定義済みか確認して使用します。

| 設定 | 主な用途 |
| --- | --- |
| Spring Boot 標準設定の `include` | Spring Boot と同じ既定値やアペンダーを再利用する |
| `springProperty` | `application.yaml` や環境変数から値を取得する |
| `RollingFileAppender` | ログをファイルへ出力し、日付やサイズでローテーションする |
| `ThresholdFilter` / `LevelFilter` | アペンダーごとに出力するログを絞り込む |
| `additivity` | 親ロガーへの伝播を制御し、ログの二重出力を防ぐ |
| `AsyncAppender` | ファイル出力などを非同期化する |
| MDC / key-value | リクエスト ID などのコンテキストをログへ付与する |
| 例外・呼び出し元パターン | スタックトレースやソース位置の出力方法を調整する |
| 構造化ログ | JSON 形式でログ収集基盤へ渡しやすくする |
| Logback 内部ステータス | XML 設定の読み込みエラーを調査する |

### 8.1 Spring Boot の標準設定を再利用する

Spring Boot には、Logback 向けの既定パターンとアペンダー設定が含まれています。独自設定を最小限にしたい場合は、`include` で再利用できます。

~~~xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
~~~

`defaults.xml` には、Spring Boot 標準のログパターン、文字コード、色付けや例外表示に使う変換ルールなどが定義されています。`console-appender.xml` は `CONSOLE` という名前のアペンダーを定義します。

現在の設定にも `CONSOLE` アペンダーがあるため、`console-appender.xml` を利用する場合は現在の `CONSOLE` 定義と置き換えます。同じ名前のアペンダーを重複して定義しません。

Spring Boot の既定値を読み込んだ後で、必要なプロパティだけを上書きすることもできます。

~~~xml
<include resource="org/springframework/boot/logging/logback/defaults.xml"/>

<property name="CONSOLE_LOG_PATTERN"
          value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger{36} - %msg%n"/>

<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
~~~

### 8.2 Spring の環境から設定値を取得する

`springProperty` を使うと、`application.yaml`、環境変数、コマンドライン引数などから構成された Spring の `Environment` を参照できます。

~~~xml
<springProperty scope="context"
                name="logPath"
                source="logging.file.path"
                defaultValue="logs"/>
~~~

取得した値は `${logPath}` のように参照します。

~~~xml
<file>${logPath}/application.log</file>
~~~

| 属性 | 内容 |
| --- | --- |
| `name` | Logback 内で参照する変数名 |
| `source` | Spring のプロパティ名。`logging.file.path` のような kebab-case 形式で指定する |
| `defaultValue` | Spring の環境に値がない場合の既定値 |
| `scope` | 変数の有効範囲。複数箇所から使う場合は `context` が分かりやすい |

### 8.3 ファイル出力とローテーション

ファイルへ出力する場合は、単純な `FileAppender` よりも、古いログを退避・削除できる `RollingFileAppender` が一般的です。次の例は、日単位かつファイルサイズ単位でログをローテーションします。

~~~xml
<springProperty scope="context"
                name="logPath"
                source="logging.file.path"
                defaultValue="logs"/>

<property name="FILE_LOG_PATTERN" value="${CONSOLE_LOG_PATTERN}"/>

<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${logPath}/application.log</file>
    <append>true</append>

    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>${logPath}/archive/application-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>30</maxHistory>
        <totalSizeCap>1GB</totalSizeCap>
        <cleanHistoryOnStart>false</cleanHistoryOnStart>
    </rollingPolicy>

    <encoder>
        <charset>UTF-8</charset>
        <pattern>${FILE_LOG_PATTERN}</pattern>
    </encoder>
</appender>

<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
~~~

| 設定 | 内容 |
| --- | --- |
| `file` | 現在書き込み中のログファイル |
| `append` | `true` の場合、起動時に既存ファイルへ追記する |
| `fileNamePattern` | アーカイブファイル名。サイズと時間を併用する場合は `%d` と `%i` の両方が必要 |
| `maxFileSize` | 1 ファイルの最大サイズ |
| `maxHistory` | 保持する期間数。この例では日単位なので 30 日分 |
| `totalSizeCap` | アーカイブログ全体の最大容量 |
| `cleanHistoryOnStart` | 起動時にも保持期限を超えたアーカイブを削除するか |

この例では簡単にするため、`FILE_LOG_PATTERN` に現在のコンソール用パターンを再利用しています。コンソールとファイルで形式を分ける場合は、`FILE_LOG_PATTERN` を独立して定義します。

コンテナ環境では、標準出力のログをプラットフォーム側で収集する構成が一般的です。その場合、アプリケーション自身によるファイル出力は不要なことがあります。ファイル出力を採用する場合は、保存先の永続性、ディスク容量、複数プロセスからの書き込みも確認します。

### 8.4 アペンダーごとにログを絞り込む

`ThresholdFilter` は、指定したレベル未満のログを除外します。例えばファイルには INFO 以上を出し、コンソールには WARN 以上だけを出す、といった構成に使えます。

~~~xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>WARN</level>
    </filter>
    <encoder>
        <charset>UTF-8</charset>
        <pattern>${CONSOLE_LOG_PATTERN}</pattern>
    </encoder>
</appender>
~~~

`LevelFilter` は、指定したレベルだけを対象にします。次の例では ERROR だけを出力します。

~~~xml
<filter class="ch.qos.logback.classic.filter.LevelFilter">
    <level>ERROR</level>
    <onMatch>ACCEPT</onMatch>
    <onMismatch>DENY</onMismatch>
</filter>
~~~

`logger` や `root` のレベルはログイベントを生成するかどうかを決め、アペンダーのフィルターは生成済みのイベントをその出力先へ流すかどうかを決めます。

### 8.5 additivity で二重出力を防ぐ

子ロガーのログは、既定では親ロガーとルートロガーのアペンダーにも伝播します。特定用途のログを専用ファイルだけに出す場合は、`additivity="false"` を指定します。

~~~xml
<logger name="com.example.audit" level="INFO" additivity="false">
    <appender-ref ref="AUDIT_FILE"/>
</logger>
~~~

この断片を使用するには、`AUDIT_FILE` という名前のアペンダーを別途定義する必要があります。

`additivity="false"` を指定しない場合、`AUDIT_FILE` とルートロガーの `CONSOLE` の両方へ同じログが出力されます。専用アペンダーを設定したロガーでログが重複するときは、最初に `additivity` を確認します。

### 8.6 ログ出力を非同期化する

`AsyncAppender` はログイベントをキューへ入れ、参照先のアペンダーへ別スレッドから渡します。ファイルやネットワークへの出力待ちがアプリケーション処理へ与える影響を抑えたい場合に使用します。

~~~xml
<appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>1024</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <neverBlock>false</neverBlock>
    <maxFlushTime>5000</maxFlushTime>
    <includeCallerData>false</includeCallerData>
    <appender-ref ref="FILE"/>
</appender>

<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="ASYNC_FILE"/>
</root>
~~~

`AsyncAppender` は参照先となるアペンダーが必要です。この例では `FILE` を直接ルートロガーへも接続するとファイルへ二重出力されるため、ルートロガーからは `ASYNC_FILE` だけを参照します。

| 設定 | 内容 |
| --- | --- |
| `queueSize` | キューへ保持できるログイベント数 |
| `discardingThreshold` | キュー残量が少ないときに低レベルログを破棄する境界。`0` は自動破棄を無効にする |
| `neverBlock` | `true` の場合、キュー満杯時に処理を待たずログを破棄する |
| `maxFlushTime` | 終了時にキューを処理するために待つ最大時間（ミリ秒） |
| `includeCallerData` | クラス、メソッド、行番号などの呼び出し元情報を引き継ぐか |

非同期化は必ず高速になるわけではなく、キュー満杯時にアプリケーションを待たせるか、ログを失うかという選択が発生します。監査ログなど欠落を許容できないログでは、負荷試験と終了時のフラッシュ確認が必要です。

### 8.7 MDC と key-value を利用する

MDC（Mapped Diagnostic Context）は、リクエスト ID、ユーザー ID、ジョブ ID などを、その処理中に出力されるログへ共通して付与する仕組みです。

パターンへ `%X{キー名}` を追加します。値がない場合の表示は `:-` の後ろで指定できます。

~~~xml
<property name="CONSOLE_LOG_PATTERN"
          value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [requestId=%X{requestId:-unknown}] [%thread] %logger{36} - %msg%n"/>
~~~

Java コードでは、処理の開始時に値を設定し、終了時に必ず削除します。

~~~java
import org.slf4j.MDC;

MDC.put("requestId", requestId);
try {
    logger.info("リクエストを処理します");
} finally {
    MDC.remove("requestId");
}
~~~

スレッドプールではスレッドが再利用されるため、削除を忘れると別の処理へ値が残る可能性があります。また、MDC は子スレッドへ自動的に引き継がれないため、非同期処理やリアクティブ処理ではコンテキスト伝播を別途設計します。

SLF4J の fluent API で付けた key-value は、パターンに `%kvp` を追加すると出力できます。

~~~java
logger.atInfo()
        .addKeyValue("orderId", orderId)
        .log("注文を処理しました");
~~~

~~~xml
<pattern>%d %-5level %logger{36} %kvp - %msg%n</pattern>
~~~

たとえば、上のパターンで次のようなログが出力されます。

~~~text
2026-08-08 12:34:56,789 INFO  com.example.service.OrderService orderId="1001" - 注文を処理しました
~~~

この例では、`%d` が日時、`%-5level` がログレベル、`%logger{36}` がロガー名、`%kvp` が `orderId="1001"` のような key-value 情報、`%msg` が実際のメッセージとして表示されます。値の引用符が不要な場合は `%kvp{NONE}` を使用できます。

### 8.8 例外と呼び出し元情報を調整する

代表的な追加パターンは次のとおりです。

| パターン | 内容 |
| --- | --- |
| `%ex` / `%ex{full}` | 例外のスタックトレース全体 |
| `%ex{short}` | 例外の先頭部分だけ |
| `%ex{数字}` | 指定した行数までのスタックトレース |
| `%rootException` | 根本原因を先頭にしたスタックトレース |
| `%class` / `%method` / `%line` | 呼び出し元のクラス、メソッド、行番号 |
| `%kvp` | SLF4J の key-value 情報 |
| `%maskedKvp{キー名}` | 指定した key-value の値をマスクして出力する |

例外用のパターンを指定しない場合も、Logback の `PatternLayout` は例外情報を末尾へ自動追加します。表示量を明示的に制御したい場合に `%ex{short}` などを指定します。

`%class`、`%method`、`%line` などの呼び出し元情報は取得コストが高いため、常時出力する基本パターンには安易に追加しません。障害調査や限定したロガーで必要性を確認して使用します。

### 8.9 JSON の構造化ログを出力する

ログ収集・検索基盤へ渡す場合は、人向けの文字列パターンより JSON の構造化ログが適することがあります。Spring Boot は `ecs`、`gelf`、`logstash` の形式を標準でサポートしています。

例えば `application.yaml` で Logstash 形式を選択します。

~~~yaml
logging:
  structured:
    format:
      console: logstash
~~~

独自の `logback-spring.xml` を使っている場合は、コンソールアペンダーの `encoder` を次のように置き換えます。

~~~xml
<encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
    <format>${CONSOLE_LOG_STRUCTURED_FORMAT}</format>
    <charset>UTF-8</charset>
</encoder>
~~~

通常の `<encoder><pattern>...</pattern></encoder>` と構造化ログ用 `StructuredLogEncoder` は同じアペンダー内で併用せず、出力形式に応じてどちらかを選びます。構造化ログでは MDC や SLF4J の key-value も JSON フィールドとして扱えます。

### 8.10 Logback の設定エラーを調査する

Logback がどの設定を読み込んだか、アペンダーを正常に開始できたかを調べる場合は、`configuration` の `debug` を一時的に有効にします。

~~~xml
<configuration debug="true">
    <!-- 設定 -->
</configuration>
~~~

有効にすると Logback 自身の内部ステータスがコンソールへ出力されます。通常運用ではノイズになるため、調査後は `debug="true"` を外します。

標準とは異なる場所に設定ファイルを置く場合は、`logging.config` で明示できます。

~~~yaml
logging:
  config: classpath:logging/logback-spring.xml
~~~

現在のように `src/main/resources/logback-spring.xml` へ配置する場合、この指定は不要です。

## 9. クラウドサービスでログを管理する

コンテナやマネージド実行環境では、アプリケーションがローカルファイルへログを保存するのではなく、標準出力へログを出し、クラウドサービスやログ収集エージェントへ管理を任せる構成が一般的です。

### 9.1 基本方針と責務分担

この構成では、アプリケーションとクラウドサービスの責務を次のように分けます。

| 対象 | 担当する内容 |
| --- | --- |
| アプリケーション | ログレベルの判定、メッセージとコンテキストの生成、標準出力への出力 |
| 実行基盤 | コンテナの標準出力・標準エラーの取得 |
| ログ管理サービス | 保存、検索、ローテーション、保持期間、アクセス制御、アラート |

そのため、クラウド向けの `logback-spring.xml` では次の方針が基本になります。

- `ConsoleAppender` だけを使用する
- `RollingFileAppender` やローカルファイルの保存先を定義しない
- 1 件のログイベントを 1 行で出力する
- 検索・集計しやすい JSON の構造化ログを検討する
- ログレベルを環境変数や Spring のプロパティから変更できるようにする
- `requestId`、`traceId`、`spanId` などを MDC や key-value で付与する
- ログのローテーションと保持期間はクラウド側で設定する

### 9.2 ログ出力形式を選ぶ

JSON の構造化ログは有力な選択肢ですが、必須ではありません。利用するログ管理サービスと検索・集計の要件に合わせて選びます。

| 形式 | 特徴と主な用途 |
| --- | --- |
| 通常の文字列 | 人が直接読みやすく、全文検索や既存の文字列解析で要件を満たせる場合に使用する |
| `ecs` | Elastic Common Schema に対応する JSON として出力する |
| `gelf` | Graylog 向けの JSON として出力する |
| `logstash` | 一般的な Logstash 形式の JSON として出力する |

#### 通常の文字列ログを使い続ける場合

次のような場合は、従来の文字列ログを標準出力へ出す構成を継続できます。

- 運用担当者がログを直接読むことを主な用途としている
- ログ管理サービスの全文検索で要件を満たせる
- 既存のログ収集基盤が現在の文字列形式を解析できる
- JSON フィールドを使った集計やダッシュボードを必要としていない

文字列ログでは `StructuredLogEncoder` を使用せず、通常の `encoder` と `pattern` を定義します。次は、ログレベルを Spring の環境から取得するコンソール専用の例です。

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty name="applicationName"
                    source="spring.application.name"
                    defaultValue="application"/>
    <springProperty name="rootLogLevel"
                    source="logging.level.root"
                    defaultValue="INFO"/>

    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] ${applicationName} %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <charset>UTF-8</charset>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <root level="${rootLogLevel}">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
~~~

このプロジェクトの現在の `logback-spring.xml` も、ファイルを作成せず `ConsoleAppender` だけを使用しているため、クラウドサービスへ文字列ログを渡す基本構成として利用できます。相関 ID が必要な場合は、8.7 章の MDC パターンを追加します。

日時、ログレベル、アプリケーション名、ロガー名などの項目と並び順は安定させます。形式を頻繁に変更すると、ログ管理サービス側の検索条件や解析ルールが利用できなくなる可能性があります。

例外のスタックトレースは複数行になることがあります。ログ管理サービスが複数行を同じイベントとして扱えるか確認し、必要に応じて収集側の複数行設定を行います。

#### JSON の構造化ログを使用する場合

項目単位の検索、集計、ダッシュボード、アラートを重視する場合は構造化ログが適しています。クラウドサービスによっては、重大度やトレース ID として認識する JSON フィールド名が決められています。その場合は、Spring Boot の構造化ログ設定でフィールドを変更するか、クラウドサービス専用のアペンダーを検討します。

専用アペンダーはクラウド固有のメタデータを扱いやすい一方で、依存関係とサービス固有設定が増えます。まず標準出力による収集で要件を満たせるか確認します。

### 9.3 構造化ログを標準出力へ出す構成例

次の例は、Spring Boot の構造化ログを標準出力へ出す構成です。

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProperty scope="context"
                    name="rootLogLevel"
                    source="logging.level.root"
                    defaultValue="INFO"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
            <format>${CONSOLE_LOG_STRUCTURED_FORMAT}</format>
            <charset>${CONSOLE_LOG_CHARSET}</charset>
        </encoder>
    </appender>

    <root level="${rootLogLevel}">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
~~~

対応する `application.yaml` では、構造化ログ形式とログレベルを指定します。

~~~yaml
spring:
  application:
    name: spring-boot-dev-tooling-lab

logging:
  structured:
    format:
      console: logstash
  level:
    root: INFO
    com.example: INFO
~~~

### 9.4 環境変数で設定を上書きする

クラウド環境では、デプロイ設定の環境変数から値を上書きできます。

~~~text
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_COM_EXAMPLE=DEBUG
LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
~~~

これにより、コンテナイメージや設定ファイルを作り直さなくても、環境ごとにログレベルや構造化ログ形式を変更できます。クラス単位ではなくパッケージ単位でログレベルを指定すると、環境変数へ変換しやすくなります。

### 9.5 ファイル出力や非同期出力を使う場合

クラウド環境でも、監査要件や既存システムとの連携によってファイル出力が必要になることはあります。その場合は、永続ボリューム、サイドカー、ログ収集エージェントのどれがファイルを収集するかを明確にします。コンテナ内の一時ファイルだけに保存すると、再起動や再配置によってログを失う可能性があります。

`AsyncAppender` はアプリケーション処理への待ち時間を減らせますが、コンテナが強制終了した場合はキュー内のログが未出力のまま失われる可能性があります。必要性を負荷試験で確認し、欠落を許容できない監査ログには慎重に適用します。

### 9.6 logback-spring.xml を置かない選択

独自のアペンダーやログパターンが不要であれば、Spring Boot の標準設定を利用し、`logback-spring.xml` を配置しない構成も選択できます。通常の文字列ログでは追加設定なしでコンソールへ出力されます。構造化ログを使う場合も、`application.yaml` で次のように指定できます。

~~~yaml
logging:
  structured:
    format:
      console: logstash
~~~

標準設定で要件を満たせる場合は `application.yaml` だけを使用し、独自の MDC 表示、フィルター、アペンダー制御が必要になった場合に `logback-spring.xml` を追加する方法が管理しやすい構成です。

## 10. ログ出力の注意事項

ログは障害調査や監査に役立つ一方、本番環境では多くの担当者や外部のログ管理サービスから参照され、バックアップにも長期間残る可能性があります。アプリケーションのデータとは別の安全な場所ではなく、保護対象となるデータの一部として扱います。

### 10.1 個人情報や秘密情報を出力しない

次の情報は、原則としてログへ直接出力しません。

| 分類 | 例 |
| --- | --- |
| 認証・認可情報 | パスワード、PIN、アクセス・リフレッシュトークン、API キー、`Authorization` ヘッダー |
| セッション情報 | セッション ID、Cookie、`Set-Cookie` ヘッダー、CSRF トークン |
| 暗号・接続情報 | 暗号鍵、秘密鍵、証明書の秘密情報、データベース接続文字列 |
| 個人情報 | 氏名、住所、メールアドレス、電話番号、生年月日、公的な識別番号、位置情報 |
| 要配慮情報 | 健康・医療情報、信条、犯罪歴など、特に慎重な取扱いが必要な情報 |
| 金融情報 | 口座情報、決済カード番号、セキュリティコード |
| 業務上の秘密 | 未公開の価格、契約、取引、設計、ソースコードなどの機密情報 |
| 本文全体 | HTTP のリクエスト・レスポンス本文、アップロードファイル、メール本文、SQL の全バインド値 |

個人情報に該当するかは、単一の値だけでなく、ほかのデータと組み合わせて個人を識別できるかも考慮します。業務、契約、法令、組織のデータ分類基準に従い、判断が必要な項目はセキュリティ・法務担当者へ確認します。

次のように、パスワードやメールアドレスをそのまま記録してはいけません。

~~~java
// 悪い例
logger.info("ログインしました。email={}, password={}", email, password);
~~~

調査に必要な場合も、直接的な個人情報ではなく、アクセス権を管理した内部識別子や結果コードを使用します。

~~~java
// 例: 必要最小限の情報だけを記録する
logger.info("ログインに成功しました。subjectId={}, result={}", subjectId, "SUCCESS");
~~~

内部識別子もほかの情報と照合できれば個人情報になり得るため、ログへのアクセス制御と保持期間は必要です。

### 10.2 必要最小限の情報だけを記録する

ログへ出す項目は、「いつか役立つかもしれない」ではなく、障害調査、監視、監査などの明確な利用目的から決めます。

- HTTP ヘッダーやリクエストオブジェクトを丸ごと出力しない
- リクエスト・レスポンス本文を通常の INFO ログへ出力しない
- Java オブジェクトの `toString()` が個人情報や秘密情報を含まないか確認する
- URL はクエリ文字列にトークンや検索条件が含まれる可能性があるため、必要に応じてパス部分だけを記録する
- SQL は値を埋め込んだ文字列ではなく、処理名、テーブルやクエリの識別子、所要時間、結果だけを記録する

値の一部が必要な場合は、削除、マスキング、仮名化を検討します。

~~~text
カード番号: ************1234
メールアドレス: m***@example.com
~~~

ただし、マスキングした値でも別の情報と組み合わせて本人を識別できる場合があります。単純なハッシュも、元の値の候補が少ない場合は推測される可能性があるため、安易に匿名情報とはみなしません。識別自体が不要なら、値を加工して残すより項目そのものを出力しない方が安全です。

### 10.3 外部入力によるログインジェクションを防ぐ

ユーザー名、HTTP ヘッダー、フォーム入力など、外部から受け取った文字列には改行や制御文字が含まれる可能性があります。そのまま文字列ログへ埋め込むと、偽のログ行を挿入したり、ログ解析を妨害したりするログインジェクションにつながります。

- 外部入力をログへ出す前に、許可する形式と長さを検証する
- CR、LF、区切り文字などを無害化する
- 出力形式に合ったエンコードを行う
- 可能であれば、文字列連結ではなく構造化ログのフィールドとして記録する
- ユーザー入力をロガー名、ログレベル、ログパターンとして使用しない

JSON の構造化ログでも、エンコーダーによるエスケープだけに依存せず、不要な外部入力を記録しないことを優先します。

### 10.4 例外とスタックトレースを確認する

例外メッセージやスタックトレースには、入力値、URL のクエリ、SQL、ファイルパス、内部ホスト名などが含まれることがあります。例外オブジェクトをログへ渡す前に、利用しているライブラリがどの情報を例外メッセージへ含めるか確認します。

同じ例外を各レイヤーで繰り返し ERROR ログへ出しながら再スローすると、ログが重複して原因を追いにくくなります。例外を処理する境界で一度だけ記録し、下位レイヤーでは追加の文脈が必要な場合に限定して記録します。

利用者へ返すエラーメッセージと、内部調査用のログは分けます。レスポンスへスタックトレースや内部構成を返してはいけません。また、ログにもリクエスト本文などを自動的に付加しないようにします。

### 10.5 適切なログレベルと出力量を選ぶ

ログレベルは、メッセージの重要度と対応の必要性に合わせます。

| レベル | 使用例 |
| --- | --- |
| `ERROR` | 処理を完了できず、調査や対応が必要な障害 |
| `WARN` | 処理は継続できるが、劣化、再試行、想定外の状態が発生した場合 |
| `INFO` | 起動・停止、主要な状態遷移、重要な業務処理の結果 |
| `DEBUG` | 開発や障害調査で必要な詳細情報 |
| `TRACE` | メソッドやデータフローを追う非常に詳細な調査情報 |

- 正常な処理を ERROR や WARN として出力しない
- ループ内、ヘルスチェック、頻繁なポーリング処理で大量の INFO ログを出さない
- DEBUG や TRACE を本番環境で長期間有効にしない
- ログレベルを変更しても、監査やセキュリティ上必須のイベントが完全に無効にならないようにする
- 同じエラーを短時間に大量出力する場合は、集約、レート制限、アラート側の抑制を検討する

ログ量が増えると、アプリケーション性能だけでなく、クラウドの保存・転送・検索コストにも影響します。メッセージの最大長や想定件数を確認し、大きなオブジェクトやバイナリデータをログへ出しません。

### 10.6 調査に必要なコンテキストを残す

秘密情報を避けながら、調査に必要な項目を一貫して記録します。

- イベントの日時とログレベル
- アプリケーション名と環境名
- ロガー名または処理名
- `requestId`、`traceId`、`spanId` などの相関 ID
- 処理結果と機械的に検索できる理由コード
- 必要に応じて、仮名化された主体・対象の識別子

相関 ID は推測しにくい値を使用し、ユーザー入力をそのまま採用しません。MDC を使用する場合は、処理終了時に値を削除し、スレッドプールで別のリクエストへ値が残らないようにします。

ログメッセージや理由コードは、検索・アラートが壊れないように命名と形式を安定させます。表示用の文章だけに依存せず、構造化ログでは `eventCode` や `result` などのフィールドを用意すると検索しやすくなります。

### 10.7 ログの保存、閲覧、削除を管理する

クラウドのログ管理サービスへ送信した後も、次の運用管理が必要です。

- ログを閲覧できる権限を業務上必要な担当者へ限定する
- ログの参照・エクスポート・設定変更自体を監査する
- 通信中と保存中の暗号化を有効にする
- 利用目的、契約、法令に合った保持期間を設定する
- 保持期間を過ぎたログ、バックアップ、エクスポートファイルを削除する
- 外部サービスへ送信するデータとリージョンを確認する
- 改ざん検知や削除防止が必要な監査ログでは、適切な保護機能を使用する

開発者が一時的にダウンロードしたログや、障害調査で作成した抜粋ファイルも同じ保護対象です。チケット、チャット、メールへログを貼り付ける場合は、個人情報や秘密情報が含まれていないことを確認します。

### 10.8 監査ログとアプリケーションログを区別する

アプリケーションのデバッグログと、誰が何を行ったかを証明する監査ログでは、目的、閲覧権限、保持期間、欠落の許容度が異なります。監査要件がある場合は、専用ロガー、専用のログストリーム、または監査サービスへ分離します。

監査ログには、一般的に次の情報を必要最小限で記録します。

- 実行日時
- 行為者を示す管理された識別子
- 操作と対象
- 成功・失敗の結果
- 理由コード
- 相関 ID

パスワードやアクセストークンなど、操作を再現できる秘密情報は監査ログにも記録しません。監査ログの出力失敗を通常のデバッグログと同じように無視せず、業務要件に応じた通知や処理方針を決めます。

### 10.9 レビュー時のチェックリスト

ログを追加・変更するときは、次を確認します。

- 個人情報、認証情報、秘密情報を含んでいないか
- 記録する各項目に明確な利用目的があるか
- 外部入力が改行や制御文字を含んでもログ形式を壊さないか
- 例外メッセージやオブジェクトの `toString()` に機密情報が含まれないか
- ログレベルと想定出力量が適切か
- 同じイベントを複数レイヤーで重複出力していないか
- MDC を必ず削除しているか
- 保持期間、アクセス権、削除方法が決まっているか
- アラートや監査に必要なイベントが欠落しないか
- テスト用の詳細ログが本番設定に残っていないか

## 11. 設定を変更するときの注意点

- ファイル名は `logback-spring.xml` とし、`src/main/resources` 直下に配置する
- Spring Boot の機能を使う設定では、`logback.xml` ではなく `logback-spring.xml` を使用する
- XML のタグや属性名を変更した場合は、アプリケーションを再起動して確認する
- `springProperty` や `springProfile` を使う場合は、Logback の自動再読み込みを行う `scan="true"` を使用しない
- ログレベルを下げる前に、出力されるデータに機密情報が含まれないことを確認する
- ファイル出力や JSON 形式を追加する場合は、保存先の容量、ローテーション、保持期間も合わせて設計する
- 複数のアペンダーや専用ロガーを追加した場合は、同じログが二重出力されていないか確認する
- 非同期出力を追加した場合は、高負荷時とアプリケーション終了時にログが欠落しないか確認する

変更後は、少なくとも次のコマンドで起動とテストを確認します。

~~~bash
git diff --check
./gradlew test
~~~

設定ファイルの実体は [src/main/resources/logback-spring.xml](../../src/main/resources/logback-spring.xml) です。

## 12. 参考資料

### Spring Boot と Logback

- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Logback Manual: Architecture](https://logback.qos.ch/manual/architecture.html)
- [Logback Manual: Appenders](https://logback.qos.ch/manual/appenders.html)
- [Logback Manual: Layouts](https://logback.qos.ch/manual/layouts.html)
- [Logback Manual: Filters](https://logback.qos.ch/manual/filters.html)
- [Logback Manual: Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html)
- [Logback Manual: Configuration](https://logback.qos.ch/manual/configuration.html)

### クラウドのログ収集

- [Kubernetes Documentation: Logging Architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/)
- [Amazon ECS Documentation: Send Amazon ECS logs to CloudWatch](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_awslogs.html)
- [Google Cloud Documentation: Logging and viewing logs in Cloud Run](https://cloud.google.com/run/docs/logging)

### セキュリティと個人情報保護

- [OWASP Cheat Sheet Series: Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [NIST SP 800-92: Guide to Computer Security Log Management](https://csrc.nist.gov/pubs/sp/800/92/final)
- [個人情報保護委員会: 個人情報の保護に関する法律についてのガイドライン（通則編）](https://www.ppc.go.jp/personalinfo/legal/guidelines_tsusoku/)
