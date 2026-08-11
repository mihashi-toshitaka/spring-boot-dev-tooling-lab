# Spring Boot / VS Code 開発環境サンプル

## このプロジェクトについて

このプロジェクトは、Spring Boot を使った Java 開発における VS Code の開発環境設定を確認するためのサンプルです。

特定の業務アプリケーションや REST API などを実装することが目的ではありません。そのため、アプリケーションのコードや業務ロジックはありません。主に次の設定を確認するために使用します。

- Java 21 と Spring Boot の開発環境
- Gradle によるビルド・テスト
- Checkstyle、Spotless、OpenRewrite、Error Prone、ArchUnit などの開発用ツール
- VS Code の Java / Spring Boot 拡張機能
- WSL2 上で AI コーディングを行うための基本的なコマンド

## 想定環境

- Windows 上の WSL2
- WSL2 の Linux ディストリビューションは Ubuntu を想定
- VS Code は Windows 側にインストールし、WSL 拡張機能でこのプロジェクトを開く構成
- JDK 21

WSL2 のバージョンは、Windows の PowerShell で次のコマンドを実行して確認できます。

~~~powershell
wsl.exe -l -v
~~~

WSL2 内では次のコマンドで Linux 環境を確認できます。

~~~bash
uname -a
cat /etc/os-release
~~~

プロジェクトは、可能であれば /mnt/c 配下ではなく WSL2 のホームディレクトリ配下（例: ~/src/spring-boot-dev-tooling-lab）に配置してください。ファイルアクセスが多い Java / Gradle プロジェクトでは、WSL2 側のファイルシステムの方が扱いやすい場合があります。

## 初期セットアップ

### 1. AI コーディングでよく使うシェルコマンド

AI がプロジェクトを調査・編集・ビルドするときは、ファイル検索、テキスト検索、JSON の確認、Git 操作などのコマンドをよく使用します。Ubuntu の WSL2 では、次のパッケージをまとめてインストールできます。

~~~bash
sudo apt update
sudo apt install -y ca-certificates build-essential curl fd-find git jq ripgrep tree unzip wget zip
~~~

主な用途は次のとおりです。

| コマンド | 用途 |
| --- | --- |
| git | 変更確認、差分確認、ブランチ操作 |
| rg | ソースコードや設定ファイルの高速な文字列検索 |
| fdfind | ファイル名による検索。Ubuntu の fd-find が提供するコマンド |
| find、xargs | ファイルの検索と一括処理。基本パッケージに含まれる |
| sed、awk、grep | ファイル内容の抽出・置換・検索。基本パッケージに含まれる |
| head、tail、sort | ログやコマンド出力の確認・整形。基本パッケージに含まれる |
| jq | JSON の整形・検索・加工 |
| tree | ディレクトリ構成の確認 |
| curl、wget | ファイルやインストールスクリプトの取得 |
| zip、unzip | アーカイブの作成・展開 |
| build-essential | Node.js のネイティブモジュールなどをビルドするためのコンパイラ類 |

インストール後は、次のコマンドで主要なコマンドを確認できます。

~~~bash
git --version
rg --version
fdfind --version
jq --version
tree --version
curl --version
~~~

fd-find の実行ファイル名は Ubuntu では fdfind です。AI や既存スクリプトが fd という名前を前提にしている場合は、任意でエイリアスを設定できます。

~~~bash
echo "alias fd=fdfind" >> ~/.bashrc
source ~/.bashrc
~~~

### 2. JDK と Gradle

このプロジェクトは build.gradle.kts で Java 21 を指定しています。JDK が未インストールの場合は、WSL2 内で次のコマンドを実行してください。

~~~bash
sudo apt update
sudo apt install -y openjdk-21-jdk
~~~

インストール済みかどうかは次のコマンドで確認できます。

~~~bash
java -version
javac -version
~~~

Gradle はグローバルにインストールせず、リポジトリに含まれる Gradle Wrapper を使用します。

~~~bash
./gradlew --version
~~~

初回のビルドでは、Gradle 本体や依存ライブラリのダウンロードのため、インターネット接続が必要です。

### 3. nvm と Node.js

インストール済みかどうかは、次のコマンドで確認できます。

~~~bash
nvm --version
node --version
npm --version
~~~

未インストールの場合は、次のコマンドを実行します。

~~~bash
sudo apt update
sudo apt install -y curl
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.6/install.sh | bash
source ~/.bashrc
nvm install --lts
~~~

### 4. Python 3

インストール済みかどうかは、次のコマンドで確認できます。

~~~bash
python3 --version
~~~

未インストールの場合は、次のコマンドを実行します。

~~~bash
sudo apt update
sudo apt install -y python3 python3-pip
~~~

## 利用開発用ツール

このプロジェクトでは、主に Gradle の check タスクを入口として、コード品質チェック、フォーマット、ソースコード変換、コンパイル時解析を行います。

