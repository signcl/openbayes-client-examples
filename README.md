# openbayes-client-examples

对接 OpenBayes（在云端算力上运行你的代码 / 任务）的参考资料。有**两种对接方式**，按需求挑一种：

| 对接方式 | 是什么 | 适合 | 入口 |
|---------|--------|------|------|
| **命令行 `bayes` CLI** | 装个命令行工具，敲命令 / 写脚本 | 人工操作、快速上手、简单自动化 | [`cli-workflow.md`](cli-workflow.md) |
| **程序化 API** | 把 OpenBayes 嵌进你自己的程序 | 产品级集成、深度定制 | 下面的 `java-client/` · `python-client/` |

两种方式做的是同一件事（建项目 → 跑任务 → 取结果），彼此一一对应。

程序化 API 提供两个**功能对等**的参考客户端：

| 目录 | 技术栈 | 怎么用 |
|------|--------|--------|
| [`java-client/`](java-client/) | Java 17 + Gradle | 见 [java-client/README.md](java-client/README.md) |
| [`python-client/`](python-client/) | Python 3.8+ + boto3 | 见 [python-client/README.md](python-client/README.md) |

**本页往下讲 API 方式的通用概念**；命令行方式独立成篇，见 [`cli-workflow.md`](cli-workflow.md)。具体怎么装、怎么跑、怎么嵌进你的工程，进对应子目录看它的 README。

```
[拿到 token] ─► 建项目 ─► 申请上传凭证 ─► 把代码文件传上去 ─► 创建任务
createProject     createSourceCodePolicy      (S3 上传)        createJob
```

> ✅ 两个客户端都已真实跑通验证：各自建出一个 CPU 任务并跑到 `SUCCEEDED`。

---

## 克隆模式 vs 上传模式

按客户的情况选一条：

**① 克隆模式（推荐）**（环境/代码已在工作空间里准备好，想省去每次上传）
客户**预先**在一个 workspace 里把代码、依赖调好、**在 workspace 内部保存到 `/output`**；之后每个 task **直接读写绑定这个工作空间的 `/output`**，命令里写 `python /output/main.py` 就能跑。
- **零上传，且对用户完全不暴露 sourceCode**——他面对的只有「我的工作空间 id + 要跑的命令」。
- 特别大的模型 / 数据集才另挂到 `/input0`。
- 方法：`createTaskFromWorkspace` / `create_task_from_workspace`。见各 client README 的「克隆模式」。

> **准备 workspace 的步骤**（一次性）：
> 1. 开一个 workspace；
> 2. 在里面（网页终端 / Jupyter / SSH）把代码、依赖放到 `/output`；
> 3. 停掉 workspace；
> 4. 之后拿它的 id 反复起克隆任务即可。
>
> （`/output` 只能由 workspace 自己写，所以必须在 workspace 内部准备；准备好停掉后再复用。已实测：这样 Java / Python 客户端的克隆任务都能读到并执行其中的代码。）

> 底层：克隆模式的 `createJob` **不带 `newTask.code`**，改用
> `dataBindings: [ <party>/jobs/<workspaceId>/output : /output : READ_WRITE ]`。
> workspace 的复用绑定规则：**只读绑定到 `/input*`，或读写绑定到 `/output`**——克隆用后者（代码挂 `/output`），大模型/数据集用前者（挂 `/input*`）。
> 已实测：任务 `sourceCode` 为空也能跑。

**② 上传模式**（代码会变、每次都要带上最新代码）
`login → createProject → createSourceCodePolicy → 逐文件上传 → createJob(带 code)`
方法：`createTask` / `create_task`。见各 client README 的「快速开始」。

---

## 名词速查

| 名词 | 一句话解释 |
|------|-----------|
| **GraphQL** | OpenBayes 对外的接口风格。你只要往一个固定网址 POST 一段 JSON 就行，客户端已封装好；想了解见下「GraphQL 30 秒入门」。 |
| **token** | 你的身份令牌（一长串字符）。每个请求都带上它，服务器才知道是你。怎么拿见下面「怎么拿 token」。 |
| **party / userId** | 「在谁名下操作」。个人账号就填**你的用户名**；组织账号填**组织名**。 |
| **task（脚本执行）** | 一次性的云端任务：上传代码 → 在指定算力+运行时里执行你的命令 → 跑完结束。 |
| **runtime（运行时）** | 容器里预装的环境镜像，比如 `pytorch-2.8-2204-cpu`。 |
| **resource（算力）** | 用什么机器跑，比如 `cpu`（最便宜）、`rtx-4090`（GPU）。 |
| **bucket / 前缀** | 代码文件上传到的对象存储位置，客户端会自动算好，你不用关心。 |

