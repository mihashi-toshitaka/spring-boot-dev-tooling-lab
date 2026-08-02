# ArchUnit ガイド

## 1. ArchUnit とは

ArchUnit は、Java のクラス、package、依存関係、annotation、継承関係などをテストコードで検査するライブラリです。設計ドキュメントだけでは崩れやすいアーキテクチャ上の制約を、JUnit のテストとして継続的に保証できます。

代表的な用途は次のとおりです。

- controller、service、repository 間の依存方向を制限する
- domain 層を framework や infrastructure から独立させる
- package 間の cycle を禁止する
- 命名と annotation の対応を保証する
- 特定 API や layer へのアクセスを限定する
- Spring component の設計ルールを検査する

ArchUnit は静的解析ライブラリですが、Gradle では JUnit テストとして実行されます。

## 2. このプロジェクトでの構成

~~~kotlin
dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
~~~

現在のルールは `SpringComponentArchitectureTest` にあります。

~~~java
@AnalyzeClasses(packages = "com.example")
class SpringComponentArchitectureTest {

    @ArchTest
    static final ArchRule SERVICE_AND_COMPONENT_FIELDS_SHOULD_BE_FINAL = fields().that()
            .areDeclaredInClassesThat(annotatedWith(Controller.class)
                    .or(annotatedWith(RestController.class))
                    .or(annotatedWith(Service.class))
                    .or(annotatedWith(Component.class)))
            .should()
            .beFinal();
}
~~~

Spring の controller、service、component が持つフィールドを `final` にし、constructor injection と不変な依存関係を促します。

## 3. 実行方法

~~~bash
./gradlew test
./gradlew test --tests '*SpringComponentArchitectureTest'
./gradlew check
~~~

失敗時は通常の JUnit failure として、違反クラス、依存元・依存先、ルール説明が出力されます。

## 4. 基本 API

ArchUnit の fluent API は概ね次の構造です。

~~~java
ArchRule rule = classes()
        .that().resideInAPackage("..service..")
        .should().haveSimpleNameEndingWith("Service")
        .because("service package の役割を名前でも明示するため");
~~~

| 部分 | 役割 |
| --- | --- |
| `classes()`、`methods()`、`fields()` | 検査対象の種類 |
| `.that()` | 対象を絞る predicate |
| `.should()` | 満たすべき condition |
| `.andShould()` / `.orShould()` | 条件の合成 |
| `.because()` | ルールの理由 |

ルール名と `because` には、違反時に修正判断ができる説明を書きます。

## 5. package と命名のルール

~~~java
@ArchTest
static final ArchRule SERVICES_SHOULD_BE_NAMED = classes()
        .that().resideInAPackage("..service..")
        .should().haveSimpleNameEndingWith("Service");

@ArchTest
static final ArchRule CONTROLLERS_SHOULD_BE_ANNOTATED = classes()
        .that().haveSimpleNameEndingWith("Controller")
        .should().beAnnotatedWith(RestController.class);
~~~

package と名前だけに頼ると移動や例外に弱くなります。Spring annotation など、設計上の意味を直接表す条件も組み合わせます。

## 6. 依存方向を制約する

~~~java
@ArchTest
static final ArchRule SERVICES_MUST_NOT_DEPEND_ON_CONTROLLERS = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat().resideInAPackage("..controller..");
~~~

よく使うパターン:

- `noClasses().that()...should().dependOnClassesThat()...`
- `classes().that()...mayOnlyBeAccessed().byClassesThat()...`
- `classes().that()...should().onlyDependOnClassesThat()...`
- `methods().that()...should().onlyBeCalled().by...`

Java 標準 API や共通 domain type まで禁止しないよう、許可条件を具体的にします。

## 7. Layered Architecture

~~~java
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@ArchTest
static final ArchRule LAYERS = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Repository").definedBy("..repository..")
        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");
~~~

依存関係の考慮方法には、全依存を見る、定義した layer 間だけを見る、package 外依存を含めるといった選択肢があります。第三者 library まで意図せず違反にしない設定を選びます。

