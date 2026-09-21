import json
import sys
import unittest
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from import_textbooks import build_assets


class KnowledgeImportTest(unittest.TestCase):
    def test_fixture_uses_production_importer_and_matches_android_assets(self):
        root = Path(__file__).parent
        fixture = root / "fixtures"
        generated = build_assets(
            fixture / "knowledge_pipeline_book1.md",
            fixture / "knowledge_pipeline_book2.md",
            [fixture / "knowledge_pipeline_fixture.json"],
        )
        names = ("textbooks", "lessons", "blocks", "sentences", "knowledge")
        assets = root.parents[1] / "app" / "src" / "test" / "resources" / "knowledge_pipeline"
        for name, data in zip(names, generated):
            expected = json.loads((assets / f"{name}.json").read_text(encoding="utf-8"))
            self.assertEqual(expected, data, f"stale generated fixture asset: {name}.json")

        sentences, knowledge = generated[3], generated[4]
        self.assertEqual(Counter({"PHRASE": 5, "GRAMMAR": 3, "PATTERN": 2, "WORD": 2}), Counter(item["type"] for item in knowledge))
        self.assertEqual("ur2-lesson-1", next(item for item in sentences if item["id"] == "ur2-lesson-1-b2-s4")["lessonId"])
        self.assertFalse(any(item["sentenceId"] == "ur2-lesson-1-b2-s4" for item in knowledge))


if __name__ == "__main__":
    unittest.main()
