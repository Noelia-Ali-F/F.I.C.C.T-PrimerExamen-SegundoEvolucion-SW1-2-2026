"""Standard-library preflight tests. No network, credentials, or production data."""
import copy
import gzip
import json
import unittest
import migrate_dynamodb as migration


class MigrationPreflightTest(unittest.TestCase):
    def source(self):
        result = {kind: [] for kind in migration.KINDS}
        result.update(format="arqnova-dynamodb-export-v1")
        result["PROJECT"] = [dict(id="P", name="Ventas", ownerUserId="owner")]
        result["DIAGRAM"] = [dict(id="D", projectId="P", modelVersion=7, model={
            "classes": [dict(id="a", name="Catálogo", x=12.5, y=71)],
            "relations": [dict(id="r", type="composition", sourceId="a", targetId="b", sourceMultiplicity="1", targetMultiplicity="1..*")]
        })]
        return result

    def test_preserves_complete_model_and_case_sensitive_ids(self):
        source = self.source()
        result = migration.records(source)
        self.assertEqual(result["DIAGRAM", "D"], source["DIAGRAM"][0])
        self.assertIn(("UNIQUE", "diagram-project:P"), result)
        self.assertEqual(json.loads(gzip.decompress(migration.encoded(result["DIAGRAM", "D"]))), source["DIAGRAM"][0])

    def test_rejects_case_insensitive_duplicate_project_names(self):
        source = self.source()
        source["PROJECT"].append(dict(id="Q", name="VENTAS", ownerUserId="owner"))
        with self.assertRaisesRegex(ValueError, "duplicado"):
            migration.records(source)

    def test_allows_same_name_for_another_owner(self):
        source = self.source()
        source["PROJECT"].append(dict(id="Q", name="Ventas", ownerUserId="another"))
        migration.records(source)

    def test_rejects_orphan_before_writing(self):
        source = self.source()
        source["PROJECT"] = []
        with self.assertRaisesRegex(ValueError, "proyecto inexistente"):
            migration.records(source)

    def test_rejects_duplicate_users_with_different_case(self):
        source = self.source()
        source["USER"] = [dict(id="u1", username="User@example.test"), dict(id="u2", username="user@example.test")]
        with self.assertRaisesRegex(ValueError, "duplicado"):
            migration.records(source)

    def test_composite_keys_do_not_collide(self):
        self.assertNotEqual(migration.compound("ab", "c"), migration.compound("a", "bc"))
        self.assertNotEqual(migration.unique_key("project-name", "Ab"), migration.unique_key("project-name", "ab"))

    def test_requires_supported_export_format(self):
        source = self.source()
        source["format"] = "unknown"
        with self.assertRaisesRegex(ValueError, "Formato"):
            migration.records(source)


if __name__ == "__main__":
    unittest.main()
