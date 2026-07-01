import json
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

from .errors import OpenBayesError


class GraphQLClient:
    """Minimal GraphQL-over-HTTP transport using the standard library only.

    Every request carries ``Authorization: Bearer <token>`` (when a token is set) and an
    ``Origin`` header (the gateway rejects requests without it: "403 Invalid CORS request").
    The single :meth:`exec` method returns the ``data`` node and raises :class:`OpenBayesError`
    if the response carries GraphQL ``errors`` or a non-2xx status.
    """

    def __init__(self, endpoint: str, token: Optional[str] = None) -> None:
        self.endpoint = endpoint
        self.token = token

    def set_token(self, token: str) -> None:
        self.token = token

    def exec(self, query: str, variables: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        payload = self.build_request_body(query, variables)
        headers = {
            "Content-Type": "application/json",
            "Origin": self.endpoint,  # required, else the gateway returns 403 Invalid CORS request
        }
        if self.token:
            headers["Authorization"] = "Bearer " + self.token

        req = urllib.request.Request(
            self.endpoint, data=payload.encode("utf-8"), headers=headers, method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read().decode("utf-8")
        except urllib.error.HTTPError as e:  # non-2xx
            err_body = e.read().decode("utf-8", "replace")
            raise OpenBayesError("GraphQL HTTP {}: {}".format(e.code, err_body))
        except urllib.error.URLError as e:
            raise OpenBayesError("GraphQL request failed: {}".format(e))

        root = json.loads(body)
        errors = root.get("errors")
        if errors:
            raise OpenBayesError(self.format_errors(errors))
        data = root.get("data")
        if data is None:
            raise OpenBayesError("GraphQL response has no data: " + body)
        return data

    @staticmethod
    def build_request_body(query: str, variables: Optional[Dict[str, Any]]) -> str:
        """Build the ``{"query": ..., "variables": ...}`` request body."""
        return json.dumps({"query": query, "variables": variables or {}})

    @staticmethod
    def format_errors(errors: List[Dict[str, Any]]) -> str:
        """Mirror the CLI's error formatting: message plus optional extensions.details."""
        first = errors[0]
        message = first.get("message", "Unknown error")
        details = (first.get("extensions") or {}).get("details")
        if details:
            return "{}. Details: {}".format(message, json.dumps(details, ensure_ascii=False))
        return message
