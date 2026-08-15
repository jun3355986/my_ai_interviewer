"""
面试流程服务：管理完整的面试流程
"""
import uuid
from datetime import datetime
from typing import Optional, List, Dict

from schemas.question_item import QuestionItem
from services.interview_session import (
    InterviewSession,
    InterviewStage,
    QuestionAnswer,
    session_manager,
)
from services.database import get_default_database
from api.interviewer import Interviewer
from services.resume_parser import ResumeParser


class InterviewService:
    """面试流程服务"""

    DEFAULT_TECHNICAL_QUESTION_TYPES = ["Java基础", "多线程", "Spring", "Redis"]
    DEFAULT_TECHNICAL_QUESTION_COUNTS = {
        "Java基础": 1,
        "多线程": 1,
        "Spring": 1,
    }
    FALLBACK_TECHNICAL_QUESTIONS = [
        "请介绍一下Java中HashMap的实现原理？",
        "请说明Java线程池的核心参数及其作用？",
        "请说明Redis缓存穿透的常见解决方案？",
    ]

    def __getattr__(self, name):
        if name == "session_manager":
            return session_manager
        raise AttributeError(name)
    
    def __init__(
        self,
        *,
        interviewer=None,
        session_manager_instance=None,
        database=None,
        persist_sessions: bool = True,
    ):
        self.interviewer = interviewer or Interviewer()
        self.resume_parser = ResumeParser()
        self.session_manager = session_manager_instance or session_manager
        self.database = database or getattr(self.session_manager, "database", None)
        if self.database is None:
            self.database = get_default_database()
        self.persist_sessions = persist_sessions
        if persist_sessions:
            self.database.init_db()

    def _coerce_question_item(self, value) -> QuestionItem:
        return QuestionItem.from_legacy(value)

    def _add_ai_question(
        self,
        session: InterviewSession,
        question,
        *,
        is_followup: bool = False,
    ) -> QuestionItem:
        item = self._coerce_question_item(question)
        message = item.to_history_message()
        # A reconstructed durable branch has a stage on every Java message. Keep
        # native Python histories equally explicit for technical questions so an
        # idempotent "start technical" request can never reuse the last project
        # question as its first technical question.
        if session.stage == InterviewStage.TECHNICAL_QNA:
            message["stage"] = session.stage.value
        if is_followup:
            message["is_followup"] = True
            message["question"]["is_followup"] = True
        session.history.append(message)
        session.updated_at = datetime.now()
        return item

    @staticmethod
    def _is_followup_message(message: dict | None) -> bool:
        if not message:
            return False
        question = message.get("question")
        metadata = message.get("metadata")
        return bool(
            message.get("is_followup")
            or (isinstance(question, dict) and question.get("is_followup"))
            or (isinstance(metadata, dict) and metadata.get("is_followup"))
        )

    def _get_current_ai_question(
        self,
        session: InterviewSession,
        *,
        expected_stage: InterviewStage | None = None,
    ) -> QuestionItem | None:
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                raw_question = msg.get("question") or msg.get("content")
                if raw_question:
                    item = self._coerce_question_item(raw_question)
                    if expected_stage == InterviewStage.TECHNICAL_QNA:
                        message_stage = str(msg.get("stage") or "").lower()
                        question_type = str(item.question_type or "").lower()
                        is_declared_project_question = question_type.startswith("project")
                        has_technical_evidence = (
                            message_stage == InterviewStage.TECHNICAL_QNA.value
                            or (bool(question_type) and not is_declared_project_question)
                        )
                        if not has_technical_evidence:
                            continue
                    return item
        return None

    def _select_technical_question_items(
        self,
        session: InterviewSession,
        question_types: List[str],
        counts: Dict[str, int],
    ) -> List[QuestionItem]:
        try:
            raw_questions = self.interviewer.select_technical_question_items(
                session,
                question_types,
                counts,
            )
        except AttributeError:
            raw_questions = self.interviewer.select_technical_questions(
                session,
                question_types,
                counts,
            )
        return [self._coerce_question_item(item) for item in raw_questions]

    def _start_technical_questions_for_session(self, session: InterviewSession) -> Dict:
        return self._initialize_technical_questions(session)
    
    def start_interview(
        self,
        resume_content: str,
        job_requirements: Optional[str] = None,
        candidate_name: Optional[str] = None,
    ) -> InterviewSession:
        """
        开始新的面试
        
        Args:
            resume_content: 简历内容（文本）
            job_requirements: 职位要求
            candidate_name: 候选人姓名
            
        Returns:
            面试会话
        """
        session_id = str(uuid.uuid4())
        session = self.session_manager.create_session(
            session_id=session_id,
            resume_content=resume_content,
            job_requirements=job_requirements,
        )
        session.candidate_name = candidate_name
        
        # 生成开场白
        opening = self.interviewer.generate_opening(resume_content, job_requirements)
        session.add_message("system", opening)
        session.stage = InterviewStage.OPENING
        
        # 保存到数据库
        self._save_session(session)
        
        return session
    
    def handle_opening_response(self, session_id: str) -> Dict:
        """
        处理开场后的响应，进入自我介绍环节
        
        Returns:
            包含问题和下一步动作的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        if session.stage != InterviewStage.OPENING:
            raise ValueError(f"当前阶段不是开场阶段: {session.stage}")
        
        # 生成自我介绍请求
        question = self.interviewer.ask_self_introduction()
        session.add_message("ai", question)
        session.stage = InterviewStage.SELF_INTRO
        
        self._save_session(session)
        
        return {
            "question": question,
            "stage": session.stage.value,
            "session_id": session_id,
        }
    
    def handle_self_introduction(
        self,
        session_id: str,
        answer: str,
    ) -> Dict:
        """
        处理自我介绍，进入项目提问环节
        
        Returns:
            包含第一个项目问题和下一步动作的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        # 记录自我介绍
        session.add_message("human", answer)
        
        # 进入项目提问环节
        session.stage = InterviewStage.PROJECT_QNA
        
        # 根据简历决定目标问题数
        if session.resume_content:
            if len(session.resume_content) < 500:
                session.target_project_questions = 3
            elif len(session.resume_content) < 1500:
                session.target_project_questions = 5
            else:
                session.target_project_questions = 10
        
        # 一次性生成所有项目问题（如果问题池为空）
        if not session.project_questions_pool:
            questions = self.interviewer.generate_project_questions(
                session, 
                question_count=session.target_project_questions
            )
            if questions:
                session.project_questions_pool = questions
            else:
                # 如果生成失败，使用默认问题
                session.project_questions_pool = [
                    "请介绍一下你简历中最有挑战性的项目？",
                    "在这个项目中你遇到的最大技术难点是什么？",
                    "你是如何解决这个问题的？"
                ]
        
        # 从问题池中取出第一个问题
        if session.project_questions_pool:
            question = session.project_questions_pool.pop(0)
            session.add_message("ai", question)
        else:
            # 如果问题池为空，使用默认问题
            question = "请介绍一下你简历中最有挑战性的项目？"
            session.add_message("ai", question)
        
        self._save_session(session)
        
        return {
            "question": question,
            "stage": session.stage.value,
            "target_questions": session.target_project_questions,
        }
    
    def handle_project_answer(
        self,
        session_id: str,
        answer: str,
    ) -> Dict:
        """
        处理项目问题回答
        
        Returns:
            包含评分、反馈、下一个问题或阶段转换的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        if session.stage != InterviewStage.PROJECT_QNA:
            raise ValueError(f"当前阶段不是项目提问阶段: {session.stage}")

        # 获取当前问题（最后一个AI消息）
        current_question = None
        current_question_message = None
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                current_question = msg.get("content")
                current_question_message = msg
                break
        
        if not current_question:
            current_question = "项目相关问题"
        
        # 记录回答
        session.add_message("human", answer)
        
        # 评估回答
        score, feedback, followup_reason = self.interviewer.evaluate_answer(
            current_question,
            answer,
            session.resume_content,
        )
        
        # 保存问答记录
        qa = QuestionAnswer(
            question=current_question,
            answer=answer,
            score=score,
            feedback=feedback,
            is_followup=self._is_followup_message(current_question_message),
        )
        session.add_project_qa(qa)
        
        result = {
            "score": score,
            "feedback": feedback,
            "qa_record": {
                "question": current_question,
                "answer": answer,
                "score": score,
                "feedback": feedback,
                "is_followup": qa.is_followup,
            },
            "stage": session.stage.value,  # 确保始终包含 stage 字段
        }
        
        # The model has already made the follow-up decision from answer quality. Do not
        # override it with an arbitrary score threshold: a low-scoring but diagnosable
        # answer is exactly when a targeted follow-up can collect missing evidence.
        need_followup = (
            followup_reason is not None
            and session.current_question_followup_count < 3
        )
        
        if need_followup:
            # 生成追问
            followup_question = self.interviewer.generate_followup_question(
                current_question,
                answer,
                followup_reason,
            )
            session.current_question_followup_count += 1
            followup_item = self._add_ai_question(
                session,
                QuestionItem(text=followup_question, question_type="PROJECT"),
                is_followup=True,
            )
            result["question"] = followup_item.to_public_dict()
            result["next_question"] = followup_item.text
            result["is_followup"] = True
            result["current_question_followup_count"] = session.current_question_followup_count
            result["stage"] = session.stage.value  # 确保包含 stage
            self._save_session(session)
            return result
        
        # 判断是否完成了项目提问
        if session.project_questions_count >= session.target_project_questions:
            # 进入技术面试环节
            session.stage = InterviewStage.TECHNICAL_QNA
            session.current_question_followup_count = 0
            technical_result = self._start_technical_questions_for_session(session)
            result["stage"] = session.stage.value
            result["message"] = "项目提问环节结束，进入技术面试环节"
            result["question"] = technical_result["question"]
            result["next_question"] = technical_result["question"]["text"]
            result["remaining_questions"] = technical_result["remaining_questions"]
            self._save_session(session)
            return result
        
        # 从问题池中取出下一个问题（不再重新生成）
        if session.project_questions_pool:
            next_question = session.project_questions_pool.pop(0)
            session.add_message("ai", next_question)
            result["next_question"] = next_question
            result["stage"] = session.stage.value
        else:
            # 问题池已空，进入技术面试
            session.stage = InterviewStage.TECHNICAL_QNA
            technical_result = self._start_technical_questions_for_session(session)
            result["stage"] = session.stage.value
            result["message"] = "项目提问环节结束，进入技术面试环节"
            result["question"] = technical_result["question"]
            result["next_question"] = technical_result["question"]["text"]
            result["remaining_questions"] = technical_result["remaining_questions"]
        
        session.current_question_followup_count = 0
        self._save_session(session)
        return result
    
    def start_technical_interview(
        self,
        session_id: str,
        question_types: List[str],
        counts: Dict[str, int],
    ) -> Dict:
        """
        开始技术面试环节
        
        Args:
            session_id: 会话ID
            question_types: 问题类型列表
            counts: 各类型题目数量
            
        Returns:
            包含第一个技术问题和阶段信息的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        if session.stage != InterviewStage.TECHNICAL_QNA:
            raise ValueError(f"当前阶段不是技术面试阶段: {session.stage}")

        current_question = self._get_current_ai_question(
            session,
            expected_stage=InterviewStage.TECHNICAL_QNA,
        )
        if current_question or session.technical_questions_pool:
            if not current_question:
                current_question = self._add_ai_question(session, session.technical_questions_pool.pop(0))
            self._save_session(session)
            return {
                "question": current_question.to_public_dict(),
                "next_question": current_question.text,
                "remaining_questions": len(session.technical_questions_pool),
                "stage": session.stage.value,
            }
        
        result = self._initialize_technical_questions(session, question_types, counts)
        self._save_session(session)
        
        return {
            "question": result["question"],
            "next_question": result["next_question"],
            "remaining_questions": result["remaining_questions"],
            "stage": result["stage"],
        }

    def _initialize_technical_questions(
        self,
        session: InterviewSession,
        question_types: Optional[List[str]] = None,
        counts: Optional[Dict[str, int]] = None,
    ) -> Dict:
        """初始化结构化技术题池，并返回第一道技术题。"""
        resolved_question_types = question_types or self.DEFAULT_TECHNICAL_QUESTION_TYPES
        resolved_counts = counts or self.DEFAULT_TECHNICAL_QUESTION_COUNTS
        if sum(resolved_counts.values()) <= 0:
            resolved_counts = self.DEFAULT_TECHNICAL_QUESTION_COUNTS

        questions = self._select_technical_question_items(
            session,
            resolved_question_types,
            resolved_counts,
        )

        if not questions:
            questions = [
                QuestionItem(text=question, question_type="TECHNICAL")
                for question in self.FALLBACK_TECHNICAL_QUESTIONS
            ]

        first_question = self._add_ai_question(session, questions[0])
        session.technical_questions_pool = [
            self._coerce_question_item(item).to_pool_dict() for item in questions[1:]
        ]

        return {
            "question": first_question.to_public_dict(),
            "next_question": first_question.text,
            "remaining_questions": len(session.technical_questions_pool),
            "stage": session.stage.value,
        }

    def _last_ai_message(self, session: InterviewSession) -> Optional[str]:
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                return msg.get("content")
        return None
    
    def handle_technical_answer(
        self,
        session_id: str,
        answer: str,
    ) -> Dict:
        """
        处理技术问题回答
        
        Returns:
            包含评分、反馈、下一个问题的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        if session.stage != InterviewStage.TECHNICAL_QNA:
            raise ValueError(f"当前阶段不是技术面试阶段: {session.stage}")
        
        # 获取当前问题
        current_question = None
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                current_question = msg.get("question") or msg.get("content")
                break
        
        if not current_question:
            current_question = "技术问题"
        current_question_item = self._coerce_question_item(current_question)
        
        # 记录回答
        session.add_message("human", answer)
        
        # 评估回答
        score, feedback, _ = self.interviewer.evaluate_answer(
            current_question_item.text,
            answer,
            session.resume_content,
        )
        
        # 保存问答记录
        qa = QuestionAnswer(
            question=current_question_item.text,
            answer=answer,
            score=score,
            feedback=feedback,
        )
        session.add_technical_qa(qa)
        
        result = {
            "score": score,
            "feedback": feedback,
            "qa_record": {
                "question": current_question_item.text,
                "answer": answer,
                "score": score,
                "feedback": feedback,
            },
            "stage": session.stage.value,  # 确保始终包含 stage 字段
        }
        
        # 获取下一个问题
        questions_pool = session.technical_questions_pool
        
        if questions_pool:
            next_question = questions_pool.pop(0)
            next_question_item = self._add_ai_question(session, next_question)
            session.technical_questions_pool = questions_pool
            result["question"] = next_question_item.to_public_dict()
            result["next_question"] = next_question_item.text
            result["remaining_questions"] = len(questions_pool)
            result["stage"] = session.stage.value  # 更新 stage
        else:
            # The final technical answer is not the final interview result. Generate
            # and persist the closing summary in the same durable turn so callers
            # cannot observe a completed branch without a conclusion.
            final_score, final_feedback = self._conclude_session(session)
            result["stage"] = session.stage.value
            result["final_score"] = final_score
            result["final_feedback"] = final_feedback
            result["message"] = final_feedback
        
        self._save_session(session)
        return result
    
    def conclude_interview(self, session_id: str) -> Dict:
        """
        总结面试
        
        Returns:
            包含最终评分和反馈的字典
        """
        session = self.session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        if session.stage != InterviewStage.CONCLUDED:
            raise ValueError("技术面试尚未完成，不能提前生成总结")
        if session.final_score is None or session.final_feedback is None:
            self._conclude_session(session)

        self._save_session(session)

        return {
            "final_score": session.final_score,
            "final_feedback": session.final_feedback,
            "average_score": session.get_average_score(),
            "stage": session.stage.value,
        }

    def _conclude_session(self, session: InterviewSession) -> tuple[int, str]:
        """Generate one idempotent final conclusion for an already-scored interview."""
        if session.final_score is not None and session.final_feedback is not None:
            session.stage = InterviewStage.CONCLUDED
            return session.final_score, session.final_feedback

        final_score, feedback = self.interviewer.conclude_interview(session)
        session.final_score = final_score
        session.final_feedback = feedback
        session.stage = InterviewStage.CONCLUDED
        return final_score, feedback
    
    def get_session(self, session_id: str) -> Optional[InterviewSession]:
        """获取会话"""
        return self.session_manager.get_session(session_id)
    
    def _save_session(self, session: InterviewSession):
        """保存会话到数据库"""
        if self.persist_sessions:
            self.database.save_session(session)


class _LazyInterviewService:
    """Avoid model/provider initialization until a legacy route actually uses it."""

    def __init__(self):
        self._instance = None

    def _get(self) -> InterviewService:
        if self._instance is None:
            self._instance = InterviewService()
        return self._instance

    def __getattr__(self, name):
        return getattr(self._get(), name)


# Backward-compatible global service handle, initialized on first real use.
interview_service = _LazyInterviewService()
