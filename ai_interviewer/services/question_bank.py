"""
问题库管理服务：导入、拆分、embedding、存储到向量数据库
"""
import os
from pathlib import Path
from typing import List, Optional

from langchain_chroma import Chroma
from langchain_community.document_loaders import PyPDFLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

from core.embeddings import get_embeddings


class QuestionBank:
    """问题库管理器"""
    
    def __init__(self, collection_name: str = "interview_questions"):
        """
        初始化问题库
        
        Args:
            collection_name: Chroma集合名称
        """
        # 统一走 core/embeddings.py，不在业务层散落模型接入细节
        self.embeddings = get_embeddings()
        
        # Chroma向量数据库
        persist_directory = str(Path(__file__).parent.parent / "storage" / "vector_db")
        os.makedirs(persist_directory, exist_ok=True)
        
        self.vectorstore = Chroma(
            collection_name=collection_name,
            embedding_function=self.embeddings,
            persist_directory=persist_directory,
        )
        
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            length_function=len,
            separators=["\n\n", "\n", "。", "，", " ", ""],
        )
    
    def import_question_file(self, file_path: str) -> int:
        """
        导入问题文件（PDF或文本）
        
        Args:
            file_path: 文件路径
            
        Returns:
            导入的问题数量
        """
        path = Path(file_path)
        suffix = path.suffix.lower()
        
        # 加载文档
        if suffix == '.pdf':
            loader = PyPDFLoader(file_path)
        elif suffix in ['.txt', '.md']:
            loader = TextLoader(file_path, encoding='utf-8')
        else:
            raise ValueError(f"不支持的文件格式: {suffix}")
        
        documents = loader.load()
        
        # 拆分文档
        texts = self.text_splitter.split_documents(documents)
        
        # 添加到向量数据库（新版本的 Chroma 会自动持久化，无需手动调用 persist()）
        self.vectorstore.add_documents(texts)
        
        return len(texts)
    
    def search_questions(
        self,
        query: str,
        job_requirements: Optional[str] = None,
        question_types: Optional[List[str]] = None,
        k: int = 10,
    ) -> List[Document]:
        """
        检索相关问题
        
        Args:
            query: 查询文本（职位要求、候选人的强项/弱项等）
            job_requirements: 职位要求
            question_types: 问题类型列表（如 ["Java基础", "多线程"]）
            k: 返回的问题数量
            
        Returns:
            相关文档列表
        """
        # 构建检索查询
        search_query = query
        if job_requirements:
            search_query = f"{job_requirements}\n{query}"
        
        if question_types:
            type_filter = " ".join(question_types)
            search_query = f"{search_query}\n{type_filter}"
        
        # 相似度检索
        results = self.vectorstore.similarity_search(search_query, k=k)
        
        return results
    
    def get_question_count(self) -> int:
        """获取问题库中的问题总数"""
        return self.vectorstore._collection.count()
