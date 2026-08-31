# Data builder tools

`audit_sources.py` performs a read-only audit of every non-temporary
`data-source/*.xlsx` and `data-source/*.docx` file. It uses only the Python
standard library and parses Office Open XML containers without changing them.

`build_data.py` converts the sources into Android JSON assets and writes
`data_validation_report.json`. XLSX parsing remains dependency-free; DOCX
conversion uses the verified `python-docx` version in `requirements.txt`.

Run from the repository root:

```shell
python tools/data_builder/audit_sources.py
python -m pip install -r tools/data_builder/requirements.txt
python -m tools.data_builder.build_data
```

The report is written as UTF-8 JSON to `data_audit_report.json`. Source and
output locations can be overridden with `--source-dir` and `--output`.

Android `preBuild` depends on the `buildDataAssets` Gradle task. Set
`RUSMORPH_PYTHON` when the required Python is not the first `python` on PATH.
