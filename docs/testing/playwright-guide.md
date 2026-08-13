# Playwright ガイド

## 概要

Playwright は、実際のブラウザーを操作して画面遷移やフォーム入力を確認する E2E テストツールです。このプロジェクトでは Java 版 Playwright と Chromium を使用し、JUnit 5 から Spring Boot の実画面をテストします。

通常のテストとブラウザーテストは、JUnit 5 の `playwright` タグで分離しています。

| Gradle タスク | 用途 |
| --- | --- |
| `test` | Playwright を除く通常のテストを実行する |
| `playwrightInstall` | Playwright が使用する Chromium をインストールする |
| `playwrightInstallWithDeps` | Chromium と Linux の実行に必要な OS パッケージをインストールする |
| `playwrightTest` | Chromium を用いた E2E テストを実行する |
| `check` | 通常のテストと Playwright テストを含む検査をまとめて実行する |

## 初期セットアップ

初回は Chromium と、ブラウザーの起動に必要な OS パッケージをインストールします。

~~~bash
./gradlew playwrightInstallWithDeps
~~~

OS パッケージが既に揃っている場合は、Chromium だけをインストールできます。

~~~bash
./gradlew playwrightInstall
~~~

`playwrightTest` は `playwrightInstall` に依存しているため、対応する Chromium が未導入の場合は自動的に取得します。Playwright のバージョンを更新した場合も、対応するブラウザーを取得し直してください。

## テストの実行

Playwright テストだけを実行する場合は、次のコマンドを使用します。

~~~bash
./gradlew playwrightTest
~~~

ほかの検査とまとめて実行する場合は、次のコマンドを使用します。

~~~bash
./gradlew check
~~~

テスト結果は `build/reports/tests/playwrightTest/index.html` で確認できます。

## 現在のテスト内容

`src/test/java/com/example/PlaywrightE2eTest.java` は、Testcontainers で PostgreSQL と Valkey を起動し、ランダムポートで動作する Spring Boot アプリケーションに Chromium からアクセスします。

次の利用者操作を確認しています。

1. 保護された `/greeting` へアクセスする
2. `/login` へリダイレクトされることを確認する
3. 初期ユーザーでログインする
4. `/greeting` に画面遷移し、ログインユーザーと見出しが表示されることを確認する

テストの実行には、Docker または互換性のあるコンテナ実行環境が必要です。

## テストを追加する

Playwright を使用するテストクラスまたはテストメソッドには、`@Tag("playwright")` を付けます。このタグにより、通常の `test` タスクからは除外され、`playwrightTest` タスクで実行されます。

### 基本構成

テストでは、Playwright、Browser、Page の順に生成します。テストごとに BrowserContext を作成すると Cookie や Local Storage が分離され、テスト間で状態が漏れません。現在のテストは単一シナリオのため `browser.newPage()` を使用していますが、テストケースを増やす場合は次の構成を基本とします。

~~~java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("playwright")
class ExamplePlaywrightTest {

    @Test
    void displaysLoginPage() {
        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            page.navigate("http://127.0.0.1:8080/login");

            assertThat(page).hasURL("http://127.0.0.1:8080/login");
            assertThat(page.getByRole(
                            AriaRole.HEADING,
                            new Page.GetByRoleOptions().setName("ログイン")))
                    .isVisible();
        }
    }
}
~~~

このプロジェクトの E2E テストでは、固定の `8080` ではなく `@LocalServerPort` で Spring Boot のランダムポートを取得します。具体例は `src/test/java/com/example/PlaywrightE2eTest.java` を参照してください。

以降のコード例はテストメソッド内の抜粋です。`BrowserContext`、`Download`、`Locator`、`Response`、`Route`、`Tracing`、`Path`、`Files`、`Paths` など、使用する型の import は IDE で追加してください。

### 要素を特定する

Playwright では Locator を通して要素を特定します。画面の構造変更に強く、利用者の操作に近い Locator を優先します。

