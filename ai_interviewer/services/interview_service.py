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
from services.database import InterviewRecord, get_db_session, init_db
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
    
    def __init__(self):
        self.interviewer = Interviewer()
        self.resume_parser = ResumeParser()
        # 初始化数据库
        init_db()

    def _coerce_question_item(self, value) -> QuestionItem:
        return QuestionItem.from_legacy(value)

    def _add_ai_question(self, session: InterviewSession, question) -> QuestionItem:
        item = self._coerce_question_item(question)
        session.history.append(item.to_history_message())
        session.updated_at = datetime.now()
        return item

    def _get_current_ai_question(self, session: InterviewSession) -> QuestionItem | None:
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                raw_question = msg.get("question") or msg.get("content")
                if raw_question:
                    return self._coerce_question_item(raw_question)
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
        session = session_manager.create_session(
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
        session = session_manager.get_session(session_id)
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
        session = session_manager.get_session(session_id)
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
        session = session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        # 获取当前问题（最后一个AI消息）
        current_question = None
        for msg in reversed(session.history):
            if msg.get("role") == "ai":
                current_question = msg.get("content")
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
            },
            "stage": session.stage.value,  # 确保始终包含 stage 字段
        }
        
        # 判断是否需要追问（如果回答有漏洞、逻辑不清，或需要深入时追问）
        # 只有高分（>=70）且有追问理由时才追问，低分不给追问机会
        need_followup = followup_reason is not None and score >= 70 and session.current_question_followup_count < 3
        
        if need_followup:
            # 生成追问
            followup_question = self.interviewer.generate_followup_question(
                current_question,
                answer,
                followup_reason,
            )
            session.current_question_followup_count += 1
            session.add_message("ai", followup_question)
            result["next_question"] = followup_question
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
        session = session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        if session.stage != InterviewStage.TECHNICAL_QNA:
            raise ValueError(f"当前阶段不是技术面试阶段: {session.stage}")

        current_question = self._get_current_ai_question(session)
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
        session = session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
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
            # 所有问题已回答，进入总结
            session.stage = InterviewStage.CONCLUDED
            result["stage"] = session.stage.value
            result["message"] = "所有技术问题已回答，面试结束"
        
        self._save_session(session)
        return result
    
    def conclude_interview(self, session_id: str) -> Dict:
        """
        总结面试
        
        Returns:
            包含最终评分和反馈的字典
        """
        session = session_manager.get_session(session_id)
        if not session:
            raise ValueError(f"会话不存在: {session_id}")
        
        # 生成总结
        final_score, feedback = self.interviewer.conclude_interview(session)
        
        session.final_score = final_score
        session.final_feedback = feedback
        session.stage = InterviewStage.CONCLUDED
        
        self._save_session(session)
        
        return {
            "final_score": final_score,
            "final_feedback": feedback,
            "average_score": session.get_average_score(),
            "stage": session.stage.value,
        }
    
    def get_session(self, session_id: str) -> Optional[InterviewSession]:
        """获取会话"""
        return session_manager.get_session(session_id)
    
    def _save_session(self, session: InterviewSession):
        """保存会话到数据库"""
        # 确保数据库表已创建
        init_db()
        
        db = get_db_session()
        try:
            record = db.query(InterviewRecord).filter(InterviewRecord.id == session.session_id).first()
            
            if record:
                # 更新
                record.candidate_name = session.candidate_name
                record.resume_content = session.resume_content
                record.job_requirements = session.job_requirements
                record.stage = session.stage.value
                record.history = session.history
                record.project_qa_list = [
                    {
                        "question": qa.question,
                        "answer": qa.answer,
                        "score": qa.score,
                        "feedback": qa.feedback,
                        "timestamp": qa.timestamp.isoformat(),
                    }
                    for qa in session.project_qa_list
                ]
                record.technical_qa_list = [
                    {
                        "question": qa.question,
                        "answer": qa.answer,
                        "score": qa.score,
                        "feedback": qa.feedback,
                        "timestamp": qa.timestamp.isoformat(),
                    }
                    for qa in session.technical_qa_list
                ]
                record.project_questions_count = session.project_questions_count
                record.target_project_questions = session.target_project_questions
                record.project_questions_pool = getattr(session, 'project_questions_pool', [])
                record.technical_questions_pool = getattr(session, 'technical_questions_pool', [])
                record.final_score = session.final_score
                record.final_feedback = session.final_feedback
                record.current_question_followup_count = session.current_question_followup_count
                record.updated_at = session.updated_at
            else:
                # 创建
                record = InterviewRecord(
                    id=session.session_id,
                    candidate_name=session.candidate_name,
                    resume_content=session.resume_content,
                    job_requirements=session.job_requirements,
                    stage=session.stage.value,
                    history=session.history,
                    project_qa_list=[
                        {
                            "question": qa.question,
                            "answer": qa.answer,
                            "score": qa.score,
                            "feedback": qa.feedback,
                            "timestamp": qa.timestamp.isoformat(),
                        }
                        for qa in session.project_qa_list
                    ],
                    technical_qa_list=[
                        {
                            "question": qa.question,
                            "answer": qa.answer,
                            "score": qa.score,
                            "feedback": qa.feedback,
                            "timestamp": qa.timestamp.isoformat(),
                        }
                        for qa in session.technical_qa_list
                    ],
                    project_questions_count=session.project_questions_count,
                    target_project_questions=session.target_project_questions,
                    project_questions_pool=getattr(session, 'project_questions_pool', []),
                    technical_questions_pool=getattr(session, 'technical_questions_pool', []),
                    final_score=session.final_score,
                    final_feedback=session.final_feedback,
                    current_question_followup_count=session.current_question_followup_count,
                    created_at=session.created_at,
                    updated_at=session.updated_at,
                )
                db.add(record)
            
            db.commit()
        except Exception as e:
            db.rollback()
            print(f"保存会话失败: {e}")
        finally:
            db.close()


# 全局服务实例
interview_service = InterviewService()
