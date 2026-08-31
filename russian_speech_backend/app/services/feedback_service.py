from app.schemas.pronunciation import Issue, PracticeItem


class FeedbackService:
    def build(
        self,
        omitted: list[str],
        stress_issues: list[tuple[str, str]],
        low_words: list[str],
    ) -> tuple[list[Issue], str, list[PracticeItem]]:
        issues: list[Issue] = []
        for word in omitted:
            issues.append(Issue(
                type="omission", word=word, severity="high",
                feedback_zh=f"目标句中的“{word}”没有被可靠检测到，请单独朗读该词后再练整句。",
            ))
        for word, syllable in stress_issues:
            issues.append(Issue(
                type="stress", word=word, syllable=syllable, severity="medium",
                feedback_zh=f"单词“{word}”的重音判断与目标不一致。请把“{syllable}”拉长并读得更清楚。",
            ))
        for word in low_words:
            issues.append(Issue(
                type="word_clarity", word=word, severity="medium",
                feedback_zh=f"单词“{word}”清晰度不足。请先慢速单独读三遍，再放回整句。",
            ))
        issues = issues[:3]
        if not issues:
            summary = "本次朗读内容完整，未发现达到可靠阈值的主要问题。可继续用正常语速练习整句。"
        else:
            summary = "本次分析优先列出最影响可懂度的可靠问题。请先逐词练习，再恢复整句连读。"
        practice = [
            PracticeItem(type="slow_repeat", content=issue.word or issue.syllable or "", repetitions=3)
            for issue in issues
        ]
        if not practice:
            practice = [PracticeItem(type="sentence_repeat", content="整句正常语速朗读", repetitions=3)]
        return issues, summary, practice
