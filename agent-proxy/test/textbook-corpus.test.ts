import { describe, expect, it } from "vitest";
import { selectTextbookLine, textbookDialogues } from "../src/TextbookDialogueCorpus";

describe("textbook dialogue corpus", () => {
  it("covers every dialogue lesson and explicitly excludes review-only lesson 8", () => {
    expect(textbookDialogues.map(item => item.lessonNumber)).toEqual([
      1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
    ]);
    expect(() => selectTextbookLine(8)).toThrow("LESSON_HAS_NO_DIALOGUE");
  });

  it("returns a Russian dialogue line with the requested lesson marker", () => {
    for (const lessonNumber of [1, 9, 18]) {
      const selected = selectTextbookLine(lessonNumber);
      expect(selected.lessonNumber).toBe(lessonNumber);
      expect(selected.text).toMatch(/[А-Яа-яЁё]/);
    }
  });
});