## 8. Onion / Hexagonal Architecture

~~~java
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

@ArchTest
static final ArchRule ONION = onionArchitecture()
        .domainModels("..domain.model..")
        .domainServices("..domain.service..")
        .applicationServices("..application..")
        .adapter("web", "..adapter.web..")
        .adapter("persistence", "..adapter.persistence..");
~~~

domain package が Spring、DB、Web adapter に依存しないことを保証する用途に向きます。実際の package 構成が onion architecture を表してから導入します。

## 9. Cycle の検出

~~~java
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@ArchTest
static final ArchRule PACKAGES_SHOULD_BE_FREE_OF_CYCLES = slices()
        .matching("com.example.(*)..")
        .should().beFreeOfCycles();
~~~

`(*)` が slice の識別部分です。粒度が粗すぎると問題を見逃し、細かすぎると許容可能な内部依存まで違反になります。

## 10. Class Import の設定

JUnit 5 integration では `@AnalyzeClasses` で対象を定義します。

~~~java
@AnalyzeClasses(
        packages = "com.example",
        importOptions = {
            ImportOption.DoNotIncludeTests.class,
            ImportOption.DoNotIncludeJars.class
        })
class ProductionArchitectureTest {}
~~~

主な選択肢:

- production class だけを検査する
- test class も含め、テスト architecture を検査する
- jar 内の class を含め、外部依存へのアクセスも見る
- location provider で複数 module の class を集める

本プロジェクトの現在の `packages = "com.example"` は、main と test の両 classpath 上にある対象 package を import します。ルールの意図に応じて import option を明示します。

## 11. 既存違反への段階導入

### FreezingArchRule

~~~java
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

@ArchTest
static final ArchRule NO_NEW_VIOLATIONS = freeze(
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.."));
~~~

初回の既存違反を violation store に記録し、新規違反だけを失敗させます。既存違反を修正すると baseline から自動的に減ります。

注意点:

- violation store をチームで共有・version control する
- store の無差別な再生成を禁止する
- baseline 削減の方針を決める
- 新規プロジェクトでは原則として freeze せず、最初から違反ゼロにする

## 12. 例外と ignore

`.ignoreDependency()` などで既知の例外を表現できますが、個別クラス名が増えると設計が見えなくなります。まず package、annotation、interface など、安定した architecture concept で条件を表現します。

除外する場合は理由を `.because()` またはコメントで残し、期限付きの移行例外は issue と紐付けます。

## 13. ルール設計の指針

- 実際に存在する architecture をテストする
- 実装詳細ではなく、守りたい依存方向を表現する
- 1ルール1目的にして失敗メッセージを読みやすくする
- package rename に過度に弱い文字列条件を避ける
- Spring annotation、interface、marker annotation を活用する
- 正常ケースと意図的な違反ケースでルール自体をテストする

ArchUnit test が複雑になったら、共通 predicate/condition を名前付きメソッドへ抽出します。

## 14. トラブルシューティング

### 対象クラスが import されない

先に対象 source set がコンパイルされているか、`@AnalyzeClasses` の package、import option、test runtime classpath を確認します。

### 外部 library の依存まで大量に表示される

依存関係の対象を `resideInAPackage` や `consideringOnlyDependenciesInLayers` で限定します。ただし必要な外部依存違反まで隠さないようにします。

### synthetic class や lambda が違反になる

compiler が生成した class の扱いを確認し、ルールの対象 predicate を調整します。ファイル名への広い除外は避けます。

### IDE と Gradle で結果が違う

テスト classpath とコンパイル済み class が異なる可能性があります。`./gradlew clean test` を基準にします。

## 15. 参考資料

- [ArchUnit](https://www.archunit.org/)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Examples](https://github.com/TNG/ArchUnit-Examples)
- [ArchUnit API](https://www.javadoc.io/doc/com.tngtech.archunit/archunit/latest/index.html)

