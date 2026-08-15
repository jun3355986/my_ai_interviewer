import os

os.environ.setdefault("AZURE_OPENAI_API_KEY", "unit-test-valid-looking-key")

from schemas.question_item import QuestionItem
from services.interview_service import InterviewService
from services.interview_session import InterviewSession, InterviewStage, session_manager


class ScriptedInterviewer:
    """Deterministic interviewer for the complete interview state-machine regression."""

    def __init__(self):
        self.evaluations = iter(
            [
                (45, "缺少关键设计取舍", "请补充一致性与失败补偿策略。"),
                (78, "已补充关键细节", None),
                (86, "项目经验完整", None),
                (82, "技术回答正确", None),
                (84, "技术回答完整", None),
            ]
        )
        self.followup_calls = []
        self.conclude_calls = 0

    def evaluate_answer(self, question, answer, resume_content=None):
        return next(self.evaluations)

    def generate_followup_question(self, question, answer, reason):
        self.followup_calls.append((question, answer, reason))
        return "这个方案发生部分失败时，如何保证数据最终一致？"

    def select_technical_question_items(self, session, question_types, counts):
        return [
            QuestionItem(
                id="tech-1",
                text="请说明 HashMap 在 JDK 8 中的树化条件。",
                question_type="TECHNICAL",
            ),
            QuestionItem(
                id="tech-2",
                text="请说明线程池拒绝策略的适用场景。",
                question_type="TECHNICAL",
            ),
        ]

    def conclude_interview(self, session):
        self.conclude_calls += 1
        return 81, "总结：项目设计与 Java 基础较扎实，建议继续强化故障恢复设计。"


def make_service(interviewer):
    service = InterviewService.__new__(InterviewService)
    service.interviewer = interviewer
    service.resume_parser = None
    service._save_session = lambda session: None
    return service


def test_full_interview_keeps_followups_out_of_project_quota_and_concludes_with_summary():
    session_id = "complete-state-machine"
    session_manager.sessions.pop(session_id, None)
    interviewer = ScriptedInterviewer()
    service = make_service(interviewer)
    session = InterviewSession(
        session_id=session_id,
        resume_content="Java 后端工程师，负责订单与库存一致性。",
        stage=InterviewStage.PROJECT_QNA,
        target_project_questions=2,
        project_questions_pool=["请介绍另一个最有挑战的项目。"],
    )
    session.add_message("ai", "请介绍订单与库存一致性方案。")
    session_manager.sessions[session_id] = session

    try:
        first_project = service.handle_project_answer(session_id, "通过消息队列异步更新库存。")
        assert first_project["is_followup"] is True
        assert first_project["next_question"] == "这个方案发生部分失败时，如何保证数据最终一致？"
        assert session.project_questions_count == 1
        assert session.project_qa_list[-1].is_followup is False

        followup = service.handle_project_answer(session_id, "使用幂等键、事务外盒和补偿任务恢复失败消息。")
        assert followup["stage"] == InterviewStage.PROJECT_QNA.value
        assert followup["next_question"] == "请介绍另一个最有挑战的项目。"
        assert session.project_questions_count == 1
        assert session.project_qa_list[-1].is_followup is True

        project_to_technical = service.handle_project_answer(session_id, "我设计过限流、降级与压测方案。")
        assert project_to_technical["stage"] == InterviewStage.TECHNICAL_QNA.value
        assert project_to_technical["next_question"] == "请说明 HashMap 在 JDK 8 中的树化条件。"
        assert session.project_questions_count == 2

        first_technical = service.handle_technical_answer(session_id, "链表长度达到阈值且数组容量足够大时树化。")
        assert first_technical["stage"] == InterviewStage.TECHNICAL_QNA.value
        assert first_technical["next_question"] == "请说明线程池拒绝策略的适用场景。"

        conclusion = service.handle_technical_answer(session_id, "由业务吞吐、延迟和降级策略选择拒绝策略。")
        assert conclusion["stage"] == InterviewStage.CONCLUDED.value
        assert conclusion["final_score"] == 81
        assert conclusion["final_feedback"].startswith("总结：")
        assert conclusion["message"] == conclusion["final_feedback"]
        assert session.final_score == 81
        assert session.final_feedback == conclusion["final_feedback"]
        assert interviewer.conclude_calls == 1
    finally:
        session_manager.sessions.pop(session_id, None)
