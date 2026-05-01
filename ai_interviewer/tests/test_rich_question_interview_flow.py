import os

os.environ.setdefault("AZURE_OPENAI_API_KEY", "unit-test-valid-looking-key")

from services.interview_session import InterviewSession, InterviewStage, QuestionAnswer, session_manager
from services.interview_service import InterviewService
from schemas.question_item import QuestionItem


class FakeInterviewer:
    def __init__(self, question_items=None):
        self.question_items = question_items or []
        self.evaluated_questions = []

    def evaluate_answer(self, question, answer, resume_content=None):
        self.evaluated_questions.append(question)
        return 88, "回答较完整", None

    def select_technical_question_items(self, session, question_types, counts):
        return list(self.question_items)


class LegacyFakeInterviewer(FakeInterviewer):
    def select_technical_questions(self, session, question_types, counts):
        return ["请解释 synchronized 和 Lock 的区别。"]

    def select_technical_question_items(self, session, question_types, counts):
        raise AttributeError("legacy interviewer does not support structured selector")


def make_service(fake_interviewer):
    service = InterviewService.__new__(InterviewService)
    service.interviewer = fake_interviewer
    service.resume_parser = None
    service._save_session = lambda session: None
    return service


def put_session(session):
    session_manager.sessions.clear()
    session_manager.sessions[session.session_id] = session
    return session


def test_legacy_string_pool_can_be_promoted_to_structured_next_question():
    fake = FakeInterviewer()
    service = make_service(fake)
    session = put_session(
        InterviewSession(
            session_id="legacy-pool",
            resume_content="Java 后端候选人",
            stage=InterviewStage.TECHNICAL_QNA,
            history=[
                QuestionItem(
                    text="请说明 HashMap 的扩容机制。",
                    question_type="TECHNICAL",
                ).to_history_message()
            ],
            technical_questions_pool=["请解释 volatile 的可见性语义。"],
        )
    )

    result = service.handle_technical_answer(session.session_id, "按 2 的幂扩容。")

    assert result["next_question"] == "请解释 volatile 的可见性语义。"
    assert result["question"]["text"] == "请解释 volatile 的可见性语义。"
    assert session.history[-1]["question"]["text"] == "请解释 volatile 的可见性语义。"
    assert session.technical_questions_pool == []


def test_project_completion_starts_structured_technical_first_question_immediately():
    first = QuestionItem(
        id="q-1",
        text="请结合图说明 Redis Lua 限流脚本关系。",
        question_type="TECHNICAL",
        skill_area="Redis",
    )
    second = QuestionItem(
        id="q-2",
        text="请说明 JVM G1 回收器的 remembered set 作用。",
        question_type="TECHNICAL",
        skill_area="JVM",
    )
    fake = FakeInterviewer([first, second])
    service = make_service(fake)
    session = put_session(
        InterviewSession(
            session_id="project-to-tech",
            resume_content="负责 Redis 限流和 JVM 调优",
            stage=InterviewStage.PROJECT_QNA,
            history=[{"role": "ai", "content": "请介绍项目里的限流方案。"}],
            target_project_questions=1,
        )
    )

    result = service.handle_project_answer(session.session_id, "使用 Redis Lua 做原子限流。")

    assert result["stage"] == InterviewStage.TECHNICAL_QNA.value
    assert result["message"] == "项目提问环节结束，进入技术面试环节"
    assert result["question"] == first.to_public_dict()
    assert result["next_question"] == first.text
    assert result["remaining_questions"] == 1
    assert session.history[-1] == first.to_history_message()
    assert session.technical_questions_pool == [second.to_pool_dict()]


def test_technical_answer_scores_current_structured_text_and_returns_structured_next_question():
    current = QuestionItem(
        id="q-1",
        text="请结合图说明 Redis Lua 限流脚本关系。",
        question_type="TECHNICAL",
        skill_area="Redis",
    )
    next_item = QuestionItem(
        id="q-2",
        text="请说明 JVM G1 回收器的 remembered set 作用。",
        question_type="TECHNICAL",
        skill_area="JVM",
    )
    fake = FakeInterviewer()
    service = make_service(fake)
    session = put_session(
        InterviewSession(
            session_id="tech-answer",
            resume_content="负责 Redis 限流和 JVM 调优",
            stage=InterviewStage.TECHNICAL_QNA,
            history=[current.to_history_message()],
            technical_questions_pool=[next_item.to_pool_dict()],
        )
    )

    result = service.handle_technical_answer(session.session_id, "入口脚本调用底层限流脚本。")

    assert fake.evaluated_questions == [current.text]
    assert session.technical_qa_list == [
        QuestionAnswer(
            question=current.text,
            answer="入口脚本调用底层限流脚本。",
            score=88,
            feedback="回答较完整",
            timestamp=session.technical_qa_list[0].timestamp,
        )
    ]
    assert result["qa_record"]["question"] == current.text
    assert result["question"] == next_item.to_public_dict()
    assert result["next_question"] == next_item.text
    assert result["remaining_questions"] == 0
    assert session.history[-1] == next_item.to_history_message()


def test_start_technical_interview_is_idempotent_after_auto_initialization():
    current = QuestionItem(
        id="q-1",
        text="请结合图说明 Redis Lua 限流脚本关系。",
        question_type="TECHNICAL",
    )
    remaining = QuestionItem(
        id="q-2",
        text="请说明 JVM G1 回收器的 remembered set 作用。",
        question_type="TECHNICAL",
    )
    fake = FakeInterviewer([QuestionItem(text="不应重新选择的问题。")])
    service = make_service(fake)
    session = put_session(
        InterviewSession(
            session_id="idempotent-tech",
            resume_content="负责 Redis 限流和 JVM 调优",
            stage=InterviewStage.TECHNICAL_QNA,
            history=[current.to_history_message()],
            technical_questions_pool=[remaining.to_pool_dict()],
        )
    )

    result = service.start_technical_interview(session.session_id, ["TECHNICAL"], {"TECHNICAL": 5})

    assert result["question"] == current.to_public_dict()
    assert result["next_question"] == current.text
    assert result["remaining_questions"] == 1
    assert session.history == [current.to_history_message()]
    assert session.technical_questions_pool == [remaining.to_pool_dict()]


def test_start_technical_interview_falls_back_to_legacy_selector():
    fake = LegacyFakeInterviewer()
    service = make_service(fake)
    session = put_session(
        InterviewSession(
            session_id="legacy-selector",
            resume_content="Java 后端候选人",
            stage=InterviewStage.TECHNICAL_QNA,
        )
    )

    result = service.start_technical_interview(session.session_id, ["TECHNICAL"], {"TECHNICAL": 1})

    assert result["question"]["text"] == "请解释 synchronized 和 Lock 的区别。"
    assert result["next_question"] == "请解释 synchronized 和 Lock 的区别。"
    assert session.history[-1]["question"]["text"] == "请解释 synchronized 和 Lock 的区别。"
