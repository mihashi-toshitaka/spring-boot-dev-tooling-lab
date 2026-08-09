# Spring Boot Thymeleaf ガイド

## 1. Thymeleaf とは

Thymeleaf は、JavaでサーバーサイドHTMLを生成するテンプレートエンジンです。Spring MVCと組み合わせることで、ControllerがModelへ渡した値をHTMLに埋め込んだり、フォーム入力をJavaオブジェクトへバインドしたりできます。

次のような画面をSpring Bootアプリケーション内で作る場合に適しています。

- 管理画面や社内向け画面
- 入力フォームと確認画面
- データベースの検索・一覧・詳細画面
- メール本文などのサーバーサイドテンプレート

ブラウザ側を独立したSPAとして構築する場合は、ReactやVueなどとREST APIを組み合わせる構成も検討してください。

## 2. プロジェクトへの導入

このプロジェクトでは、`build.gradle.kts` に次の依存関係を追加しています。

~~~kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
}
~~~

Spring Bootの依存関係管理を利用しているため、Thymeleafのバージョンを個別に指定する必要はありません。Spring MVCとの連携に必要なテンプレートエンジンやViewResolverも自動構成されます。

## 3. ディレクトリ構成

既定では、HTMLテンプレートを `src/main/resources/templates` に配置します。CSS、JavaScript、画像など、そのまま配信するファイルは `src/main/resources/static` に配置します。

~~~text
src/main/resources/
├── templates/
│   ├── greeting.html
│   └── fragments/
│       └── header.html
└── static/
    ├── css/
    │   └── app.css
    └── js/
        └── app.js
~~~

テンプレートの既定の接頭辞と接尾辞は次のとおりです。

~~~properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
~~~

通常は変更せず、Spring Bootの規約に沿って配置するのがおすすめです。

## 4. 最初の画面を表示する

### Controller

HTMLを返すControllerには `@Controller` を使用します。`@RestController` を付けると戻り値の文字列自体がレスポンス本文になるため、View名を返す用途には適しません。

~~~java
package com.example.page.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GreetingController {

    @GetMapping("/greeting")
    public String greeting(Model model) {
        model.addAttribute("message", "Hello, Thymeleaf!");
        return "greeting";
    }
}
~~~

戻り値の `greeting` は、既定設定では次のテンプレートに解決されます。

~~~text
src/main/resources/templates/greeting.html
~~~

### HTMLテンプレート

~~~html
<!doctype html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Greeting</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
    <main>
        <h1 th:text="${message}">メッセージのプレビュー</h1>
    </main>
</body>
</html>
~~~

アプリケーションを起動し、ブラウザで `http://localhost:8080/greeting` を開いて確認します。

~~~bash
./gradlew bootRun
~~~

## 5. よく使う式と属性

| 構文 | 用途 | 例 |
| --- | --- | --- |
| `${...}` | Modelやコンテキストの値を参照する | `${user.name}` |
| `*{...}` | `th:object`で選択したオブジェクトを参照する | `*{email}` |
| `@{...}` | コンテキストパスを考慮したURLを生成する | `@{/users/{id}(id=${user.id})}` |
| `#{...}` | メッセージリソースを参照する | `#{page.title}` |
| `th:text` | 値をHTMLエスケープして出力する | `th:text="${message}"` |
| `th:if` / `th:unless` | 条件によって要素を表示する | `th:if="${user != null}"` |
| `th:each` | コレクションを繰り返し表示する | `th:each="user : ${users}"` |
| `th:classappend` | CSSクラスを条件付きで追加する | `th:classappend="${active} ? 'active'"` |

一覧表示の例です。

~~~html
<ul>
    <li th:each="user : ${users}">
        <a th:href="@{/users/{id}(id=${user.id})}"
           th:text="${user.name}">ユーザー名</a>
    </li>
</ul>
~~~

## 6. フォームを扱う

Spring MVCのフォームオブジェクトを `th:object` で選択し、各入力欄を `th:field` で関連付けます。

~~~java
package com.example.form;

