import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from openbayes_client.graphql_client import GraphQLClient  # noqa: E402


class GraphQLClientTest(unittest.TestCase):
    def test_builds_request_body_with_query_and_variables(self):
        body = GraphQLClient.build_request_body(
            "mutation Foo { foo }", {"userId": "demo-user", "storageType": "TEMPORARY"}
        )
        node = json.loads(body)
        self.assertEqual(node["query"], "mutation Foo { foo }")
        self.assertEqual(node["variables"]["userId"], "demo-user")
        self.assertEqual(node["variables"]["storageType"], "TEMPORARY")

    def test_builds_request_body_with_empty_variables_when_none(self):
        node = json.loads(GraphQLClient.build_request_body("query Me { me }", None))
        self.assertEqual(node["variables"], {})

    def test_formats_error_message_only(self):
        self.assertEqual(GraphQLClient.format_errors([{"message": "boom"}]), "boom")

    def test_formats_error_with_details(self):
        formatted = GraphQLClient.format_errors(
            [{"message": "bad", "extensions": {"details": {"field": "x"}}}]
        )
        self.assertTrue(formatted.startswith("bad. Details: "))
        self.assertIn("field", formatted)


if __name__ == "__main__":
    unittest.main()
