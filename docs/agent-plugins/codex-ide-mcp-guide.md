# Codex IDE 拡張の MCP 管理ガイド

このガイドは、Agent Plugins 1.0.0 の `mcp.json` で MCP サーバーを管理しながら、VS Code の Codex IDE 拡張から同じサーバーを利用する方法を説明します。

Agent Plugins 1.0.0 と Codex は、MCP サーバー設定に異なるファイル形式を使用します。Agent Plugins のポータブルな設定はプラグインルート直下の `mcp.json`、Codex のプロジェクト設定は `.codex/config.toml` です。OpenAI 公式ドキュメントには、Codex が Agent Plugins の `mcp.json` を `.codex/config.toml` へ自動変換するという記載はありません。そのため、このプロジェクトでは同じ接続内容を両方のファイルへ定義します。

## 1. ファイルの役割

| ファイル | 対象 | 役割 |
| --- | --- | --- |
| `mcp.json` | Agent Plugins 1.0.0 対応クライアント | ポータブルな MCP サーバー接続を定義する正本 |
| `.codex/config.toml` | Codex CLI、Codex IDE 拡張 | Codex が実際に読み込むプロジェクト単位の MCP 設定 |

サーバー名、トランスポート、URL、起動コマンド、引数など、両形式で表現できる接続情報は `mcp.json` を基準に同期します。`enabled`、`required`、ツール承認設定などの Codex 固有項目は `.codex/config.toml` だけで管理します。

~~~text
spring-boot-dev-tooling-lab/
├── plugin.json
├── mcp.json
├── .codex/
│   └── config.toml
└── docs/
    └── agent-plugins/
        ├── codex-ide-mcp-guide.md
        └── mcp/
            └── context7-mcp-guide.md
~~~

## 2. 推奨する管理手順

MCP サーバーを追加または変更するときは、次の順序で作業します。

1. `mcp.json` のサーバー定義を追加または変更する
2. 同じサーバー名と接続情報を `.codex/config.toml` へ反映する
3. `mcp.json` の JSON 構文と Agent Plugins スキーマを検証する
4. Codex CLI または Codex IDE 拡張で接続を確認する
5. 接続情報を変更した場合は、必要に応じて Codex IDE 拡張を再起動する

サーバーが少ない間は手動で同期します。定義数が増え、同期漏れが継続的な問題になった場合にだけ、自動生成や検証スクリプトの導入を検討します。

## 3. Streamable HTTP サーバー

リモート MCP サーバーには `streamable-http` を使用します。ローカルランタイムをインストールする必要がなく、複数のクライアントで同じ接続先を共有しやすいため、利用可能であればこの形式を優先します。

### 3.1 Agent Plugins の設定

プラグインルートの `mcp.json` に定義します。

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "example-docs": {
      "type": "streamable-http",
      "url": "https://docs.example.com/mcp"
    }
  }
}
~~~

### 3.2 Codex の設定

プロジェクトルートの `.codex/config.toml` に、同じサーバー名と URL を定義します。

~~~toml
[mcp_servers.example-docs]
url = "https://docs.example.com/mcp"
enabled = true
required = false
~~~

`required = false` にすると、MCP サーバーが一時的に利用できない場合でも Codex 自体の起動を継続できます。MCP 接続が作業の必須条件である場合だけ `true` を検討します。

## 4. STDIO サーバー

ローカルプロセスとして起動する MCP サーバーには `stdio` を使用します。チームで共有する場合は、必要なランタイム、実行ファイル、対応バージョンを別途ドキュメント化します。

### 4.1 Agent Plugins の設定

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "example-local": {
      "type": "stdio",
      "command": "node",
      "args": ["${PLUGIN_ROOT}/server.js"]
    }
  }
}
~~~

### 4.2 Codex の設定

~~~toml
[mcp_servers.example-local]
command = "node"
args = ["server.js"]
enabled = true
required = false
~~~

`command` と `args` は分離します。`command = "node server.js"` のように、一つの文字列へまとめないでください。

Agent Plugins の `${PLUGIN_ROOT}` や `${PLUGIN_DATA}` は Agent Plugins クライアントが展開するプレースホルダーです。Codex の `.codex/config.toml` へそのまま転記せず、Codex の実行環境で解決できる `cwd` またはパスへ置き換えます。

## 5. Context7 の設定例

Context7 は Streamable HTTP と STDIO の両方で利用できます。このプロジェクトでは、Node.js を必要としない Streamable HTTP 接続を推奨します。

本プロジェクトへ導入した設定の利用方法と検証手順は、[Context7 MCP ガイド](mcp/context7-mcp-guide.md)を参照してください。

例ではサーバー識別名を `context7-project` としています。ユーザーの `~/.codex/config.toml` に同名の設定があるとCodexの設定階層で項目が混在する可能性があるため、プロジェクト固有の名前を使用します。

### 5.1 推奨: Streamable HTTP

`mcp.json`:

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

`.codex/config.toml`:

~~~toml
[mcp_servers.context7-project]
url = "https://mcp.context7.com/mcp"
enabled = true
required = false
~~~

### 5.2 代替: STDIO

ローカルで起動する場合は、先に Context7 が要求するバージョンの Node.js と `npx` を利用できることを確認します。

