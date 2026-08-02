# SonarQube for IDE ガイド

## 1. SonarQube for IDE とは

SonarQube for IDE は、コードを編集している段階で bug、vulnerability、code smell、secret などを検出する IDE 拡張です。以前は SonarLint という名称でした。

このプロジェクトでは VS Code 拡張 `sonarsource.sonarlint-vscode` を推奨していますが、Gradle の解析 plugin や SonarQube Server/Cloud への送信設定は導入していません。したがって、現在の SonarQube for IDE の指摘は `./gradlew check` の品質ゲートには含まれません。

## 2. このプロジェクトでの構成

`.vscode/extensions.json`:

~~~json
{
  "recommendations": [
    "sonarsource.sonarlint-vscode"
  ]
}
~~~

インストール:

~~~bash
code --install-extension sonarsource.sonarlint-vscode
~~~

WSL2 では拡張を Windows のローカル側ではなく、対象 workspace を開いている WSL 側へインストールします。

## 3. Standalone mode

server へ接続しない標準の使い方です。

- ファイルを開く、編集する、保存する操作で解析される
- IDE 内で rule の有効・無効や一部パラメーターを設定できる
- 問題パネルと editor 上に指摘が表示される
- Quick Fix がある rule は IDE から修正候補を適用できる
- 一部の高度な project-level / security rule はローカル解析対象外

個人開発や server 未導入のプロジェクトで、早い feedback を得る用途に向きます。

## 4. Connected Mode

SonarQube Server、SonarQube Cloud、または SonarQube Community Build の project と workspace を binding します。

主な利点:

- server の Quality Profile と active rule を同期
- server の file exclusion と analyzer parameter を適用
- Accepted / False Positive などの issue status を同期
- Quality Gate や新規 issue の notification
- server 側で検出した一部の高度な issue を IDE に表示
- Focus on New Code で変更コードへ集中

Connected Mode は IDE のローカル issue を server へ upload する仕組みではありません。server 上の正式な解析は別途 CI scanner などで実行します。

設定は Command Palette の接続・binding コマンドまたは SONARQUBE panel の wizard から行います。token や認証情報を repository にコミットしません。

## 5. Rule と Severity

Standalone mode では IDE 設定から rule を有効・無効化し、対応する rule のパラメーターを変更できます。例えば cognitive complexity のしきい値などです。

Connected Mode では server の Quality Profile が優先され、ローカルの rule 設定は無視される場合があります。チーム共通ルールは server 側で管理します。

指摘の調査手順:

1. rule key、software quality、severity を確認する
2. Why is this an issue? で問題となる理由を読む
3. Noncompliant / Compliant code example を比較する
4. 実際の入力、制御フロー、framework の挙動を確認する
5. 修正または Quick Fix を適用して test を実行する
6. 誤検知なら根拠を確認し、Connected Mode では server 上で status を管理する

## 6. Focus on New Code

既存 issue が多い場合、変更・追加されたコードの issue に集中する機能です。

- Connected Mode: server project の New Code Definition を利用
- Standalone mode: Git を基準としたローカルの new code period を利用
- SONARQUBE panel または status bar から focus を切り替える

Focus は表示対象を絞る機能であり、既存 issue が解消されたことを意味しません。

## 7. File exclusion

Standalone mode では workspace setting で除外できます。

~~~json
{
  "sonarlint.analysisExcludesStandalone": [
    "**/build/**",
    "**/generated/**"
  ]
}
~~~

wildcard:

| 記号 | 意味 |
| --- | --- |
| `*` | directory separator を除く0文字以上 |
| `**` | 複数 directory segment を含む0個以上 |
| `?` | separator を除く1文字 |

Connected Mode では server 側の exclusion が優先され、ローカル exclusion は無視されます。生成コードなど根拠の明確な対象だけを除外します。

## 8. Security issue の扱い

Sonar の security 関連 finding には vulnerability、security hotspot、secret などがあります。

- vulnerability: 実際に悪用可能かを data flow と入力境界から確認する
- security hotspot: security-sensitive なコードを人がレビューする必要がある
- secret: credential らしい値が repository や設定へ入っていないか確認する
- injection vulnerability: 一部は server/cloud 解析と Connected Mode が必要

security finding を単純な code smell と同じように無効化せず、入力の信頼境界、sanitization、framework protection を確認します。

## 9. 他ツールとの役割分担

| ツール | 主な役割 | `check` に参加 |
| --- | --- | --- |
| SonarQube for IDE | 編集中の幅広い早期 feedback | いいえ |
| Checkstyle | コーディング規約 | はい |
| Error Prone | javac 型情報を使う bug pattern | コンパイル経由 |
| SpotBugs | bytecode bug pattern | はい |
| ArchUnit | architecture rule | test 経由 |

同じ問題が複数ツールに出ることがあります。IDE の指摘を直した後も `./gradlew check` を最終確認にします。

## 10. チーム運用

- standalone の個人設定だけを品質基準にしない
- 共通化が必要なら Connected Mode と Quality Profile を利用する
- Quality Profile の変更は理由と影響範囲をレビューする
- issue を Accepted / False Positive にするときは根拠を残す
- CI quality gate が必要なら server/cloud scanner を別途正式に導入する
- IDE extension と server の対応 version policy を確認する

## 11. トラブルシューティング

### Java ファイルが解析されない

拡張の出力ログ、Java runtime、workspace trust、対象言語 support、automatic analysis の状態を確認します。WSL 側へ拡張がインストールされているかも確認します。

### Connected Mode の rule とローカル設定が違う

Connected Mode では server の Quality Profile が優先されます。binding 対象 project、branch matching、同期状態を確認します。

### issue が IDE と server で一致しない

branch、commit、new code definition、Quality Profile、解析 scope、server だけで実行可能な rule を確認します。完全一致しない rule もあります。

### 解析が重い

不要な生成物を exclusion し、whole-folder scan を必要時だけ行います。Java language server と SonarQube for IDE の出力ログで原因を切り分けます。

## 12. 参考資料

- [SonarQube for VS Code](https://docs.sonarsource.com/sonarqube-for-vs-code/)
- [Running an analysis](https://docs.sonarsource.com/sonarqube-for-vs-code/getting-started/running-an-analysis)
- [Connected Mode](https://docs.sonarsource.com/sonarqube-for-vs-code/connect-your-ide/connected-mode)
- [Rules and languages](https://docs.sonarsource.com/sonarqube-for-vs-code/using/rules)
- [File exclusions](https://docs.sonarsource.com/sonarqube-for-vs-code/using/file-exclusions)

