# Agent Plugins 1.0.0 標準化ガイド

このガイドは、ベンダー中立の [Agent Plugins Specification 1.0.0](https://agent-plugins.org/specification) に基づき、ポータブルな Agent Plugin の構成、マニフェスト、Agent Skills、MCP サーバー、クライアント拡張、検証方法を説明します。

Agent Plugins 1.0.0 の公式ステータスは、本ガイド作成時点では **Working Draft** です。実装時は最新の公式仕様も確認してください。仕様本文と JSON Schema が矛盾する場合は、仕様本文が優先されます。

## 1. Agent Plugins が標準化するもの

Agent Plugin は、AI エージェントへ再利用可能な機能を追加する自己完結したディレクトリです。Agent Plugins 1.0.0 は、異なるクライアントが同じパッケージを発見、検証、読み込みできるための最小限の共通形式を定義します。

ポータブルなコンポーネントは次の二種類です。

| コンポーネント | 役割 | 固定位置 |
| --- | --- | --- |
| Agent Skills | 手順、専門知識、スクリプト、参照資料を提供する | `skills/<skill-name>/SKILL.md` |
| MCP servers | ローカルまたはリモートの MCP サーバー接続を定義する | `mcp.json` |

次の項目は Agent Plugins 1.0.0 のポータブルコアには含まれません。

- プラグインの配布元、マーケットプレイス、インストール方法
- 有効化、更新、公開、権限承認のユーザーインターフェース
- OAuth フロー、資格情報の保存、秘密情報の管理
- フック、コマンド、サブエージェント、ルールなどのクライアント固有機能

クライアント固有機能は、後述するクライアント拡張として名前空間を分離できます。特定製品が使用する `.codex-plugin/plugin.json` などの形式は、ルートの `plugin.json` を使用するポータブルコアとは別のものです。

公式仕様中の `MUST`、`MUST NOT`、`SHOULD`、`MAY` は RFC 2119 / RFC 8174 の規範用語です。本ガイドでは読みやすさのため日本語で説明していますが、適合性の最終判断には公式仕様を使用してください。

## 2. 標準ディレクトリ構成

Skills、MCP サーバー、クライアント拡張を含む構成例は次のとおりです。

~~~text
my-plugin/
├── plugin.json
├── skills/
│   └── summarize/
│       ├── SKILL.md
│       ├── scripts/
│       │   └── analyze.sh
│       ├── references/
│       │   └── checklist.md
│       └── assets/
├── mcp.json
├── com.example.client/
│   └── hooks/
└── LICENSE
~~~

必須ファイルは、プラグインルート直下の `plugin.json` だけです。`skills/`、`mcp.json`、クライアント拡張ディレクトリは、必要な機能がある場合だけ追加します。不在であること自体はエラーではありません。

コンポーネントの位置は固定です。`plugin.json` で別のパスを指定したり、Skills や MCP サーバーの設定をインライン定義したりすることはできません。

## 3. `plugin.json`

### 3.1 最小マニフェスト

`plugin.json` は JSON オブジェクトで、`$schema` と `name` が必須です。

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "my-plugin"
}
~~~

`$schema` は、プラグインが対象とする Agent Plugins 仕様のバージョンを識別します。クライアントは、この識別子からローカルで対応している検証規則を選択します。プラグインの読み込み中に、スキーマをネットワークから取得するための URL ではありません。

### 3.2 許可されるフィールド

`plugin.json` のトップレベルスキーマは閉じています。

| フィールド | 必須 | 内容 |
| --- | --- | --- |
| `$schema` | 必須 | Agent Plugins 1.0.0 の正規スキーマ識別子 |
| `name` | 必須 | プラグイン識別名 |
| `version` | 任意 | プラグイン自身のバージョン。Semantic Versioning を推奨 |
| `description` | 任意 | プラグインの短い説明 |
| `author` | 任意 | `name`、`email`、`url` の文字列を持つオブジェクト |
| `homepage` | 任意 | ドキュメントまたはホームページ |
| `repository` | 任意 | ソースリポジトリ |
| `license` | 任意 | ライセンス。SPDX 識別子を推奨 |
| `keywords` | 任意 | 検索用文字列の配列 |
| `extensions` | 任意 | 逆ドメイン名前空間で分離したクライアント固有データ |

これ以外のフィールドをポータブルな設定として追加しないでください。未知のトップレベルフィールドは適合しませんが、クライアントはそのフィールドを報告して無視し、ほかの部分が有効であれば読み込みを継続します。それ以外の通常のスキーマ違反はプラグイン全体を無効にします。

### 3.3 `name` の制約

