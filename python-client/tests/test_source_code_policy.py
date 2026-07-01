import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from openbayes_client.models import SourceCodePolicy  # noqa: E402


class SourceCodePolicyTest(unittest.TestCase):
    def test_splits_bucket_and_prefix(self):
        p = SourceCodePolicy("WFk", "https://s3.openbayes.com", "ak", "sk", "demo-user/codes/WFk")
        self.assertEqual(p.bucket, "demo-user")
        self.assertEqual(p.key_prefix, "codes/WFk")

    def test_tolerates_leading_slash(self):
        self.assertEqual(SourceCodePolicy.split_path("/bkt/a/b"), ("bkt", "a/b"))

    def test_handles_bucket_only(self):
        self.assertEqual(SourceCodePolicy.split_path("bkt"), ("bkt", ""))


if __name__ == "__main__":
    unittest.main()
