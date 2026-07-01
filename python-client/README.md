# python-client

OpenBayes 建 task 流程的 **Python 参考客户端**。功能与 [`../java-client`](../java-client) 完全对等。

> 📖 通用概念（GraphQL 是什么、名词速查、怎么拿 token、4 个 GraphQL 操作、查合法 resource/runtime、
> 常见报错）都在**仓库根目录的 [../README.md](../README.md)**。本页只讲 Python 这边怎么装、怎么跑、怎么用。

GraphQL 用 Python **标准库**（`urllib` + `json`）实现，不引入额外依赖；S3 上传用 **boto3**。

---

## 环境要求
- Python 3.8+
- 唯一外部依赖：`boto3`

```bash
cd python-client
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## 快速开始（跑通一个任务）

先设置环境变量（token 与「账号密码」二选一）：

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

然后运行：
```bash
python create_task_example.py
```
成功后打印任务 id 和网页链接：
```
创建项目: python-client-demo
  projectId = yDCZJRFz7vM
获取上传授权 (createSourceCodePolicy)...
共发现 3 个文件，开始上传到 demo-user/codes/GYGnz0zQRHH ...
  sourceCodeId = GYGnz0zQRHH
✅ 任务创建成功 jobId = t5qvtgqlutf5
   https://openbayes.com/console/demo-user/jobs/t5qvtgqlutf5
```

> ⚠️ 只有最后一步 `create_task` 会**真实创建并计费**一个容器；建项目、拿凭证、上传都不花钱。

## 把它用进你自己的项目

```python
from openbayes_client import GraphQLClient, OpenBayesClient, TaskSpec, upload_source_code

# 1) 建一个带鉴权的客户端
gql = GraphQLClient("https://openbayes.com/gateway", token)
client = OpenBayesClient(gql)

# 没有 token？用账号密码换一个（会自动设置到 gql 上）
# token = client.login("用户名", "密码")

party = "你的用户名"

# 2) 建项目（一次就够，之后可一直复用这个 project_id）
project_id = client.create_project(party, "my-project", "说明文字")

# 3) 申请上传凭证，并把某个目录里的所有文件传上去
policy = client.create_source_code_policy(party)
source_code_id = upload_source_code(policy, "/path/to/code")

# 4) 创建任务
job = client.create_task(party, TaskSpec(
    project_id=project_id, runtime="pytorch-2.8-2204-cpu", resource="cpu",
    source_code_id=source_code_id, command="python /workspace/main.py"))
print("任务 id =", job.id)
```

把 `openbayes_client/` 这个包拷进你的工程即可（或按需只拿其中几个文件）。

## 克隆模式：复用已准备好的工作空间（零上传）

适合小白：客户**预先**在一个 workspace 里把代码/依赖调好、放进 `/output`，之后每个 task 直接绑定它——零上传、对用户不暴露 sourceCode。

```bash
export OPENBAYES_TOKEN="<你的 token>"                 # 或账号密码
export OPENBAYES_PARTY="<你的用户名>"
export OPENBAYES_PROJECT_ID="<工作空间所在项目 id>"
export OPENBAYES_WORKSPACE_ID="<预先准备好的工作空间 id>"
export OPENBAYES_RUNTIME="pytorch-2.8-2204-cpu"       # 建议与工作空间一致
export OPENBAYES_RESOURCE="cpu"
export OPENBAYES_COMMAND="python /output/main.py"      # 绑定进来的代码就在 /output

python run_workspace_task_example.py
```

用进你自己的代码：
```python
from openbayes_client import WorkspaceTaskSpec

spec = WorkspaceTaskSpec(
    project_id=project_id, workspace_id=workspace_id,
    runtime="pytorch-2.8-2204-cpu", resource="cpu", command="python /output/main.py")
job = client.create_task_from_workspace(party, spec)
```
内部：`createJob` 不带 code，改把 `<party>/jobs/<workspace_id>/output` **读写绑定到 `/output`**。
workspace 的复用绑定规则：只读绑定到 `/input*`，或读写绑定到 `/output`；克隆用后者。内容由客户在 workspace 内保存到 `/output`。
大模型 / 数据集才另挂 `/input0`。概念详见根 README「两种用法」。

## 代码结构（与 Java 版一一对应）

| 文件 | 职责 |
|------|------|
| `openbayes_client/graphql_client.py` | 最底层：用 `urllib` 发 GraphQL 请求、带 token/Origin、解析返回。一般不用直接碰。 |
| `openbayes_client/client.py` | 业务方法：`login` / `create_project` / `create_source_code_policy` / `create_task`。**你主要用这个。** |
| `openbayes_client/uploader.py` | 把代码目录上传到对象存储（含一处 **MinIO 必需的关键配置**，见下）。 |
| `openbayes_client/models.py` | 数据类：`SourceCodePolicy` / `JobResult` / `TaskSpec`。 |
| `create_task_example.py` | 示范 `main()`：读环境变量，把上面几步串起来。 |

## ⚠️ 一处千万别动的配置

`openbayes_client/uploader.py` 的 `build_s3_client` 里：
```python
config = BotoConfig(
    request_checksum_calculation="when_required",
    response_checksum_validation="when_required",
    ...
)
```
**别删这两行**。原因见根 README「一处千万别动的配置」：botocore ≥ 1.36 默认会让上传丢掉
`Content-Length`，被 MinIO 拒收或掐断连接。代码里已用 `try/except TypeError` 兼容老版本 botocore（< 1.36 没有这两个参数，也不需要）。

## 测试
```bash
python -m unittest discover -s tests -v
```
覆盖纯逻辑：路径解析、GraphQL 请求体构造、错误信息格式化。
涉及真实网络的部分（HTTP / S3）由「快速开始」那次真实建任务来验证。
