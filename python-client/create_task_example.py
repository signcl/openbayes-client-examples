"""End-to-end demo of the CLI's "create a task" flow, driven by environment variables:

    [token or login] -> createProject -> createSourceCodePolicy -> upload files -> createTask

Run it from the python-client/ directory:  python create_task_example.py
"""
import os
import sys

from openbayes_client import (
    GraphQLClient,
    OpenBayesClient,
    OpenBayesError,
    TaskSpec,
    upload_source_code,
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
    project_id = _env("OPENBAYES_PROJECT_ID")
    project_name = _env("OPENBAYES_PROJECT_NAME", "python-client-demo")
    project_dir = _require("OPENBAYES_PROJECT_DIR")
    resource = _env("OPENBAYES_RESOURCE", "cpu")
    # 默认给一组"当前真实存在"的取值，方便首次直接跑通。
    # 注意：这些名字平台会更新/下线，正式接入请按 README「查合法 resource/runtime」确认。
    runtime = _env("OPENBAYES_RUNTIME", "pytorch-2.8-2204-cpu")
    command = _require("OPENBAYES_COMMAND")

    if not token and not (username and password):
        raise OpenBayesError(
            "缺少鉴权: 请设置 OPENBAYES_TOKEN，或同时设置 OPENBAYES_USERNAME 和 OPENBAYES_PASSWORD"
        )

    gql = GraphQLClient(endpoint, token)
    client = OpenBayesClient(gql)

    # 1. Auth: prefer a token; otherwise log in with username/password.
    if not token:
        print("使用账号密码登录获取 token...")
        client.login(username, password)

    # 2. Project: reuse if given, else create one.
    if not project_id:
        print("创建项目: " + project_name)
        project_id = client.create_project(party, project_name, "created by openbayes python client example")
        print("  projectId = " + project_id)
    else:
        print("复用项目 projectId = " + project_id)

    # 3. Upload source code -> source_code_id.
    print("获取上传授权 (createSourceCodePolicy)...")
    policy = client.create_source_code_policy(party)
    print("  上传目标 = {}/{}".format(policy.endpoint, policy.path))
    source_code_id = upload_source_code(policy, project_dir)
    print("  sourceCodeId = " + source_code_id)

    # 4. Create the task.
    print("创建 task...")
    job = client.create_task(party, TaskSpec(project_id, runtime, resource, source_code_id, command))
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
