"""
面试流程服务：管理完整的面试流程
"""
import uuid
from typing import Optional, List, Dict

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
    
    def __init__(self):
        self.interviewer = Interviewer()
        self.resume_parser = ResumeParser()
        # 初始化数据库
        init_db()
    
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
            result["stage"] = session.stage.value
            result["message"] = "项目提问环节结束，进入技术面试环节"
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
            result["stage"] = session.stage.value
            result["message"] = "项目提问环节结束，进入技术面试环节"
        
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
        
        # 选择技术问题
        questions = self.interviewer.select_technical_questions(
            session,
            question_types,
            counts,
        )
        
        if not questions:
            # 如果没有问题，使用默认提示
            question = "请介绍一下Java中HashMap的实现原理？"
        else:
            question = questions[0]
        
        session.add_message("ai", question)
        session.technical_questions_pool = questions[1:]  # 保存剩余问题
        
        self._save_session(session)
        
        return {
            "question": question,
            "remaining_questions": len(questions) - 1,
            "stage": session.stage.value,
        }
    
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
                current_question = msg.get("content")
                break
        
        if not current_question:
            current_question = "技术问题"
        
        # 记录回答
        session.add_message("human", answer)
        
        # 评估回答
        score, feedback, _ = self.interviewer.evaluate_answer(
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
        session.add_technical_qa(qa)
        
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
        
        # 获取下一个问题
        questions_pool = session.technical_questions_pool
        
        if questions_pool:
            next_question = questions_pool.pop(0)
            session.add_message("ai", next_question)
            session.technical_questions_pool = questions_pool
            result["next_question"] = next_question
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

