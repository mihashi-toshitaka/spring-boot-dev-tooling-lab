# Spring Security ガイド

## 1. Spring Security とは

Spring Security は、Spring アプリケーションの認証、認可、および一般的な Web 攻撃への対策を提供するフレームワークです。ログイン画面を追加するだけでなく、次の機能を一貫した仕組みで扱えます。

| 機能 | 主な用途 |
| --- | --- |
| 認証 | フォームログイン、HTTP Basic、OAuth 2.0 / OpenID Connect、Bearer Token などで利用者を確認する |
| 認可 | URL、HTTP メソッド、ロール、権限、Java メソッドごとにアクセスを制御する |
| Security Context | 認証済み利用者の情報を現在のリクエストやセッションで利用する |
| 攻撃対策 | CSRF、セキュリティ用 HTTP レスポンスヘッダー、セッション固定攻撃などに対処する |
| 外部連携 | ID プロバイダー、OAuth 2.0 Authorization Server、LDAP、SAML 2.0 などと連携する |
| テスト支援 | 認証済み利用者、CSRF Token、フォームログイン、HTTP Basic などをテストする |

本ガイドは、このプロジェクトで使用している Servlet / Spring MVC 向け機能を対象とします。現在の `build.gradle.kts` は Spring Boot 4.1.0 を使用しており、Spring Boot の依存関係管理によって Spring Security 7.1.0 が選択されます。

Spring Security はアプリケーションセキュリティの一部です。TLS、秘密情報の管理、入力検証、依存関係の更新、監査、レート制限、アカウント回復などは別途設計してください。

### 依存関係

このプロジェクトでは、実行用とテスト用の Starter を `build.gradle.kts` に追加しています。バージョンは Spring Boot の依存関係管理に任せます。

~~~kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
~~~

Starter を追加すると Spring Boot の既定の Web セキュリティが有効になります。このプロジェクトは独自の `SecurityFilterChain` と `JdbcUserDetailsManager` を Bean として定義しているため、保護対象とユーザーの取得方法をアプリケーション側で明示的に管理します。

## 2. 処理の仕組みと基本用語

Servlet アプリケーションでは、Spring Security は Controller より前に動作する Servlet Filter の連鎖としてリクエストを処理します。`FilterChainProxy` がリクエストに一致する `SecurityFilterChain` を選び、そのチェーン内で攻撃対策、認証、認可などの Filter が順番に実行されます。

| 用語 | 役割 |
| --- | --- |
| `SecurityFilterChain` | どのリクエストを、どの認証・認可・攻撃対策で処理するかを定義する |
| `SecurityContext` | 現在の認証情報を保持する |
| `Authentication` | 認証処理への入力、または認証済み利用者とその権限を表す |
| `GrantedAuthority` | `ROLE_USER` や `report:read` など、利用者に付与された権限を表す |
| `AuthenticationManager` | 認証を実行する API。代表実装の `ProviderManager` は認証要求を適切な `AuthenticationProvider` へ渡す |
| `AuthenticationProvider` | パスワード、Token などの方式に応じて資格情報を検証する |
| `UserDetailsService` | ユーザー名からパスワードハッシュ、状態、権限を読み込む |
| `PasswordEncoder` | パスワードを一方向変換し、入力値と保存値を照合する |
| `AuthorizationManager` | 認証情報とアクセス先を基に認可を判定する |

ユーザー名とパスワードによる代表的な処理の流れは次のとおりです。

1. 認証 Filter がリクエストから資格情報を取り出し、`AuthenticationManager` へ渡す
2. `AuthenticationProvider` が `UserDetailsService` と `PasswordEncoder` を使って資格情報を検証する
3. 成功時は認証済みの `Authentication` を `SecurityContext` に設定する
4. `AuthorizationManager` が URL や権限のルールを評価する
5. 許可されたリクエストだけが Controller へ到達する

