package com.openbayes.client;

import com.openbayes.client.model.JobResult;
import com.openbayes.client.model.OpenBayesException;
import com.openbayes.client.model.WorkspaceTaskSpec;

/**
 * Clone mode: run a task that reuses a workspace you prepared earlier — no code upload.
 *
 * <p>The customer prepares a workspace (debugs code, puts it under {@code /output}), then every
 * task just binds that workspace's output and runs a command. No source code, no upload.
 */
public final class RunWorkspaceTaskExample {

    public static void main(String[] args) {
        Config cfg = Config.fromEnv();

        GraphQLClient gql = new GraphQLClient(cfg.graphqlEndpoint, cfg.token);
        OpenBayesClient client = new OpenBayesClient(gql);

        if (isBlank(cfg.token)) {
            System.out.println("使用账号密码登录获取 token...");
            client.login(cfg.username, cfg.password);
        }

        System.out.println("创建 task（绑定已准备好的工作空间 " + cfg.workspaceId + " 的 /output，零上传）...");
        WorkspaceTaskSpec spec = new WorkspaceTaskSpec(
                cfg.projectId, cfg.workspaceId, cfg.runtime, cfg.resource, cfg.command);
        JobResult job = client.createTaskFromWorkspace(cfg.party, spec);

        System.out.println("✅ 任务创建成功 jobId = " + job.id());
        String frontend = job.link("frontend");
        if (frontend != null) {
            System.out.println("   " + frontend);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Reads configuration from environment variables and validates required ones. */
    static final class Config {
        String graphqlEndpoint;
        String token;
        String username;
        String password;
        String party;
        String projectId;
        String workspaceId;
        String resource;
        String runtime;
        String command;

        static Config fromEnv() {
            Config c = new Config();
            c.graphqlEndpoint = env("OPENBAYES_GRAPHQL", "https://openbayes.com/gateway");
            c.token = System.getenv("OPENBAYES_TOKEN");
            c.username = System.getenv("OPENBAYES_USERNAME");
            c.password = System.getenv("OPENBAYES_PASSWORD");
            c.party = require("OPENBAYES_PARTY");
            c.projectId = require("OPENBAYES_PROJECT_ID");        // 工作空间所在的项目
            c.workspaceId = require("OPENBAYES_WORKSPACE_ID");    // 客户预先准备好的工作空间 id
            c.resource = env("OPENBAYES_RESOURCE", "cpu");
            c.runtime = require("OPENBAYES_RUNTIME");             // 建议与工作空间一致
            c.command = require("OPENBAYES_COMMAND");             // 如 python /output/main.py

            boolean hasToken = c.token != null && !c.token.isBlank();
            boolean hasLogin = c.username != null && !c.username.isBlank()
                    && c.password != null && !c.password.isBlank();
            if (!hasToken && !hasLogin) {
                throw new OpenBayesException(
                        "缺少鉴权: 请设置 OPENBAYES_TOKEN，或同时设置 OPENBAYES_USERNAME 和 OPENBAYES_PASSWORD");
            }
            return c;
        }

        private static String env(String name, String fallback) {
            String v = System.getenv(name);
            return (v == null || v.isBlank()) ? fallback : v;
        }

        private static String require(String name) {
            String v = System.getenv(name);
            if (v == null || v.isBlank()) {
                throw new OpenBayesException("缺少必需的环境变量: " + name);
            }
            return v;
        }
    }
}