| 項目 | 制約 |
| --- | --- |
| 長さ | 1〜64 文字 |
| 使用可能文字 | 小文字の `a-z`、数字の `0-9`、ハイフン、ピリオド |
| 先頭と末尾 | 英数字 |
| 連続記号 | `--` と `..` は使用不可 |

有効な例は `my-plugin`、`acme.tools`、`lint3r` です。`My-Plugin`、`my_plugin`、`-plugin`、`has--double` は無効です。

### 3.4 仕様バージョンとプラグインバージョン

次の二つは別の意味を持ちます。

- `$schema` の `1.0.0`: Agent Plugins の仕様およびスキーマのバージョン
- `version` の値: 作成したプラグイン自身のリリースバージョン

プラグイン自身の `version` には Semantic Versioning の使用を推奨します。`mcp.json` がある場合、その `$schema` は `plugin.json` と同じ Agent Plugins バージョンを対象にする必要があります。

## 4. Agent Skills

Agent Plugins は Skill の発見位置と失敗時の分離だけを定義します。`SKILL.md` の形式は [Agent Skills Specification](https://agentskills.io/specification) に従います。

`skills/` の直下に Skill ごとのディレクトリを作成し、その直下へ大文字小文字を含めて正確に `SKILL.md` を配置します。

~~~text
skills/
└── deploy/
    ├── SKILL.md
    ├── scripts/
    │   └── rollback.sh
    ├── references/
    │   └── runbook.md
    └── assets/
~~~

クライアントは `skills/` の直下だけを走査し、より深い階層を再帰検索しません。`SKILL.md` は通常ファイルとして解決できる必要があります。

最小の `SKILL.md` は、YAML frontmatter の `name` と `description`、それに続く Markdown の手順で構成します。

~~~markdown
---
name: deploy
description: Deploy an application and verify its health. Use when preparing or running a deployment.
---

# Deploy

Inspect the deployment configuration, run the deployment, and verify health checks.
~~~

Agent Skills 側の `name` は親ディレクトリ名と一致させ、1〜64 文字の小文字英数字とハイフンで構成します。先頭と末尾にハイフンを置かず、`--` を含めないでください。Agent Plugin の名前と異なり、Skill 名ではピリオドを使用しません。

一つの Skill が無効な場合、クライアントはその Skill だけをスキップし、ほかの Skills や MCP サーバーの読み込みを継続します。

## 5. MCP サーバー

### 5.1 `mcp.json`

MCP サーバーを含める場合は、プラグインルート直下へ `.mcp.json` ではなく `mcp.json` を配置します。トップレベルで許可されるフィールドは `$schema` と `mcpServers` だけです。

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
  "mcpServers": {
    "local-validator": {
      "type": "stdio",
      "command": "./bin/validator",
      "args": ["--data", "${PLUGIN_DATA}/validator"],
      "env": {
        "CONFIG": "${PLUGIN_ROOT}/config.json"
      },
      "cwd": "${PLUGIN_ROOT}"
    },
    "deployment-api": {
      "type": "streamable-http",
      "url": "https://deploy.example.com/mcp",
      "headers": {
        "X-Tenant": "public-tenant"
      }
    }
  }
}
~~~

空の `mcpServers` オブジェクトもスキーマ上は有効ですが、実際の MCP サーバーがない場合は `mcp.json` 自体を省略する方が構成を明確にできます。

### 5.2 トランスポート

| `type` | 必須フィールド | 任意フィールド | 用途 |
| --- | --- | --- | --- |
| `stdio` | `type`、`command` | `args`、`env`、`cwd` | ローカルプロセスとして起動する MCP サーバー |
| `streamable-http` | `type`、`url` | `headers` | 現行のリモート MCP トランスポート |
| `sse` | `type`、`url` | `headers` | 非推奨の HTTP+SSE トランスポート |

MCP 対応クライアントは `stdio` または `streamable-http` の少なくとも一方をサポートし、両方をサポートすることが推奨されます。`sse` のサポートは任意です。接続失敗時の別トランスポートへのフォールバックは、この仕様では定義されません。

各サーバー設定は、いずれか一つの閉じたトランスポート形式へ一致させます。未知の `type` やフィールド、別トランスポート用フィールドの混在は、そのサーバーエントリだけを無効にします。

### 5.3 `stdio` の実行規則

`command` はシェルコマンド文字列ではなく、一つの実行可能トークンです。

~~~json
{
  "type": "stdio",
  "command": "./bin/server",
  "args": ["--config", "${PLUGIN_ROOT}/config.json"]
}
~~~

`command: "node server.js"` のようにコマンドと引数を一つの文字列へまとめないでください。次のように分離します。

