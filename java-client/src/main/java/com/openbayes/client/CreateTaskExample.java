package com.openbayes.client;

import com.openbayes.client.model.JobResult;
import com.openbayes.client.model.OpenBayesException;
import com.openbayes.client.model.SourceCodePolicy;
import com.openbayes.client.model.TaskSpec;

import java.nio.file.Paths;

/**
 * End-to-end demo of the CLI's "create a task" flow, driven entirely by environment variables:
 *
 * <pre>
 *   [token or login] -> createProject -> createSourceCodePolicy -> upload files -> createTask
 * </pre>
 */
public final class CreateTaskExample {

    public static void main(String[] args) {
        Config cfg = Config.fromEnv();

        GraphQLClient gql = new GraphQLClient(cfg.graphqlEndpoint, cfg.token);
        OpenBayesClient client = new OpenBayesClient(gql);

        // 1. Auth: prefer a token; otherwise log in with username/password.
        if (isBlank(cfg.token)) {
            System.out.println("使用账号密码登录获取 token...");
            client.login(cfg.username, cfg.password);
        }

        // 2. Project: reuse if given, else create one.
        String projectId = cfg.projectId;
        if (isBlank(projectId)) {
            System.out.println("创建项目: " + cfg.projectName);
            projectId = client.createProject(cfg.party, cfg.projectName, "created by openbayes-java-client");
            System.out.println("  projectId = " + projectId);
        } else {
            System.out.println("复用项目 projectId = " + projectId);
        }

        // 3. Upload source code -> sourceCodeId.
        System.out.println("获取上传授权 (createSourceCodePolicy)...");
        SourceCodePolicy policy = client.createSourceCodePolicy(cfg.party);
        System.out.println("  上传目标 = " + policy.endpoint() + "/" + policy.path());
        String sourceCodeId = new SourceCodeUploader().upload(policy, Paths.get(cfg.projectDir));
        System.out.println("  sourceCodeId = " + sourceCodeId);

        // 4. Create the task.
        System.out.println("创建 task...");
        TaskSpec spec = new TaskSpec(projectId, cfg.runtime, cfg.resource, sourceCodeId, cfg.command);
        JobResult job = client.createTask(cfg.party, spec);

        System.out.println("✅ 任务创建成功 jobId = " + job.id());
        String frontend = job.link("frontend");
        if (frontend != null) {
            System.out.println("   frontend link 模板 = " + frontend + " (把其中的 id 占位替换成 " + job.id() + " 即为网页地址)");
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
        String projectName;
        String projectDir;
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
            c.projectId = System.getenv("OPENBAYES_PROJECT_ID");
            c.projectName = env("OPENBAYES_PROJECT_NAME", "java-client-demo");
            c.projectDir = require("OPENBAYES_PROJECT_DIR");
            // 默认给一组"当前真实存在"的取值，方便首次直接跑通。
            // 注意：这些名字平台会更新/下线，正式接入请按 README「查合法 resource/runtime」确认。
            c.resource = env("OPENBAYES_RESOURCE", "cpu");
            c.runtime = env("OPENBAYES_RUNTIME", "pytorch-2.8-2204-cpu");
            c.command = require("OPENBAYES_COMMAND");

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
