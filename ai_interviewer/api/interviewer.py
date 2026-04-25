"""
AI面试官：支持完整面试流程的智能面试助手
"""
import json
import re
from typing import List, Dict, Optional, Tuple

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from core.config import get_llm
from services.interview_session import InterviewSession, InterviewStage
from services.question_bank import QuestionBank


class Interviewer:
    """智能面试官"""
    
    def __init__(self, model: Optional[str] = None) -> None:
        self.llm = get_llm(model=model)
        self.question_bank = QuestionBank()
    
    def generate_opening(self, resume_content: str, job_requirements: Optional[str] = None) -> str:
        """
        生成面试开场白
        
        Args:
            resume_content: 简历内容
            job_requirements: 职位要求
            
        Returns:
            开场白文本
        """
        context = f"简历内容：\n{resume_content[:2000]}\n"  # 限制长度
        if job_requirements:
            context += f"\n职位要求：\n{job_requirements}"
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "根据职位要求和当前岗位竞争者水平，评价面试者的简历。"
                        "要做到真实、客观，以高要求、严格的方式对待面试者，不应该太客气。"
                        "现在需要你做一个简洁的面试开场白（2-3句话），欢迎面试者并说明面试流程。"),
            ("human", context + "\n\n请生成面试开场白："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        return chain.invoke({})
    
    def ask_self_introduction(self) -> str:
        """请面试者自我介绍"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "现在面试刚开始，请礼貌地请面试者做一个自我介绍。"
                        "只需要一句话即可。"),
            ("human", "请让面试者做自我介绍"),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        return chain.invoke({})
    
    def generate_project_questions(
        self,
        session: InterviewSession,
        question_count: int = 1,
    ) -> List[str]:
        """
        根据简历生成项目相关问题
        
        Args:
            session: 面试会话
            question_count: 要生成的问题数量
            
        Returns:
            问题列表
        """
        resume_content = session.resume_content or ""
        job_requirements = session.job_requirements or ""
        
        # 根据简历丰富度决定问题数量
        if len(resume_content) < 500:
            target_count = min(3, question_count)
        elif len(resume_content) < 1500:
            target_count = min(5, question_count)
        else:
            target_count = min(10, question_count)
        
        context = f"简历内容：\n{resume_content[:2000]}\n"
        if job_requirements:
            context += f"\n职位要求：\n{job_requirements}"
        context += f"\n\n已问过的项目问题数：{session.project_questions_count}"
        context += f"\n目标问题数：{target_count}"
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "根据简历的项目亮点、难点提问。"
                        "要求：\n"
                        "1. 问题要简洁明确；\n"
                        "2. 重点关注项目中的技术难点、架构设计、问题解决等；\n"
                        "3. 根据简历内容的丰富度，提出合适深度的问题。\n\n"
                        "请生成{count}个项目相关的问题，只输出问题，不要输出任何其他内容，每个问题一行，用序号标记。"),
            ("human", context + "\n\n请生成项目问题："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        response = chain.invoke({"count": target_count})
        
        # 解析问题列表
        questions = []
        for line in response.split('\n'):
            line = line.strip()
            if not line:
                continue
            # 移除序号（如 "1. " 或 "1、"）
            line = re.sub(r'^\d+[\.、]\s*', '', line)
            if line:
                questions.append(line)
        
        return questions[:target_count]
    
    def evaluate_answer(
        self,
        question: str,
        answer: str,
        resume_content: Optional[str] = None,
    ) -> Tuple[int, str, Optional[str]]:
        """
        评估面试者回答并打分
        
        Args:
            question: 问题
            answer: 回答
            resume_content: 简历内容（用于验证一致性）
            
        Returns:
            (分数, 反馈, 是否追问)
        """
        context = f"问题：{question}\n回答：{answer}\n"
        if resume_content:
            context += f"\n简历内容（参考）：\n{resume_content[:1000]}"
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "请评估面试者的回答：\n"
                        "1. 给出客观的分数（0-100分），必须客观，不要虚高；\n"
                        "2. 提供结构化反馈，指出优点和不足；\n"
                        "3. 判断是否需要追问（如果回答有明显漏洞、逻辑不清，或需要深入时追问）；\n"
                        "4. 如果明显缺乏实际经验、只是背诵或随意回答，给低分并说明理由。\n\n"
                        "请以JSON格式返回：{{\"score\": 分数, \"feedback\": \"反馈内容\", \"need_followup\": true/false, \"followup_reason\": \"追问原因（如果需要追问）\"}}"),
            ("human", context + "\n\n请评估这个回答："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        response = chain.invoke({})
        
        # 解析JSON响应
        try:
            # 提取JSON部分
            json_match = re.search(r'\{[^}]+\}', response, re.DOTALL)
            if json_match:
                result = json.loads(json_match.group())
            else:
                # 如果没找到JSON，尝试解析整段文本
                result = {"score": 70, "feedback": response, "need_followup": False}
            
            score = int(result.get("score", 70))
            feedback = result.get("feedback", "回答基本符合要求")
            need_followup = result.get("need_followup", False)
            followup_reason = result.get("followup_reason")
            
            return score, feedback, followup_reason if need_followup else None
            
        except Exception as e:
            # 解析失败，返回默认值
            print(f"解析评估结果失败: {e}, 响应: {response}")
            return 70, "回答基本符合要求", None
    
    def generate_followup_question(
        self,
        original_question: str,
        answer: str,
        followup_reason: str,
    ) -> str:
        """生成追问问题"""
        context = f"原问题：{original_question}\n回答：{answer}\n追问原因：{followup_reason}"
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "根据面试者的回答和追问原因，生成一个简洁明确的追问问题。"
                        "追问问题要有面试价值，不要问无意义的问题。"),
            ("human", context + "\n\n请生成追问问题："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        return chain.invoke({})
    
    def select_technical_questions(
        self,
        session: InterviewSession,
        question_types: List[str],
        counts: Dict[str, int],
    ) -> List[str]:
        """
        选择技术面试题
        
        Args:
            session: 面试会话
            question_types: 问题类型列表（如 ["Java基础", "多线程"]）
            counts: 各类型的题目数量（如 {"Java基础": 3, "多线程": 2}）
            
        Returns:
            问题列表
        """
        # 构建检索查询
        query_parts = []
        if session.job_requirements:
            query_parts.append(session.job_requirements)
        
        # 添加问题类型
        type_str = " ".join(question_types)
        query_parts.append(type_str)
        
        # 添加候选人强项/弱项（如果有历史问答）
        if session.project_qa_list:
            avg_score = session.get_average_score()
            if avg_score:
                query_parts.append(f"候选人平均分: {avg_score:.1f}")
        
        query = "\n".join(query_parts)
        print(f"检索查询内容: {query}")
        
        # 检索问题
        total_count = sum(counts.values())
        documents = self.question_bank.search_questions(query, k=total_count * 2)
        
        # 简单筛选：提取问题文本
        questions = []
        for doc in documents[:total_count]:
            content = doc.page_content.strip()
            # 提取问题（假设格式是 "问题：xxx" 或直接是问题）
            if "问题：" in content:
                q = content.split("问题：")[1].split("\n")[0].strip()
            elif "问：" in content:
                q = content.split("问：")[1].split("\n")[0].strip()
            else:
                q = content.split("\n")[0].strip()
            
            if q and len(q) > 10:  # 过滤太短的内容
                questions.append(q)
        
        return questions[:total_count]
    
    def conclude_interview(self, session: InterviewSession) -> Tuple[int, str]:
        """
        总结面试并给出最终评分
        
        Args:
            session: 面试会话
            
        Returns:
            (最终分数, 总结反馈)
        """
        # 收集所有问答记录
        all_qa = session.project_qa_list + session.technical_qa_list
        qa_summary = []
        for qa in all_qa:
            qa_summary.append(f"问题：{qa.question}\n回答：{qa.answer}\n得分：{qa.score}")
        
        context = f"面试会话总结：\n"
        context += f"项目问题数：{len(session.project_qa_list)}\n"
        context += f"技术问题数：{len(session.technical_qa_list)}\n"
        context += f"\n详细问答记录：\n" + "\n\n".join(qa_summary)
        
        if session.resume_content:
            context += f"\n\n简历内容：\n{session.resume_content[:1000]}"
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官。"
                        "请总结这次面试情况：\n"
                        "1. 概括面试者的整体表现；\n"
                        "2. 指出优点和需要改进的地方；\n"
                        "3. 给出客观的总体评分（0-100分），必须客观，不要虚高；\n"
                        "4. 提供具体的改进建议。\n\n"
                        "请以JSON格式返回：{{\"final_score\": 分数, \"feedback\": \"详细反馈内容\"}}"),
            ("human", context + "\n\n请总结面试并给出最终评分："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        response = chain.invoke({})
        
        # 解析JSON
        try:
            json_match = re.search(r'\{[^}]+\}', response, re.DOTALL)
            if json_match:
                result = json.loads(json_match.group())
            else:
                avg_score = session.get_average_score()
                final_score = int(avg_score) if avg_score else 70
                result = {"final_score": final_score, "feedback": response}
            
            final_score = int(result.get("final_score", 70))
            feedback = result.get("feedback", "面试完成")
            return final_score, feedback
            
        except Exception as e:
            print(f"解析总结结果失败: {e}, 响应: {response}")
            avg_score = session.get_average_score()
            final_score = int(avg_score) if avg_score else 70
            return final_score, "面试完成，请继续努力提升技术水平。"
    
    def ask(self, question: str, history: Optional[List[Dict]] = None) -> str:
        """
        简单对话接口（保留向后兼容）
        """
        safe_history = history or []
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位专业的高级JAVA开发工程师面试官，要求：\n"
                        "- 问题要简洁明确；\n"
                        "- 回答要结构化，突出重点；\n"
                        "- 必要时指出改进建议，但保持礼貌；"),
            MessagesPlaceholder(variable_name="history"),
            ("human", "{question}"),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        return chain.invoke({
            "question": question,
            "history": safe_history,
        })

