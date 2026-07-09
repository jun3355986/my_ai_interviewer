import unittest
import os

os.environ.setdefault("AZURE_OPENAI_API_KEY", "unit-test-key")

from services.interview_service import InterviewService
from services.interview_session import InterviewSession, InterviewStage, session_manager


class FakeInterviewer:
    def __init__(self):
        self.technical_question_select_count = 0

    def evaluate_answer(self, question, answer, resume_content=None):
        return 85, "回答基本符合要求", None

    def select_technical_questions(self, session, question_types, counts):
        self.technical_question_select_count += 1
        return [
            "请说明 HashMap 的底层实现原理。",
            "请说明 Java 线程池的核心参数。",
            "请说明 Redis 缓存穿透如何解决。",
        ]


class InterviewTechnicalTransitionTest(unittest.TestCase):
    def setUp(self):
        self.service = InterviewService.__new__(InterviewService)
        self.fake_interviewer = FakeInterviewer()
        self.service.interviewer = self.fake_interviewer
        self.service._save_session = lambda session: None

        self.session_id = "technical-transition-test"
        session_manager.sessions.pop(self.session_id, None)

    def tearDown(self):
        session_manager.sessions.pop(self.session_id, None)

    def make_project_session(self):
        session = InterviewSession(
            session_id=self.session_id,
            resume_content="Java 后端工程师，熟悉 Spring、Redis、并发编程。",
            job_requirements="Java 后端开发，要求熟悉集合、多线程、Redis。",
            stage=InterviewStage.PROJECT_QNA,
            target_project_questions=1,
        )
        session.add_message("ai", "请介绍一下你简历中最有挑战性的项目？")
        session_manager.sessions[self.session_id] = session
        return session

    def test_project_completion_returns_first_technical_question(self):
        session = self.make_project_session()

        result = self.service.handle_project_answer(self.session_id, "我负责核心链路开发。")

        self.assertEqual(InterviewStage.TECHNICAL_QNA, session.stage)
        self.assertEqual("technical_qna", result["stage"])
        self.assertEqual("请说明 HashMap 的底层实现原理。", result["next_question"])
        self.assertEqual(2, result["remaining_questions"])
        self.assertEqual(0, len(session.technical_qa_list))
        self.assertEqual("请说明 HashMap 的底层实现原理。", session.history[-1]["content"])

    def test_first_technical_answer_advances_to_next_question(self):
        session = self.make_project_session()
        self.service.handle_project_answer(self.session_id, "我负责核心链路开发。")

        result = self.service.handle_technical_answer(self.session_id, "HashMap 基于数组、链表和红黑树。")

        self.assertEqual(InterviewStage.TECHNICAL_QNA, session.stage)
        self.assertEqual(1, len(session.technical_qa_list))
        self.assertEqual("请说明 Java 线程池的核心参数。", result["next_question"])
        self.assertEqual(1, result["remaining_questions"])

    def test_start_technical_is_idempotent_after_auto_initialization(self):
        session = self.make_project_session()
        self.service.handle_project_answer(self.session_id, "我负责核心链路开发。")

        result = self.service.start_technical_interview(
            self.session_id,
            ["Java基础"],
            {"Java基础": 1},
        )

        self.assertEqual("请说明 HashMap 的底层实现原理。", result["question"]["text"])
        self.assertEqual("请说明 HashMap 的底层实现原理。", result["next_question"])
        self.assertEqual(2, result["remaining_questions"])
        self.assertEqual(1, self.fake_interviewer.technical_question_select_count)
        self.assertEqual(1, len([msg for msg in session.history if msg.get("role") == "ai" and msg.get("content") == result["next_question"]]))


if __name__ == "__main__":
    unittest.main()
