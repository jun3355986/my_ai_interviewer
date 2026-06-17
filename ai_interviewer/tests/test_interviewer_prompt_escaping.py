import os
import unittest

from langchain_core.runnables import RunnableLambda

os.environ.setdefault("AZURE_OPENAI_API_KEY", "unit-test-key")

from api.interviewer import Interviewer


class InterviewerPromptEscapingTest(unittest.TestCase):
    def make_interviewer(self, response):
        interviewer = Interviewer.__new__(Interviewer)
        interviewer.llm = RunnableLambda(lambda _: response)
        return interviewer

    def test_evaluate_answer_allows_literal_braces_in_user_answer(self):
        interviewer = self.make_interviewer(
            '{"score": 80, "feedback": "回答清晰", "need_followup": false}'
        )

        score, feedback, followup_reason = interviewer.evaluate_answer(
            "请说明 Redis 快照状态 key 的设计。",
            "使用 SNAPSHOT:{activityId}:status 保存状态。",
            "Java 后端工程师",
        )

        self.assertEqual(80, score)
        self.assertEqual("回答清晰", feedback)
        self.assertIsNone(followup_reason)

    def test_followup_generation_allows_literal_braces_in_context(self):
        interviewer = self.make_interviewer("请进一步说明 activityId 的幂等控制。")

        question = interviewer.generate_followup_question(
            "请说明 Redis 快照状态 key 的设计。",
            "使用 SNAPSHOT:{activityId}:status 保存状态。",
            "需要确认状态一致性。",
        )

        self.assertEqual("请进一步说明 activityId 的幂等控制。", question)


if __name__ == "__main__":
    unittest.main()
