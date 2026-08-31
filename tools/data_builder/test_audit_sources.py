from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.data_builder.audit_sources import build_report


WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
 <sheets>
  <sheet name="大学俄语（一）" sheetId="1" r:id="rId1"/>
  <sheet name="大学俄语（一） (2)" sheetId="2" r:id="rId2"/>
 </sheets>
</workbook>
"""

RELATIONSHIPS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Target="worksheets/sheet1.xml"
  Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
 <Relationship Id="rId2" Target="worksheets/sheet2.xml"
  Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
</Relationships>
"""

SHEET_XML = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
 <sheetData>
  <row r="1">
   <c r="A1" t="inlineStr"><is><t>俄语</t></is></c>
   <c r="C1" t="inlineStr"><is><t>词类</t></is></c>
   <c r="D1" t="inlineStr"><is><t>词类</t></is></c>
  </row>
  <row r="2">
   <c r="A2" t="inlineStr"><is><t>сло́во</t></is></c>
   <c r="B2" t="inlineStr"><is><t>ignored</t></is></c>
   <c r="C2"><v>8</v></c>
   <c r="D2" t="inlineStr"><is><t>8</t></is></c>
  </row>
 </sheetData>
</worksheet>
"""

DOCUMENT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:body><w:p/><w:tbl/></w:body>
</w:document>
"""


class AuditSourcesTest(unittest.TestCase):
    def test_audits_ooxml_and_detects_quality_issues(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            with zipfile.ZipFile(source_dir / "source.xlsx", "w") as archive:
                archive.writestr("xl/workbook.xml", WORKBOOK_XML)
                archive.writestr("xl/_rels/workbook.xml.rels", RELATIONSHIPS_XML)
                archive.writestr("xl/worksheets/sheet1.xml", SHEET_XML)
                archive.writestr("xl/worksheets/sheet2.xml", SHEET_XML)
            with zipfile.ZipFile(source_dir / "notes.docx", "w") as archive:
                archive.writestr("word/document.xml", DOCUMENT_XML)

            report, has_errors = build_report(source_dir)

            self.assertFalse(has_errors)
            self.assertEqual(report["xlsx_file_count"], 1)
            self.assertEqual(report["docx_file_count"], 1)
            sheet = report["xlsx_files"][0]["sheets"][0]
            self.assertEqual(sheet["empty_header_columns"], ["B"])
            self.assertEqual(sheet["duplicate_headers"][0]["columns"], ["C", "D"])
            self.assertEqual(sheet["placeholder_8_count"], 2)
            self.assertEqual(sheet["column_stats"][0]["unique_count"], 1)
            self.assertEqual(len(report["suspected_duplicate_sheets"]), 1)
            self.assertEqual(report["docx_files"][0]["paragraph_count"], 1)
            self.assertEqual(report["docx_files"][0]["table_count"], 1)


if __name__ == "__main__":
    unittest.main()

