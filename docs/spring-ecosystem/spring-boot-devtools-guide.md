# Spring Boot DevTools ガイド

このガイドは、本プロジェクトで使用する Spring Boot DevTools の役割、VS Code での利用方法、設定、注意点を説明します。内容は、現在の [build.gradle.kts](../../build.gradle.kts) で使用している Spring Boot 4.1.0 を前提としています。

## 1. Spring Boot DevTools とは

Spring Boot DevTools は、開発中のフィードバックサイクルを短くするための機能をまとめたモジュールです。主に次の機能を提供します。

- クラスパス上のファイル変更を検知したアプリケーションの自動再起動
- テンプレートや静的リソースのキャッシュを無効にするなど、開発向けのプロパティ既定値
- 再起動前後における自動構成の条件評価結果の差分表示
- ファイル監視対象、除外対象、再起動タイミングの調整

DevTools は、本番アプリケーションへ機能を追加するためのライブラリではありません。ローカル開発時だけ利用します。

## 2. このプロジェクトでの構成

本プロジェクトでは、`developmentOnly` 構成で DevTools を追加しています。

~~~kotlin
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
~~~

バージョンは個別に指定せず、Spring Boot の依存関係管理に任せます。

`developmentOnly` を使用すると、DevTools は開発時のクラスパスにだけ追加されます。通常の実行用 JAR を利用する環境へ、DevTools を意図せず伝播させることを防げます。また、Spring Boot が生成する実行可能 JAR には、既定で DevTools が含まれません。

## 3. 自動再起動の仕組み

DevTools は二つのクラスローダーを使用します。

| クラスローダー | 主な対象 |
| --- | --- |
| base classloader | あまり変更されない外部ライブラリ |
| restart classloader | 開発中のアプリケーションクラス |

変更時にはアプリケーション全体の JVM プロセスを起動し直すのではなく、restart classloader を作り直します。依存ライブラリを読み込んだ base classloader を再利用するため、通常のコールドスタートより短い時間で再起動できます。

DevTools が監視するのはソースファイルそのものではなく、クラスパス上のコンパイル結果です。Java ファイルを保存しただけで再起動しない場合は、変更したソースがコンパイルされているか確認してください。

## 4. VS Code で使用する

### Spring Boot Dashboard から起動する

このプロジェクトが推奨している Spring Boot Extension Pack には、Spring Boot Dashboard が含まれています。

1. VS Code の Spring Boot Dashboard を開く
2. 対象アプリケーションを Run または Debug で起動する
3. Java ファイルを変更して保存する
4. Java Language Server によるコンパイル後、起動コンソールで再起動ログを確認する

VS Code の Java ワークスペースが Lightweight mode の場合、ビルド、実行、デバッグなどの一部機能が利用できません。期待した動作にならない場合は、Java Language Server が Standard mode で動作していることを確認します。

### Gradle から起動する

ターミナルから起動する場合は、次のコマンドを実行します。

~~~bash
./gradlew bootRun
~~~

`bootRun` の実行中に別のターミナルでクラスを再コンパイルすると、DevTools が変更を検知します。

~~~bash
./gradlew classes
~~~

VS Code が使用する出力ディレクトリと Gradle が使用する出力ディレクトリが異なる場合、エディターで保存しただけでは `bootRun` 側のクラスパスが更新されないことがあります。その場合は `classes` タスクを実行するか、Spring Boot Dashboard から起動します。

## 5. 動作を確認する

アプリケーションを起動した状態で、例えば `SampleController` の戻り値を変更して再コンパイルします。起動側のコンソールに再起動を示すログが出力された後、別のターミナルからエンドポイントを確認します。

~~~bash
curl http://localhost:8080/test
~~~

確認後は、動作確認のためだけに変更したソースを元に戻します。

依存関係が開発用構成へ追加されていることは、次のコマンドでも確認できます。

~~~bash
./gradlew dependencies --configuration developmentOnly
~~~

## 6. 開発向けのプロパティ既定値

DevTools は、開発時に変更を確認しやすくするため、一部のライブラリや Spring Boot 機能へ開発向けの既定値を適用します。代表例は次のとおりです。

- テンプレートエンジンのキャッシュを無効にする
- 静的リソースのキャッシュ期間を無効にする
- Web のエラーレスポンスにメッセージやスタックトレースを含める
- 開発時のトレースサンプリング確率を高くする

これらは開発中には便利ですが、本番環境に適した設定ではありません。DevTools を本番環境で有効にしないことに加え、開発プロファイルの設定を本番プロファイルへ流用しないようにします。

DevTools によるプロパティ既定値をまとめて無効にする場合は、次のように設定します。

~~~yaml
spring:
  devtools:
    add-properties: false
~~~

## 7. 再起動対象を調整する

### 既定の除外対象

静的リソースやテンプレートなど、一部のリソース変更は既定で完全な再起動の対象外です。独自の対象を追加で除外するときは、`additional-exclude` を使用します。

~~~yaml
spring:
  devtools:
    restart:
      additional-exclude: "generated/**,reports/**"
~~~

`exclude` を指定すると既定の除外設定を置き換えます。通常は既定値を維持できる `additional-exclude` を使用します。

### 追加の監視パス

クラスパス外のファイル変更でも再起動させたい場合は、監視パスを追加できます。

~~~yaml
spring:
  devtools:
    restart:
      additional-paths: "../shared-config"