`mcp.json`:

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "context7-project": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    }
  }
}
~~~

`.codex/config.toml`:

~~~toml
[mcp_servers.context7-project]
command = "npx"
args = ["-y", "@upstash/context7-mcp@latest"]
enabled = true
required = false
~~~

`@latest` は常に最新リリースを取得するため、更新によって動作が変わる可能性があります。再現性を優先する場合は、動作確認した具体的なバージョンへ固定し、両方のファイルを同時に更新します。

## 6. 認証情報の管理

API キー、アクセストークン、パスワードなどの秘密情報を、リポジトリへコミットする `mcp.json` や `.codex/config.toml` に直接記述しないでください。

Agent Plugins 1.0.0 は、ポータブルな資格情報参照や OAuth フローを定義していません。認証情報は利用するクライアント側で管理します。

Codex の Streamable HTTP 接続では、トークンの値ではなく環境変数名を指定できます。

~~~toml
[mcp_servers.private-docs]
url = "https://docs.example.com/mcp"
bearer_token_env_var = "PRIVATE_DOCS_TOKEN"
~~~

独自ヘッダーが必要な場合は、ヘッダー名と環境変数名を対応付けます。

~~~toml
[mcp_servers.private-docs]
url = "https://docs.example.com/mcp"
env_http_headers = { "X-API-Key" = "PRIVATE_DOCS_API_KEY" }
~~~

OAuth 対応サーバーでは、Codex IDE 拡張の MCP サーバー一覧から認証するか、Codex CLI で次を実行します。

~~~bash
codex mcp login <server-name>
~~~

## 7. Codex によるプロジェクト設定の読み込み

Codex CLI と Codex IDE 拡張は、同じ Codex ホスト上で `config.toml` の設定階層を共有します。プロジェクト固有の `.codex/config.toml` は、そのプロジェクトが信頼済みの場合だけ読み込まれます。

主な優先順位は次のとおりです。

1. CLI オプションと `--config` による上書き
2. プロジェクトの `.codex/config.toml`
3. 選択したプロファイル
4. ユーザーの `~/.codex/config.toml`
5. システム設定
6. Codex の既定値

同じ MCP サーバー名をユーザー設定とプロジェクト設定の両方へ定義した場合は、意図した設定が選択されているか確認してください。チームで共有するサーバーはプロジェクト設定、個人だけが使うサーバーはユーザー設定へ分けます。

## 8. 検証方法

### 8.1 Agent Plugins 設定

JSON 構文を確認します。

~~~bash
jq empty mcp.json
~~~

Agent Plugins の JSON Schema 検証については、[Agent Plugins 1.0.0 標準化ガイド](agent-plugins-1.0.0-guide.md#103-json-schema-検証)を参照してください。

### 8.2 Codex 設定

Codex CLI が利用できる場合は、認識された MCP サーバーを確認します。

~~~bash
codex mcp list
~~~

VS Code では次の手順で確認します。

1. 対象プロジェクトが信頼済みであることを確認する
2. Codex IDE 拡張の歯車メニューから `MCP servers` を開く
3. 対象サーバーが有効で、接続エラーがないことを確認する
4. 設定変更後に反映されない場合は `Restart extension` を実行する
5. 新しい会話で、対象 MCP サーバーの情報を使う依頼を試す

Context7 の確認例:

~~~text
Context7 を使って、現在の Spring Boot の公式ドキュメントを確認してください。
~~~

## 9. よくある問題

### `.codex/config.toml` が読み込まれない

- リポジトリルート直下の `.codex/config.toml` であることを確認する
- プロジェクトが Codex で信頼済みになっていることを確認する
- Codex IDE 拡張を再起動する
- ユーザー設定や CLI オプションによる上書きがないか確認する

### `mcp.json` だけでは Codex IDE 拡張に表示されない

Codex のローカルクライアントが MCP 設定として読み込むファイルは `config.toml` です。Agent Plugins の `mcp.json` と同じサーバーを `.codex/config.toml` にも定義します。

### STDIO サーバーを起動できない

- `command` がインストールされ、`PATH` から解決できるか確認する
- `command` と `args` を分離しているか確認する
- Node.js など、サーバーが要求するランタイムのバージョンを確認する
- 必要に応じて `cwd` を明示する

### 認証に失敗する

- 秘密情報を設定ファイルへ直接記述していないか確認する
- 指定した環境変数が Codex の実行環境へ渡されているか確認する
- OAuth 対応サーバーでは、MCP サーバー一覧の認証状態または `codex mcp login` の結果を確認する

## 10. 参考リンク

- [OpenAI Docs: Model Context Protocol](https://developers.openai.com/codex/mcp)
- [OpenAI Docs: Config basics](https://learn.chatgpt.com/docs/config-file/config-basic)
- [OpenAI Docs: Configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Context7 MCP](https://github.com/upstash/context7/tree/master/packages/mcp)
- [Context7 MCP ガイド](mcp/context7-mcp-guide.md)
- [Agent Plugins 1.0.0 標準化ガイド](agent-plugins-1.0.0-guide.md)
- [AGENTS.md ガイド](agents-md-guide.md)