public class ProfileForm {

    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
~~~

~~~java
@GetMapping("/profile")
public String showProfile(Model model) {
    model.addAttribute("profileForm", new ProfileForm());
    return "profile";
}

@PostMapping("/profile")
public String updateProfile(
        @ModelAttribute ProfileForm profileForm) {
    // 入力値を検証し、アプリケーションサービスへ渡します。
    return "redirect:/profile";
}
~~~

~~~html
<form th:action="@{/profile}"
      th:object="${profileForm}"
      method="post">
    <label for="displayName">表示名</label>
    <input id="displayName"
           type="text"
           th:field="*{displayName}">
    <button type="submit">保存</button>
</form>
~~~

入力値を検証する場合は、`spring-boot-starter-validation`、`@Valid`、Jakarta Validationの制約アノテーションを組み合わせます。エラーは `th:errors="*{displayName}"` などで表示できます。

Post/Redirect/Getパターンを使ってPOST後にリダイレクトすると、ブラウザの再読み込みによる二重送信を避けやすくなります。

## 7. 共通部品をフラグメント化する

ヘッダーやナビゲーションなどはフラグメントとして共通化できます。

`templates/fragments/header.html`:

~~~html
<!doctype html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org">
<body>
    <header th:fragment="siteHeader(title)">
        <h1 th:text="${title}">サイト名</h1>
    </header>
</body>
</html>
~~~

利用するテンプレート:

~~~html
<header th:replace="~{fragments/header :: siteHeader('管理画面')}"></header>
~~~

画面全体のレイアウト機能が必要な場合は、追加ライブラリのThymeleaf Layout Dialectも選択肢になります。まずは標準のフラグメントで十分かを確認してください。

## 8. 静的リソースとURL

`src/main/resources/static/css/app.css` は、既定では `/css/app.css` として配信されます。テンプレート内では `th:href` や `th:src` を使い、コンテキストパスを考慮したURLを生成します。

~~~html
<link rel="stylesheet" th:href="@{/css/app.css}">
<script defer th:src="@{/js/app.js}"></script>
~~~

ファイル名にコンテンツハッシュを付けるリソースチェーンを有効にした場合も、ThymeleafではSpring MVCのURL書き換え機能を利用できます。

## 9. 開発時のキャッシュ

Thymeleafは本番性能のためテンプレートをキャッシュします。ローカル開発で変更をすぐ反映したい場合は、開発用プロファイルに次を設定します。

~~~yaml
spring:
  thymeleaf:
    cache: false
~~~

このプロジェクトではSpring Boot DevToolsを導入しているため、DevToolsのプロパティ既定値によって開発時のテンプレートキャッシュは無効化されます。明示設定する場合も、本番環境ではキャッシュを有効に戻してください。

HTMLテンプレートの変更は通常、Javaクラスの再コンパイルを必要としません。ブラウザを更新して反映を確認します。

## 10. セキュリティ上の注意

- 通常の文字列出力には、HTMLエスケープされる `th:text` または `[[...]]` を使う
- エスケープしない `th:utext` と `[(...)]` へ、利用者が入力した値を直接渡さない
- 利用者が編集できる文字列をテンプレート名やThymeleaf式として評価しない
- POSTフォームではSpring SecurityのCSRF対策を有効にする
- ControllerからEntityをそのまま公開せず、画面に必要なView ModelやDTOだけを渡す
- URLは文字列連結ではなく `@{...}` で生成する

Thymeleafの式評価制限は多層防御の一部であり、入力検証や出力先に応じた安全対策の代わりにはなりません。

## 11. テスト

画面の動作は、HTTPステータス、View名、Model、最終的なHTMLに分けて確認します。

~~~java
package com.example.page.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GreetingController.class)
class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetingを表示する() throws Exception {
        mockMvc.perform(get("/greeting"))
                .andExpect(status().isOk())
                .andExpect(view().name("greeting"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "Hello, Thymeleaf!")));
    }
}
~~~

実プロジェクトでは日本語のテストメソッド名を採用するか、チームの命名規約に合わせてください。テンプレートが存在し、正常にレンダリングされるところまで確認することが重要です。

## 12. 動作確認

Thymeleafが実行時クラスパスへ追加されたことを確認します。

~~~bash
./gradlew dependencies --configuration runtimeClasspath
~~~

プロジェクト全体の検査を実行します。

~~~bash
./gradlew check
~~~

画面を実装した後はアプリケーションを起動し、対象URLのレスポンスを確認します。

~~~bash
./gradlew bootRun
curl --fail http://localhost:8080/greeting
~~~

### よくある問題

- View名の文字列がそのまま表示される: `@RestController` ではなく `@Controller` を使用する
- `TemplateInputException` が発生する: `templates` 配下のファイル名とControllerが返すView名を確認する
- CSSやJavaScriptが `404` になる: `static` 配下の配置と `@{...}` のURLを確認する
- テンプレート変更が反映されない: `spring.thymeleaf.cache` とDevToolsの有効状態を確認する
- Modelの値が表示されない: `model.addAttribute` の名前と `${...}` の名前を一致させる
- POSTが `403` になる: Spring SecurityのCSRFトークンとフォーム送信先を確認する

## 13. 公式ドキュメント

- [Spring Boot: Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [Spring Boot: Spring MVC How-to](https://docs.spring.io/spring-boot/how-to/spring-mvc.html)
- [Thymeleaf: Using Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)
- [Thymeleaf + Spring](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html)
