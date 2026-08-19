# Context7 MCP ガイド

このガイドは、本プロジェクトへ導入した Context7 MCP の構成、利用方法、検証方法、更新時の注意点を説明します。

Context7 は、ライブラリやフレームワークのドキュメントを AI エージェントへ提供する MCP サーバーです。本プロジェクトでは、ローカルプロセスを起動する STDIO ではなく、Context7 が提供するリモートの Streamable HTTP エンドポイントへ接続します。

## 1. 導入済みの構成

接続先は次の URL です。

~~~text
https://mcp.context7.com/mcp
~~~

設定は二つのファイルで管理します。

| ファイル | 用途 |
| --- | --- |
| [`mcp.json`](../../../mcp.json) | Agent Plugins 1.0.0 対応クライアント向けのポータブルな正本 |
| [`.codex/config.toml`](../../../.codex/config.toml) | Codex CLI と VS Code の Codex IDE 拡張向けのプロジェクト設定 |

Streamable HTTP 接続では、Context7 の npm パッケージをローカルへインストールしません。そのため、Context7 の利用だけを目的とした Node.js のインストールや、パッケージバージョンの更新作業は不要です。

## 2. Agent Plugins の設定

プラグインルートの `mcp.json` には、次の接続を定義しています。

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "context7-project": {
      "type": "streamable-http",
      "url": "https://mcp.context7.com/mcp"
    }
  }
}
~~~

このファイルを Context7 の接続情報の正本とします。サーバー名、トランスポート、URL を変更する場合は、先に `mcp.json` を更新します。

サーバー識別名には `context7-project` を使用しています。Codex はユーザー設定とプロジェクト設定の同名テーブルを階層的に解決するため、`~/.codex/config.toml` に `context7` が存在する環境でも衝突しない名前にしています。

## 3. Codex IDE 拡張の設定

プロジェクトルートの `.codex/config.toml` には、同じ接続先を Codex の形式で定義しています。

~~~toml
[mcp_servers.context7-project]
url = "https://mcp.context7.com/mcp"
enabled = true
required = false
~~~

`required = false` としているため、Context7 が一時的に利用できない場合でも Codex 自体は起動を継続できます。

Codex は、信頼済みプロジェクトの `.codex/config.toml` だけをプロジェクト設定として読み込みます。設定を反映するには、このリポジトリを Codex で信頼済みにしたうえで、Codex IDE 拡張を再起動するか、新しい Codex セッションを開始してください。

## 4. 認証情報

現在の構成には Context7 の API キーを設定していません。API キーなどの秘密情報が必要になった場合も、`mcp.json` や `.codex/config.toml` へ値を直接記述しないでください。

Codex で独自ヘッダーへ認証情報を渡す場合は、`env_http_headers` でヘッダー名と環境変数名を対応付けます。具体的なヘッダー名は、設定時点の Context7 公式ドキュメントで確認します。

~~~toml
[mcp_servers.context7-project]
url = "https://mcp.context7.com/mcp"
env_http_headers = { "<header-name>" = "CONTEXT7_API_KEY" }
enabled = true
required = false
~~~

環境変数の値は、VS Code と Codex を起動する環境へ安全な方法で設定します。

## 5. 利用方法

通常の依頼に、Context7を使用することを明記します。

~~~text
Context7 を使って、現在の Spring Boot の公式ドキュメントを確認してください。
~~~

対象ライブラリと確認したい内容を具体的にすると、関連するドキュメントを選びやすくなります。

~~~text
Context7 を使って、Spring Boot の Testcontainers 連携方法を確認してください。
~~~

## 6. 検証方法

### 6.1 Agent Plugins 設定

リポジトリルートで JSON 構文を確認します。

~~~bash
jq empty mcp.json
~~~

JSON Schema を含む詳細な検証方法は、[Agent Plugins 1.0.0 標準化ガイド](../agent-plugins-1.0.0-guide.md#103-json-schema-検証)を参照してください。

### 6.2 Codex 設定

Codex CLI が利用できる場合は、認識されたサーバーを確認します。

~~~bash
codex mcp list
~~~

VS Codeでは次を確認します。

1. このプロジェクトが Codex で信頼済みになっている
2. Codex IDE 拡張の歯車メニューにある `MCP servers` に `context7-project` が表示される
3. `context7-project` が有効で、接続エラーが表示されていない
4. 設定変更後に `Restart extension` を実行している
5. 新しい会話で Context7 を指定した依頼を実行できる

## 7. 更新方法

Context7の接続先を変更する場合は、次の順序で更新します。

1. `mcp.json` の `context7-project` 定義を更新する
2. `.codex/config.toml` へ同じ接続情報を反映する
3. `jq empty mcp.json` を実行する
4. `codex mcp list` または Codex IDE 拡張の MCP サーバー一覧を確認する
5. Codex IDE 拡張を再起動し、新しい会話で動作確認する

リモート接続では npm パッケージを固定していないため、通常はローカルのパッケージ更新作業はありません。Context7 側の要件やエンドポイントが変更された場合は、公式ドキュメントを確認して両方の設定ファイルを更新します。

## 8. トラブルシューティング

### `context7-project` が一覧に表示されない

- `.codex/config.toml` の位置とTOML構文を確認する
- プロジェクトが Codex で信頼済みか確認する
- Codex IDE 拡張を再起動する
- ユーザーの `~/.codex/config.toml` に同名サーバーの競合設定がないか確認する

### 接続エラーになる

- `https://mcp.context7.com/mcp` へ接続できるネットワーク環境か確認する
- プロキシ、VPN、ファイアウォールの制限を確認する
- Context7 の稼働状況と最新の公式ドキュメントを確認する
- 認証が必要な場合は、API キーの設定方法と利用制限を確認する

### 設定変更が反映されない

実行中の Codex セッションは、変更した MCP 設定を自動的に取り込まない場合があります。Codex IDE 拡張を再起動し、新しい会話で確認します。

## 9. 参考リンク

- [Codex IDE 拡張の MCP 管理ガイド](../codex-ide-mcp-guide.md)
- [Agent Plugins 1.0.0 標準化ガイド](../agent-plugins-1.0.0-guide.md)
- [OpenAI Docs: Model Context Protocol](https://developers.openai.com/codex/mcp)
- [OpenAI Docs: Config basics](https://learn.chatgpt.com/docs/config-file/config-basic)
- [Context7 MCP](https://github.com/upstash/context7/tree/master/packages/mcp)
