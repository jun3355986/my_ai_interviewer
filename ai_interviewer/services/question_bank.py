"""
问题库管理服务：导入、拆分、embedding、存储到向量数据库
"""
import json
import os
import re
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

from langchain_chroma import Chroma
from langchain_community.document_loaders import PyPDFLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

from core.embeddings import get_embeddings
from schemas.question_item import QuestionItem, QuestionMedia


class QuestionBank:
    """问题库管理器"""

    RRF_K = 60
    VECTOR_WEIGHT = 1.0
    KEYWORD_WEIGHT = 1.15
    METADATA_MATCH_BOOST = 0.03
    HOTNESS_BOOST = 0.02
    
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
        
        fetch_k = max(k * 4, k, 20)
        vector_results = self.vectorstore.similarity_search(search_query, k=fetch_k)
        keyword_results = self._keyword_search(search_query, k=fetch_k)

        return self._merge_ranked_results(
            vector_results=vector_results,
            keyword_results=keyword_results,
            query=search_query,
            question_types=question_types or [],
            k=k,
        )

    def search_question_items(
        self,
        query: str,
        job_requirements: Optional[str] = None,
        question_types: Optional[List[str]] = None,
        k: int = 10,
    ) -> List[QuestionItem]:
        """检索结构化题目，兼容保留 search_questions 的 Document 返回。"""
        documents = self.search_questions(
            query=query,
            job_requirements=job_requirements,
            question_types=question_types,
            k=k,
        )
        return [QuestionItem.from_document(document) for document in documents]
    
    def get_question_count(self) -> int:
        """获取问题库中的问题总数"""
        return self.vectorstore._collection.count()

    def sync_structured_questions(self, questions: List[dict]) -> List[dict]:
        """同步 Admin 后台维护的结构化题目到向量库。"""
        documents: List[Document] = []
        ids: List[str] = []
        for question in questions:
            question_id = question.get("id")
            if question_id is None:
                raise ValueError("question id is required")
            question_text = (question.get("question_text") or "").strip()
            if not question_text:
                raise ValueError(f"question_text is required for id={question_id}")

            answer_reference = (question.get("answer_reference") or "").strip()
            question_type = (question.get("question_type") or "").strip()
            difficulty = (question.get("difficulty") or "").strip()
            skill_area = (question.get("skill_area") or "").strip()
            tags = question.get("tags") or []
            if not isinstance(tags, list):
                tags = []
            media_items = [
                item if isinstance(item, QuestionMedia) else QuestionMedia.model_validate(item)
                for item in question.get("media") or []
            ]

            content_parts = [question_text]
            for media in media_items:
                if media.caption:
                    content_parts.append(f"图注：{media.caption}")
                if media.alt:
                    content_parts.append(f"图片说明：{media.alt}")
            if answer_reference:
                content_parts.append(f"参考答案：{answer_reference}")
            if question_type:
                content_parts.append(f"题型：{question_type}")
            if difficulty:
                content_parts.append(f"难度：{difficulty}")
            if skill_area:
                content_parts.append(f"技能领域：{skill_area}")
            if tags:
                content_parts.append("标签：" + "、".join(str(tag) for tag in tags))

            vector_id = f"admin-question-{question_id}"
            documents.append(
                Document(
                    page_content="\n".join(content_parts),
                    metadata={
                        "source": "admin-question-bank",
                        "question_id": str(question_id),
                        "question_text": question_text,
                        "question_type": question_type,
                        "difficulty": difficulty,
                        "skill_area": skill_area,
                        "tags": ",".join(str(tag) for tag in tags),
                        "answer_reference": answer_reference,
                        "media_json": json.dumps(
                            [media.model_dump() for media in media_items],
                            ensure_ascii=False,
                        ),
                    },
                )
            )
            ids.append(vector_id)

        if documents:
            self.vectorstore.add_documents(documents, ids=ids)

        return [{"id": question.get("id"), "vector_store_id": vector_id} for question, vector_id in zip(questions, ids)]

    def delete_structured_questions(self, questions: List[dict]) -> List[dict]:
        """从向量库删除 Admin 后台下架、驳回或删除的题目。"""
        vector_ids: List[str] = []
        results: List[dict] = []
        for question in questions:
            question_id = question.get("id")
            if question_id is None:
                raise ValueError("question id is required")
            vector_id = f"admin-question-{question_id}"
            vector_ids.append(vector_id)
            results.append({"id": question_id, "vector_store_id": vector_id})

        if vector_ids:
            self.vectorstore.delete(ids=vector_ids)

        return results

    def _keyword_search(self, query: str, k: int) -> List[Document]:
        """Lightweight lexical recall over the local Chroma collection."""
        tokens = self._tokenize(query)
        if not tokens:
            return []

        documents = self._get_all_documents()
        scored_documents: List[tuple[float, Document]] = []
        for document in documents:
            score = self._keyword_score(tokens, document)
            if score > 0:
                scored_documents.append((score, document))

        scored_documents.sort(key=lambda item: item[0], reverse=True)
        return [document for _, document in scored_documents[:k]]

    def _get_all_documents(self) -> List[Document]:
        try:
            raw_collection = self.vectorstore.get(include=["documents", "metadatas"])
        except Exception:
            return []

        texts = raw_collection.get("documents") or []
        metadatas = raw_collection.get("metadatas") or []
        ids = raw_collection.get("ids") or []

        documents: List[Document] = []
        for index, text in enumerate(texts):
            metadata = metadatas[index] if index < len(metadatas) and metadatas[index] else {}
            metadata = dict(metadata)
            if index < len(ids) and ids[index] and not metadata.get("vector_store_id"):
                metadata["vector_store_id"] = ids[index]
            documents.append(Document(page_content=text or "", metadata=metadata))
        return documents

    def _merge_ranked_results(
        self,
        vector_results: Sequence[Document],
        keyword_results: Sequence[Document],
        query: str,
        question_types: Sequence[str],
        k: int,
    ) -> List[Document]:
        scores: Dict[str, float] = defaultdict(float)
        documents_by_key: Dict[str, Document] = {}

        for rank, document in enumerate(vector_results, start=1):
            key = self._document_key(document)
            documents_by_key.setdefault(key, document)
            scores[key] += self.VECTOR_WEIGHT / (self.RRF_K + rank)

        for rank, document in enumerate(keyword_results, start=1):
            key = self._document_key(document)
            documents_by_key.setdefault(key, document)
            scores[key] += self.KEYWORD_WEIGHT / (self.RRF_K + rank)

        for key, document in documents_by_key.items():
            scores[key] += self._metadata_score(query, question_types, document)

        ranked = sorted(
            documents_by_key.values(),
            key=lambda document: scores[self._document_key(document)],
            reverse=True,
        )
        return ranked[:k]

    def _metadata_score(
        self,
        query: str,
        question_types: Sequence[str],
        document: Document,
    ) -> float:
        metadata = document.metadata or {}
        query_tokens = set(self._tokenize(query))
        requested_types = {self._normalize_text(item) for item in question_types if item}
        metadata_fields = [
            metadata.get("question_type"),
            metadata.get("difficulty"),
            metadata.get("skill_area"),
            metadata.get("tags"),
        ]
        metadata_text = self._normalize_text(" ".join(str(value) for value in metadata_fields if value))

        score = 0.0
        for requested_type in requested_types:
            if requested_type and requested_type in metadata_text:
                score += self.METADATA_MATCH_BOOST

        for token in query_tokens:
            if len(token) >= 2 and token in metadata_text:
                score += self.METADATA_MATCH_BOOST / 2

        score += self._hotness_score(metadata)
        return score

    def _hotness_score(self, metadata: Dict[str, Any]) -> float:
        for field in ("trend_score", "hot_score", "popularity_score", "usage_score"):
            raw_score = metadata.get(field)
            if raw_score is None:
                continue
            try:
                return min(max(float(raw_score), 0.0), 1.0) * self.HOTNESS_BOOST
            except (TypeError, ValueError):
                continue
        return 0.0

    def _keyword_score(self, tokens: Sequence[str], document: Document) -> float:
        content = self._normalize_text(document.page_content)
        metadata = self._normalize_text(
            " ".join(str(value) for value in (document.metadata or {}).values() if value)
        )

        score = 0.0
        for token in tokens:
            if token in content:
                score += 2.0 + min(len(token), 12) / 12
            if token in metadata:
                score += 1.0
        return score

    def _tokenize(self, text: str) -> List[str]:
        normalized_text = self._normalize_text(text)
        raw_tokens = re.findall(r"[a-z0-9_+#.]+|[\u4e00-\u9fff]{2,}", normalized_text)
        tokens: List[str] = []
        for token in raw_tokens:
            if len(token) < 2:
                continue
            tokens.append(token)
            if re.fullmatch(r"[\u4e00-\u9fff]+", token) and len(token) > 4:
                for size in range(2, min(6, len(token)) + 1):
                    tokens.extend(token[index:index + size] for index in range(0, len(token) - size + 1))
        return list(dict.fromkeys(tokens))

    def _normalize_text(self, text: str) -> str:
        return re.sub(r"\s+", " ", str(text or "").lower()).strip()

    def _document_key(self, document: Document) -> str:
        metadata = document.metadata or {}
        for field in ("question_id", "vector_store_id"):
            value = metadata.get(field)
            if value:
                return f"{field}:{value}"
        source = metadata.get("source", "")
        if source:
            return f"source:{source}:content:{document.page_content[:200]}"
        return f"content:{document.page_content[:200]}"
