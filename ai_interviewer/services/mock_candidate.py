"""模拟面试候选人 Agent：AI 代替用户在真实面试流程中生成回答。

由 Flutter MockAutoDriver 经 Java `/interviews/mock/candidate-answer` 代理调用，
无状态、不落库；提示词沿用 test_interview.py 中已验证的版本，
并补充职位要求与最近问答上下文以保持整场回答的一致性。
"""

from typing import Any, List, Optional

from langchain_core.prompts import ChatPromptTemplate

from core.config import get_llm
from schemas.mock import MockCandidateHistoryItem, MockQuestionType
from services.observability.langchain import invoke_observable

_HISTORY_LIMIT = 3
_RESUME_CHAR_LIMIT = 1500
_JOB_CHAR_LIMIT = 500


def build_candidate_context(
    resume_content: str,
    job_requirements: Optional[str],
    recent_history: Optional[List[MockCandidateHistoryItem]],
) -> str:
    """组装候选人可感知的上下文（简历 + 职位要求 + 最近问答）。"""
    context = f"简历内容：\n{(resume_content or '')[:_RESUME_CHAR_LIMIT]}\n\n"
    if job_requirements:
        context += f"职位要求：\n{job_requirements[:_JOB_CHAR_LIMIT]}\n\n"
    if recent_history:
        context += "已回答的问题：\n"
        for item in recent_history[-_HISTORY_LIMIT:]:
            context += f"Q: {item.question}\nA: {item.answer}\n\n"
    return context


def candidate_system_prompt(question_type: MockQuestionType) -> str:
    """按问题类型返回候选人 system prompt。"""
    if question_type == "self_introduction":
        return (
            "你是一位有经验的Java开发工程师，正在参加面试。"
            "请根据简历内容，生成一个简洁的自我介绍（2-3句话），"
            "突出你的工作经验和主要技能。"
        )
    if question_type == "project":
        return (
            "你是一位有经验的Java开发工程师，正在参加项目经验面试。"
            "请根据简历中的项目经验，回答面试官的问题。"
            "要求：\n"
            "1. 回答要具体、真实，体现实际项目经验；\n"
            "2. 可以提到技术细节、遇到的问题和解决方案；\n"
            "3. 回答要简洁明了，控制在100-200字；\n"
            "4. 如果简历中没有相关内容，可以基于经验合理推断，但不要编造。"
        )
    return (
        "你是一位有经验的Java开发工程师，正在参加技术面试。"
        "请根据你的技术知识，回答面试官的问题。"
        "要求：\n"
        "1. 回答要准确、专业；\n"
        "2. 可以结合项目经验举例说明；\n"
        "3. 回答要结构化，突出重点；\n"
        "4. 控制在100-200字。"
    )


class MockCandidate:
    """无状态候选人：每次调用携带完整上下文。"""

    def __init__(self, llm: Any = None) -> None:
        self.llm = llm if llm is not None else get_llm()

    def generate_answer(
        self,
        *,
        question: str,
        question_type: MockQuestionType,
        resume_content: str,
        job_requirements: Optional[str] = None,
        candidate_name: Optional[str] = None,
        recent_history: Optional[List[MockCandidateHistoryItem]] = None,
    ) -> str:
        context = build_candidate_context(
            resume_content, job_requirements, recent_history
        )
        # context/question 作为模板变量传入而非字符串内插，
        # 这样简历/问题里的 `{...}`（如 JSON 简历）不会被当成模板变量。
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", candidate_system_prompt(question_type)),
                ("human", "{context}\n问题：{question}\n\n请回答："),
            ]
        )
        return invoke_observable(
            prompt=prompt,
            llm=self.llm,
            input_values={"context": context, "question": question},
            call_type=f"mock_candidate_{question_type}",
        ).text