| 優先度 | Locator | 主な用途 |
| --- | --- | --- |
| 1 | `getByRole()` | ボタン、リンク、見出しなどをロールとアクセシブル名で特定する |
| 2 | `getByLabel()` | ラベルに関連付けられた入力欄を特定する |
| 3 | `getByText()` | 利用者に表示されるテキストで特定する |
| 4 | `getByPlaceholder()`、`getByAltText()`、`getByTitle()` | 対応する意味情報で特定する |
| 5 | `getByTestId()` | 利用者向けの情報だけでは安定して特定できない要素に使用する |
| 6 | `locator()` | CSS セレクターなどが必要な場合に限定して使用する |

同じロールの要素が複数ある場合は、アクセシブル名を指定して対象を絞ります。

~~~java
page.getByLabel("ユーザー名").fill("user");
page.getByLabel("パスワード").fill("password");
page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("ログイン"))
        .click();
~~~

DOM の階層や自動生成された CSS クラスに強く依存する長いセレクターは、画面変更で壊れやすいため避けます。Locator が複数要素に一致した状態で単一要素向けの操作を行うとエラーになるため、利用者が対象を区別する情報を Locator に加えてください。

### 基本操作

代表的な操作は次のとおりです。

~~~java
page.navigate(baseUrl + "/signup");

page.getByLabel("ユーザー名").fill("new-user");
page.getByRole(AriaRole.CHECKBOX).check();
page.getByLabel("都道府県").selectOption("tokyo");
page.getByLabel("検索").press("Enter");
page.getByText("詳細を表示").hover();
page.getByRole(AriaRole.BUTTON).click();
~~~

`fill()` は入力欄の既存値を置き換えます。一文字ずつのキー入力そのものがテスト対象でなければ、`pressSequentially()` より `fill()` を優先します。

### 画面を検証する

`PlaywrightAssertions.assertThat()` は、条件を満たすまで再試行する Web-first assertion です。画面の状態を直接取得して即座に比較するより、次の assertion を使用します。

~~~java
assertThat(page).hasURL(baseUrl + "/login");
assertThat(page).hasTitle("ログイン");
assertThat(page.getByRole(AriaRole.HEADING)).hasText("ログイン");
assertThat(page.getByRole(AriaRole.BUTTON)).isEnabled();
assertThat(page.locator(".notice-error")).containsText("正しくありません");
assertThat(page.getByRole(AriaRole.LIST_ITEM)).hasCount(3);
~~~

よく使用する検証は次のとおりです。

| 対象 | 検証例 |
| --- | --- |
| ページ | `hasURL()`、`hasTitle()` |
| 表示状態 | `isVisible()`、`isHidden()` |
| 操作可否 | `isEnabled()`、`isDisabled()`、`isEditable()` |
| テキスト | `hasText()`、`containsText()` |
| 属性・入力値 | `hasAttribute()`、`hasValue()` |
| 件数 | `hasCount()` |

## 自動待機とタイムアウト

`click()` や `fill()` は、要素が表示され、安定し、操作可能になるまで自動的に待機します。Web-first assertion も期待した状態になるまで再試行します。そのため、`Thread.sleep()` や `page.waitForTimeout()` による固定時間の待機は原則として使用しません。

既定時間で不足する処理だけ、対象を限定してタイムアウトを指定します。

~~~java
assertThat(page.locator(".slow-result"))
        .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(10_000));
~~~

ページ全体の操作タイムアウトを変える場合は、次のように設定できます。

~~~java
page.setDefaultTimeout(10_000);
page.setDefaultNavigationTimeout(30_000);
~~~

タイムアウトを長くする前に、Locator が正しいか、アプリケーションでエラーが発生していないか、待つべき状態を assertion で表現できないかを確認してください。

## よく使われる機能

### BrowserContext によるテスト分離

BrowserContext は、独立した Cookie、Local Storage、権限などを持つ軽量なブラウザープロファイルです。テストケースごとに新しく作成します。Browser 自体はクラス内で共有できますが、Playwright の Java API はスレッドセーフではないため、並列実行する場合はスレッドをまたいで同じ Playwright オブジェクトを操作しないでください。