---

## GraphQL 30 秒入门

OpenBayes 的接口用的是 **GraphQL**。对接它你只需记住三件事：

1. **只有一个网址**：所有请求都 POST 到同一个地址 `https://openbayes.com/gateway`。
2. **请求体是一段 JSON**：用 `query` 写「要做什么、想要哪些返回字段」，`variables` 传参数。
   ```json
   { "query": "一段 GraphQL 语句", "variables": { "参数名": "参数值" } }
   ```
3. **返回也是固定形状的 JSON**：成功在 `data` 里，出错在 `errors` 里。
   ```json
   { "data":   { ... } }                     // 成功
   { "errors": [ { "message": "..." } ] }    // 失败
   ```

语句分两种（看开头单词）：`query` = **读**（查算力、查状态）；`mutation` = **写 / 创建**（建项目、建任务、登录）。

**每个请求必须带两个 HTTP 头，缺一不可：**
- `Authorization: Bearer <token>` —— 你的身份。
- `Origin: https://openbayes.com/gateway` —— **少了它会被直接拒：`403 Invalid CORS request`**（最容易忘的一点）。

**动手试一下**（能返回用户名就说明 token 有效，只读、免费）：
```bash
curl -s https://openbayes.com/gateway \
  -H "Authorization: Bearer <你的 token>" \
  -H "Origin: https://openbayes.com/gateway" \
  -H "Content-Type: application/json" \
  -d '{"query":"query { me { username email } }"}'
# 成功返回：{"data":{"me":{"username":"你的用户名","email":"..."}}}
```

> 两个客户端里的 GraphQL 层都已把这些（拼 JSON、带上这两个头、解析 `data`/`errors`）封装好，
> `Origin` 也自动带上；你平时只调高层方法即可。这一节是帮你看懂下面的 GraphQL 片段、以及自己用 curl 调试时不踩 403。

---

## 怎么拿 token

两种方式，**推荐第二种**（省去手动找 token）：

1. **网页方式**：登录 OpenBayes 控制台，在账号设置里找到「访问令牌 / token」复制出来。
2. **账号密码方式（推荐）**：不用自己找 token，把用户名密码交给程序，它会用 `login` 接口自动换 token。
   对应各客户端的 `login(...)` 方法，或直接设 `OPENBAYES_USERNAME` / `OPENBAYES_PASSWORD` 环境变量。

---

## 代码里调用的 4 个 GraphQL

整个建任务流程，客户端只调用 **4 个 GraphQL 操作**（Java / Python 方法名略有差异）：

| GraphQL 操作 | Java 方法 | Python 方法 | 作用 |
|-------------|-----------|-------------|------|
| `mutation Login` | `login()` | `login()` | 用账号密码换 token（只在不直接给 token 时用） |
| `mutation CreateProject` | `createProject()` | `create_project()` | 建项目（任务挂在项目下，可反复用） |
| `mutation CreateSourceCodePolicy` | `createSourceCodePolicy()` | `create_source_code_policy()` | 申请一组临时 S3 凭证 + 上传位置 |
| `mutation CreateJob` | `createTask()` | `create_task()` | 真正创建任务（**这一步会计费**） |

> 上传文件那一步（把代码 PUT 到 S3）**不是 GraphQL**，是对象存储操作，见下面「千万别动的配置」。
> 另外 `me`、`normalClusterResources`、`normalClusterRuntimes` 这几个查询**代码并不调用**，只是给你 curl 自测 / 查合法取值用。

<details>
<summary><b>点开看四个操作的原始 GraphQL 与参数</b></summary>

```graphql
# 1) 登录换 token
mutation Login($username: String!, $password: String!) {
  login(username: $username, password: $password) { email token username }
}

# 2) 建项目 → 返回 id 即 projectId
mutation CreateProject($userId: String!, $name: String!, $description: String, $tagNames: [TagInput]) {
  createProject(userId: $userId, name: $name, description: $description, tagNames: $tagNames) {
    id name links { name value }
  }
}

# 3) 申请临时 S3 凭证。storageType 固定传 "TEMPORARY"
#    返回：id(=sourceCodeId) / endpoint / accessKey / secretKey / path(=bucket/前缀)
#    注意：只有 accessKey/secretKey，没有 session token
mutation CreateSourceCodePolicy($userId: String!, $storageType: StorageType!) {
  createSourceCodePolicy(userId: $userId, storageType: $storageType) {
    id endpoint accessKey secretKey path
  }
}

# 4) 建任务 → 返回 id 即 jobId，links 含 frontend 控制台链接
mutation CreateJob($userId: String!, $input: CreateJobInput) {
  createJob(userId: $userId, input: $input) { id links { name value } }
}
```