~~~

監視範囲を広げすぎると、生成ファイルやログの更新によって再起動が繰り返されることがあります。必要なパスだけを指定します。

## 8. トリガーファイルを使用する

自動ビルドのたびに再起動したくない場合は、トリガーファイルを指定できます。

~~~yaml
spring:
  devtools:
    restart:
      trigger-file: ".reloadtrigger"
~~~

例えば次のファイルをクラスパス上へ配置します。

~~~text
src/main/resources/.reloadtrigger
~~~

この構成では、クラスパスに変更があっても、トリガーファイルが更新されるまで再起動しません。複数ファイルをまとめて編集してから再起動したい場合に有効です。

## 9. WSL2 でファイル監視を調整する

本プロジェクトでは、WSL2 の Linux ファイルシステム内へリポジトリを配置し、VS Code の WSL 接続で開く構成を推奨しています。この構成で再起動の検知が不安定な場合は、ファイル監視のポーリング間隔と quiet period を調整します。

~~~yaml
spring:
  devtools:
    restart:
      poll-interval: 2s
      quiet-period: 1s
~~~

`poll-interval` は変更を確認する間隔、`quiet-period` は変更が落ち着くまで待つ時間です。`quiet-period` は、コンパイル結果が複数回に分かれて書き込まれる環境で、不完全な状態のまま再起動することを防ぎます。

値を短くしすぎると、コンパイル途中で再起動したり、連続して再起動したりする可能性があります。まず上記程度の値から試し、必要な場合だけ調整します。

## 10. DevTools を無効にする

一時的に自動再起動だけを止める場合は、次のプロパティを指定します。

~~~yaml
spring:
  devtools:
    restart:
      enabled: false
~~~

この設定ではファイル監視は無効になりますが、restart classloader の初期化自体は行われます。クラスローダーに起因する問題を切り分ける場合は、VS Code の一時的な起動構成などで、起動前にシステムプロパティとして無効化します。

~~~json
{
  "type": "java",
  "name": "Spring Boot without DevTools restart",
  "request": "launch",
  "mainClass": "com.example.SpringBootDevToolingLabApplication",
  "vmArgs": "-Dspring.devtools.restart.enabled=false"
}
~~~

恒久的に利用しない場合は、`build.gradle.kts` から DevTools の依存関係を削除する方法が最も明確です。

## 11. 注意点

### 本番環境で有効にしない

DevTools のリモート機能を本番環境で有効にすると、セキュリティ上の危険があります。本プロジェクトではリモート更新機能を使用しません。実行可能 JAR へ DevTools を意図的に含めたり、`spring.devtools.remote.secret` を本番設定へ追加したりしないでください。

### クラスローダーの問題

DevTools の再起動は二つのクラスローダーに依存するため、特に複数モジュールのプロジェクトやリフレクションを多用するライブラリで、クラスが見つからない、型が一致しないなどの問題が発生することがあります。

自動再起動を無効にすると問題が解消する場合は、`META-INF/spring-devtools.properties` の `restart.include` と `restart.exclude` でクラスローダーの割り当てを調整します。設定を追加する前に、問題となる JAR やモジュールを起動時のクラスパスから特定します。

### AspectJ weaving

DevTools の自動再起動は AspectJ weaving と組み合わせて利用できません。AspectJ を導入する場合は、DevTools の再起動を無効にするか、別の開発フローを検討します。

### LiveReload

LiveReload は Spring Boot 4.1.0 で非推奨になりました。新しい開発環境では、LiveReload ブラウザー拡張を前提にせず、DevTools の自動再起動やフロントエンド側の開発サーバーを利用します。

## 12. トラブルシューティング

### ソースを保存しても再起動しない

次の順に確認します。

1. DevTools が `developmentOnly` に存在するか確認する
2. アプリケーションが `bootRun`、Spring Boot Dashboard、または IDE のクラスパスから起動されているか確認する
3. 変更した Java ソースがコンパイルされ、クラスパス上の `.class` ファイルが更新されたか確認する
4. 変更対象が `restart.exclude` または `additional-exclude` に一致していないか確認する
5. トリガーファイルを設定している場合は、そのファイルを更新したか確認する

### 再起動が何度も発生する

ログ、レポート、生成コードなど、アプリケーション自身が更新するディレクトリを監視していないか確認します。該当するパスを `additional-exclude` へ追加するか、トリガーファイルを使用します。

### 変更の一部だけが反映される

コンパイル結果の書き込みが完了する前に再起動している可能性があります。`poll-interval` と `quiet-period` を長くします。WSL2、ネットワークファイルシステム、大きなマルチモジュールプロジェクトでは特に確認してください。

### DevTools を外すと正常に動く

restart classloader に起因する可能性があります。自動再起動をシステムプロパティで完全に無効化して再確認し、必要に応じて `spring-devtools.properties` で対象を調整します。

## 13. 参考リンク

- [Spring Boot: Developer Tools](https://docs.spring.io/spring-boot/reference/using/devtools.html)
- [Spring Boot: Development-time Services](https://docs.spring.io/spring-boot/reference/features/dev-services.html)
- [VS Code: Spring Boot in Visual Studio Code](https://code.visualstudio.com/docs/java/java-spring-boot)
- [VS Code: Java Project Management](https://code.visualstudio.com/docs/java/java-project)