~~~json
{
  "type": "stdio",
  "command": "node",
  "args": ["${PLUGIN_ROOT}/server.js"]
}
~~~

`command` は、プラットフォームの検索規則で解決する実行ファイル名、または `./` で始まるプラグイン相対パスです。プラグインへ同梱した実行ファイルには `./` から始まるパスを使用します。`command` 自体ではプレースホルダーを展開しません。

設定した `PATH` が実行ファイル名の検索へ使われるかはクライアント依存です。ポータブルなプラグインはその挙動に依存せず、同梱した実行ファイルをプラグイン相対パスで指定します。

`cwd` を省略した場合はプラグインルートが使用されます。指定できる形式は次のとおりです。

- `./` で始まるプラグイン相対パス
- `${PLUGIN_ROOT}` または `${PLUGIN_ROOT}/` で始まるパス
- `${PLUGIN_DATA}` または `${PLUGIN_DATA}/` で始まるパス

クライアントはプレースホルダーを展開してから `cwd` を解決します。`./` または `${PLUGIN_ROOT}` を基準とする値は、解決後もプラグインルート内に残る必要があります。`${PLUGIN_DATA}` を基準とする値は、解決後もプラグインデータディレクトリ内に残る必要があります。それ以外の形式や、対応する境界外へ出るパスは、その MCP サーバーエントリを無効にします。

### 5.4 リモート接続

リモート MCP の URL には次の制約があります。

- 絶対 HTTP または HTTPS URL を使用する
- ユーザー情報とフラグメントを含めない
- ループバック以外の接続先では HTTPS を使用する
- HTTP は `localhost` またはループバック IP リテラルだけで使用する

`headers` の名前と値は有効な HTTP ヘッダーフィールドである必要があり、リテラルとして扱われてプレースホルダー展開の対象にはなりません。同じヘッダー名を大文字小文字だけ変えて重複させることもできません。クライアントが認証などのために生成した同名ヘッダーは、パッケージに設定されたヘッダーより優先されます。

`headers` はパッケージを読む利用者から見えるため、API キー、アクセストークン、パスワードを記述しないでください。リダイレクトまたは legacy SSE endpoint event を介して別オリジンへ接続する場合、クライアントは明示的な利用者の許可なしに設定済みヘッダーを転送してはいけません。

Agent Plugins 1.0.0 は、ポータブルな OAuth 設定や資格情報参照を定義しません。認証処理と資格情報の保存はクライアントが管理します。

## 6. パス境界と実行環境

### 6.1 パッケージ境界

プラグインが提供するファイルやディレクトリは、シンボリックリンクなどを解決した後もプラグインルート内に存在する必要があります。仕様上のプラグイン相対パスは `./` で始め、ルート外へ移動する `../` を使用しないでください。

~~~text
有効:   ./bin/server
有効:   ./config/settings.json
無効:   ../shared/server
無効:   config/settings.json
~~~

このパッケージ境界は、プラグインが提供するファイルへのアクセス規則です。起動した MCP プロセスそのものを隔離するサンドボックスではありません。プロセスの権限分離や OS レベルの制限はクライアント側で別途実施します。

### 6.2 `PLUGIN_ROOT` と `PLUGIN_DATA`

`stdio` MCP サーバーを起動するクライアントは、次の環境変数を提供します。

| 変数 | 用途 |
| --- | --- |
| `PLUGIN_ROOT` | インストールされたプラグインの同梱ファイル、設定、スクリプトを参照する |
| `PLUGIN_DATA` | 依存ライブラリ、仮想環境、生成コード、キャッシュなど、更新後も保持する書き込み可能データを格納する |

`${PLUGIN_ROOT}` と `${PLUGIN_DATA}` の展開対象は、`args` の各文字列、`env` の値、`cwd` です。展開は一回だけ行われ、再帰的には行われません。次の場所では展開されません。

- `command`
- `env` のキー
- リモート URL
- HTTP ヘッダー
- `skills/` や `mcp.json` などの固定位置

`env` へ `PLUGIN_ROOT` または `PLUGIN_DATA` を定義して上書きすることはできません。また、`env` の値も秘密情報の保存場所ではありません。

ポータブルなプラグインは、仕様が保証する環境変数または `env` で明示した環境変数以外の、クライアント実行環境に偶然存在する変数へ依存してはいけません。

## 7. クライアント拡張

クライアント固有のマニフェストデータは、`extensions` 内で逆ドメイン形式の名前空間に分離します。

~~~json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "example-plugin",
  "extensions": {
    "com.example.client": {
      "setting": true
    }
  }
}
~~~

