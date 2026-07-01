"""Clone mode: run a task that reuses a workspace you prepared earlier — no code upload.

The customer prepares a workspace (debugs code, puts it under /output), then every task just
binds that workspace's output and runs a command. No source code, no upload.

    [token or login] -> create_task_from_workspace(bind workspace /output -> /output, run command)

Run it from the python-client/ directory:  python run_workspace_task_example.py
"""
import os
import sys

from openbayes_client import (
    GraphQLClient,
    OpenBayesClient,
    OpenBayesError,
    WorkspaceTaskSpec,
)


def _env(name, fallback=None):
    v = os.environ.get(name)
    return v if v else fallback


def _require(name):
    v = os.environ.get(name)
    if not v:
        raise OpenBayesError("缺少必需的环境变量: " + name)
    return v


def main():
    endpoint = _env("OPENBAYES_GRAPHQL", "https://openbayes.com/gateway")
    token = _env("OPENBAYES_TOKEN")
    username = _env("OPENBAYES_USERNAME")
    password = _env("OPENBAYES_PASSWORD")
    party = _require("OPENBAYES_PARTY")
    project_id = _require("OPENBAYES_PROJECT_ID")      # 工作空间所在的项目
    workspace_id = _require("OPENBAYES_WORKSPACE_ID")  # 客户预先准备好的工作空间 id
    resource = _env("OPENBAYES_RESOURCE", "cpu")
    runtime = _require("OPENBAYES_RUNTIME")            # 建议与工作空间一致
    command = _require("OPENBAYES_COMMAND")            # 如 python /output/main.py

    if not token and not (username and password):
        raise OpenBayesError(
            "缺少鉴权: 请设置 OPENBAYES_TOKEN，或同时设置 OPENBAYES_USERNAME 和 OPENBAYES_PASSWORD"
        )

    gql = GraphQLClient(endpoint, token)
    client = OpenBayesClient(gql)

    if not token:
        print("使用账号密码登录获取 token...")
        client.login(username, password)

    print("创建 task（绑定已准备好的工作空间 {} 的 /output，零上传）...".format(workspace_id))
    spec = WorkspaceTaskSpec(
        project_id=project_id,
        workspace_id=workspace_id,
        runtime=runtime,
        resource=resource,
        command=command,
    )
    job = client.create_task_from_workspace(party, spec)

    print("✅ 任务创建成功 jobId = " + job.id)
    frontend = job.link("frontend")
    if frontend:
        print("   " + frontend)


if __name__ == "__main__":
    try:
        main()
    except OpenBayesError as e:
        print("错误: {}".format(e), file=sys.stderr)
        sys.exit(1)
