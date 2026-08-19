# npx skills ガイド

このガイドは、`npx skills` を使用して Agent Skills を検索、追加、確認、更新する方法を説明します。本プロジェクトでは、主に Codex で Skill を利用する場合を想定しています。

## 1. npx skills とは

`npx skills` は、オープンな Agent Skills エコシステム向けの `skills` CLI を `npx` から実行するコマンドです。

- `npx`: ローカルまたは npm レジストリ上のパッケージが提供するコマンドを実行する
- `skills`: Agent Skills の検索、追加、一覧表示、更新、削除などを行う

Agent Skill は、AI コーディングエージェントへ特定の作業手順や専門知識を提供する再利用可能なディレクトリです。基本となる `SKILL.md` のほか、必要に応じて参考資料、テンプレート、補助スクリプトを含みます。

`npx skills` 自体は Agent Skill ではなく、Skill を管理するための CLI です。また、外部サービスへ接続する MCP サーバーや Agent Plugin のマニフェストとも役割が異なります。

## 2. 前提条件

Node.js、npm、Git が必要です。WSL2 のターミナルで次のコマンドを実行し、利用できることを確認します。

~~~bash
node --version
npm --version
npx --version
git --version
~~~

Node.js が未インストールの場合は、[`README.md`](../../README.md#3-nvm-と-nodejs) の nvm と Node.js の手順に従ってください。

## 3. セットアップ

`npx` は npm に含まれるため、通常は `skills` パッケージをグローバルにインストールする必要はありません。プロジェクトのルートディレクトリで次のコマンドを実行します。

~~~bash
npx skills --help
~~~

ローカルに `skills` パッケージがない場合、`npx` はパッケージを npm のキャッシュへ取得してからコマンドを実行します。初回にインストール確認が表示された場合は、パッケージ名が `skills` であることを確認して続行します。

意図しないパッケージの実行を避けるため、コマンドのスペルとパッケージの提供元を確認してください。継続的インテグレーションなどで同じバージョンを再現する必要がある場合は、確認済みのバージョンを明示します。

~~~bash
npx skills@<version> --help
~~~

## 4. Skill を探す

キーワードを指定して Skill を検索できます。

~~~bash
npx skills find spring-boot
~~~

追加元のリポジトリが決まっている場合は、インストールせずに、そのリポジトリから検出される Skill を一覧表示できます。

~~~bash
npx skills add <owner>/<repository> --list
~~~

検索結果だけで導入を決めず、追加元リポジトリの所有者、更新状況、ライセンス、`SKILL.md`、同梱スクリプトを確認します。

## 5. Codex 用の Skill を追加する

GitHub リポジトリから、このプロジェクトへ Codex 用の Skill を追加する基本形は次のとおりです。

~~~bash
npx skills add <owner>/<repository> --agent codex
~~~

複数の Skill が含まれるリポジトリでは、追加する Skill を指定できます。

~~~bash
npx skills add <owner>/<repository> \
  --skill <skill-name> \
  --agent codex
~~~

`--agent codex` を指定したプロジェクトスコープの追加先は、通常 `.agents/skills/` です。個々の Skill は次のような構成になります。

~~~text
.agents/
└── skills/
    └── <skill-name>/
        ├── SKILL.md
        ├── references/
        ├── scripts/
        └── assets/
~~~

`references/`、`scripts/`、`assets/` は、Skill が必要とする場合だけ存在します。対話形式でインストール方法を尋ねられた場合は、同じ Skill を複数のエージェントで共有しやすいシンボリックリンク、または独立したファイルを配置するコピーを選択できます。

すべてのプロジェクトで使用する個人用 Skill として追加する場合は、`--global` または `-g` を指定します。

~~~bash
npx skills add <owner>/<repository> --skill <skill-name> --agent codex --global
~~~

Codex のグローバルな追加先は通常 `~/.codex/skills/` です。チームで同じ Skill を共有する場合は、内容をレビューしたうえでプロジェクトスコープへ追加し、必要なファイルをリポジトリで管理する方法を検討してください。

## 6. インストール済み Skill を管理する

プロジェクトとグローバルに追加されている Skill を一覧表示します。

~~~bash
npx skills list
~~~

グローバルな Skill だけを表示する場合は、次のように実行します。

~~~bash
npx skills list --global
~~~

Skill を更新する前には、追加元の変更内容を確認してください。すべての対象を対話形式で更新する場合は、次のコマンドを使用します。

~~~bash
npx skills update
~~~

特定の Skill を更新する場合は名前を指定します。

~~~bash
npx skills update <skill-name>
~~~

不要になった Skill は、次のコマンドで削除します。

~~~bash
npx skills remove <skill-name>
~~~

削除時は、プロジェクトスコープとグローバルスコープ、対象エージェントを確認してから確定してください。

## 7. インストールせず一時的に利用する

`use` コマンドを使うと、Skill を恒久的に追加せず、その内容からプロンプトを生成できます。

~~~bash
npx skills use <owner>/<repository>@<skill-name>
~~~

対応するエージェントを起動しながら利用する場合は、エージェントを指定します。

~~~bash
npx skills use <owner>/<repository> \
  --skill <skill-name> \
  --agent codex
~~~

一時利用であっても外部から取得した内容をエージェントへ渡すため、信頼できる提供元か確認してください。

## 8. Skill のひな型を作成する

現在のディレクトリへ最小限の `SKILL.md` を作成する場合は、次のコマンドを使用します。

~~~bash
npx skills init
~~~

名前を指定してサブディレクトリへ作成することもできます。

~~~bash
npx skills init <skill-name>
~~~

作成した Skill の `name`、`description`、適用条件、手順を具体的に記述します。Agent Skill の構成については、[Agent Plugins 1.0.0 標準化ガイド](agent-plugins-1.0.0-guide.md#4-agent-skills)も参照してください。

## 9. セキュリティと運用上の注意

- `npx` は、ローカルに対象パッケージがなければ npm レジストリから取得して実行する
- パッケージ名のタイプミスに注意し、必要に応じてバージョンを固定する
- Skill の追加前に `SKILL.md` だけでなく、`scripts/` などの同梱ファイルも確認する
- API キー、トークン、パスワードを `SKILL.md` やリポジトリへ直接記録しない
- `--yes` や `-y` による確認省略は、追加元と対象が明確な自動化でだけ使用する
- グローバル追加はほかのプロジェクトにも影響するため、用途がプロジェクト固有ならプロジェクトスコープを使用する
- 更新後は `git status` と `git diff` で追加・変更されたファイルを確認する

Skill はエージェントへの指示を追加しますが、OS のアクセス制御や実行環境の権限を提供するものではありません。補助スクリプトの実行や外部サービスへの接続には、別途ランタイム、認証情報、利用者の承認が必要になる場合があります。

## 10. トラブルシューティング

### `npx: command not found` と表示される

Node.js と npm が現在の WSL2 環境へインストールされているか確認します。nvm をインストールした直後であれば、シェルを開き直すか、次のコマンドで設定を再読み込みします。

~~~bash
source ~/.bashrc
node --version
npm --version
npx --version
~~~

### `No skills found` と表示される

指定したリポジトリまたはディレクトリに、有効な `SKILL.md` が存在するか確認します。`SKILL.md` の YAML frontmatter には、少なくとも `name` と `description` が必要です。

### Skill が Codex で認識されない

1. `npx skills list --agent codex` で追加状況を確認する
2. プロジェクト用なら `.agents/skills/<skill-name>/SKILL.md`、グローバル用なら `~/.codex/skills/<skill-name>/SKILL.md` が存在するか確認する
3. `SKILL.md` の `name` と `description` が正しいか確認する
4. Codex の新しいセッションを開始して再確認する

### GitHub から取得できない

リポジトリURL、ネットワーク接続、Git の認証状態を確認します。非公開リポジトリでは、Git の credential helper、GitHub CLI、SSH などによる認証が必要です。トークンをコマンドやドキュメントへ直接記述しないでください。

## 11. 参考リンク

- [Vercel Labs: skills CLI](https://github.com/vercel-labs/skills)
- [Agent Skills](https://agentskills.io/)
- [npm Docs: npm exec / npx](https://docs.npmjs.com/cli/npm-exec/)
- [AGENTS.md ガイド](agents-md-guide.md)
- [Agent Plugins 1.0.0 標準化ガイド](agent-plugins-1.0.0-guide.md)
