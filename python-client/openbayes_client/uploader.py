import os
from typing import List

import boto3
from botocore.config import Config as BotoConfig

from .errors import OpenBayesError
from .models import SourceCodePolicy


def build_s3_client(policy: SourceCodePolicy):
    """Build a boto3 S3 client for the policy endpoint with MinIO-compatible settings.

    CRITICAL: ``request_checksum_calculation="when_required"``. Without it, botocore >= 1.36
    auto-adds a CRC32 checksum and degrades the PUT to ``aws-chunked`` streaming, dropping
    ``Content-Length`` — which MinIO/Ceph reject with ``MissingContentLength`` (or a dropped
    connection behind some proxies). The two checksum args don't exist on botocore < 1.36, so
    we fall back gracefully (older botocore already sends a normal Content-Length PUT).
    """
    kwargs = dict(
        retries={"max_attempts": 10, "mode": "adaptive"},
        connect_timeout=10,
        read_timeout=900,
        s3={"addressing_style": "path"},  # MinIO requires path-style addressing
    )
    try:
        config = BotoConfig(
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
            **kwargs,
        )
    except TypeError:
        config = BotoConfig(**kwargs)

    return boto3.client(
        "s3",
        endpoint_url=policy.endpoint,
        aws_access_key_id=policy.access_key,
        aws_secret_access_key=policy.secret_key,
        config=config,
    )


def _list_files(project_dir: str) -> List[str]:
    files: List[str] = []
    for root, _dirs, names in os.walk(project_dir):
        for name in names:
            files.append(os.path.join(root, name))
    return files


def upload_source_code(policy: SourceCodePolicy, project_dir: str) -> str:
    """Upload every file under ``project_dir`` to the policy's bucket/prefix.

    Returns the ``source_code_id`` (``policy.id``) to hand to ``create_task``.
    """
    if not os.path.isdir(project_dir):
        raise OpenBayesError("project dir not found: {}".format(project_dir))

    bucket = policy.bucket
    prefix = policy.key_prefix
    files = _list_files(project_dir)
    print("共发现 {} 个文件，开始上传到 {}/{} ...".format(len(files), bucket, prefix))

    s3 = build_s3_client(policy)
    for path in files:
        rel = os.path.relpath(path, project_dir).replace(os.sep, "/")
        key = "{}/{}".format(prefix, rel) if prefix else rel
        try:
            s3.upload_file(path, bucket, key)
            print("  上传: " + rel)
        except Exception as e:  # noqa: BLE001 - surface a clear per-file message
            raise OpenBayesError("上传失败: {} - {}".format(rel, e))
    return policy.id
