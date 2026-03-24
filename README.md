# OkHttpMonitor

A lightweight, **production-ready** HTTP monitoring library for Kotlin. Supports both **OkHttp** and **Ktor** clients.

**[中文文档](#okhttpmonitor-中文文档)**

---

## Modules

| Module | HTTP Client | Artifact |
|--------|-------------|---------|
| `okhttpmonitor-core` | OkHttp 4.x / 5.x | `com.github.OCNYang.OkHttpMonitor:okhttpmonitor-core:<version>` |
| `ktormonitor-core` | Ktor 2.x | `com.github.OCNYang.OkHttpMonitor:ktormonitor-core:<version>` |

Both modules share the same `HttpTransaction`, `TransactionCollector`, and `BodyDecoder` design — the data model and callback interface are identical, only the installation method differs.

---

## What is OkHttpMonitor?

OkHttpMonitor is inspired by [Chucker](https://github.com/ChuckerTeam/chucker), a popular HTTP inspection library for Android development. While Chucker is designed as a **debug-time tool** (with UI, notifications, and local database), OkHttpMonitor takes a different approach:

- **Production-ready**: No UI, no database, no Android dependencies — just a pure Kotlin/JVM interceptor that captures HTTP data and delivers it to your code via a simple callback interface.
- **You control the reporting**: Implement `TransactionCollector` to send data wherever you need — Firebase Analytics, your own backend, logging systems, or any combination.
- **Monitor API quality at scale**: Report error requests, sample all traffic, or track slow endpoints — all in your release builds.

### Chucker vs OkHttpMonitor

| | Chucker | OkHttpMonitor |
|---|---------|--------------|
| Purpose | Debug-time HTTP inspection | Production HTTP monitoring & reporting |
| UI | Full Activity + notifications | None |
| Storage | Room database | In-memory only |
| Platform | Android only | Pure Kotlin/JVM (Android, backend, desktop) |
| Output | On-device viewing | Callback interface — you decide |
| Build type | `debugImplementation` | `implementation` (all build types) |

## Getting Started

### 1. Add Dependency

Add JitPack repository to your project:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Add the dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.OCNYang:OkHttpMonitor:<latest-version>")
}
```

### 2. Implement TransactionCollector

```kotlin
val collector = object : TransactionCollector {
    override fun onResponseReceived(transaction: HttpTransaction) {
        // Example: Report errors to Firebase
        if (transaction.isError) {
            val params = transaction.toMap(maxFieldLength = 90)
            FirebaseAnalytics.getInstance(context)
                .logEvent("http_error", bundleOf(*params.toList().toTypedArray()))
        }

        // Example: Log all requests
        Log.d("HTTP", "${transaction.method} ${transaction.url} " +
            "→ ${transaction.responseCode} (${transaction.tookMs}ms)")
    }
}
```

### 3. Add Interceptor to OkHttp

```kotlin
val monitor = OkHttpMonitorInterceptor.Builder()
    .collector(collector)
    .build()

val client = OkHttpClient.Builder()
    .addInterceptor(monitor)
    .build()
```

---

## KtorMonitor — Ktor Client Support

`ktormonitor-core` provides the same monitoring capabilities for **Ktor 2.x** clients via a Ktor `ClientPlugin`.

### 1. Add Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.OCNYang.OkHttpMonitor:ktormonitor-core:<latest-version>")
}
```

### 2. Install Plugin

```kotlin
val client = HttpClient(Android) {
    install(KtorMonitorPlugin) {
        collector = object : TransactionCollector {
            override fun onResponseReceived(transaction: HttpTransaction) {
                if (transaction.isError) {
                    Log.e("HTTP", "${transaction.method} ${transaction.url} → ${transaction.responseCode} (${transaction.tookMs}ms)")
                }
            }
        }
    }
}
```

### Full Configuration

```kotlin
val client = HttpClient(Android) {
    install(KtorMonitorPlugin) {
        // Required
        collector = myCollector

        // Optional: redact sensitive headers
        redactHeaders("Authorization", "Cookie", "Set-Cookie")

        // Optional: body capture limit (default 250 KB)
        maxContentLength = 250_000L

        // Optional: skip paths
        skipPaths("/health", "/ping")

        // Optional: skip domains
        skipDomains("analytics.google.com")

        // Optional: custom body decoder
        addBodyDecoder(object : BodyDecoder {
            override fun decodeRequest(contentType: String?, body: ByteString): String? = null
            override fun decodeResponse(contentType: String?, body: ByteString): String? = null
        })
    }
}
```

### Differences from OkHttpMonitor

| | `okhttpmonitor-core` | `ktormonitor-core` |
|---|---|---|
| Installation | `OkHttpClient.Builder().addInterceptor(monitor)` | `HttpClient { install(KtorMonitorPlugin) { ... } }` |
| TLS info | `responseTlsVersion`, `responseCipherSuite` populated | Always `null` (not exposed by Ktor API) |
| Body capture | Stream tee (zero-copy) | `call.save()` (buffer then dual-read) |
| Threading | OkHttp calling thread | Ktor coroutine context |

---

## Configuration

All options are set through the `OkHttpMonitorInterceptor.Builder`:

### collector (Required)

Sets the `TransactionCollector` that receives captured HTTP transaction data.

```kotlin
.collector(myCollector)
```

The `TransactionCollector` interface has two callbacks:

| Callback | Timing | Description |
|----------|--------|-------------|
| `onRequestSent(transaction)` | Before network call | Optional (default no-op). Transaction contains request data only. |
| `onResponseReceived(transaction)` | After response body is consumed | **Required**. Transaction contains full request + response data. Also called on IOException (with `transaction.error` set). |

> **Threading**: Callbacks run on the OkHttp calling thread. If your reporting logic is time-consuming, switch to a background thread.

### maxContentLength

Maximum bytes to capture from request/response bodies. Bodies exceeding this limit are truncated. Default: `250,000` (250 KB).

```kotlin
.maxContentLength(500_000L)  // 500 KB
```

### redactHeaders

Replaces the values of sensitive headers with `**`. Header name matching is case-insensitive.

```kotlin
.redactHeaders("Authorization", "Cookie", "Set-Cookie", "X-Api-Key")
```

### skipPaths

Excludes specific URL paths from monitoring. Supports exact match and regex.

```kotlin
// Exact match
.skipPaths("/health", "/ping", "/metrics")

// Regex match
.skipPaths(".*\\.(jpg|png|gif|webp)$".toRegex())
```

### skipDomains

Excludes specific domains from monitoring. Domain names are evaluated in lowercase. Supports exact match and regex.

```kotlin
// Exact match
.skipDomains("analytics.google.com", "crashlytics.googleapis.com")

// Regex match
.skipDomains(".*\\.googleapis\\.com".toRegex())
```

### addBodyDecoder

Adds a custom body decoder to the processing pipeline. Decoders are applied in order; the first non-null result is used. A built-in `PlainTextDecoder` is always appended as the last decoder.

```kotlin
.addBodyDecoder(object : BodyDecoder {
    override fun decodeRequest(contentType: String?, body: ByteString): String? {
        if (contentType?.contains("protobuf") == true) {
            return MyProtoDecoder.decode(body.toByteArray())
        }
        return null // pass to next decoder
    }

    override fun decodeResponse(contentType: String?, body: ByteString): String? {
        // same logic
        return null
    }
})
```

## Full Configuration Example

```kotlin
val monitor = OkHttpMonitorInterceptor.Builder()
    // Required: set your collector
    .collector(object : TransactionCollector {
        override fun onResponseReceived(transaction: HttpTransaction) {
            when {
                // Report all errors
                transaction.isError -> reportError(transaction)
                // Sample 10% of successful requests
                Random.nextFloat() < 0.1f -> reportSample(transaction)
            }
        }
    })

    // Privacy: redact sensitive headers
    .redactHeaders("Authorization", "Cookie", "Set-Cookie")

    // Body capture: max 250KB (default)
    .maxContentLength(250_000L)

    // Skip: don't monitor these paths
    .skipPaths("/health", "/ping")

    // Skip: don't monitor these domains
    .skipDomains("analytics.google.com")

    .build()

val client = OkHttpClient.Builder()
    .addInterceptor(monitor)
    .build()
```

## HttpTransaction Fields

The `HttpTransaction` object passed to your collector contains:

| Field | Type | Description |
|-------|------|-------------|
| `method` | String? | HTTP method (GET, POST, etc.) |
| `url` | String? | Full URL |
| `host` | String? | Host name |
| `path` | String? | URL path |
| `scheme` | String? | http or https |
| `requestDate` | Long? | Request timestamp (millis) |
| `requestContentType` | String? | Request Content-Type |
| `requestPayloadSize` | Long? | Request body size in bytes |
| `requestHeaders` | String? | Request headers (redacted) |
| `requestBody` | String? | Decoded request body preview |
| `responseCode` | Int? | HTTP status code |
| `responseMessage` | String? | HTTP status message |
| `responseDate` | Long? | Response timestamp (millis) |
| `responseContentType` | String? | Response Content-Type |
| `responsePayloadSize` | Long? | Response body size in bytes |
| `responseHeaders` | String? | Response headers (redacted) |
| `responseBody` | String? | Decoded response body preview |
| `protocol` | String? | Protocol (http/1.1, h2, etc.) |
| `responseTlsVersion` | String? | TLS version |
| `responseCipherSuite` | String? | TLS cipher suite |
| `tookMs` | Long? | Request duration in milliseconds |
| `error` | String? | Error message (if IOException) |
| `status` | Status | `Requested`, `Complete`, or `Failed` |
| `isError` | Boolean | `true` if error or non-2xx status |

### Utility Methods

```kotlin
transaction.toMap()                  // Convert to Map<String, String>
transaction.toMap(maxFieldLength = 90)  // With field length truncation (e.g. for Firebase)
transaction.isSuccessful()           // true if responseCode in 200..299
```

## Acknowledgments

This library is built upon the excellent work of [Chucker](https://github.com/ChuckerTeam/chucker). Core components including the stream tee mechanism (`TeeSource`), byte limiting (`LimitingSource`), body capture (`ReportingSink`), plain text detection, and the interceptor architecture are adapted from Chucker's codebase. OkHttpMonitor repurposes these components from a debug inspection tool into a production monitoring library.

## License

```
Copyright 2024 �� ocnyang

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

# OkHttpMonitor 中文文档

一个轻量级、**可用于生产环境**的 Kotlin HTTP 监控库。同时支持 **OkHttp** 和 **Ktor** 客户端。

## 模块说明

| 模块 | HTTP 客户端 | Artifact |
|------|------------|---------|
| `okhttpmonitor-core` | OkHttp 4.x / 5.x | `com.github.OCNYang.OkHttpMonitor:okhttpmonitor-core:<version>` |
| `ktormonitor-core` | Ktor 2.x | `com.github.OCNYang.OkHttpMonitor:ktormonitor-core:<version>` |

两个模块共享相同的 `HttpTransaction`、`TransactionCollector` 和 `BodyDecoder` 设计 —— 数据模型和回调接口完全一致，只有安装方式不同。

---

## 这个库是做什么的？

OkHttpMonitor 的设计灵感来自 [Chucker](https://github.com/ChuckerTeam/chucker)。Chucker 是一个广受欢迎的 Android HTTP 调试工具，它提供了完整的 UI 界面、通知栏提示和本地数据库存储，**但它只适用于开发阶段**。

OkHttpMonitor 借鉴了 Chucker 的核心拦截能力，走了一条不同的路：

- **面向生产环境**：没有 UI、没有数据库、没有 Android 依赖 —— 纯 Kotlin/JVM 拦截器，通过简单的回调接口将 HTTP 数据传递给你的代码。
- **你来决定如何上报**：实现 `TransactionCollector` 接口，把数据发送到任何地方 —— Firebase Analytics、自建后端、日志系统等。
- **大规模监控接口质量**：在正式发布版本中上报错误请求、采样全量流量、追踪慢接口 —— 帮助你在后台监控整体的接口质量。

### Chucker 与 OkHttpMonitor 的对比

| | Chucker | OkHttpMonitor |
|---|---------|--------------|
| 定位 | 开发阶段的 HTTP 调试工具 | 生产环境的 HTTP 监控与上报 |
| UI | 完整的 Activity + 通知栏 | 无 |
| 存储 | Room 数据库 | 仅内存（不持久化） |
| 平台 | 仅 Android | 纯 Kotlin/JVM（Android、后端、桌面均可） |
| 数据输出 | 在设备上查看 | 回调接口 —— 由你决定 |
| 依赖方式 | `debugImplementation` | `implementation`（所有构建类型） |

### 典型使用场景

- **上报错误请求**：捕获所有 4xx/5xx 响应或网络异常，上报到 Firebase / 自建监控平台
- **全量流量采样**：对所有请求进行 10% 采样上报，在后台分析整体接口质量（响应时间、成功率等）
- **慢接口追踪**：记录耗时超过阈值的请求，定位性能瓶颈
- **接口可用性监控**：按 host/path 聚合成功率和响应时间，生成接口质量报表

## 快速开始

### 1. 添加依赖

在项目中添加 JitPack 仓库：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

添加依赖：

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.OCNYang:OkHttpMonitor:<latest-version>")
}
```

### 2. 实现 TransactionCollector

```kotlin
val collector = object : TransactionCollector {
    override fun onResponseReceived(transaction: HttpTransaction) {
        // 示例：上报错误请求到 Firebase
        if (transaction.isError) {
            val params = transaction.toMap(maxFieldLength = 90)
            FirebaseAnalytics.getInstance(context)
                .logEvent("http_error", bundleOf(*params.toList().toTypedArray()))
        }

        // 示例：打印所有请求日志
        Log.d("HTTP", "${transaction.method} ${transaction.url} " +
            "→ ${transaction.responseCode} (${transaction.tookMs}ms)")
    }
}
```

### 3. 将拦截器添加到 OkHttp

```kotlin
val monitor = OkHttpMonitorInterceptor.Builder()
    .collector(collector)
    .build()

val client = OkHttpClient.Builder()
    .addInterceptor(monitor)
    .build()
```

---

## KtorMonitor —— Ktor 客户端支持

`ktormonitor-core` 通过 Ktor `ClientPlugin` 为 **Ktor 2.x** 客户端提供等价的监控能力。

### 1. 添加依赖

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.OCNYang.OkHttpMonitor:ktormonitor-core:<latest-version>")
}
```

### 2. 安装插件

```kotlin
val client = HttpClient(Android) {
    install(KtorMonitorPlugin) {
        collector = object : TransactionCollector {
            override fun onResponseReceived(transaction: HttpTransaction) {
                if (transaction.isError) {
                    Log.e("HTTP", "${transaction.method} ${transaction.url} → ${transaction.responseCode} (${transaction.tookMs}ms)")
                }
            }
        }
    }
}
```

### 完整配置示例

```kotlin
val client = HttpClient(Android) {
    install(KtorMonitorPlugin) {
        // 必填
        collector = myCollector

        // 脱敏敏感 header
        redactHeaders("Authorization", "Cookie", "Set-Cookie")

        // Body 捕获上限（默认 250 KB）
        maxContentLength = 250_000L

        // 跳过指定路径
        skipPaths("/health", "/ping")

        // 跳过指定域名
        skipDomains("analytics.google.com")

        // 自定义 body 解码器
        addBodyDecoder(object : BodyDecoder {
            override fun decodeRequest(contentType: String?, body: ByteString): String? = null
            override fun decodeResponse(contentType: String?, body: ByteString): String? = null
        })
    }
}
```

### 与 OkHttpMonitor 的差异

| | `okhttpmonitor-core` | `ktormonitor-core` |
|---|---|---|
| 安装方式 | `OkHttpClient.Builder().addInterceptor(monitor)` | `HttpClient { install(KtorMonitorPlugin) { ... } }` |
| TLS 信息 | 填充 `responseTlsVersion`、`responseCipherSuite` | 始终为 `null`（Ktor API 不暴露） |
| Body 捕获 | 流分流（零额外拷贝） | `call.save()` 先缓冲再双读 |
| 线程 | OkHttp 调用线程 | Ktor 协程上下文 |

---

## 配置选项

所有选项通过 `OkHttpMonitorInterceptor.Builder` 设置：

### collector（必填）

设置接收 HTTP 事务数据的 `TransactionCollector`。

```kotlin
.collector(myCollector)
```

`TransactionCollector` 接口有两个回调：

| 回调 | 调用时机 | 说明 |
|------|---------|------|
| `onRequestSent(transaction)` | 网络请求发出前 | 可选（默认空实现）。此时 transaction 只包含请求数据。 |
| `onResponseReceived(transaction)` | 响应 body 消费完毕后 | **必须实现**。此时 transaction 包含完整的请求 + 响应数据。IOException 时也会调用（`transaction.error` 非空）。 |

> **线程说明**：回调在 OkHttp 的调用线程上执行。如果上报逻辑耗时，建议在回调中切换到后台线程。

### maxContentLength

捕获请求/响应 body 的最大字节数。超过此限制的 body 会被截断。默认值：`250,000`（250 KB）。

```kotlin
.maxContentLength(500_000L)  // 500 KB
```

### redactHeaders

将敏感 header 的值替换为 `**`。header 名称匹配不区分大小写。

```kotlin
.redactHeaders("Authorization", "Cookie", "Set-Cookie", "X-Api-Key")
```

### skipPaths

排除特定 URL 路径的监控。支持精确匹配和正则表达式。

```kotlin
// 精确匹配
.skipPaths("/health", "/ping", "/metrics")

// 正则匹配
.skipPaths(".*\\.(jpg|png|gif|webp)$".toRegex())
```

### skipDomains

排除特定域名的监控。域名以小写形式比较。支持精确匹配和正则表达式。

```kotlin
// 精确匹配
.skipDomains("analytics.google.com", "crashlytics.googleapis.com")

// 正则匹配
.skipDomains(".*\\.googleapis\\.com".toRegex())
```

### addBodyDecoder

添加自定义 body 解码器。解码器按添加顺序执行，使用第一个返回非 null 结果的解码器。内置的 `PlainTextDecoder` 始终作为最后一个解码器。

```kotlin
.addBodyDecoder(object : BodyDecoder {
    override fun decodeRequest(contentType: String?, body: ByteString): String? {
        if (contentType?.contains("protobuf") == true) {
            return MyProtoDecoder.decode(body.toByteArray())
        }
        return null // 传递给下一个解码器
    }

    override fun decodeResponse(contentType: String?, body: ByteString): String? {
        // 同上
        return null
    }
})
```

## 完整配置示例

```kotlin
val monitor = OkHttpMonitorInterceptor.Builder()
    // 必填：设置收集器
    .collector(object : TransactionCollector {
        override fun onResponseReceived(transaction: HttpTransaction) {
            when {
                // 上报所有错误请求
                transaction.isError -> reportError(transaction)
                // 成功请求 10% 采样上报
                Random.nextFloat() < 0.1f -> reportSample(transaction)
            }
        }
    })

    // 隐私保护：脱敏敏感 header
    .redactHeaders("Authorization", "Cookie", "Set-Cookie")

    // Body 捕获：最大 250KB（默认值）
    .maxContentLength(250_000L)

    // 过滤：不监控这些路径
    .skipPaths("/health", "/ping")

    // 过滤：不监控这些域名
    .skipDomains("analytics.google.com")

    .build()

val client = OkHttpClient.Builder()
    .addInterceptor(monitor)
    .build()
```

## HttpTransaction 字段

传递给 collector 的 `HttpTransaction` 对象包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `method` | String? | HTTP 方法（GET, POST 等） |
| `url` | String? | 完整 URL |
| `host` | String? | 主机名 |
| `path` | String? | URL 路径 |
| `scheme` | String? | http 或 https |
| `requestDate` | Long? | 请求时间戳（毫秒） |
| `requestContentType` | String? | 请求 Content-Type |
| `requestPayloadSize` | Long? | 请求 body 大小（字节） |
| `requestHeaders` | String? | 请求头（已脱敏） |
| `requestBody` | String? | 解码后的请求 body 预览 |
| `responseCode` | Int? | HTTP 状态码 |
| `responseMessage` | String? | HTTP 状态消息 |
| `responseDate` | Long? | 响应时间戳（毫秒） |
| `responseContentType` | String? | 响应 Content-Type |
| `responsePayloadSize` | Long? | 响应 body 大小（字节） |
| `responseHeaders` | String? | 响应头（已脱敏） |
| `responseBody` | String? | 解码后的响应 body 预览 |
| `protocol` | String? | 协议（http/1.1, h2 等） |
| `responseTlsVersion` | String? | TLS 版本 |
| `responseCipherSuite` | String? | TLS 加密套件 |
| `tookMs` | Long? | 请求耗时（毫秒） |
| `error` | String? | 错误信息（IOException 时） |
| `status` | Status | `Requested`、`Complete` 或 `Failed` |
| `isError` | Boolean | 有错误或非 2xx 状态码时为 `true` |

### 工具方法

```kotlin
transaction.toMap()                     // 转换为 Map<String, String>
transaction.toMap(maxFieldLength = 90)  // 截断字段长度（如适配 Firebase 参数限制）
transaction.isSuccessful()              // responseCode 在 200..299 范围内返回 true
```

## 致谢

本库基于 [Chucker](https://github.com/ChuckerTeam/chucker) 的优秀工作构建。核心组件包括流分流机制（`TeeSource`）、字节限流（`LimitingSource`）、body 捕获（`ReportingSink`）、纯文本检测以及拦截器架构均改编自 Chucker 的代码。OkHttpMonitor 将这些组件从调试检查工具重新定位为生产环境的监控库。

## 开源协议

```
Copyright 2024 ocnyang

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