~~~java
BrowserContext context = browser.newContext();
try {
    Page page = context.newPage();
    page.navigate(baseUrl);
} finally {
    context.close();
}
~~~

### スクリーンショット

失敗時の画面確認や表示崩れの調査にはスクリーンショットを使用します。保存先の親ディレクトリは事前に作成します。

~~~java
Path screenshotPath = Paths.get("build/playwright/screenshots/greeting.png");
Files.createDirectories(screenshotPath.getParent());

page.screenshot(new Page.ScreenshotOptions()
        .setPath(screenshotPath)
        .setFullPage(true));
~~~

特定の要素だけを保存することもできます。

~~~java
page.locator(".session-card")
        .screenshot(new Locator.ScreenshotOptions()
                .setPath(Paths.get("build/playwright/screenshots/session-card.png")));
~~~

### トレース

トレースには、各操作、DOM スナップショット、スクリーンショット、ネットワーク情報などが記録されます。CI でのみ発生する失敗の調査に有効です。

~~~java
Path tracePath = Paths.get("build/playwright/traces/trace.zip");
Files.createDirectories(tracePath.getParent());

context.tracing().start(new Tracing.StartOptions()
        .setScreenshots(true)
        .setSnapshots(true)
        .setSources(true));
try {
    Page page = context.newPage();
    page.navigate(baseUrl + "/login");
    // テスト操作
} finally {
    context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
}
~~~

生成された `trace.zip` は [Trace Viewer](https://trace.playwright.dev/) で確認できます。トレースには DOM、通信内容、Cookie や入力値などが含まれる可能性があります。信頼できない場所へ公開せず、CI の成果物として保存する場合も閲覧権限と保存期間を制限してください。

### ネットワークの待機と検証

画面操作によって発生する特定の API レスポンスを待つ場合は、操作を `waitForResponse()` のコールバック内で実行します。先にクリックしてから待機を開始すると、高速なレスポンスを取り逃す可能性があります。

~~~java
Response response = page.waitForResponse(
        responseCandidate -> responseCandidate.url().equals(baseUrl + "/api/public"),
        () -> page.navigate(baseUrl + "/api/public"));

assertThat(response).isOK();
~~~

リクエストとレスポンスを記録して調査することもできます。

~~~java
page.onRequest(request ->
        System.out.println(">> " + request.method() + " " + request.url()));
page.onResponse(response ->
        System.out.println("<< " + response.status() + " " + response.url()));
~~~

ログには認証ヘッダーや個人情報を出力しないでください。

### API レスポンスのモック

外部サービスの異常応答や、通常は再現しにくい状態を画面側で確認する場合は、`route()` でレスポンスを差し替えられます。

~~~java
page.route("**/api/public", route ->
        route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("{\"message\":\"モックされた応答です。\"}")));

page.navigate(baseUrl + "/api/public");
assertThat(page.locator("body")).containsText("モックされた応答です。");
~~~

Spring Boot 自体との結合を確認するシナリオでは実際の API を使用し、ブラウザー側の分岐だけを独立して確認したい場合にモックを使用します。

### 認証状態の再利用

ログイン処理が多くテスト時間に影響する場合は、認証後の BrowserContext から Cookie や Local Storage を保存し、新しい Context に読み込めます。

~~~java
Path authStatePath = Paths.get("build/playwright/auth/state.json");
Files.createDirectories(authStatePath.getParent());

loggedInContext.storageState(new BrowserContext.StorageStateOptions()
        .setPath(authStatePath));

BrowserContext authenticatedContext = browser.newContext(
        new Browser.NewContextOptions().setStorageStatePath(authStatePath));
~~~

このプロジェクトのログイン状態は Spring Session と Valkey にも依存するため、保存状態を再利用できるのは対応するサーバー側セッションが有効な間だけです。また、保存ファイルには認証 Cookie が含まれます。`build/` の外へ保存する場合は必ず `.gitignore` の対象にし、Git へコミットしないでください。

### ファイルのアップロードとダウンロード

ファイル入力には `setInputFiles()` を使用します。