| ツール | 目的 | このプロジェクトでの設定・実行例 |
| --- | --- | --- |
| **Checkstyle** | Java のコーディング規約を検査する | [Checkstyle ガイド](docs/checkstyle-guide.md)、config/checkstyle/checkstyle.xml、./gradlew checkstyleMain checkstyleTest |
| **SpotBugs** | Java バイトコードから潜在的なバグを検出する | [SpotBugs ガイド](docs/spotbugs-guide.md)、./gradlew spotbugsMain spotbugsTest または ./gradlew check |
| **SonarLint** | コードスメル、バグ、脆弱性などをエディター上で早期検出する | [SonarQube for IDE ガイド](docs/sonarqube-for-ide-guide.md)。Gradle の解析タスクはこのサンプルでは設定していない |
| **Spotless** | Java ソースコードを自動整形し、フォーマット違反を検出する | [Spotless ガイド](docs/spotless-guide.md)、Palantir Java Format、./gradlew spotlessCheck、./gradlew spotlessApply |
| **OpenRewrite** | Java / Spring の移行や静的なコード変換を自動化する | [OpenRewrite ガイド](docs/openrewrite-guide.md)、./gradlew rewriteDryRun、./gradlew rewriteRun |
| **Error Prone** | Java のコンパイル時に潜在的なバグパターンを検出する | [Error Prone ガイド](docs/error-prone-guide.md)、./gradlew compileJava または ./gradlew check |
| **ArchUnit** | クラス構成やアーキテクチャ上のルールをテストとして検査する | [ArchUnit ガイド](docs/archunit-guide.md)、./gradlew test または ./gradlew check |

各ツールの役割分担と推奨実行順は、[コード品質ツールガイド](docs/code-quality-tools.md) にまとめています。

Spring Boot の各種機能については、次のガイドを参照してください。

- [Spring Boot DevTools ガイド](docs/spring-ecosystem/spring-boot-devtools-guide.md)
- [Spring Boot Actuator ガイド](docs/spring-ecosystem/spring-boot-actuator-guide.md)
- [Spring Boot Configuration Processor ガイド](docs/spring-ecosystem/spring-boot-configuration-processor-guide.md)
- [Spring Boot Thymeleaf ガイド](docs/spring-ecosystem/spring-boot-thymeleaf-guide.md)
- [Spring Security ログインガイド](docs/spring-ecosystem/spring-security-login-guide.md)
- [Spring Session + Valkey ガイド](docs/spring-ecosystem/spring-session-valkey-guide.md)

### SpotBugs

SpotBugs は、コンパイル済みの Java バイトコードを解析して潜在的なバグを検出します。このプロジェクトでは、アプリケーションコードとテストコードをそれぞれ `spotbugsMain`、`spotbugsTest` で解析します。

~~~bash
./gradlew spotbugsMain spotbugsTest
~~~

指摘がある場合はタスクが失敗します。HTML レポートは `build/reports/spotbugs/` 配下の `spotbugsMain.html` と `spotbugsTest.html` に生成されます。両タスクは `check` に含まれるため、通常は `./gradlew check` でほかの品質チェックとまとめて実行できます。

### ArchUnit

ArchUnit は、Java のクラス構成や依存関係などのアーキテクチャルールを、JUnit テストとして検査するために使用します。依存関係は `build.gradle.kts` の `testImplementation` に定義しています。

現在は、`src/main/java` 配下の次のルールを検査しています。

- `@Service` または `@Component` が直接付与されたクラスのフィールドは、`static` を含めて `final` でなければならない

ルールは [SpringComponentArchitectureTest.java](src/test/java/com/example/architecture/SpringComponentArchitectureTest.java) に定義されています。違反がある場合は、通常のテストと同様に `./gradlew test` または `./gradlew check` が失敗します。

### まとめて実行する

~~~bash
./gradlew rewriteRun
./gradlew spotlessApply
./gradlew clean
./gradlew check
~~~

`rewriteRun` と `spotlessApply` はソースコードを修正するタスクです。実行後、必要に応じて Git の差分を確認し、意図した変更だけを残してください。

~~~bash
git diff
~~~

`check` では、テスト、Checkstyle、SpotBugs、Spotless の検査など、プロジェクトに設定された検査タスクが実行されます。

OpenRewrite の適用内容を事前に確認する場合は、`rewriteDryRun` を使用できます。

~~~bash
./gradlew rewriteDryRun
~~~

現在の主な設定は次のファイルにあります。

- build.gradle.kts: Gradle プラグイン、依存関係、各ツールの設定
- config/checkstyle/checkstyle.xml: Checkstyle のルール
- gradle/wrapper/gradle-wrapper.properties: Gradle Wrapper のバージョン

## VS Code の拡張機能

### インストール

WSL2 内でプロジェクトを開いた状態で、次のコマンドを実行できます。code コマンドが利用できない場合は、VS Code の拡張機能ビュー（Ctrl+Shift+X）から拡張機能 ID を検索してインストールしてください。

