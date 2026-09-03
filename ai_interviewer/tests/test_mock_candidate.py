import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from schemas.mock import MockCandidateHistoryItem
from services.mock_candidate import (
    MockCandidate,
    build_candidate_context,
    candidate_system_prompt,
)


@dataclass
class FakeAiMessage:
    content: str
    usage_metadata: dict
    response_metadata: dict


class FakeLlm:
    """记录调用并返回固定回答的假 LLM。"""

    def __init__(self, response="这是一个候选人回答。"):
        self.response = response
        self.prompt_texts = []

    def invoke(self, prompt_value):
        self.prompt_texts.append(prompt_value.to_string())
        return FakeAiMessage(
            content=self.response,
            usage_metadata={"input_tokens": 1, "output_tokens": 1},
            response_metadata={},
        )


def test_build_candidate_context_contains_resume_and_job():
    context = build_candidate_context("五年 Java 经验简历", "Java 高级开发", None)
    assert "五年 Java 经验简历" in context
    assert "Java 高级开发" in context


def test_build_candidate_context_keeps_recent_history_only():
    history = [
        MockCandidateHistoryItem(question=f"问题{i}", answer=f"回答{i}")
        for i in range(5)
    ]
    context = build_candidate_context("简历", None, history)
    # 只保留最近 3 条问答，避免 prompt 无限膨胀。
    assert "问题4" in context and "回答4" in context
    assert "问题2" in context and "回答2" in context
    assert "问题0" not in context and "问题1" not in context


def test_system_prompt_per_question_type():
    intro = candidate_system_prompt("self_introduction")
    project = candidate_system_prompt("project")
    technical = candidate_system_prompt("technical")
    assert "自我介绍" in intro
    assert "项目经验" in project
    assert "技术面试" in technical


def test_generate_answer_passes_question_and_context_to_llm():
    llm = FakeLlm(response="候选人回答内容")
    candidate = MockCandidate(llm=llm)
    answer = candidate.generate_answer(
        question="请介绍一个你解决过的线上问题",
        question_type="project",
        resume_content="电商订单系统经验",
        job_requirements="熟悉消息队列",
        recent_history=[
            MockCandidateHistoryItem(question="自我介绍一下", answer="我是张三")
        ],
    )
    assert answer == "候选人回答内容"
    assert len(llm.prompt_texts) == 1
    prompt_text = llm.prompt_texts[0]
    assert "请介绍一个你解决过的线上问题" in prompt_text
    assert "电商订单系统经验" in prompt_text
    assert "熟悉消息队列" in prompt_text
    assert "我是张三" in prompt_text
    assert "项目经验" in prompt_text  # project 类型的 system prompt


def test_generate_answer_allows_literal_braces_in_json_resume():
    """JSON 简历（parsed_content）含 `{"age": ...}` 时不能被当成模板变量。"""
    llm = FakeLlm(response="候选人回答内容")
    candidate = MockCandidate(llm=llm)
    answer = candidate.generate_answer(
        question='介绍一下 {"age"} 相关经验 {name}',
        question_type="project",
        resume_content='{"name": "张三", "age": 28, "projects": [{"repo": "x"}]}',
    )
    assert answer == "候选人回答内容"
    prompt_text = llm.prompt_texts[0]
    assert '{"name": "张三", "age": 28' in prompt_text
    assert '介绍一下 {"age"} 相关经验 {name}' in prompt_text
