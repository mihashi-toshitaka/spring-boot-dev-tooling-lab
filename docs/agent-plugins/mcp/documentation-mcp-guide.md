# ドキュメント MCP ガイド

このガイドは、本プロジェクトで使用する mcpdoc、DeepWiki、GitHub MCP の役割、設定、利用方法、検証方法を説明します。

## 1. 役割分担

三つの MCP は、同じ情報源を重複して検索するためではなく、次の順序で根拠を補完するために使用します。

| MCP | 主な用途 | 注意点 |
| --- | --- | --- |
| mcpdoc | Java、Spring、Gradleなどの公式ドキュメントを取得する | 登録済みの公式サイトだけを取得対象にする |
| DeepWiki | 公開GitHubリポジトリの構造や設計を把握する | 生成された説明なので、バージョン固有の事実は別の根拠で確認する |
| GitHub MCP | タグ、リリース、実際のファイルやソースコードを確認する | GitHubアカウントによるOAuth認証が必要 |

バージョン依存の質問では、最初に `build.gradle.kts` や Gradle Wrapper から対象バージョンを特定します。その後、公式ドキュメントをmcpdocで確認し、必要に応じてGitHub MCPで該当タグの実装を確認します。DeepWikiは概要や関連箇所を探すために使用し、最終的なバージョン判定の唯一の根拠にはしません。

## 2. 設定ファイル

設定は二つのファイルで管理します。

| ファイル | 用途 |
| --- | --- |
| [`mcp.json`](../../../mcp.json) | Agent Plugins 1.0.0 対応クライアント向けのポータブルな正本 |
| [`.codex/config.toml`](../../../.codex/config.toml) | Codex CLI と VS Code の Codex IDE 拡張向けのプロジェクト設定 |

両方に `mcpdoc-project`、`deepwiki-project`、`github-project` を定義します。Codex固有の `enabled`、`required`、起動タイムアウトは `.codex/config.toml` だけで管理します。すべて `required = false` のため、一つのMCPが利用できない場合でもCodex自体は起動を継続します。

Agent Pluginsでは `${PLUGIN_ROOT}` を使用し、mcpdocのローカル索引をプラグインルートから解決します。Codexの `cwd` は設定ファイルではなく起動プロセスを基準に解決されるため、`.codex/config.toml` ではBashからGitのリポジトリルートへ移動して `uvx` を起動します。これにより、リポジトリ直下とサブディレクトリのどちらからCodexを起動しても同じ索引を読み込めます。

## 3. 前提条件

### 3.1 mcpdoc

mcpdoc 0.0.10を `uvx` から起動します。mcpdocはMCP Python SDK 2.xと互換性がないため、SDKも1.28.0へ固定します。リポジトリへPythonパッケージをインストールする必要はありません。

~~~bash
uvx --version
~~~

初回起動時はmcpdocと依存パッケージを取得するため、インターネット接続が必要です。

### 3.2 DeepWiki

DeepWikiは `https://mcp.deepwiki.com/mcp` へStreamable HTTPで接続します。公開リポジトリの参照にはアカウントやローカルランタイムは不要です。非公開リポジトリの確認には使用しません。

### 3.3 GitHub MCP

GitHub MCP 1.10.0を公式Dockerイメージから起動します。

~~~bash
docker version
~~~

初回利用時はブラウザまたはデバイスコードでGitHubへログインします。OAuthトークンはMCPプロセスのメモリ上だけに保持され、PATやトークンを `mcp.json` や `.codex/config.toml` へ保存しません。コンテナを再作成した場合は、再認証が必要になることがあります。

## 4. mcpdoc

### 4.1 ドキュメント索引

プロジェクト固有の索引は [`llms.txt`](llms.txt) です。現在のビルド設定に合わせ、次の公式ドキュメントを登録しています。

- Java SE 21とJDK 21 API
- Spring Boot 4.1
- Spring Security 7.1
- Spring Session 4.1
- Flyway
- Testcontainers for Java
- Playwright for Java

公式に `llms.txt` が公開されている次のサイトは、ローカル索引を経由せずmcpdocへ直接登録しています。

- Gradle
- OpenAI Developers
- Model Context Protocol
- GitHub Docs

### 4.2 アクセス制限

ローカル索引から取得できるURLは、mcpdocの `--allowed-domains` で次の公式サイトに限定しています。

- `https://docs.oracle.com/`
- `https://docs.spring.io/`
- `https://documentation.red-gate.com/`
- `https://java.testcontainers.org/`
- `https://playwright.dev/`

`*`による全ドメイン許可は使用しません。新しいドキュメントを索引へ追加するときは、提供元が公式サイトであることを確認し、必要なドメインだけを両方のMCP設定へ追加します。

### 4.3 利用方法

mcpdocを使う依頼では、最初に `list_doc_sources` で索引を確認し、`fetch_docs` で索引と必要なページを順に取得します。

~~~text
mcpdocを使って、このプロジェクトが使用するSpring Boot 4.1のTestcontainers連携を公式ドキュメントで確認してください。
~~~

## 5. DeepWiki

DeepWikiは公開リポジトリの構造や、機能がどのモジュールへ実装されているかを把握するときに使用します。

~~~text
DeepWikiを使って、spring-projects/spring-bootのTestcontainers連携がどのモジュールで構成されているか説明してください。
~~~