独自 Filter や Controller で `SecurityContext` を直接設定する高度な実装では、次回以降のリクエストにも認証を保持するために `SecurityContextRepository` への明示的な保存が必要です。通常は Spring Security が提供する認証方式を利用してください。

## 3. このプロジェクトの構成

このプロジェクトは、画面と API で異なる状態管理を行うため、2 つの `SecurityFilterChain` を定義しています。

| 対象 | 認証・認可 | 状態管理 |
| --- | --- | --- |
| `GET /api/public` | 認証不要 | ステートレス |
| `GET /api/private` | HTTP Basic による認証が必要 | ステートレス |
| `/css/**`、`/error`、`/login`、`/signup` | 認証不要 | `HttpSession`（必要な場合のみ） |
| `/greeting` など上記以外 | フォームログインによる認証が必要 | `HttpSession` を Valkey に保存 |

`GET /login` は Controller が独自画面を表示し、`POST /login` と `/logout` は Spring Security の Filter が認証・ログアウトを処理します。`/signup` は Controller と `UserRegistrationService` が入力検証とユーザー作成を担当します。

設定の中心は `SecurityConfiguration.java` です。

~~~java
@Bean
@Order(1)
SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/public")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
            .httpBasic(withDefaults())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}

@Bean
SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/css/**", "/error", "/login", "/signup")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
            .formLogin(form -> form.loginPage("/login").permitAll())
            .logout(withDefaults());
    return http.build();
}
~~~

複数のチェーンは設定内容を合成しません。`@Order` の順に評価し、`securityMatcher` が最初に一致した 1 つのチェーンだけを使用します。

- `securityMatcher("/api/**")` は、そのチェーンを適用するリクエストを選ぶ
- チェーン内の `requestMatchers(...)` は、選択後のリクエストに認可ルールを適用する
- matcher を指定していない画面用チェーンは、API 以外を受ける最後の catch-all になる
- どのチェーンにも一致しないリクエストは Spring Security で保護されないため、最後の catch-all を維持する

`requestMatchers("/api/public")` 自体は HTTP メソッドを限定しません。現在は Controller が `GET` だけを定義していますが、同じパスへ状態変更処理を追加する場合は `requestMatchers(HttpMethod.GET, "/api/public")` のようにメソッドも含めて最小権限にします。

現在の `/actuator/**` も画面用の catch-all チェーンに入り、認証が必要です。Actuator 専用チェーンを追加する場合は API チェーンより後、catch-all より前となるよう matcher と `@Order` を設計してください。

静的リソースには `permitAll()` を使っています。セキュリティ Filter 自体を迂回する `ignoring()` と異なり、認証不要のリソースにもセキュリティ用レスポンスヘッダーを付与できます。

## 4. 認証方式を選ぶ

### フォームログイン

画面用チェーンの `formLogin(form -> form.loginPage("/login").permitAll())` は、未認証のブラウザーを独自の `/login` 画面へリダイレクトします。画面のフォームは `POST /login` へユーザー名とパスワードを送り、認証処理自体は Spring Security に任せます。認証成功後の `SecurityContext` は `HttpSession` に入り、このプロジェクトでは Spring Session を通して Valkey に保存されます。

成功時の遷移を変更する場合は `defaultSuccessUrl(...)` または `AuthenticationSuccessHandler`、失敗時の処理を変更する場合は `AuthenticationFailureHandler` で構成できます。画面やエラー表示を変更しても、資格情報をログへ出力しないでください。

### HTTP Basic

API 用チェーンの `httpBasic(withDefaults())` は、各リクエストの `Authorization: Basic ...` ヘッダーを認証します。認証情報がない、または誤っている場合は `401 Unauthorized` と `WWW-Authenticate` ヘッダーを返します。

Base64 は暗号化ではありません。本番環境では必ず HTTPS 経由で使用してください。HTTP Basic は簡単な内部 API や動作確認には利用できますが、失効、スコープ、委譲が必要な API では OAuth 2.0 Bearer Token の利用を検討します。

### よく利用される追加機能

次の機能は現在のプロジェクトには実装していません。利用目的が生じた時点で必要な依存関係とチェーン設定を追加します。

| 機能 | 適した用途 | 主な注意点 |
| --- | --- | --- |
| OAuth 2.0 / OIDC Login | 組織 ID や外部 ID プロバイダーによるブラウザーログイン | Spring Boot 4.1 では `spring-boot-starter-security-oauth2-client` を追加し、Authorization Code Flow を利用する |
| OAuth 2.0 Resource Server（JWT） | 受信 API の Bearer JWT をローカルで検証する | `spring-boot-starter-security-oauth2-resource-server` を追加し、署名、issuer、有効期間を検証する。audience は明示的に設定する |
| OAuth 2.0 Resource Server（Opaque Token） | Introspection Endpoint で Bearer Token の状態と属性を確認する | Introspection 用の Client 資格情報、TLS、Authorization Server 障害時の動作を設計する |
| OAuth 2.0 Client | 利用者またはアプリケーションの権限で外部 API を呼び出す | Access Token と Refresh Token をログやレスポンスへ出さず、安全に保存する |
| Remember-Me | セッション終了後もブラウザーのログイン状態を復元する | 長期間有効な Cookie になるため、必要性、失効、Token の保存方式を設計する |
| LDAP / SAML 2.0 | 既存の企業ディレクトリやフェデレーションと連携する | 専用モジュールとプロバイダー側の設定が必要になる |
| MFA | 複数の認証要素を必須にする | Spring Security 7.1 の `FactorGrantedAuthority` などを構成し、必要な要素を認可ルールで明示的に要求する |
| Passkeys / One-Time Token | パスワード以外の認証方式を提供する | 登録、配信、失効、回復、監査を設計する。One-Time Token はサーバーが生成した Token をメールや SMS などで届ける方式で、TOTP とは異なる |

OAuth 2.0 Login は利用者をブラウザーでログインさせる Client 機能、Resource Server は API に届いた Bearer Token を検証する機能です。Resource Server 自体は Token を発行しません。JWT を独自コードで分解して認証済みと判断せず、Spring Security の検証機能を使用してください。

JWT や Opaque Token の scope は通常 `SCOPE_` から始まる authority へ変換されます。必要な scope は `hasAuthority("SCOPE_reports.read")` などの認可ルールで要求します。認証方式を複数追加しただけでは MFA にはならないため、保護対象ごとに必要な factor を認可ルールへ設定します。

Controller で現在の利用者名だけが必要な場合は `Principal`、権限を含む認証情報が必要な場合は `Authentication`、独自の principal を受け取る場合は `@AuthenticationPrincipal` を利用できます。

## 5. ユーザーとパスワードを管理する

このプロジェクトは `JdbcUserDetailsManager` を使用し、PostgreSQL の `users` と `authorities` テーブルからユーザーを読み込みます。Flyway の `V1__create_security_tables.sql` が、Spring Security の既定 SQL に対応するスキーマを作成します。

初期ユーザーは、アプリケーション起動時に対象のユーザー名が存在しない場合だけ作成します。

~~~java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
~~~

`DelegatingPasswordEncoder` の保存形式は `{id}encodedPassword` です。現在の encode の既定は bcrypt であるため、このプロジェクトで新規作成するパスワードは `{bcrypt}` から始まります。`{bcrypt}` を手動で連結せず、必ず `PasswordEncoder#encode` の結果全体を保存してください。

`roles("USER")` は `ROLE_USER` という authority を作成します。データベースの `authorities.authority` にも `ROLE_USER` が保存されます。

アカウント作成画面は `UserRegistrationService` から `JdbcUserDetailsManager#createUser` を呼び出します。パスワードは Controller や SQL で直接保存せず、既存の `PasswordEncoder` でエンコードします。登録処理はトランザクション内でユーザーと authority をまとめて保存します。

パスワードを扱うときは次を守ります。

- 平文、復号可能な暗号、単純な SHA 系ハッシュで保存しない
- パスワード、ハッシュ、Authorization ヘッダーをログへ出力しない
- 適応型ハッシュの計算コストを本番相当の環境で計測し、セキュリティと応答時間のバランスを調整する
- アルゴリズムやコストを変更するときは、`{id}` を利用してログイン時に段階的に再エンコードする運用を検討する
- 初期ユーザーを恒久的なユーザー管理機能の代わりにせず、追加、無効化、パスワード変更、監査の手段を用意する
- 総当たり攻撃へのレート制限、アカウントロック、通知、MFA は要件に応じて別途実装または ID プロバイダーへ委譲する

初期ユーザーが既に存在する場合、環境変数のパスワードを変更してもデータベース上の値は更新されません。

## 6. 認可を設定する

認証は利用者が誰かを確認し、認可はその利用者に操作を許可するかを決めます。まず URL と HTTP メソッドで境界を作り、業務ルールに関わる制御はサービス層のメソッド認可で補います。

### リクエスト単位の認可

`authorizeHttpRequests` では、具体的なルールを先に、広いルールを後に記述します。最初に一致した認可ルールが使用されます。

~~~java
http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/css/**", "/error").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/reports/**").hasAuthority("report:read")
        .anyRequest().authenticated());
~~~

この例は追加時の書き方を示すもので、現在のプロジェクトには `/admin/**` と `/reports/**` はありません。

| ルール | 意味 |
| --- | --- |
| `permitAll()` | 認証の有無にかかわらず許可する |
| `authenticated()` | 認証済みであれば許可する |
| `hasRole("ADMIN")` | `ROLE_ADMIN` authority を持つ場合に許可する |
| `hasAuthority("report:read")` | 指定した authority をそのまま持つ場合に許可する |
| `denyAll()` | 常に拒否する。未定義領域を閉じる場合などに使う |

ロールと authority を混在させる場合は命名規則を決めてください。`hasRole("ADMIN")` は `ROLE_` を自動で補いますが、`hasAuthority("ADMIN")` は補いません。

### メソッド単位の認可

メソッド認可は Spring Boot Starter Security だけでは有効になりません。利用する場合は構成クラスに `@EnableMethodSecurity` を付け、Spring 管理下のクラスに `@PreAuthorize` などを付けます。

~~~java
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class MethodSecurityConfiguration {
}

@Service
class ReportService {

    @PreAuthorize("hasAuthority('report:read')")
    public String readReport(String id) {
        // 権限が確認された後で処理する
        return id;
    }
}
~~~

メソッド認可は、別の Controller、バッチ処理、またはほかの Bean からサービスが呼ばれた場合にも適用できます。URL 認可を削除する代わりではなく、入口と業務処理を多層で保護するために使います。

メソッド認可は Spring AOP の proxy を通る呼び出しに適用されます。同じ Bean 内から対象メソッドを直接呼ぶ self-invocation では認可が実行されないため、認可境界となる処理を別の Bean に分けてください。現在のプロジェクトではメソッド認可を有効化していません。

### `401`、`403`、リダイレクトの違い

| 状態 | 代表的な応答 | Spring Security の処理 |
| --- | --- | --- |
| API で未認証 | `401 Unauthorized` | `AuthenticationEntryPoint` が認証を要求する |
| 画面で未認証 | `/login` への `302` | フォームログイン用の `AuthenticationEntryPoint` が遷移させる |
| 認証済みだが権限不足 | `403 Forbidden` | `AccessDeniedHandler` が拒否を処理する |
| CSRF Token が不正または不足 | 通常は `403 Forbidden` | 認可ルールへ到達する前に CSRF 検証が拒否する |

REST API で JSON 形式のエラー本文が必要な場合は、API 用チェーンだけに `AuthenticationEntryPoint` と `AccessDeniedHandler` を設定します。

## 7. Web 攻撃への対策

### CSRF

CSRF 保護は既定で有効です。`POST`、`PUT`、`PATCH`、`DELETE` など状態を変更するリクエストでは、ブラウザーから正しい CSRF Token を送信します。`GET`、`HEAD`、`OPTIONS`、`TRACE` は状態を変更しない実装にしてください。

このプロジェクトの `greeting.html` は `th:action` を使った `POST /logout` フォームです。Thymeleaf と Spring MVC の連携によって hidden field に CSRF Token が追加され、Spring Security がログアウト前に検証します。

ステートレスであることだけを理由に CSRF を無効化しないでください。ブラウザーが Cookie や HTTP Basic の資格情報を自動送信する構成では、サーバーがセッションを使わなくても CSRF の影響を受ける可能性があります。

CSRF を無効化または対象外にするのは、ブラウザーが資格情報を自動送信しない Bearer Token 専用 API など、不要である根拠を確認できる範囲に限定します。現在の API は HTTP Basic を使用しているため CSRF を有効なままにしています。現時点では読み取り専用の `GET` だけなので Token は要求されません。

既定の `HttpSessionCsrfTokenRepository` は CSRF Token を `HttpSession` に保存します。このため、将来 Basic 認証の API に状態変更メソッドを追加すると、`SessionCreationPolicy.STATELESS` でも CSRF Token のためにセッションが作られる場合があります。クライアントが Token を取得・送信する手順と `CsrfTokenRepository` を含めて設計してください。

### CORS

CORS は、どのオリジンのブラウザー JavaScript にレスポンスの読み取りを許可するかを制御する仕組みです。認証や CSRF 対策の代わりにはなりません。現在のプロジェクトは同一オリジンで画面と API を利用するため、CORS を構成していません。

別オリジンの SPA から呼び出す場合は、許可するオリジン、HTTP メソッド、ヘッダーを列挙し、対象チェーンで `.cors(withDefaults())` を有効にします。CORS の preflight リクエストには通常 Cookie がないため、認証より前に処理される構成が必要です。

~~~java
@Bean
UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("https://app.example.com"));
    configuration.setAllowedMethods(List.of("GET", "POST"));
    configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
~~~

`setAllowCredentials(true)` は、Cookie や HTTP 認証をオリジン間で送る必要がある場合だけ指定します。その場合は許可オリジンを無制限にせず、CSRF Token の送信方法も設計してください。クロスサイトで Cookie を送る必要がある場合は、`SameSite=None` と `Secure` の設定も確認します。複数の `SecurityFilterChain` で異なる CORS ポリシーが必要な場合は、それぞれの `.cors(...)` に対応する `CorsConfigurationSource` を指定します。

### セキュリティ用 HTTP レスポンスヘッダー

Spring Security は既定で、キャッシュ制御、`X-Content-Type-Options`、クリックジャッキング対策、HTTPS 応答時の HSTS などのヘッダーを追加します。静的リソースも `permitAll()` でチェーン内に残しているため、これらのヘッダーが適用されます。

Content Security Policy（CSP）はアプリケーションごとに許可する配信元が異なるため、既定では追加されません。画面で使用する JavaScript、CSS、画像、外部接続を確認し、必要に応じて `headers(...)` で明示します。既定ヘッダーを一括で無効化せず、変更理由とブラウザーでの確認結果を残してください。

### HTTPS

本番の HTTP 通信はすべて TLS で保護します。ロードバランサーやリバースプロキシで TLS を終端する場合は、信頼するプロキシからの Forwarded Header だけを受け入れ、アプリケーションが元のスキームを HTTPS と認識できるようにします。これが誤っていると、Secure Cookie、リダイレクト、HSTS の動作に影響します。

## 8. セッションとログアウトを管理する

画面用チェーンは必要になった時点で `HttpSession` を作成し、認証済みの `SecurityContext` を保存します。このプロジェクトは Spring Session を利用して Valkey に保存するため、複数インスタンスでも同じセッションを参照できます。

| 項目 | 現在の値・動作 |
| --- | --- |
| Cookie 名 | `SESSION` |
| セッション有効期間 | `SESSION_TIMEOUT`。既定値は `30m` |
| Valkey の名前空間 | `SESSION_REDIS_NAMESPACE`。既定値は `spring-boot-dev-tooling-lab:session` |
| セッション固定攻撃対策 | ログイン成功時にセッション ID を変更する Spring Security の既定保護を使用 |
| API の Security Context | `SessionCreationPolicy.STATELESS` により `HttpSession` へ保存しない |

`STATELESS` は Spring Security が Security Context のためにセッションを作成・参照しない設定です。Controller などのアプリケーションコードが `HttpSession` を明示的に作ることまで禁止する設定ではありません。

本番では Cookie の `Secure`、`HttpOnly`、`SameSite`、有効期間、適用 Path を確認します。同時ログイン数を制限する場合、標準のインメモリ `SessionRegistry` だけでは制限が JVM ごとに分かれます。複数インスタンス全体で制限するには、Spring Session の indexed repository と `SpringSessionBackedSessionRegistry` などを構成し、失効が正しく共有されることをテストしてください。

`logout(withDefaults())` により、Spring Security はログアウトを処理します。CSRF が有効な場合、実際のログアウトは CSRF Token を含む `POST /logout` で行います。既定ではセッションを無効化し、`SecurityContext` と CSRF Token を消去して、`/login?logout` へリダイレクトします。

セッションの設定と Valkey 上の保存形式は [Spring Session + Valkey ガイド](spring-session-valkey-guide.md)を参照してください。

## 9. ローカルで動作を確認する

Docker を起動してから、次のコマンドを実行します。

~~~bash
./gradlew bootTestRun
~~~

Testcontainers が PostgreSQL と Valkey を起動し、アプリケーションへ接続情報を渡します。ブラウザーで `http://localhost:8080/greeting` を開くと、Spring Security の既定ログイン画面へ移動します。

ローカル確認用の初期ユーザーは次のとおりです。

| 項目 | 既定値 |
| --- | --- |
| ユーザー名 | `user` |
| パスワード | `password` |

初期ユーザーは PostgreSQL に存在しない場合だけ作成します。値を変更する場合は、最初の起動前に環境変数を指定してください。

~~~bash
APP_INITIAL_USER_USERNAME=developer \
APP_INITIAL_USER_PASSWORD='local-secret' \
  ./gradlew bootTestRun
~~~

### API を確認する

公開 API は認証なしでアクセスできます。

~~~bash
curl --fail-with-body http://localhost:8080/api/public
~~~

保護 API は認証情報なしでは `401 Unauthorized` と `WWW-Authenticate` ヘッダーを返します。

~~~bash
curl --include http://localhost:8080/api/private
curl --fail-with-body --user user:password http://localhost:8080/api/private
~~~

成功時の JSON には認証済みユーザー名が含まれます。現在テストしている `GET` の公開 API、認証成功、認証失敗では、API 用チェーンは `SESSION` Cookie を作成しません。状態変更 API と CSRF Token を追加した場合は、CSRF Token の保存方式に応じてこの前提を見直します。

## 10. 本番設定と運用

本番用 JAR を作成し、PostgreSQL、Valkey、初期ユーザーの値をデプロイ基盤の環境変数で渡します。

~~~bash
./gradlew bootJar

java -jar build/libs/spring-boot-dev-tooling-lab-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=production
~~~

起動前にデプロイ基盤から次の環境変数を注入します。

| 環境変数 | 内容 |
| --- | --- |
| `DATABASE_URL` | PostgreSQL の JDBC URL |
| `DATABASE_USERNAME` | PostgreSQL のユーザー名 |
| `DATABASE_PASSWORD` | PostgreSQL のパスワード |
| `VALKEY_URL` | 認証情報と必要に応じて TLS を含む Valkey URL。TLS 接続では `rediss://` を使用する |
| `APP_INITIAL_USER_USERNAME` | 初期ユーザー名 |
| `APP_INITIAL_USER_PASSWORD` | 初期ユーザーのパスワード |

`production` プロファイルでは、上記の環境変数に既定値を設けていません。実環境ではデプロイ基盤の Secret 管理機能から値を注入し、シェル履歴、ログ、構成ファイル、コンテナイメージへ秘密情報を残さないでください。

初期ユーザーのパスワードを後から環境変数だけで変更しても、既存ユーザーは更新されません。運用時のユーザー追加、無効化、パスワード変更は、管理機能または適切に管理された SQL で行ってください。

本番公開前に少なくとも次を確認します。

- 全通信を HTTPS にし、HTTP Basic、Cookie、Token を平文通信へ流さない
- 公開 URL を列挙し、`permitAll()` の範囲と catch-all ルールをレビューする
- 認証失敗と権限不足を区別し、内部情報をエラー本文へ含めない
- CSRF の有効範囲、CORS の許可オリジン、Cookie 属性を実際のクライアント構成に合わせる
- パスワードと Token の保存、ローテーション、失効、回復手順を用意する
- ログイン成功・失敗、権限拒否、ユーザー管理操作を監査し、資格情報そのものは記録しない
- `/actuator/**` の公開範囲を限定し、管理ネットワークと認可で保護する
- 総当たり攻撃へのレート制限、ロック、通知、必要に応じた MFA を設計する
- Spring Boot と Spring Security のサポート対象バージョンを維持し、セキュリティ更新を適用する

Actuator の保護方法は [Spring Boot Actuator ガイド](spring-boot-actuator-guide.md)、画面フォームの扱いは [Spring Boot Thymeleaf ガイド](spring-boot-thymeleaf-guide.md)も参照してください。

## 11. テスト

Docker を起動した状態で実行します。

~~~bash
./gradlew test
~~~

現在のテストは、次を確認します。

- 未認証の画面がログインページへリダイレクトされる
- PostgreSQL 上のユーザーでフォームログインでき、Security Context が Valkey に保存される
- 公開 API は認証不要で、セッションを作成しない
- 保護 API は未認証と誤ったパスワードを拒否する
- 正しい HTTP Basic 認証で保護 API へアクセスでき、セッションを作成しない
- `@WithMockUser` を使用して認証済みの画面表示をテストする

Spring Security の MockMvc 支援では、次をよく使用します。

| テスト機能 | 用途 |
| --- | --- |
| `formLogin()` | ログイン URL、ユーザー名、パスワード、成功・失敗をテストする |
| `httpBasic(...)` | HTTP Basic の Authorization ヘッダーを付ける |
| `csrf()` | 正しい CSRF Token を付ける。無効な Token のテストもできる |
| `user(...)` | 1 リクエストだけ任意の認証済み利用者として実行する |
| `@WithMockUser` | テストメソッドまたはクラスを任意のロールで実行する |
| `authenticated()` / `unauthenticated()` | テスト後の認証状態を検証する |

`user(...)` と `@WithMockUser` は実際の JDBC 読み込みや `PasswordEncoder` を通りません。認可だけのテストには適していますが、資格情報の検証には `formLogin()`、`httpBasic()`、または実際の認証処理を通る統合テストも使用します。

認可や機能を追加したときは、匿名利用者、必要な権限を持つ利用者、権限不足の利用者を分けてテストします。状態変更 API では CSRF Token の不足・不正・正常、CORS を追加した場合は preflight と不許可オリジン、独自ヘッダーを追加した場合はレスポンスヘッダーも確認してください。

## 12. 主な実装ファイル

- `build.gradle.kts`: Spring Security とテスト支援の依存関係
- `src/main/java/com/example/security/SecurityConfiguration.java`: Filter Chain、フォームログイン、HTTP Basic、JDBC ユーザー、Password Encoder の設定
- `src/main/java/com/example/api/controller/AuthenticationApiController.java`: 公開 API と保護 API
- `src/main/java/com/example/page/controller/GreetingController.java`: 認証済みユーザー向け画面
- `src/main/resources/templates/greeting.html`: CSRF 保護されたログアウトフォームを含む画面
- `src/main/resources/db/migration/V1__create_security_tables.sql`: 認証テーブルの Flyway migration
- `src/main/resources/application.yaml`: ローカル既定値、初期ユーザー、Valkey セッション設定
- `src/main/resources/application-production.yaml`: 本番の PostgreSQL、Valkey、初期ユーザー設定
- `src/test/java/com/example/SpringBootDevToolingLabApplicationTests.java`: フォームログイン、Basic 認証、JDBC、Valkey を使う統合テスト
- `src/test/java/com/example/page/controller/GreetingControllerTest.java`: `@WithMockUser` を使う画面テスト
- `src/test/java/com/example/PostgresTestcontainersConfiguration.java`: ローカル・テスト用 PostgreSQL
- `src/test/java/com/example/ValkeyTestcontainersConfiguration.java`: ローカル・テスト用 Valkey

## 13. よくある問題

| 現象 | 確認すること |
| --- | --- |
| API が `/login` へリダイレクトされる | API 用 `securityMatcher`、チェーンの `@Order`、リクエスト URL が一致しているか確認する |
| `401 Unauthorized` になる | Authorization ヘッダー、ユーザーの存在、パスワード、ユーザーの有効状態を確認する |
| 認証済みなのに `403 Forbidden` になる | 必要なロールまたは authority と、CSRF Token の不足・不正を分けて確認する |
| `POST /logout` が `403` になる | POST フォームまたはリクエストヘッダーに正しい CSRF Token があるか確認する |
| CSS がログイン画面へリダイレクトされる | 静的リソースの URL を `permitAll()` に含め、`anyRequest()` より前に記述しているか確認する |
| CORS preflight が `401` / `403` になる | 対象チェーンで CORS を有効にし、許可オリジン、メソッド、ヘッダー、`OPTIONS` の処理を確認する |
| API で `SESSION` Cookie が作られる | 対象リクエストが API チェーンに一致するか、アプリケーションコードまたは CSRF Token の保存処理が `HttpSession` を作っていないか確認する |
| 環境変数を変えてもパスワードが変わらない | 初期化処理は既存ユーザーを更新しないため、管理手順でパスワードを変更する |
| Actuator がログイン画面へ移動する | 現在は画面用 catch-all の認証対象。専用チェーンを設ける場合も公開範囲を限定する |

デバッグ時は `org.springframework.security` のログレベルを一時的に `DEBUG` または `TRACE` にすると、選択された Filter Chain や認可処理を確認できます。本番で常時詳細ログを有効にせず、ログに Cookie、Token、パスワードが含まれていないことを確認してください。

## 14. 公式ドキュメント

- [Spring Boot の Spring Security サポート](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
- [Servlet アーキテクチャ](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Java Configuration と複数の SecurityFilterChain](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)
- [Servlet の認証アーキテクチャ](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [HTTP リクエストの認可](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [メソッド認可](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [パスワードの保存](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [セッション管理](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [ログアウト](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- [CSRF 対策](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [CORS 連携](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [セキュリティ用 HTTP レスポンスヘッダー](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html)
- [OAuth 2.0](https://docs.spring.io/spring-security/reference/servlet/oauth2/)
- [OAuth 2.0 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Opaque Token Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html)
- [MockMvc によるテスト](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/)
- [多要素認証](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html)
- [One-Time Token ログイン](https://docs.spring.io/spring-security/reference/servlet/authentication/onetimetoken.html)
- [Spring Session と Spring Security の連携](https://docs.spring.io/spring-session/reference/spring-security.html)
