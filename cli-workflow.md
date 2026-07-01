# 命令行方式：用 `bayes` CLI 跑通流程

对接 OpenBayes 的**两种方式之一**：直接用命令行工具 `bayes` 管理容器化任务——装个工具、敲命令或写脚本就能跑，最快上手，适合人工操作与简单自动化。
（另一种是**程序化 API**，把 OpenBayes 嵌进你自己的程序，见本仓库的 [`java-client/`](java-client/) 与 [`python-client/`](python-client/)。两种方式做的是同一件事。）

## 两类运行形态

| 形态 | 说明 | 适用场景 |
|---|---|---|
| **任务（Task）** | 执行指定命令，运行结束后自动退出并停止计费 | 生产与自动化批处理 |
| **工作空间（Workspace）** | 常驻的交互式环境，可通过 SSH / JupyterLab 操作；需手动关闭，否则持续计费 | 调试与试运行 |

---

## 一、安装与登录（一次性）

```bash
# 安装 / 升级命令行工具（请使用 0.28.2 及以上版本）
pip install -U "openbayes-cli>=0.28.2"

# 登录（Token 获取路径：控制台 → 右上角头像 → 账号设置 → 认证 Token → 复制）
bayes login <Token>
```

## 二、创建并初始化项目

```bash
mkdir my-proj && cd my-proj
bayes gear init my-proj
```

该命令会生成 `openbayes.yaml`，可在其中预设默认参数：

```yaml
env: "<运行环境>"         # 运行环境（镜像），可用 bayes gear env 查询
resource: "rtx-5090"      # 算力规格，可用 bayes gear resource 查询
command: "python run.py"  # 执行命令
data_bindings: []
```

> **命令前需要先加载环境时**：把加载脚本拼在命令最前面，例如
> `source /path/to/setup.sh && python run.py`。
> （容器中 `$HOME` 为 `/root`，每次重启后会重置，仅 `/output` 目录持久保留；因此不建议通过修改 `~/.bashrc` 实现自动加载，统一用「命令前缀」方式最稳妥。）

## 三、启动容器

启动任务（执行结束后自动退出并停止计费）：
```bash
bayes gear run task -f        # 参数读取 openbayes.yaml；-f 表示实时输出日志直至任务结束
```

或启动工作空间（交互式环境）：
```bash
bayes gear run workspace
```

## 四、运行期间的常用命令

| 操作 | 命令 |
|---|---|
| 查看全部容器及状态 | `bayes gear status` |
| 查看某容器详情及 SSH 信息 | `bayes gear info <ID>` |
| 查看日志 | `bayes gear logs <ID>` |
| 在浏览器中打开 | `bayes gear open <ID>` |
| 停止容器 | `bayes gear stop <ID>` |
| 重新启动 | `bayes gear restart <ID>` |
| 下载结果至本地 | `bayes gear download <ID>` |

> `<ID>` 为创建容器时返回的执行编号。

## 五、结果的保存与获取

- 请将结果写入 `/output` 目录（等价于 `/openbayes/home`），该目录为重启后持久保留的工作目录。
- 获取结果可采用以下任一方式：
  1. 执行 `bayes gear download <ID>`，将 `/output` 下载至本地；
  2. 在执行脚本中将结果推送至你自有的对象存储，并按需回调通知你的系统，通知时机与数据落点均由你掌控。

## 六、清理（无需手动删除）

任务执行结束后**无需手动删除容器**。当某容器下没有活跃执行后，平台会进入自动清理流程：若在规定时间内未开启新执行或进行任何操作，系统会将该容器视为无用并自动删除；创建新执行或重启该容器下的工作空间，都会刷新清理计时。数据集（数据仓库）不在自动清理范围，不会被自动删除。

> 提示：自动清理会一并删除该容器的工作目录与日志，请在此之前确认结果已完成下载或转存。

---

## 七、数据量较大时的处理建议

`bayes gear run task` 会将当前目录整体打包作为源代码上传。建议源代码包仅保留体积较小、迭代频繁的脚本，较大的内容按下述方式处理。

### 1. 较大且相对稳定、可复用的数据（模型、大文件等）：使用数据集挂载

数据集仅需上传一次，之后每次运行任务均以挂载方式直接访问，无需重复上传，可支持较大规模的数据：

```bash
bayes data create my-data                    # 创建数据集，返回数据集 ID
bayes data upload <数据集ID> -p ./big_data   # 上传（大文件自动分块，支持断点续传）

# 运行任务时绑定，挂载至 /input0（只读）：
bayes gear run task -e <运行环境> -r rtx-5090 \
  -d <用户名>/my-data/1:/input0 \
  "python run.py --data /input0"
```

亦可写入 `openbayes.yaml`，免去每次指定 `-d`：
```yaml
data_bindings:
  - data: <用户名>/my-data/1
    path: /input0
    type: ro
```

> 绑定引用格式为 `用户名/数据集名/版本号`：上例中 `my-data` 为数据集名，`1` 为版本号（首次上传即为版本 1），`<用户名>` 为你的 OpenBayes 用户名。

### 2. 当前目录中无需上传的文件：使用 `.openbayesignore` 排除

`.openbayesignore` 的写法与 `.gitignore` 一致（位于项目目录下的同名文件，默认已忽略 `.git`、`.DS_Store`）。将大文件、编译产物、数据目录加入其中，可显著减小源代码包体积：
```
data/
build/
*.zip
*.bin
```

---

## 八、自动化流程

上述操作均可脚本化，契合按量付费模式：

```
bayes login  →  bayes gear run task -f  →  （执行脚本内回传结果并回调）  →  任务自动结束并停止计费
```

> 如需更深度的程序化集成（把上述流程嵌进你自己的系统），平台同样提供 GraphQL API 实现等价能力——
> 见本仓库的 [`java-client/`](java-client/) 与 [`python-client/`](python-client/)，两者是与本文一一对应的最小可运行示例。

---

## 附：注意事项

1. 任务（Task）在执行结束后自动停止计费；工作空间（Workspace）需在使用完毕后执行 `bayes gear stop <ID>` 手动关闭，否则将持续计费。
2. 仅 `/output` 目录在重启后持久保留。结果请务必写入该目录，写入其他目录的内容在容器停止或重启后将丢失。

---

## 参考文档

- 配置文件 `openbayes.yaml` 字段说明（`command` / `env` / `resource` / `data_bindings` 等）：<https://openbayes.com/docs/cli/config-file/>
- 命令行管理容器（创建、查看、停止、下载等）：<https://openbayes.com/docs/cli/managing-containers/>
- 算力容器数据绑定与数据集挂载：<https://openbayes.com/docs/gear/databinding/>
- 工作目录 `/output` 说明：<https://openbayes.com/docs/gear/output/>
- 存储持久化机制（哪些目录在重启后保留）：<https://openbayes.com/docs/gear/storage-persistence/>
- 容器自动清理规则：<https://openbayes.com/docs/gear/limitation#容器的自动清理>
- Jupyter 工作空间使用说明：<https://openbayes.com/docs/gear/workspace/>
