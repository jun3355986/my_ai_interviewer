import unittest

from langchain_core.documents import Document

from services.question_bank import QuestionBank


class FakeVectorStore:
    def __init__(self, vector_results, all_documents):
        self._vector_results = vector_results
        self._all_documents = all_documents
        self.deleted_ids = []

    def similarity_search(self, query, k=10):
        return self._vector_results[:k]

    def get(self, include=None):
        return {
            "documents": [document.page_content for document in self._all_documents],
            "metadatas": [document.metadata for document in self._all_documents],
            "ids": [document.metadata.get("question_id") for document in self._all_documents],
        }

    def delete(self, ids=None):
        self.deleted_ids.extend(ids or [])


class QuestionBankHybridSearchTest(unittest.TestCase):
    def make_bank(self, vector_results, all_documents):
        bank = QuestionBank.__new__(QuestionBank)
        bank.vectorstore = FakeVectorStore(vector_results, all_documents)
        return bank

    def test_keyword_match_can_outrank_vector_only_results(self):
        redis_doc = Document(
            page_content="Redis 缓存雪崩、缓存击穿、缓存穿透分别怎么解决？",
            metadata={"question_id": "redis", "question_type": "技术题"},
        )
        generic_doc = Document(
            page_content="请介绍一次你做过的系统架构优化。",
            metadata={"question_id": "generic", "question_type": "技术题"},
        )
        kafka_doc = Document(
            page_content="Kafka ISR 机制是什么？",
            metadata={"question_id": "kafka", "question_type": "技术题"},
        )
        bank = self.make_bank(
            vector_results=[generic_doc, kafka_doc, redis_doc],
            all_documents=[generic_doc, kafka_doc, redis_doc],
        )

        results = bank.search_questions("Java 后端 Redis 缓存雪崩 击穿 穿透", k=2)

        self.assertEqual("redis", results[0].metadata["question_id"])

    def test_continuous_chinese_query_can_match_partial_terms(self):
        redis_doc = Document(
            page_content="缓存雪崩、缓存击穿、缓存穿透分别怎么解决？",
            metadata={"question_id": "redis"},
        )
        generic_doc = Document(
            page_content="请介绍一次你做过的系统架构优化。",
            metadata={"question_id": "generic"},
        )
        bank = self.make_bank(
            vector_results=[generic_doc, redis_doc],
            all_documents=[generic_doc, redis_doc],
        )

        results = bank.search_questions("缓存雪崩怎么解决", k=1)

        self.assertEqual("redis", results[0].metadata["question_id"])

    def test_metadata_match_boosts_question_type_and_skill_area(self):
        java_doc = Document(
            page_content="HashMap 在 JDK 8 中为什么会转红黑树？",
            metadata={
                "question_id": "java",
                "question_type": "Java基础",
                "skill_area": "Java集合",
                "tags": "HashMap,集合",
            },
        )
        db_doc = Document(
            page_content="PostgreSQL 的 MVCC 是怎么实现的？",
            metadata={
                "question_id": "db",
                "question_type": "数据库",
                "skill_area": "PostgreSQL",
                "tags": "MVCC,数据库",
            },
        )
        bank = self.make_bank(
            vector_results=[db_doc, java_doc],
            all_documents=[db_doc, java_doc],
        )

        results = bank.search_questions(
            "Java HashMap 红黑树",
            question_types=["Java基础", "Java集合"],
            k=1,
        )

        self.assertEqual("java", results[0].metadata["question_id"])

    def test_same_source_chunks_are_not_deduplicated(self):
        first_doc = Document(
            page_content="问题：Redis 缓存淘汰策略有哪些？",
            metadata={"source": "questions.md"},
        )
        second_doc = Document(
            page_content="问题：Redis 持久化 RDB 和 AOF 有什么区别？",
            metadata={"source": "questions.md"},
        )
        bank = self.make_bank(
            vector_results=[first_doc, second_doc],
            all_documents=[first_doc, second_doc],
        )

        results = bank.search_questions("Redis", k=2)

        self.assertEqual(2, len(results))

    def test_delete_structured_questions_removes_admin_vector_ids(self):
        redis_doc = Document(
            page_content="Redis 缓存淘汰策略有哪些？",
            metadata={"question_id": "redis"},
        )
        bank = self.make_bank(vector_results=[redis_doc], all_documents=[redis_doc])

        result = bank.delete_structured_questions([{"id": 42}])

        self.assertEqual([{"id": 42, "vector_store_id": "admin-question-42"}], result)
        self.assertEqual(["admin-question-42"], bank.vectorstore.deleted_ids)


if __name__ == "__main__":
    unittest.main()
