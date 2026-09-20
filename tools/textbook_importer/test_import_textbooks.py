import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from import_textbooks import parse_book


class TextbookImporterTest(unittest.TestCase):
    def test_supplied_sources_have_expected_lesson_counts(self):
        downloads = Path("D:/Downloads")
        if not downloads.exists():
            self.skipTest("Supplied textbook source files are not available")
        _, book1_lessons, _, book1_paragraphs = parse_book(downloads / "1.md", "university-russian-1", "大学俄语 1", "1")
        _, book2_lessons, _, book2_paragraphs = parse_book(downloads / "2.md", "university-russian-2", "大学俄语 2", "2")
        self.assertEqual(18, len(book1_lessons))
        self.assertEqual(12, len(book2_lessons))
        self.assertGreater(len(book1_paragraphs), 0)
        self.assertGreater(len(book2_paragraphs), 0)
