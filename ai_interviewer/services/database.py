"""
数据库模型和连接管理
"""
from datetime import datetime
from typing import Optional

from sqlalchemy import create_engine, Column, String, Integer, Text, DateTime, Float, JSON
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from pathlib import Path


Base = declarative_base()


class InterviewRecord(Base):
    """面试记录数据模型"""
    __tablename__ = "interview_records"
    
    id = Column(String, primary_key=True)  # session_id
    candidate_name = Column(String, nullable=True)
    resume_content = Column(Text, nullable=True)
    job_requirements = Column(Text, nullable=True)
    stage = Column(String, nullable=False)
    history = Column(JSON, default=list)
    project_qa_list = Column(JSON, default=list)
    technical_qa_list = Column(JSON, default=list)
    project_questions_count = Column(Integer, default=0)
    target_project_questions = Column(Integer, default=5)
    project_questions_pool = Column(JSON, default=list)  # 项目问题池
    technical_questions_pool = Column(JSON, default=list)  # 技术问题池
    final_score = Column(Integer, nullable=True)
    final_feedback = Column(Text, nullable=True)
    current_question_followup_count = Column(Integer, default=0) # 当前问题追问次数
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)


def get_db_engine():
    """获取数据库引擎"""
    db_path = Path(__file__).parent.parent / "storage" / "database" / "interviews.db"
    db_path.parent.mkdir(parents=True, exist_ok=True)
    engine = create_engine(f"sqlite:///{db_path}", echo=False)
    return engine


def init_db():
    """初始化数据库表（如果不存在则创建，如果缺少列则添加）"""
    engine = get_db_engine()
    Base.metadata.create_all(engine)
    
    # 检查并添加可能缺失的列（用于数据库迁移）
    try:
        from sqlalchemy import inspect, text
        
        inspector = inspect(engine)
        columns = {col['name'] for col in inspector.get_columns('interview_records')}
        
        # 如果缺少 project_questions_pool 列，则添加
        if 'project_questions_pool' not in columns:
            with engine.connect() as conn:
                conn.execute(text(
                    "ALTER TABLE interview_records ADD COLUMN project_questions_pool TEXT DEFAULT '[]'"
                ))
                conn.commit()
        
        # 如果缺少 current_question_followup_count 列，则添加
        if 'current_question_followup_count' not in columns:
            with engine.connect() as conn:
                conn.execute(text(
                    "ALTER TABLE interview_records ADD COLUMN current_question_followup_count INTEGER DEFAULT 0"
                ))
                conn.commit()

        # 如果缺少 technical_questions_pool 列，则添加
        if 'technical_questions_pool' not in columns:
            with engine.connect() as conn:
                conn.execute(text(
                    "ALTER TABLE interview_records ADD COLUMN technical_questions_pool TEXT DEFAULT '[]'"
                ))
                conn.commit()
    except Exception as e:
        # 如果表不存在，create_all 会创建它，这里的错误可以忽略
        pass


def get_db_session():
    """获取数据库会话"""
    engine = get_db_engine()
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    return SessionLocal()

