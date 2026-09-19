import os
import json
import subprocess
import sys

TRANSCRIPTIONS_DIR = r"C:\Users\jisub\Documents\RusMorph\data-source\transcriptions"
EXPECTED_LESSONS = 18

def check_progress():
    completed = []
    missing = []
    total_pages = 0
    total_exercises = 0
    total_texts = 0
    total_dialogues = 0

    for i in range(1, EXPECTED_LESSONS + 1):
        fname = f"urok_{i:02d}.json"
        fpath = os.path.join(TRANSCRIPTIONS_DIR, fname)
        if os.path.exists(fpath):
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if isinstance(data, list) and len(data) > 0:
                        completed.append(i)
                        total_pages += len(data)
                        for page in data:
                            for sec in page.get("sections", []):
                                stype = sec.get("type")
                                if stype == "EXERCISE":
                                    total_exercises += 1
                                elif stype == "TEXT":
                                    total_texts += 1
                                elif stype == "DIALOGUE":
                                    total_dialogues += 1
                        continue
            except Exception as e:
                print(f"Error reading {fname}: {e}")
        missing.append(i)

    print(f"Progress: {len(completed)}/{EXPECTED_LESSONS} lessons completed.")
    print(f"Completed: {completed}")
    print(f"Missing: {missing}")
    print(f"Stats: {total_pages} pages, {total_exercises} exercises, {total_texts} texts, {total_dialogues} dialogues.")

    if len(completed) == EXPECTED_LESSONS:
        print("ALL LESSONS COMPLETED! Initiating shutdown in 60 seconds...")
        subprocess.run(["shutdown", "/s", "/f", "/t", "60", "/c", "俄语教材全部课程转录完成，系统即将在60秒后自动关机。"], check=False)
        return True
    return False

if __name__ == "__main__":
    check_progress()