`CreateJob` 的 `input`（单机 task）：
```json
{
  "mode": "TASK",
  "projectId": "<projectId>",
  "runtime": "<runtime>",
  "resource": "<resource>",
  "newTask": { "code": "<sourceCodeId>", "command": "<容器里执行的命令>" },
  "tagNames": [ { "name": "BUSINESS_CHANNEL_ML" } ]
}
```
</details>

---

## 查合法的 resource / runtime 取值

`resource` / `runtime` 必须是平台上**真实存在且未废弃**的名字，否则建任务会报参数错误。
用这两个查询列出可选项（和所有请求一样，带 `Authorization: Bearer <token>` 和 `Origin` 两个头）：

```graphql
# 可用算力：gpuResource=false 的就是 CPU 规格（如 cpu / cpu-large）
query Resources($partyId: String) {
  normalClusterResources(partyId: $partyId) { name verboseName gpuResource }
}

# 可用运行时：挑 deprecated=false 的（如 pytorch-2.8-2204-cpu）
query Runtimes($partyId: String) {
  normalClusterRuntimes(partyId: $partyId) { name framework version device deprecated }
}
```

---

## ⚠️ 一处千万别动的配置（两个客户端都有）

上传文件时，S3 客户端有一处关键设置 **别删、别改**：

- Java（`SourceCodeUploader`）：`requestChecksumCalculation(WHEN_REQUIRED)`
- Python（`uploader.build_s3_client`）：`request_checksum_calculation="when_required"`

原因（看不懂可跳过）：AWS SDK 新版（Java v2 / Python botocore ≥ 1.36）默认会自动给上传加一种校验，
导致请求丢掉 `Content-Length` 头。OpenBayes 用的 MinIO 存储不认这种请求，会**直接拒收或把连接掐断**
（表现为上传报 `MissingContentLength`、或卡住不动/连接被断）。上面这行就是把这个默认行为关掉。

---

## 常见报错与含义

| 报错 / 现象 | 多半是什么原因 | 怎么办 |
|------------|--------------|--------|
| `缺少鉴权...` | token 和账号密码都没设 | 设 `OPENBAYES_TOKEN`，或设 `OPENBAYES_USERNAME` + `OPENBAYES_PASSWORD` |
| `缺少必需的环境变量: XXX` | 少设了某个必填项 | 按提示补上那个环境变量 |
| `login failed` | 账号或密码不对 | 核对用户名/密码 |
| `createJob failed` 且提示 runtime/resource 不对 | 名字写错或已下线 | 用上面「查合法取值」列出可用名字再填 |
| 上传报 `MissingContentLength`，或上传卡住/连接被断 | 改动/删掉了 checksum 配置 | 恢复「千万别动的配置」 |
| 上传报 `AccessDenied` / `SignatureDoesNotMatch` | 凭证过期或被改动 | 重新 `createSourceCodePolicy` 拿一份新凭证再传 |
| 自己用 curl/HTTP 调时返回 `403 Invalid CORS request` | 忘了带 `Origin` 头 | 加上 `-H "Origin: https://openbayes.com/gateway"`（客户端代码已自动带） |

---

## 参考文档

OpenBayes 的 GraphQL API 没有公开文档——**这个仓库本身就是 API 级对接的参考**。
API 背后的概念（task / workspace / dataset / runtime / resource / 数据绑定）与命令行工具一致，可查官方文档：

| 链接 | 说明 |
|------|------|
| [OpenBayes 文档首页](https://openbayes.com/docs/) | 平台总文档 |
| [命令行工具 CLI](https://openbayes.com/docs/cli/) | 本客户端复刻的就是 CLI 的行为，概念以此为准 |
| [配置文件 openbayes.yaml](https://openbayes.com/docs/cli/config-file/) | `resource` / `runtime` / `data_bindings` 等字段说明，对应 `createJob` 的 input |
| [控制台](https://openbayes.com/console/) | 看任务 / 日志、拿 token、准备工作空间的地方 |
