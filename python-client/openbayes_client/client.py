from typing import Any, Dict

from .errors import OpenBayesError
from .graphql_client import GraphQLClient
from .models import JobResult, SourceCodePolicy, TaskSpec, WorkspaceTaskSpec


class OpenBayesClient:
    """High-level OpenBayes operations needed to create a task: login, project creation,
    source-code upload policy, and job creation. Each method maps to one GraphQL mutation and
    mirrors what the ``bayes`` CLI sends. The file upload itself lives in :mod:`uploader`.
    """

    def __init__(self, gql: GraphQLClient) -> None:
        self.gql = gql

    def login(self, username: str, password: str) -> str:
        """Log in with username/password; returns the token and sets it on the client."""
        query = """
        mutation Login($username: String!, $password: String!) {
          login(username: $username, password: $password) { email token username }
        }
        """
        data = self.gql.exec(query, {"username": username, "password": password})
        login = data.get("login")
        if not login or not login.get("token"):
            raise OpenBayesError("login failed: unexpected response")
        token = login["token"]
        self.gql.set_token(token)
        return token

    def create_project(self, user_id: str, name: str, description: str) -> str:
        """Create a project; returns its id. A project can be reused across many jobs."""
        query = """
        mutation CreateProject($userId: String!, $name: String!, $description: String, $tagNames: [TagInput]) {
          createProject(userId: $userId, name: $name, description: $description, tagNames: $tagNames) {
            id name links { name value }
          }
        }
        """
        data = self.gql.exec(
            query,
            {"userId": user_id, "name": name, "description": description, "tagNames": []},
        )
        project = data.get("createProject")
        if not project or not project.get("id"):
            raise OpenBayesError("createProject failed: unexpected response")
        return project["id"]

    def create_source_code_policy(self, user_id: str) -> SourceCodePolicy:
        """Request temporary S3 upload credentials (TEMPORARY storage type)."""
        query = """
        mutation CreateSourceCodePolicy($userId: String!, $storageType: StorageType!) {
          createSourceCodePolicy(userId: $userId, storageType: $storageType) {
            id endpoint accessKey secretKey path
          }
        }
        """
        data = self.gql.exec(query, {"userId": user_id, "storageType": "TEMPORARY"})
        n = data.get("createSourceCodePolicy")
        if not n or not n.get("id"):
            raise OpenBayesError("createSourceCodePolicy failed: unexpected response")
        return SourceCodePolicy(
            id=n["id"],
            endpoint=n["endpoint"],
            access_key=n["accessKey"],
            secret_key=n["secretKey"],
            path=n["path"],
        )

    def create_task(self, user_id: str, spec: TaskSpec) -> JobResult:
        """Create a single-node TASK job from an uploaded source code id (upload mode)."""
        input_data: Dict[str, Any] = {
            "mode": "TASK",
            "projectId": spec.project_id,
            "runtime": spec.runtime,
            "resource": spec.resource,
            "newTask": {"code": spec.source_code_id, "command": spec.command},
            "tagNames": [{"name": "BUSINESS_CHANNEL_ML"}],
        }
        return self._create_job(user_id, input_data)

    def create_task_from_workspace(
        self,
        user_id: str,
        spec: WorkspaceTaskSpec,
        mount_path: str = "/output",
        binding_auth: str = "READ_WRITE",
    ) -> JobResult:
        """Create a TASK that reuses a prepared workspace's output (clone mode, no upload).

        Binds ``<user_id>/jobs/<workspace_id>/output`` at ``mount_path`` (default ``/output``,
        the clone convention; big models/datasets would instead go to ``/input0``). The task
        carries **no sourceCode** — the prepared code/env comes entirely from the binding.
        """
        binding = {
            "name": "{}/jobs/{}/output".format(user_id, spec.workspace_id),
            "path": mount_path,
            "bindingAuth": binding_auth,
        }
        input_data: Dict[str, Any] = {
            "mode": "TASK",
            "projectId": spec.project_id,
            "runtime": spec.runtime,
            "resource": spec.resource,
            "newTask": {"command": spec.command},  # note: no "code"
            "dataBindings": [binding],
            "tagNames": [{"name": "BUSINESS_CHANNEL_ML"}],
        }
        return self._create_job(user_id, input_data)

    def _create_job(self, user_id: str, input_data: Dict[str, Any]) -> JobResult:
        """Send the CreateJob mutation with a prebuilt input and parse the result."""
        query = """
        mutation CreateJob($userId: String!, $input: CreateJobInput) {
          createJob(userId: $userId, input: $input) {
            id links { name value }
          }
        }
        """
        data = self.gql.exec(query, {"userId": user_id, "input": input_data})
        job = data.get("createJob")
        if not job or not job.get("id"):
            raise OpenBayesError("createJob failed: unexpected response")
        links = {l.get("name"): l.get("value") for l in (job.get("links") or [])}
        return JobResult(id=job["id"], links=links)