クライアント固有ファイルは、プラグインルート直下の同じ名前空間ディレクトリへ配置します。

~~~text
example-plugin/
├── plugin.json
├── skills/
└── com.example.client/
    └── hooks/
        └── hooks.json
~~~

名前空間は、管理しているドメインを基に安定した名前を選びます。クライアントは未実装の名前空間を、その内容を検証せずに無視します。クライアント拡張のファイル形式、読み込み、検証、失敗時の処理は、名前空間の所有者が定義します。

`extensions` がオブジェクトである場合、各名前空間の値もオブジェクトでなければなりません。`extensions` 自体がオブジェクトでない場合は、そのフィールドだけを報告して無視する特例があります。一方、名前空間の値がオブジェクトでない場合は通常のマニフェストスキーマ違反となり、プラグイン全体が無効になります。

## 8. 失敗の分離

Agent Plugins は、一つの局所的な問題で独立したコンポーネントまで利用不能にしないよう、失敗範囲を定義しています。

| 状況 | 処理 |
| --- | --- |
| `plugin.json` がない、未対応の `$schema`、通常のスキーマ違反 | プラグイン全体を拒否する |
| 未知の `plugin.json` トップレベルフィールド | 報告してそのフィールドを無視し、読み込みを継続する |
| `extensions` がオブジェクトでない | 報告して `extensions` を無視し、読み込みを継続する |
| `skills/` または `mcp.json` がない | エラーにしない |
| `skills/` がディレクトリでない | Skills だけを無効にする |
| 一つの `SKILL.md` が無効 | その Skill だけをスキップする |
| `mcp.json` 全体が無効、またはスキーマバージョンが不一致 | MCP だけを無効にする |
| 一つの MCP エントリが無効、未対応、接続失敗 | そのサーバーだけをスキップする |
| 未対応のコンポーネント種別またはクライアント拡張 | 無視し、対応している部分を読み込む |
| 解決後のパッケージパスがルート外 | 最も狭い該当範囲を無効化するか、そのパスへのアクセスを拒否する |

クライアントは Skills と MCP の両方を実装する必要はありません。少なくとも一つのコンポーネント種別をサポートし、実装した種別の規則を満たせば、段階的に Agent Plugins へ対応できます。

## 9. このプロジェクトでの構成

本プロジェクトは、リポジトリルート自体をプラグインルートとして使用しています。

~~~text
spring-boot-dev-tooling-lab/
├── AGENTS.md
├── plugin.json
├── skills/
│   └── fix-sonarlint-issues/
│       └── SKILL.md
└── docs/
    └── agent-plugins/
        ├── agent-plugins-1.0.0-guide.md
        └── agents-md-guide.md
~~~

- [`AGENTS.md`](../../AGENTS.md) は Codex へリポジトリ内の作業方針を伝えるクライアント固有ファイルであり、Agent Plugins 1.0.0 のポータブルコンポーネントではありません。詳細は [AGENTS.md ガイド](agents-md-guide.md)を参照してください。
- [`plugin.json`](../../plugin.json) は Agent Plugins 1.0.0 の正規 `$schema` とプラグインメタデータを定義します。
- [`fix-sonarlint-issues/SKILL.md`](../../skills/fix-sonarlint-issues/SKILL.md) は、SonarLint が表示した指摘を調査し、安全に修正して回帰確認する手順を提供します。
- MCP サーバー実装がないため、`mcp.json` は作成していません。
- `docs/` は利用者向け資料であり、ポータブルコンポーネントとして自動発見されるディレクトリではありません。

## 10. 作成と検証の手順

### 10.1 作成手順

1. プラグインルートを決める
2. ルート直下へ `plugin.json` を作成する
3. 必要な場合だけ `skills/<skill-name>/SKILL.md` を追加する
4. MCP サーバーがある場合だけルート直下へ `mcp.json` を追加する
5. クライアント固有機能は逆ドメイン名前空間へ分離する
6. JSON Schema、Agent Skills 形式、パス境界を検証する
7. 対応クライアントで各コンポーネントを個別に動作確認する

### 10.2 JSON の構文確認

リポジトリルートで次を実行します。

~~~bash
jq empty plugin.json
~~~

`mcp.json` がある場合は、同様に確認します。

~~~bash
jq empty mcp.json
~~~

### 10.3 JSON Schema 検証

Draft 2020-12 対応の JSON Schema バリデーターを使用します。次は `check-jsonschema` が導入済みの場合の例です。

~~~bash
curl --fail --silent --show-error --location \
  --output /tmp/agent-plugins-1.0.0-plugin.schema.json \
  https://agent-plugins.org/schemas/1.0.0/plugin.schema.json

