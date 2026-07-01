# java-client

OpenBayes 建 task 流程的 **Java 参考客户端**。功能与 [`../python-client`](../python-client) 完全对等。

> 📖 通用概念（GraphQL 是什么、名词速查、怎么拿 token、4 个 GraphQL 操作、查合法 resource/runtime、
> 常见报错）都在**仓库根目录的 [../README.md](../README.md)**。本页只讲 Java 这边怎么装、怎么跑、怎么用。

GraphQL 用 JDK 自带的 `java.net.http` 实现（不引入额外 GraphQL 依赖）；S3 上传用 **AWS SDK for Java v2**。

---

## 环境要求

- **JDK 17 或更高**。检查方式：终端运行 `java -version`，看到 `17`（或更高）即可。
- **不需要单独装 Gradle**：本工程自带 Gradle Wrapper（`gradlew`），第一次运行会自动下载对应版本。
  - macOS / Linux 用 `./gradlew ...`
  - Windows 用 `gradlew.bat ...`（下文把 `./gradlew` 换成 `gradlew.bat` 即可）

## 快速开始（跑通一个任务）

### 第 0 步：编译，确认环境没问题
```bash
cd java-client
./gradlew build
```
看到 `BUILD SUCCESSFUL` 就说明 JDK / 依赖都正常。

### 第 1 步：设置环境变量（token 与「账号密码」二选一）
```bash
export OPENBAYES_TOKEN="<你的 token>"
# 或用账号密码（不用自己找 token）：
# export OPENBAYES_USERNAME="<用户名>"
# export OPENBAYES_PASSWORD="<密码>"

export OPENBAYES_PARTY="<你的用户名（组织则填组织名）>"
export OPENBAYES_PROJECT_DIR="/path/to/your/code"     # 要上传的代码目录
export OPENBAYES_COMMAND="python /workspace/main.py"   # 任务启动命令

# 下面几个不填会用默认值，想换见根 README「查合法取值」
# export OPENBAYES_RESOURCE="cpu"
# export OPENBAYES_RUNTIME="pytorch-2.8-2204-cpu"
# export OPENBAYES_PROJECT_ID="<已有项目 id>"          # 可选：复用项目就不新建
```

> 不熟悉环境变量？Windows PowerShell 用 `$env:名字="值"`；在 IDE 里可在「运行配置」的
> Environment variables 里逐条填。这些变量只在当前窗口有效，适合放 token/密码这类敏感信息。

### 第 2 步：运行
```bash
./gradlew run
```
成功后打印任务 id 和网页链接：
```
创建项目: java-client-demo
  projectId = yDCZJRFz7vM
获取上传授权 (createSourceCodePolicy)...
共发现 3 个文件，开始上传到 demo-user/codes/j1dAbCBpSVI ...
  sourceCodeId = j1dAbCBpSVI
✅ 任务创建成功 jobId = t5qvtgqlutf5
   frontend link 模板 = https://openbayes.com/console/demo-user/jobs/t5qvtgqlutf5
```

> ⚠️ 只有最后一步 `createTask` 会**真实创建并计费**一个容器；建项目、拿凭证、上传都不花钱。

## 把它用进你自己的项目

上面的 `CreateTaskExample` 只是个「能直接跑的示范」。真正接入时，在自己的代码里这样调四个方法即可：

```java
// 1) 建一个带鉴权的客户端
GraphQLClient gql = new GraphQLClient("https://openbayes.com/gateway", token);
OpenBayesClient client = new OpenBayesClient(gql);

// 没有 token？用账号密码换一个（会自动设置到 gql 上）
// String token = client.login("用户名", "密码");

String party = "你的用户名";

// 2) 建项目（一次就够，之后可以一直复用这个 projectId）
String projectId = client.createProject(party, "my-project", "说明文字");

// 3) 申请上传凭证，并把某个目录里的所有文件传上去
SourceCodePolicy policy = client.createSourceCodePolicy(party);
String sourceCodeId = new SourceCodeUploader().upload(policy, Paths.get("/path/to/code"));

// 4) 创建任务
TaskSpec spec = new TaskSpec(projectId, "pytorch-2.8-2204-cpu", "cpu",
                             sourceCodeId, "python /workspace/main.py");
JobResult job = client.createTask(party, spec);
System.out.println("任务 id = " + job.id());
```

把 `build.gradle` 里这两个依赖加进你自己的工程即可（GraphQL 部分用的是 JDK 自带的 `java.net.http`，不引入额外库）：
```gradle
implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.2'
implementation platform('software.amazon.awssdk:bom:2.39.6')
implementation 'software.amazon.awssdk:s3'
```

## 克隆模式：复用已准备好的工作空间（零上传）

适合环境/代码已在工作空间里准备好的场景：客户**预先**在一个 workspace 里把代码/依赖调好、放进 `/output`，之后每个 task 直接绑定它——零上传、对用户不暴露 sourceCode。

```bash
export OPENBAYES_TOKEN="<你的 token>"                 # 或账号密码
export OPENBAYES_PARTY="<你的用户名>"
export OPENBAYES_PROJECT_ID="<工作空间所在项目 id>"
export OPENBAYES_WORKSPACE_ID="<预先准备好的工作空间 id>"
export OPENBAYES_RUNTIME="pytorch-2.8-2204-cpu"       # 建议与工作空间一致
export OPENBAYES_RESOURCE="cpu"
export OPENBAYES_COMMAND="python /output/main.py"      # 绑定进来的代码就在 /output

./gradlew runWorkspace
```

用进你自己的代码：
```java
WorkspaceTaskSpec spec = new WorkspaceTaskSpec(
        projectId, workspaceId, "pytorch-2.8-2204-cpu", "cpu", "python /output/main.py");
JobResult job = client.createTaskFromWorkspace(party, spec);
```
内部：`createJob` 不带 code，改把 `<party>/jobs/<workspaceId>/output` **读写绑定到 `/output`**。
workspace 的复用绑定规则：只读绑定到 `/input*`，或读写绑定到 `/output`；克隆用后者。内容由客户在 workspace 内保存到 `/output`。
大模型 / 数据集才另挂 `/input0`。概念详见根 README「克隆模式 vs 上传模式」。

## 代码结构（每个类干一件事，与 Python 版一一对应）

| 类 | 职责 |
|----|------|
| `GraphQLClient` | 最底层：往 OpenBayes 发请求、带上 token/Origin、解析返回。一般你不用直接碰它。 |
| `OpenBayesClient` | 业务方法：`login` / `createProject` / `createSourceCodePolicy` / `createTask`。**你主要用这个。** |
| `SourceCodeUploader` | 把代码目录上传到对象存储（含一处 **MinIO 必需的关键配置**，见下）。 |
| `CreateTaskExample` | `main()` 示范：读环境变量，把上面几步串起来。 |
| `model/*` | 几个简单的数据类：`SourceCodePolicy` / `JobResult` / `TaskSpec` / `OpenBayesException`。 |

## ⚠️ 一处千万别动的配置

`SourceCodeUploader.buildClient` 里：
```java
.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
```
**别删这两行，也别改成 `WHEN_SUPPORTED`。** 原因见根 README「一处千万别动的配置」：AWS SDK 新版默认会让
上传丢掉 `Content-Length`，被 MinIO 拒收或掐断连接。另外上传务必用 `RequestBody.fromFile(...)`（自带
Content-Length），别用没有长度的输入流。

## 测试
```bash
./gradlew test
```
覆盖纯逻辑：路径解析、GraphQL 请求体构造、错误信息格式化。
涉及真实网络的部分（HTTP / S3）由「快速开始」那次真实建任务来验证。
