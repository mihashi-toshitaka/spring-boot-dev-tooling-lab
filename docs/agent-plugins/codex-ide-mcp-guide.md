# Codex IDE 拡張の MCP 管理ガイド

このガイドは、Agent Plugins 1.0.0 の `mcp.json` で MCP サーバーを管理しながら、VS Code の Codex IDE 拡張から同じサーバーを利用する方法を説明します。

Agent Plugins 1.0.0 と Codex は、MCP サーバー設定に異なるファイル形式を使用します。Agent Plugins のポータブルな設定はプラグインルート直下の `mcp.json`、Codex のプロジェクト設定は `.codex/config.toml` です。OpenAI 公式ドキュメントには、Codex が Agent Plugins の `mcp.json` を `.codex/config.toml` へ自動変換するという記載はありません。そのため、両方で利用する接続はそれぞれのファイルへ定義します。

DeepWikiは例外です。`ask_question`を公開せず、公開OSSのWiki閲覧だけに限定するため、ツール許可リストを設定できるCodexの `.codex/config.toml` だけへ定義します。Agent Plugins向けの `mcp.json` にはDeepWikiを定義しません。

本プロジェクトは商用の非公開リポジトリとして扱います。MCPサーバーを導入する前に、送信先、ツール入力、認証権限、保存期間、二次利用を確認し、[`AGENTS.md`](../../AGENTS.md#機密情報と外部ツール) の条件を満たす接続だけを追加します。

## 1. ファイルの役割

| ファイル | 対象 | 役割 |
| --- | --- | --- |
| `mcp.json` | Agent Plugins 1.0.0 対応クライアント | ポータブルな MCP サーバー接続を定義する正本 |
| `.codex/config.toml` | Codex CLI、Codex IDE 拡張 | Codex が実際に読み込むプロジェクト単位の MCP 設定 |

サーバー名、トランスポート、URL、起動コマンド、引数など、両形式で表現できる接続情報は `mcp.json` を基準に同期します。`enabled`、`required`、ツール許可リスト、ツール承認設定などのCodex固有項目は `.codex/config.toml` だけで管理します。DeepWikiのようにCodex固有の制限が必須となる接続は、同期対象から除外します。

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
            ├── documentation-mcp-guide.md
            └── llms.txt
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

## 5. このプロジェクトの設定例

本プロジェクトでは、公式ドキュメント用のmcpdoc、公開OSSのWiki閲覧用のDeepWiki、正確なソース確認用のGitHub MCPを使用します。DeepWikiはCodex限定であり、ほかの2サーバーだけをAgent PluginsとCodexの両方へ定義します。設定全体と運用方法は、[ドキュメント MCP ガイド](mcp/documentation-mcp-guide.md)を参照してください。

### 5.1 mcpdoc

mcpdocは `uvx` からSTDIOで起動します。Agent Pluginsでは `${PLUGIN_ROOT}` からローカルの `llms.txt` を解決します。Codexの相対 `cwd` は設定ファイルではなく起動プロセスを基準に解決されるため、BashでGitのリポジトリルートへ移動してから `uvx` を起動します。

~~~toml
[mcp_servers.mcpdoc-project]
command = "bash"
args = [
  "-c",
  '''
set -e
cd "$(git rev-parse --show-toplevel)"
exec uvx \
  --from mcpdoc==0.0.10 \
  --with mcp==1.28.0 \
  mcpdoc \
  --urls \
    Project:docs/agent-plugins/mcp/llms.txt \
    Gradle:https://docs.gradle.org/llms.txt \
    OpenAI:https://developers.openai.com/llms.txt \
    MCP:https://modelcontextprotocol.io/llms.txt \
    GitHub:https://docs.github.com/llms.txt \
  --allowed-domains \
    https://docs.oracle.com/ \
    https://docs.spring.io/ \
    https://documentation.red-gate.com/ \
    https://java.testcontainers.org/ \
    https://playwright.dev/ \
  --follow-redirects \
  --timeout 15 \
  --transport stdio
''',
]
startup_timeout_sec = 60
enabled = true
required = false
~~~

### 5.2 DeepWiki

DeepWikiはCodexからStreamable HTTPで接続します。`enabled_tools`を許可リストとして使用し、公開OSSのリポジトリ名だけを入力に取る `read_wiki_structure` と `read_wiki_contents` を公開します。質問文を入力に取る `ask_question` と、将来追加される未知のツールは公開しません。各呼び出しは実行前に承認を求めます。

~~~toml
[mcp_servers.deepwiki-project]
url = "https://mcp.deepwiki.com/mcp"
enabled = true
required = false
enabled_tools = ["read_wiki_structure", "read_wiki_contents"]
default_tools_approval_mode = "prompt"
~~~

非公開リポジトリの解析には使用しません。DeepWikiへの通信自体を禁止する環境では、`enabled = false`へ変更します。

### 5.3 GitHub MCP

GitHub MCPは公式DockerイメージをSTDIOで起動します。OAuth用ポートだけをループバックへ公開し、リポジトリ参照ツールに限定した読み取り専用モードを使用します。

~~~toml
[mcp_servers.github-project]
command = "docker"
args = [
  "run",
  "-i",
  "--rm",
  "-p",
  "127.0.0.1:8085:8085",
  "-e",
  "GITHUB_OAUTH_CALLBACK_PORT",
  "-e",
  "GITHUB_READ_ONLY",
  "-e",
  "GITHUB_TOOLSETS",
  "ghcr.io/github/github-mcp-server:v1.10.0",
]
env = { GITHUB_OAUTH_CALLBACK_PORT = "8085", GITHUB_READ_ONLY = "1", GITHUB_TOOLSETS = "repos" }
startup_timeout_sec = 120
enabled = true
required = false
~~~

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

接続確認例:

~~~text
mcpdocを使って、Spring Boot 4.1の公式ドキュメントを確認してください。

DeepWikiの `read_wiki_structure` を使って、公開リポジトリ `spring-projects/spring-boot` の構成を確認してください。

GitHub MCPを使って、spring-projects/spring-bootのv4.1.0タグからファイルを確認してください。
~~~

## 9. よくある問題

### `.codex/config.toml` が読み込まれない

- リポジトリルート直下の `.codex/config.toml` であることを確認する
- プロジェクトが Codex で信頼済みになっていることを確認する
- Codex IDE 拡張を再起動する
- ユーザー設定や CLI オプションによる上書きがないか確認する

### `mcp.json` だけでは Codex IDE 拡張に表示されない

Codex のローカルクライアントが MCP 設定として読み込むファイルは `config.toml` です。Agent Plugins とCodexの両方で使用するサーバーは `.codex/config.toml` にも定義します。Codex限定のDeepWikiは、`mcp.json` ではなく `.codex/config.toml` だけへ定義します。

### STDIO サーバーを起動できない

- `command` がインストールされ、`PATH` から解決できるか確認する
- `command` と `args` を分離しているか確認する
- `uvx` やDockerなど、サーバーが要求するランタイムのバージョンと起動状態を確認する
- 必要に応じて `cwd` を明示する

### 認証に失敗する

- 秘密情報を設定ファイルへ直接記述していないか確認する
- 指定した環境変数が Codex の実行環境へ渡されているか確認する
- OAuth 対応サーバーでは、MCP サーバー一覧の認証状態または `codex mcp login` の結果を確認する

## 10. 参考リンク

- [OpenAI Docs: Model Context Protocol](https://developers.openai.com/codex/mcp)
- [OpenAI Docs: Config basics](https://learn.chatgpt.com/docs/config-file/config-basic)
- [OpenAI Docs: Configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [mcpdoc](https://github.com/langchain-ai/mcpdoc)
- [DeepWiki MCP](https://docs.devin.ai/work-with-devin/deepwiki-mcp)
- [GitHub MCP Server](https://github.com/github/github-mcp-server)
- [ドキュメント MCP ガイド](mcp/documentation-mcp-guide.md)
- [Agent Plugins 1.0.0 標準化ガイド](agent-plugins-1.0.0-guide.md)
- [AGENTS.md ガイド](agents-md-guide.md)
