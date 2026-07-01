from dataclasses import dataclass
from typing import Dict, Optional, Tuple


@dataclass(frozen=True)
class SourceCodePolicy:
    """Temporary upload credentials returned by the ``createSourceCodePolicy`` mutation.

    ``path`` is ``<bucket>/<key_prefix>``, e.g. ``demo-user/codes/WFkMTSaBui2``.
    There is no session token — ``access_key``/``secret_key`` is a plain pair used for
    standard SigV4 signing. ``id`` is the ``source_code_id`` to pass to ``createJob``.
    """

    id: str
    endpoint: str
    access_key: str
    secret_key: str
    path: str

    @staticmethod
    def split_path(path: str) -> Tuple[str, str]:
        """Split "bucket/a/b/c" (with optional leading slash) into ("bucket", "a/b/c")."""
        p = path[1:] if path.startswith("/") else path
        i = p.find("/")
        if i < 0:
            return (p, "")
        return (p[:i], p[i + 1:])

    @property
    def bucket(self) -> str:
        return self.split_path(self.path)[0]

    @property
    def key_prefix(self) -> str:
        return self.split_path(self.path)[1]


@dataclass(frozen=True)
class JobResult:
    """Result of ``createJob``: the job id and its named links (e.g. "frontend")."""

    id: str
    links: Dict[str, str]

    def link(self, name: str) -> Optional[str]:
        return self.links.get(name)


@dataclass(frozen=True)
class TaskSpec:
    """Inputs needed to create a single-node TASK job (upload mode)."""

    project_id: str
    runtime: str
    resource: str
    source_code_id: str
    command: str


@dataclass(frozen=True)
class WorkspaceTaskSpec:
    """Inputs to create a TASK that reuses a prepared workspace (clone mode, no upload).

    The task carries no sourceCode; it binds ``<party>/jobs/<workspace_id>/output`` so the
    code/env prepared in that workspace's ``/output`` is available in the container.
    """

    project_id: str
    workspace_id: str
    runtime: str
    resource: str
    command: str
