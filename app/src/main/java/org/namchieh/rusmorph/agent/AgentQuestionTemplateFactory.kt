package org.namchieh.rusmorph.agent

object AgentQuestionTemplateFactory {
    fun create(type: AgentQuestionType, displayForm: String): String = when (type) {
        AgentQuestionType.ETYMOLOGY -> """请解释俄语词“$displayForm”的词源和历史演变。请依次说明现代词义、可可靠确认的历史来源、词形或语义演变和相关同源词。资料不足时请明确说明，不要创造古语形式。"""
        AgentQuestionType.DERIVATION -> """请分析俄语词“$displayForm”的派生关系，区分词根、前缀、后缀、派生词以及仅属于变格或变位的形式。不确定的关系必须标记为可能或待核验。"""
        AgentQuestionType.MORPHOLOGY -> """请解释俄语词“$displayForm”可能存在的特殊变格、变位、重音、词干变化或语音交替。先列本地词表字段；字段为空时须明确表示本地未记录，不得伪造，再解释规则并给出可靠例词。"""
        AgentQuestionType.CUSTOM -> ""
    }
}
