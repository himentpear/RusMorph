import sys
import json
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from import_textbooks import apply_external_knowledge, build_assets, parse_book


class TextbookImporterTest(unittest.TestCase):
    def test_supplied_sources_have_expected_lesson_counts(self):
        downloads = Path("D:/Downloads")
        if not downloads.exists():
            self.skipTest("Supplied textbook source files are not available")
        _, book1_lessons, _, book1_sentences = parse_book(downloads / "1.md", "university-russian-1", "大学俄语 1", "1")
        _, book2_lessons, _, book2_sentences = parse_book(downloads / "2.md", "university-russian-2", "大学俄语 2", "2")
        self.assertEqual(18, len(book1_lessons))
        self.assertEqual(12, len(book2_lessons))
        self.assertGreater(len(book1_sentences), 0)
        self.assertGreater(len(book2_sentences), 0)
        project = downloads / "rusmorph_annotation_project.json"
        if project.exists():
            _, lessons, _, sentences, knowledge = build_assets(downloads / "1.md", downloads / "2.md", [project])
            self.assertEqual(30, len(lessons))
            self.assertEqual(259, sum(item["lessonId"].startswith("ur1-") for item in sentences))
            self.assertEqual(0, len(knowledge))

    def test_external_annotation_maps_to_sentence_knowledge(self):
        lessons = [{"id": "lesson", "textbookId": "book", "lessonNumber": 1, "title": "Урок 1"}]
        blocks = [{"id": "block", "lessonId": "lesson", "order": 1, "type": "READING", "title": None}]
        sentences = [{"id": "sentence", "lessonId": "lesson", "blockId": "block", "order": 1, "text": "С де́тства", "sourceText": "С де́тства"}]
        document = {"id": "sentence", "sourceText": "С де́тства", "annotations": [{"type": "PHRASE", "text": "С де́тства", "label": "从小", "explanation": "с + 第二格", "example": None, "start": 0, "end": 10, "status": "OK"}]}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "knowledge.json"
            path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
            knowledge = []
            apply_external_knowledge(path, lessons, blocks, sentences, knowledge)
        self.assertEqual("sentence", knowledge[0]["sentenceId"])
        self.assertEqual("PHRASE", knowledge[0]["type"])
        self.assertIsNone(knowledge[0]["generatedBy"])