check-jsonschema \
  --schemafile /tmp/agent-plugins-1.0.0-plugin.schema.json \
  plugin.json
~~~

`/tmp/agent-plugins-1.0.0-plugin.schema.json` は開発時の検証用コピーです。プラグインの実行や配布には不要で、検証後に削除できます。`plugin.json` の `$schema` は正規識別子として残します。

`mcp.json` を検証する場合は、MCP 用スキーマを使用します。

~~~bash
curl --fail --silent --show-error --location \
  --output /tmp/agent-plugins-1.0.0-mcp.schema.json \
  https://agent-plugins.org/schemas/1.0.0/mcp.schema.json

check-jsonschema \
  --schemafile /tmp/agent-plugins-1.0.0-mcp.schema.json \
  mcp.json
~~~

### 10.4 Agent Skill の検証

Agent Skills の参照バリデーターを利用できる場合は、Skill ディレクトリを指定します。

~~~bash
skills-ref validate ./skills/fix-sonarlint-issues
~~~

少なくとも次の項目を確認してください。

- `SKILL.md` のファイル名が正確である
- YAML frontmatter に `name` と `description` がある
- `name` が親ディレクトリ名と一致する
- Skill が `skills/` の直下にある
- 参照するスクリプトや資料がプラグインルート内にある

### 10.5 スキーマ以外の確認

JSON Schema の検証だけでは、仕様本文が定義するすべての実行時規則を確認できません。次の項目も確認します。

- シンボリックリンク解決後もパッケージパスがプラグインルート内にある
- `plugin.json` と `mcp.json` の Agent Plugins バージョンが一致する
- `command` が一つの実行可能トークンである
- URL、ヘッダー、`cwd` が仕様の制約を満たす
- `env` と `headers` に秘密情報が含まれていない
- 利用するクライアントが必要なコンポーネントとトランスポートに対応している

## 11. よくある問題

### プラグインが認識されない

1. `plugin.json` がプラグインルート直下にあるか確認する
2. `$schema` が Agent Plugins 1.0.0 の正規識別子と完全一致するか確認する
3. `name` に大文字、アンダースコア、連続ハイフンがないか確認する
4. クライアントが Agent Plugins 1.0.0 に対応しているか確認する
5. インストールや配布方法はクライアント固有の資料を確認する

### Skill が読み込まれない

1. `skills/<skill-name>/SKILL.md` の位置と大文字小文字を確認する
2. Skill が `skills/` の直下にあることを確認する
3. frontmatter の `name` と親ディレクトリ名を一致させる
4. `description` が空でないことを確認する
5. より深い階層の Skill が再帰検索されると想定していないか確認する

### MCP サーバーが読み込まれない

1. ファイル名が `.mcp.json` ではなく `mcp.json` であることを確認する
2. `$schema` と `mcpServers` 以外のトップレベルフィールドがないか確認する
3. `plugin.json` と同じ Agent Plugins バージョンを使用しているか確認する
4. `command` と `args` を一つの文字列へまとめていないか確認する
5. クライアントが宣言したトランスポートをサポートしているか確認する
6. パス、URL、ヘッダー、環境変数の制約を確認する

### クライアントによって動作が異なる

Agent Plugins はクライアントによる段階的な対応を許可しています。Skills だけをサポートするクライアントや、特定の MCP トランスポートをサポートしないクライアントも適合できます。必要な機能の対応状況とクライアント拡張の仕様を確認してください。

## 12. 参考リンク

- [Agent Plugins](https://agent-plugins.org/)
- [Agent Plugins Specification 1.0.0](https://agent-plugins.org/specification)
- [Build an Agent Plugin](https://agent-plugins.org/plugin-authors)
- [Plugin manifest](https://agent-plugins.org/plugin-authors/manifest)
- [Skills](https://agent-plugins.org/plugin-authors/skills)
- [MCP servers](https://agent-plugins.org/plugin-authors/mcp-servers)
- [Client extensions](https://agent-plugins.org/plugin-authors/client-extensions)
- [Loading and discovery](https://agent-plugins.org/client-implementers/loading-and-discovery)
- [MCP runtime](https://agent-plugins.org/client-implementers/mcp-runtime)
- [Client conformance checklist](https://agent-plugins.org/client-implementers/conformance)
- [Agent Plugins 1.0.0 plugin schema](https://agent-plugins.org/schemas/1.0.0/plugin.schema.json)
- [Agent Plugins 1.0.0 MCP schema](https://agent-plugins.org/schemas/1.0.0/mcp.schema.json)
- [Agent Skills Specification](https://agentskills.io/specification)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/latest)
