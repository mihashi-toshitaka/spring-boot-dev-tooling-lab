# Spring Boot Configuration Processor ガイド

## 1. Configuration Processor とは

`spring-boot-configuration-processor` は、独自の `@ConfigurationProperties` クラスをコンパイル時に解析し、設定メタデータを生成するアノテーションプロセッサです。

生成されたメタデータを VS Code の Spring Tools などが読み取ることで、`application.yaml` や `application.properties` に対して次の編集支援を提供できます。

- 独自プロパティ名の入力補完
- プロパティの型や説明の表示
- 既定値や非推奨情報の表示
- 列挙型など、指定可能な値の候補表示

このライブラリはコンパイル時にだけ使われます。設定値の読み込み、バインド、実行時の検証を行うライブラリではありません。

## 2. プロジェクトへの導入

このプロジェクトでは、`build.gradle.kts` に次の依存関係を追加しています。

~~~kotlin
dependencies {
    annotationProcessor(
        "org.springframework.boot:spring-boot-configuration-processor"
    )
}
~~~

Spring Boot の依存関係管理を利用しているため、個別のバージョン指定は不要です。

`implementation` ではなく `annotationProcessor` に指定します。これにより、プロセッサはコンパイル時に利用されますが、アプリケーションの実行時クラスパスには含まれません。

## 3. 基本的な利用例

### `@ConfigurationProperties` を作成する

メール送信設定を受け取る例です。

~~~java
package com.example.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メール送信に使用する設定です。
 *
 * @param host SMTPサーバーのホスト名
 * @param port SMTPサーバーのポート番号
 * @param enabled メール送信を有効にするか
 */
@ConfigurationProperties("app.mail")
public record MailProperties(
        String host,
        int port,
        boolean enabled) {
}
~~~

Javaのrecordを使用する場合、各プロパティの説明はクラスのJavadocに `@param` として記述します。通常のJavaBeanでは、フィールドやgetterのJavadocも説明として利用されます。

### Spring Bean として登録する

アプリケーションクラスに `@ConfigurationPropertiesScan` を追加すると、対象パッケージ以下の `@ConfigurationProperties` クラスが検出されます。

~~~java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootDevToolingLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SpringBootDevToolingLabApplication.class,
                args);
    }
}
~~~

特定のクラスだけを登録する場合は、代わりに `@EnableConfigurationProperties(MailProperties.class)` を利用できます。

### `application.yaml` に設定する

~~~yaml
app:
  mail:
    host: "smtp.example.com"
    port: 587
    enabled: true
~~~

Configuration Processorを導入してコンパイルした後は、VS Code上で `app.mail.host` などの補完やJavadocの説明を利用できます。

## 4. 生成されるメタデータ

Javaをコンパイルすると、一般的には次のファイルが生成されます。

~~~text
build/classes/java/main/META-INF/spring-configuration-metadata.json
~~~

生成内容は概ね次のようになります。通常、このファイルを手作業で編集する必要はありません。

~~~json
{
  "groups": [
    {
      "name": "app.mail",
      "type": "com.example.configuration.MailProperties"
    }
  ],
  "properties": [
    {
      "name": "app.mail.host",
      "type": "java.lang.String",
      "description": "SMTPサーバーのホスト名"
    },
    {
      "name": "app.mail.port",
      "type": "java.lang.Integer",
      "description": "SMTPサーバーのポート番号"
    }
  ]
}
~~~

このメタデータはアプリケーションのjarにも格納されます。設定クラスをライブラリとして配布する場合、利用側のIDEもそのメタデータを使って補完できます。

## 5. 値の検証との違い

Configuration Processorは、設定値が正しいかを実行時に検証しません。必須値、文字数、数値範囲などを検証する場合はJakarta Validationを利用します。

~~~java
package com.example.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        boolean enabled) {
}
~~~

Validation APIと実装がクラスパスにある場合、不正な設定値はアプリケーション起動時のバインドエラーになります。Configuration Processorが担当するのはIDE向けメタデータの生成です。

## 6. 手動メタデータを追加する

自動生成できない候補値や説明を補足する場合は、次のファイルを作成します。

~~~text
src/main/resources/META-INF/additional-spring-configuration-metadata.json
~~~

たとえば、文字列プロパティの候補を追加できます。

~~~json
{
  "hints": [
    {
      "name": "app.mail.security",
      "values": [
        {
          "value": "none",
          "description": "暗号化しません。"
        },
        {
          "value": "starttls",
          "description": "STARTTLSを使用します。"
        },
        {
          "value": "tls",
          "description": "TLS接続を使用します。"
        }
      ]
    }
  ]
}
~~~

追加メタデータをプロセッサへ確実に渡す必要がある場合は、公式ドキュメントに従い `compileJava` へ `processResources` の出力を入力として関連付けます。

~~~kotlin
tasks.named("compileJava") {
    inputs.files(tasks.named("processResources"))
}
~~~

手動で補う情報がなければ、追加メタデータファイルやこのタスク設定は不要です。

## 7. VS Code で確認する

1. Extension Pack for JavaとSpring Boot Extension Pack、または同等のSpring Toolsを有効にする
2. `./gradlew compileJava` を実行してメタデータを生成する
3. `application.yaml` で独自プロパティの補完と説明を確認する

追加直後に補完されない場合は、次を確認します。

- `@ConfigurationProperties` クラスがコンパイル対象になっているか
- VS CodeがGradleプロジェクトの同期を完了しているか
- `Java: Clean Java Language Server Workspace` を実行して再読み込みすると改善するか
- 生成された `spring-configuration-metadata.json` に対象プロパティが含まれているか

## 8. 利用時の注意

### Spring Boot標準プロパティだけなら不要

`server.port` や `management.endpoints.web.exposure.include` など、Spring Boot標準プロパティのメタデータはSpring Bootのjarにあらかじめ含まれています。独自の `@ConfigurationProperties` を作らないプロジェクトでは、Configuration Processorを追加する効果はほとんどありません。

### `@Value` は自動生成の対象ではない

~~~java
@Value("${app.mail.host}")
private String mailHost;
~~~

この形式だけでは、独自プロパティのメタデータは自動生成されません。関連する設定を型安全にまとめる場合は、`@ConfigurationProperties` の利用を検討してください。

### マルチモジュールプロジェクト

設定クラスを定義するモジュールにConfiguration Processorを追加します。設定クラスを利用するだけのモジュールへ重複して追加する必要はありません。

### Lombokと併用する場合

Lombokを使う場合は、Lombokのアノテーションプロセッサが先に実行されるよう、Gradleの `annotationProcessor` 依存関係ではLombokを先に宣言します。

~~~kotlin
annotationProcessor("org.projectlombok:lombok")
annotationProcessor(
    "org.springframework.boot:spring-boot-configuration-processor"
)
~~~

## 9. 動作確認

アノテーションプロセッサの依存関係を確認します。

~~~bash
./gradlew dependencies --configuration annotationProcessor
~~~

独自の `@ConfigurationProperties` クラスを作成した後、コンパイルしてメタデータを確認します。

~~~bash
./gradlew clean compileJava
find build/classes/java/main/META-INF \
    -name spring-configuration-metadata.json \
    -print
~~~

プロジェクト全体の検査も実行します。

~~~bash
./gradlew check
~~~

## 10. 公式ドキュメント

- [Configuration Metadata](https://docs.spring.io/spring-boot/specification/configuration-metadata/)
- [Generating Your Own Metadata by Using the Annotation Processor](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)
- [Metadata Format](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)
- [Providing Manual Hints](https://docs.spring.io/spring-boot/specification/configuration-metadata/manual-hints.html)
