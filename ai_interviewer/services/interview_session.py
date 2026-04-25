"""
面试会话管理：管理面试流程状态和记录
"""
from enum import Enum
from typing import Dict, List, Optional, TYPE_CHECKING
from datetime import datetime
from dataclasses import dataclass, field

if TYPE_CHECKING:
    from services.database import InterviewRecord


class InterviewStage(Enum):
    """面试阶段枚举"""
    RESUM_SUBMITTED = "resume_submitted"  # 简历已提交
    OPENING = "opening"  # 开场白
    SELF_INTRO = "self_introduction"  # 自我介绍
    PROJECT_QNA = "project_qna"  # 项目提问环节
    TECHNICAL_QNA = "technical_qna"  # 技术面试环节
    CONCLUDED = "concluded"  # 面试结束


@dataclass
class QuestionAnswer:
    """单个问答记录"""
    question: str
    answer: str
    score: Optional[int] = None  # 0-100分
    feedback: Optional[str] = None
    timestamp: datetime = field(default_factory=datetime.now)


@dataclass
class InterviewSession:
    """面试会话"""
    session_id: str
    candidate_name: Optional[str] = None
    resume_content: Optional[str] = None
    job_requirements: Optional[str] = None
    stage: InterviewStage = InterviewStage.RESUM_SUBMITTED
    history: List[Dict] = field(default_factory=list)  # 对话历史
    project_qa_list: List[QuestionAnswer] = field(default_factory=list)
    technical_qa_list: List[QuestionAnswer] = field(default_factory=list)
    project_questions_count: int = 0  # 已问的项目问题数
    target_project_questions: int = 5  # 目标项目问题数
    project_questions_pool: List[str] = field(default_factory=list)  # 项目问题池
    technical_questions_pool: List[str] = field(default_factory=list)  # 技术问题池
    final_score: Optional[int] = None
    final_feedback: Optional[str] = None
    current_question_followup_count: int = 0 # 当前问题追问次数
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
    
    def add_message(self, role: str, content: str):
        """添加对话消息"""
        self.history.append({"role": role, "content": content})
        self.updated_at = datetime.now()
    
    def add_project_qa(self, qa: QuestionAnswer):
        """添加项目问答"""
        self.project_qa_list.append(qa)
        self.project_questions_count += 1
        self.updated_at = datetime.now()
    
    def add_technical_qa(self, qa: QuestionAnswer):
        """添加技术问答"""
        self.technical_qa_list.append(qa)
        self.updated_at = datetime.now()
    
    def get_average_score(self) -> Optional[float]:
        """计算平均分"""
        all_scores = [
            qa.score for qa in self.project_qa_list + self.technical_qa_list
            if qa.score is not None
        ]
        if not all_scores:
            return None
        return sum(all_scores) / len(all_scores)


class SessionManager:
    """会话管理器（支持内存缓存和数据库持久化）"""
    
    def __init__(self):
        self.sessions: Dict[str, InterviewSession] = {}
    
    def create_session(
        self,
        session_id: str,
        resume_content: Optional[str] = None,
        job_requirements: Optional[str] = None,
    ) -> InterviewSession:
        """创建新会话"""
        session = InterviewSession(
            session_id=session_id,
            resume_content=resume_content,
            job_requirements=job_requirements,
        )
        self.sessions[session_id] = session
        return session
    
    def get_session(self, session_id: str) -> Optional[InterviewSession]:
        """
        获取会话，如果内存中没有则从数据库加载
        
        Args:
            session_id: 会话ID
            
        Returns:
            会话对象，如果不存在则返回None
        """
        # 先从内存查找
        if session_id in self.sessions:
            return self.sessions[session_id]
        
        # 内存中没有，从数据库加载
        return self._load_from_database(session_id)
    
    def _load_from_database(self, session_id: str) -> Optional[InterviewSession]:
        """
        从数据库加载会话
        
        Args:
            session_id: 会话ID
            
        Returns:
            会话对象，如果数据库中没有则返回None
        """
        try:
            from services.database import get_db_session, InterviewRecord, init_db
            
            # 确保数据库表已创建
            init_db()
            
            db = get_db_session()
            try:
                record = db.query(InterviewRecord).filter(InterviewRecord.id == session_id).first()
                
                if not record:
                    return None
                
                # 将数据库记录转换为 InterviewSession 对象
                
                session = self._record_to_session(record)
                
                # 加载到内存缓存
                self.sessions[session_id] = session
                
                return session
            finally:
                db.close()
        except Exception as e:
            print(f"从数据库加载会话失败: {e}")
            return None
    
    def _record_to_session(self, record) -> InterviewSession:
        """
        将数据库记录转换为 InterviewSession 对象
        
        Args:
            record: 数据库记录
            
        Returns:
            InterviewSession 对象
        """
        # 转换问答记录
        project_qa_list = []
        if record.project_qa_list:
            for qa_data in record.project_qa_list:
                qa = QuestionAnswer(
                    question=qa_data.get("question", ""),
                    answer=qa_data.get("answer", ""),
                    score=qa_data.get("score"),
                    feedback=qa_data.get("feedback"),
                    timestamp=datetime.fromisoformat(qa_data.get("timestamp", datetime.now().isoformat())),
                )
                project_qa_list.append(qa)
        
        technical_qa_list = []
        if record.technical_qa_list:
            for qa_data in record.technical_qa_list:
                qa = QuestionAnswer(
                    question=qa_data.get("question", ""),
                    answer=qa_data.get("answer", ""),
                    score=qa_data.get("score"),
                    feedback=qa_data.get("feedback"),
                    timestamp=datetime.fromisoformat(qa_data.get("timestamp", datetime.now().isoformat())),
                )
                technical_qa_list.append(qa)
        
        # 转换阶段枚举
        try:
            stage = InterviewStage(record.stage)
        except ValueError:
            stage = InterviewStage.RESUM_SUBMITTED
        
        # 创建会话对象
        session = InterviewSession(
            session_id=record.id,
            candidate_name=record.candidate_name,
            resume_content=record.resume_content,
            job_requirements=record.job_requirements,
            stage=stage,
            history=record.history or [],
            project_qa_list=project_qa_list,
            technical_qa_list=technical_qa_list,
            project_questions_count=record.project_questions_count,
            target_project_questions=record.target_project_questions,
            final_score=record.final_score,
            final_feedback=record.final_feedback,
            current_question_followup_count=getattr(record, 'current_question_followup_count', 0),
            created_at=record.created_at or datetime.now(),
            updated_at=record.updated_at or datetime.now(),
        )
        
        # 恢复 project_questions_pool
        if hasattr(record, 'project_questions_pool') and record.project_questions_pool:
            session.project_questions_pool = record.project_questions_pool
        else:
            session.project_questions_pool = []
        
        # 恢复 technical_questions_pool
        if hasattr(record, 'technical_questions_pool') and record.technical_questions_pool:
            session.technical_questions_pool = record.technical_questions_pool
        else:
            session.technical_questions_pool = []
        
        return session
    
    def update_session(self, session: InterviewSession):
        """更新会话"""
        session.updated_at = datetime.now()
        self.sessions[session.session_id] = session
    
    def list_sessions(self) -> List[InterviewSession]:
        """列出所有会话"""
        return list(self.sessions.values())


# 全局会话管理器
session_manager = SessionManager()