~~~bash
code --install-extension ms-vscode-remote.remote-wsl
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-boot-dev-pack
code --install-extension shengchen.vscode-checkstyle
code --install-extension sonarsource.sonarlint-vscode
code --install-extension richardwillis.vscode-spotless-gradle
~~~

WSL2 のプロジェクトを開くときは、WSL2 内のプロジェクトディレクトリで次のコマンドを実行します。

~~~bash
code .
~~~

VS Code の左下に WSL: Ubuntu などの表示が出ていることを確認してください。拡張機能は、Windows 側ではなく接続先の WSL 環境側にインストールされている必要があります。

### vscjava.vscode-java-pack

[Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) は、Java 開発に必要な拡張機能をまとめたパックです。主に次の機能を提供します。

- Java のコード補完、定義ジャンプ、リファクタリング
- Java アプリケーションのデバッグ
- JUnit / TestNG のテスト実行
- Gradle / Maven プロジェクトの読み込みとタスク実行
- Java プロジェクトの管理

このプロジェクトでは、Gradle Wrapper と Java 21 が正しく認識されていることを確認してください。

### vmware.vscode-boot-dev-pack

[Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack) は、Spring Boot 開発向けの拡張機能パックです。

- Spring Boot の Java 開発支援
- application.properties / application.yml の編集支援
- Spring Initializr によるプロジェクト作成
- Spring Boot Dashboard によるアプリケーションの表示、起動、停止、デバッグ

このリポジトリはアプリケーション実装のサンプルではありませんが、Spring Boot の開発環境が正しく動作するかを確認するために利用できます。

### shengchen.vscode-checkstyle

[Checkstyle for VS Code](https://marketplace.visualstudio.com/items?itemName=shengchen.vscode-checkstyle) は、Java ファイルを編集中に Checkstyle の違反を表示し、可能なものには Quick Fix を提供します。

このプロジェクトの Gradle 側の Checkstyle バージョンと設定ファイルに合わせるには、VS Code の settings.json に次の設定を追加します。

~~~json
{
  "java.checkstyle.version": "13.7.0",
  "java.checkstyle.configuration": "${workspaceFolder}/config/checkstyle/checkstyle.xml"
}
~~~

Checkstyle の最終的な判定は Gradle の ./gradlew check でも行われます。エディター上の表示と Gradle の結果が異なる場合は、まず Checkstyle のバージョンと設定ファイルのパスを確認してください。

### sonarsource.sonarlint-vscode

[SonarQube for IDE](https://marketplace.visualstudio.com/items?itemName=sonarsource.sonarlint-vscode) は、以前 SonarLint と呼ばれていた VS Code 拡張機能です。Java などのコードを編集中に、バグ、脆弱性、コードスメル、セキュリティ上の問題を検出します。

ローカルでの基本的な解析はすぐに利用できます。チームで SonarQube Server または SonarQube Cloud を利用する場合は、Connected Mode を設定してプロジェクトのルールや設定を共有できます。

### richardwillis.vscode-spotless-gradle

[Spotless Gradle](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle) は、Gradle の Spotless 設定を利用して、編集中のファイルのフォーマットと診断を行います。Java Extension Pack に含まれる Gradle for Java 拡張機能を経由して Gradle タスクを実行します。

このプロジェクトでは build.gradle.kts の palantirJavaFormat() がフォーマットルールです。必要に応じて、settings.json に次の設定を追加できます。

~~~json
{
  "java.format.enabled": false,
  "[java]": {
    "spotlessGradle.diagnostics.enable": true,
    "spotlessGradle.format.enable": true,
    "editor.defaultFormatter": "richardwillis.vscode-spotless-gradle",
    "editor.codeActionsOnSave": {
      "source.fixAll.spotlessGradle": "explicit"
    }
  }
}
~~~

Java 用のフォーマッターを複数有効にすると、保存時にフォーマットが競合することがあります。Spotless を使う場合は、他の Java フォーマッターを無効にしてください。

## 基本的な確認コマンド

環境構築後は、プロジェクトのルートディレクトリで次を実行してください。

~~~bash
./gradlew clean test
./gradlew check
~~~

VS Code からは、Gradle のサイドバーでタスクを確認したり、Java ファイル上でテストの実行・デバッグを行ったりできます。

## 参考リンク

- [Visual Studio Code: Developing in WSL](https://code.visualstudio.com/docs/remote/wsl)
- [nvm 公式リポジトリ](https://github.com/nvm-sh/nvm)
- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- [Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack)
- [Checkstyle for VS Code](https://marketplace.visualstudio.com/items?itemName=shengchen.vscode-checkstyle)
- [SpotBugs](https://spotbugs.github.io/)
- [SonarQube for IDE](https://marketplace.visualstudio.com/items?itemName=sonarsource.sonarlint-vscode)
- [Spotless Gradle](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle)
- [Gradle 公式ドキュメント](https://docs.gradle.org/)
- [Spring Boot 公式ドキュメント](https://docs.spring.io/spring-boot/)