`read_wiki_structure` で構成を確認し、必要な項目を `read_wiki_contents` または `ask_question` で調査します。説明の更新時点や対象ブランチがプロジェクトの依存バージョンと一致するとは限らないため、API名や挙動は公式ドキュメントまたはGitHub MCPで確認します。

## 6. GitHub MCP

### 6.1 読み取り専用設定

Dockerコンテナには次の環境変数を渡します。

| 環境変数 | 値 | 目的 |
| --- | --- | --- |
| `GITHUB_OAUTH_CALLBACK_PORT` | `8085` | OAuthコールバックの待受ポートを固定する |
| `GITHUB_READ_ONLY` | `1` | 書き込みツールを公開しない |
| `GITHUB_TOOLSETS` | `repos` | リポジトリ参照ツールだけを公開する |

ホスト側では `127.0.0.1:8085` だけをコンテナへ公開します。Issue、Pull Request、ブランチ、ファイルなどを変更する用途には使用しません。

### 6.2 OAuth認証

最初のツール利用時に表示されるURLをブラウザで開き、GitHubでアクセスを承認します。ブラウザを直接開けないWSL2やヘッドレス環境では、表示されたデバイスコードによる認証を使用します。

ポート8085がほかのプロセスに使われている場合は、`mcp.json` と `.codex/config.toml` のポート公開値および `GITHUB_OAUTH_CALLBACK_PORT` を同じ未使用ポートへ変更します。

### 6.3 利用方法

ライブラリの実装を確認するときは、プロジェクトが使用するバージョンに対応するタグまたはコミットを明示します。

~~~text
GitHub MCPを使って、spring-projects/spring-bootのv4.1.0タグからTestcontainers連携の実装を確認してください。
~~~

## 7. 推奨する調査手順

1. `build.gradle.kts`、Gradle Wrapper、依存関係レポートから対象バージョンを特定する
2. mcpdocで対応する公式ドキュメントを確認する
3. 全体構造を把握する必要があればDeepWikiを使用する
4. バージョン固有のAPIや実装をGitHub MCPで該当タグから確認する
5. 回答には確認したバージョンと情報源を明記する

## 8. 検証方法

### 8.1 設定ファイル

リポジトリルートでJSON構文を確認します。

~~~bash
jq empty plugin.json mcp.json
~~~

JSON Schemaを含む詳細な検証方法は、[Agent Plugins 1.0.0標準化ガイド](../agent-plugins-1.0.0-guide.md#103-json-schema-検証)を参照してください。

Codexが三つのサーバーを認識していることを確認します。

~~~bash
codex mcp list
codex mcp get mcpdoc-project
codex mcp get deepwiki-project
codex mcp get github-project
~~~

### 8.2 接続確認

新しいCodexセッションで次を確認します。

1. mcpdocの `list_doc_sources` にプロジェクト、Gradle、OpenAI、MCP、GitHubの索引が表示される
2. mcpdocの `fetch_docs` でSpring Boot 4.1とGradleのページを取得できる
3. DeepWikiで `spring-projects/spring-boot` の構造を取得できる
4. GitHub OAuthを完了し、`v4.1.0`タグのファイルを取得できる
5. GitHub MCPに書き込みツールが表示されない

## 9. 更新方法

- mcpdocを更新するときは、PyPIのリリースとMCP Python SDKの互換性を確認し、`mcpdoc==<version>` と `mcp==<version>` を両方の設定で同時に変更する
- GitHub MCPを更新するときは、公式リリースを確認してDockerイメージのタグを両方の設定で同時に変更する
- `build.gradle.kts` やGradle Wrapperのバージョンを変更したときは、`llms.txt` のリンクも見直す
- 接続先や引数を変更した後はJSON Schema、`codex mcp list`、実際のツール呼び出しを再確認する

## 10. トラブルシューティング

### mcpdocを起動できない

- `uvx --version` が成功するか確認する
- 初回パッケージ取得がプロキシやファイアウォールで遮断されていないか確認する
- `git rev-parse --show-toplevel` と `docs/agent-plugins/mcp/llms.txt` のパスを確認する
- 取得先が `--allowed-domains` に含まれているか確認する

### DeepWikiへ接続できない

- `https://mcp.deepwiki.com/mcp` へHTTPS接続できるか確認する
- 対象が公開GitHubリポジトリであることを確認する
- プロキシ、VPN、ファイアウォールの制限を確認する

### GitHub MCPを起動または認証できない

- Dockerが起動しており、公式イメージを取得できるか確認する
- `127.0.0.1:8085` が使用中でないか確認する
- OAuth URLまたはデバイスコードの有効期限が切れていないか確認する
- 組織のGitHub OAuthポリシーで承認が必要でないか確認する

### 設定変更が反映されない

実行中のセッションは変更したMCP設定を自動的に取り込まない場合があります。Codex IDE拡張を再起動し、新しい会話で確認します。

## 11. 参考リンク

- [Codex IDE 拡張のMCP管理ガイド](../codex-ide-mcp-guide.md)
- [Agent Plugins 1.0.0標準化ガイド](../agent-plugins-1.0.0-guide.md)
- [OpenAI Docs: Model Context Protocol](https://developers.openai.com/codex/mcp)
- [mcpdoc](https://github.com/langchain-ai/mcpdoc)
- [DeepWiki MCP](https://docs.devin.ai/work-with-devin/deepwiki-mcp)
- [GitHub MCP Server](https://github.com/github/github-mcp-server)
