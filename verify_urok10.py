import json
import re

with open(r'C:\Users\jisub\Documents\RusMorph\data-source\transcriptions\urok_10.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

print(f"Total pages: {len(data)}")
for i, page in enumerate(data):
    pdf = page.get('pdf_page')
    printed = page.get('printed_page')
    secs = page.get('sections', [])
    desc = []
    for s in secs:
        st = s.get('type')
        if st == 'EXERCISE':
            desc.append(f"EX_{s.get('exercise_number')}")
        elif st == 'DIALOGUE':
            desc.append("DIALOGUE")
        elif st == 'TEXT':
            desc.append("TEXT")
        elif st == 'OTHER':
            desc.append(f"OTHER({s.get('heading')})")
    print(f"Page {i+1} (pdf {pdf}, p{printed}): {', '.join(desc)}")

# Check Latin characters in Russian text
# Latin letters that look like Cyrillic: a, c, e, o, p, x, y, k, i, etc.
latin_pattern = re.compile(r'[a-zA-Z]')
# Check each section
print("\n--- Checking for potential Latin characters in Russian strings ---")
for p_idx, page in enumerate(data):
    for s_idx, sec in enumerate(page.get('sections', [])):
        raw = sec.get('raw_text', '')
        # remove instructions or headings that might have Chinese or expected characters
        # let's find all words with latin letters
        words = re.findall(r'[a-zA-Z\u0300-\u036f]+', raw)
        if words:
            print(f"Page {page['pdf_page']} sec {sec.get('type')}: Latin words: {words}")