~~~java
Path uploadFile = prepareUploadFile();
page.getByLabel("添付ファイル").setInputFiles(uploadFile);
~~~

`prepareUploadFile()` は説明用の名前です。実際のテストでは、テスト用ファイルを一時ディレクトリなどへ準備し、その `Path` を渡します。

ダウンロードは、開始操作を `waitForDownload()` の中で行ってから保存します。

~~~java
Download download = page.waitForDownload(() ->
        page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("CSVをダウンロード"))
                .click());

Path downloadPath = Paths.get("build/playwright/downloads", download.suggestedFilename());
Files.createDirectories(downloadPath.getParent());
download.saveAs(downloadPath);
~~~

### ダイアログと新しいタブ

`alert`、`confirm`、`prompt` を確認する場合は、ダイアログを発生させる操作より先にハンドラーを登録します。ハンドラーを登録した場合は、必ず `accept()` または `dismiss()` で閉じます。

~~~java
page.onDialog(dialog -> {
    assertEquals("削除しますか？", dialog.message());
    dialog.accept();
});
page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("削除"))
        .click();
~~~

リンクやボタンから新しいタブが開く場合は `waitForPopup()` を使用します。

~~~java
Page popup = page.waitForPopup(() ->
        page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("利用規約"))
                .click());
assertThat(popup).hasTitle("利用規約");
~~~

### 画面サイズ、ロケール、タイムゾーン

レスポンシブ表示や日時・言語依存の画面は BrowserContext の設定で再現します。

~~~java
BrowserContext context = browser.newContext(new Browser.NewContextOptions()
        .setViewportSize(390, 844)
        .setLocale("ja-JP")
        .setTimezoneId("Asia/Tokyo"));
~~~

ほかにも、カラースキーム、位置情報、権限、オフライン状態、User-Agent などを Context 単位で設定できます。実機そのものを再現する機能ではないため、重要な端末固有の動作は実機テストも組み合わせてください。

### デバッグ

ローカルでブラウザーを表示し、操作を遅くして確認できます。

~~~java
Browser browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(200));
~~~

確認したい位置に `page.pause()` を一時的に追加すると、Playwright Inspector で Locator や操作履歴を調査できます。`setHeadless(false)`、`setSlowMo()`、`page.pause()` は調査後に削除し、CI ではヘッドレス実行に戻します。WSL2 で画面を表示するには、WSLg など GUI アプリケーションを起動できる環境が必要です。

ブラウザーのコンソールエラーを調べる場合は、ページを開く前にイベントを登録します。

~~~java
page.onConsoleMessage(message ->
        System.out.println(message.type() + ": " + message.text()));
page.onPageError(error -> System.out.println("page error: " + error));
~~~

## 安定したテストにするための指針

- テストケースごとに BrowserContext とテストデータを分離する
- CSS の実装詳細より、ロール、ラベル、表示テキストを Locator に使用する
- `Thread.sleep()` ではなく Locator の自動待機と Web-first assertion を使用する
- 1 テストでは、利用者から見た一つの目的やシナリオを確認する
- 外部サービスの検証とブラウザー表示の検証を分け、必要な箇所だけネットワークをモックする
- 失敗時のスクリーンショットやトレースは `build/playwright/` 配下へ保存する
- パスワード、認証状態、個人情報をソースコード、ログ、トレースへ残さない
- 操作が繰り返されるようになってから Page Object を導入し、早すぎる抽象化を避ける

## 公式ドキュメント

- [Writing tests](https://playwright.dev/java/docs/writing-tests)
- [Locators](https://playwright.dev/java/docs/locators)
- [Auto-waiting](https://playwright.dev/java/docs/actionability)
- [Authentication](https://playwright.dev/java/docs/auth)
- [Screenshots](https://playwright.dev/java/docs/screenshots)
- [Trace Viewer](https://playwright.dev/java/docs/trace-viewer)
- [Network](https://playwright.dev/java/docs/network)
- [Downloads](https://playwright.dev/java/docs/downloads)
- [Emulation](https://playwright.dev/java/docs/emulation)
- [Debugging tests](https://playwright.dev/java/docs/debug)
