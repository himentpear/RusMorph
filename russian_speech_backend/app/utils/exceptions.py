class SpeechError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 422):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


class DependencyUnavailable(SpeechError):
    def __init__(self, dependency: str, detail: str = ""):
        suffix = f"：{detail}" if detail else ""
        super().__init__(
            "dependency_unavailable",
            f"语音依赖 {dependency} 不可用{suffix}",
            503,
        )


class AlignmentFailed(SpeechError):
    def __init__(self, detail: str = ""):
        super().__init__(
            "alignment_failed",
            "强制对齐失败，无法可靠生成音节或音素评分。" + (f" {detail}" if detail else ""),
            422,
        )
