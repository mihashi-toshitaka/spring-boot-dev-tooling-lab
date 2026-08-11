package com.example;

import org.springframework.boot.SpringApplication;

/** Testcontainers でローカル依存サービスを起動する開発用アプリケーション。 */
public final class TestSpringBootDevToolingLabApplication {

    private TestSpringBootDevToolingLabApplication() {}

    /**
     * PostgreSQL、Valkey コンテナとアプリケーションを起動する。
     *
     * @param args 実行時引数
     */
    public static void main(String[] args) {
        SpringApplication.from(SpringBootDevToolingLabApplication::main)
                .with(PostgresTestcontainersConfiguration.class, ValkeyTestcontainersConfiguration.class)
                .run(args);
    }
}
