"""
优化的面试测试脚本 - 使用两个 Agent 模拟完整面试流程
- InterviewerAgent: 面试官 Agent，负责提问
- CandidateAgent: 面试者 Agent，负责回答问题
"""
import requests
import json
import random
from typing import Optional, Dict, List
from core.config import get_llm
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

BASE_URL = "http://127.0.0.1:8000"


class CandidateAgent:
    """面试者 Agent - 根据简历和问题生成回答"""
    
    def __init__(self, resume_content: str, candidate_name: str = "张三"):
        self.resume_content = resume_content
        self.candidate_name = candidate_name
        self.llm = get_llm()
        self.answer_history = []  # 记录已回答的问题，保持一致性
    
    def generate_self_introduction(self) -> str:
        """生成自我介绍"""
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一位有经验的Java开发工程师，正在参加面试。"
                        "请根据简历内容，生成一个简洁的自我介绍（2-3句话），"
                        "突出你的工作经验和主要技能。"),
            ("human", f"简历内容：\n{self.resume_content[:1500]}\n\n请生成自我介绍："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        return chain.invoke({})
    
    def generate_answer(self, question: str, question_type: str = "project") -> str:
        """
        根据问题生成回答
        
        Args:
            question: 问题内容
            question_type: 问题类型（"project" 或 "technical"）
        """
        # 构建上下文
        context = f"简历内容：\n{self.resume_content[:1500]}\n\n"
        
        # 添加历史回答（保持一致性）
        if self.answer_history:
            context += "已回答的问题：\n"
            for q, a in self.answer_history[-3:]:  # 只保留最近3个
                context += f"Q: {q}\nA: {a}\n\n"
        
        if question_type == "project":
            system_prompt = (
                "你是一位有经验的Java开发工程师，正在参加项目经验面试。"
                "请根据简历中的项目经验，回答面试官的问题。"
                "要求：\n"
                "1. 回答要具体、真实，体现实际项目经验；\n"
                "2. 可以提到技术细节、遇到的问题和解决方案；\n"
                "3. 回答要简洁明了，控制在100-200字；\n"
                "4. 如果简历中没有相关内容，可以基于经验合理推断，但不要编造。"
            )
        else:  # technical
            system_prompt = (
                "你是一位有经验的Java开发工程师，正在参加技术面试。"
                "请根据你的技术知识，回答面试官的问题。"
                "要求：\n"
                "1. 回答要准确、专业；\n"
                "2. 可以结合项目经验举例说明；\n"
                "3. 回答要结构化，突出重点；\n"
                "4. 控制在100-200字。"
            )
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("human", f"{context}\n\n问题：{question}\n\n请回答："),
        ])
        
        chain = prompt | self.llm | StrOutputParser()
        answer = chain.invoke({})
        
        # 记录问答历史
        self.answer_history.append((question, answer))
        
        return answer


class InterviewerAgent:
    """面试官 Agent - 负责调用 API 进行提问"""
    
    def __init__(self):
        self.session_id: Optional[str] = None
        self.stage: Optional[str] = None
    
    def start_interview(
        self,
        resume_content: str,
        job_requirements: str,
        candidate_name: str,
    ) -> Dict:
        """开始面试"""
        response = requests.post(
            f"{BASE_URL}/interview/start",
            json={
                "resume_content": resume_content,
                "job_requirements": job_requirements,
                "candidate_name": candidate_name,
            }
        )
        
        if response.status_code != 200:
            raise Exception(f"启动面试失败: {response.text}")
        
        data = response.json()
        self.session_id = data["session_id"]
        self.stage = data["stage"]
        
        return data
    
    def get_opening_response(self) -> Dict:
        """获取开场后的第一个问题（自我介绍）"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/opening-response"
        )
        
        if response.status_code != 200:
            raise Exception(f"获取开场响应失败: {response.text}")
        
        return response.json()
    
    def submit_self_introduction(self, answer: str) -> Dict:
        """提交自我介绍"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/self-introduction",
            json={
                "session_id": self.session_id,
                "answer": answer,
            }
        )
        
        if response.status_code != 200:
            raise Exception(f"提交自我介绍失败: {response.text}")
        
        data = response.json()
        self.stage = data.get("stage", self.stage)
        return data
    
    def submit_project_answer(self, answer: str) -> Dict:
        """提交项目问题回答"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/project-answer",
            json={
                "session_id": self.session_id,
                "answer": answer,
            }
        )
        
        if response.status_code != 200:
            raise Exception(f"提交项目回答失败: {response.text}")
        
        data = response.json()
        self.stage = data.get("stage", self.stage)
        return data
    
    def start_technical_interview(
        self,
        question_types: List[str],
        counts: Dict[str, int],
    ) -> Dict:
        """开始技术面试"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/start-technical",
            json={
                "session_id": self.session_id,
                "question_types": question_types,
                "counts": counts,
            }
        )
        
        if response.status_code != 200:
            raise Exception(f"开始技术面试失败: {response.text}")
        
        data = response.json()
        self.stage = data.get("stage", self.stage)
        return data
    
    def submit_technical_answer(self, answer: str) -> Dict:
        """提交技术问题回答"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/technical-answer",
            json={
                "session_id": self.session_id,
                "answer": answer,
            }
        )
        
        if response.status_code != 200:
            raise Exception(f"提交技术回答失败: {response.text}")
        
        data = response.json()
        self.stage = data.get("stage", self.stage)
        return data
    
    def conclude_interview(self) -> Dict:
        """结束面试并获取总结"""
        response = requests.post(
            f"{BASE_URL}/interview/{self.session_id}/conclude"
        )
        
        if response.status_code != 200:
            raise Exception(f"结束面试失败: {response.text}")
        
        return response.json()


def test_interview_flow():
    """测试完整的面试流程"""
    
    print("=" * 70)
    print("AI 面试助手 - 完整流程测试（双 Agent 模式）")
    print("=" * 70)
    
    # 准备简历和职位要求
    # 注意：简历长度控制在合理范围，使系统自动设置的目标问题数在2-4个范围内
    resume_content = """
    姓名：张三
    工作经验：5年Java开发经验
    
    项目经验：
    1. 电商系统（2020-2022）
       - 负责订单模块开发，使用Spring Boot + Redis
       - 优化了订单查询性能，响应时间从500ms降低到50ms
       - 使用消息队列处理高并发订单
       - 解决了分布式事务问题，使用Seata实现
    
    2. 支付系统（2022-2024）
       - 负责支付网关开发
       - 使用Spring Cloud微服务架构
       - 处理日交易量100万+的支付请求
       - 实现了支付幂等性保证
       - 使用Redis实现分布式锁，防止重复支付
    
    技术栈：Java、Spring Boot、Spring Cloud、Redis、MySQL、RabbitMQ
    """
    
    job_requirements = "Java高级开发工程师，要求3年以上经验，熟悉Spring Boot、Redis、消息队列、分布式系统等"
    candidate_name = "张三"
    
    # 创建两个 Agent
    interviewer = InterviewerAgent()
    candidate = CandidateAgent(resume_content, candidate_name)
    
    # ============ 步骤 1: 开始面试 ============
    print("\n" + "=" * 70)
    print("[步骤 1] 开始面试")
    print("=" * 70)
    
    start_data = interviewer.start_interview(
        resume_content=resume_content,
        job_requirements=job_requirements,
        candidate_name=candidate_name,
    )
    
    print(f"✅ 面试已开始，会话ID: {start_data['session_id']}")
    print(f"\n📢 面试官开场白：\n{start_data['opening']}\n")
    
    # ============ 步骤 2: 自我介绍 ============
    print("\n" + "=" * 70)
    print("[步骤 2] 自我介绍环节")
    print("=" * 70)
    
    intro_data = interviewer.get_opening_response()
    print(f"🤵 面试官：{intro_data['question']}\n")
    
    # 面试者生成自我介绍
    self_intro = candidate.generate_self_introduction()
    print(f"👤 面试者：{self_intro}\n")
    
    # 提交自我介绍
    intro_result = interviewer.submit_self_introduction(self_intro)
    print(f"✅ 面试官：{intro_result.get('question', '')}\n")
    
    # ============ 步骤 3: 项目提问环节 (2-4个问题) ============
    print("\n" + "=" * 70)
    print("[步骤 3] 项目提问环节")
    print("=" * 70)
    
    # 获取目标问题数（从会话信息获取）
    target_project_questions = intro_result.get('message', '')
    print(f"📋 {target_project_questions}\n")
    
    project_question_count = 0
    max_project_questions = random.randint(2, 4)  # 随机2-4个问题
    print(f"📊 计划提问约 {max_project_questions} 个项目问题（实际可能因追问而略有变化）\n")
    
    current_question = intro_result.get('question', '')
    followup_count = 0  # 追问计数
    
    # 继续回答项目问题，直到达到目标数量或进入技术面试阶段
    while project_question_count < max_project_questions and interviewer.stage != 'technical_qna':
        # 如果当前没有问题，说明项目提问环节已结束
        if not current_question:
            # 如果阶段还没转换，等待系统转换
            if interviewer.stage != 'technical_qna':
                print("📝 项目问题已答完，等待系统进入技术面试环节...\n")
            break
        
        # 如果已经进入技术面试阶段，退出
        if interviewer.stage == 'technical_qna':
            print("📝 项目提问环节结束，进入技术面试环节\n")
            break
        
        project_question_count += 1
        print(f"\n--- 项目问题 {project_question_count} ---")
        print(f"🤵 面试官：{current_question}\n")
        
        # 面试者生成回答
        answer = candidate.generate_answer(current_question, question_type="project")
        print(f"👤 面试者：{answer}\n")
        
        # 提交回答
        answer_result = interviewer.submit_project_answer(answer)
        
        # 显示评分和反馈
        if 'score' in answer_result:
            print(f"📊 评分: {answer_result['score']}/100")
            print(f"💬 反馈: {answer_result['feedback']}\n")
        
        # 判断是否进入技术面试
        if answer_result.get('stage') == 'technical_qna':
            print("📝 项目提问环节结束，进入技术面试环节\n")
            break
        
        # 检查是否有追问（最多处理1次追问）
        if answer_result.get('is_followup') and followup_count < 1:
            followup_count += 1
            current_question = answer_result.get('next_question') or answer_result.get('question')
            if current_question:
                print(f"🔍 追问：{current_question}\n")
                # 继续回答追问，不增加问题计数
                followup_answer = candidate.generate_answer(current_question, question_type="project")
                print(f"👤 面试者：{followup_answer}\n")
                answer_result = interviewer.submit_project_answer(followup_answer)
                if 'score' in answer_result:
                    print(f"📊 评分: {answer_result['score']}/100")
                    print(f"💬 反馈: {answer_result['feedback']}\n")
                
                # 如果追问后进入技术面试，退出
                if answer_result.get('stage') == 'technical_qna':
                    print("📝 项目提问环节结束，进入技术面试环节\n")
                    break
        
        # 重置追问计数（每个问题独立）
        followup_count = 0
        
        # 获取下一个问题
        current_question = answer_result.get('next_question') or answer_result.get('question')
        
        # 如果阶段已变更，退出循环
        if answer_result.get('stage') == 'technical_qna':
            break
    
    print(f"\n✅ 项目提问环节完成，共回答 {project_question_count} 个问题\n")
    
    # 如果还没进入技术面试阶段，但还有问题，继续回答直到系统转换
    if interviewer.stage != 'technical_qna' and current_question:
        print("📝 继续回答项目问题，直到系统进入技术面试环节...\n")
        while interviewer.stage != 'technical_qna' and current_question:
            project_question_count += 1
            print(f"\n--- 项目问题 {project_question_count} ---")
            print(f"🤵 面试官：{current_question}\n")
            
            answer = candidate.generate_answer(current_question, question_type="project")
            print(f"👤 面试者：{answer}\n")
            
            answer_result = interviewer.submit_project_answer(answer)
            
            if 'score' in answer_result:
                print(f"📊 评分: {answer_result['score']}/100")
                print(f"💬 反馈: {answer_result['feedback']}\n")
            
            if answer_result.get('stage') == 'technical_qna':
                break
            
            current_question = answer_result.get('next_question') or answer_result.get('question')
    
    # ============ 步骤 4: 技术面试环节 (2-4个问题) ============
    # 检查是否已进入技术面试阶段
    if interviewer.stage == 'technical_qna':
        print("\n" + "=" * 70)
        print("[步骤 4] 技术面试环节")
        print("=" * 70)
        
        # 随机选择问题类型和数量
        tech_question_count = random.randint(2, 4)
        question_types = ["Java基础", "多线程", "Spring", "Redis"]
        selected_types = random.sample(question_types, min(2, len(question_types)))
        
        # 分配问题数量
        counts = {}
        remaining = tech_question_count
        for i, qtype in enumerate(selected_types):
            if i == len(selected_types) - 1:
                counts[qtype] = remaining
            else:
                count = max(1, remaining // (len(selected_types) - i))
                counts[qtype] = count
                remaining -= count
        
        print(f"📊 计划提问 {tech_question_count} 个技术问题")
        print(f"📋 问题类型分布: {counts}\n")
        
        # 开始技术面试
        tech_start = interviewer.start_technical_interview(
            question_types=selected_types,
            counts=counts,
        )
        
        current_tech_question = tech_start.get('question', '')
        tech_question_num = 0
        
        while current_tech_question and tech_question_num < tech_question_count:
            tech_question_num += 1
            print(f"\n--- 技术问题 {tech_question_num}/{tech_question_count} ---")
            print(f"🤵 面试官：{current_tech_question}\n")
            
            # 面试者生成回答
            tech_answer = candidate.generate_answer(current_tech_question, question_type="technical")
            print(f"👤 面试者：{tech_answer}\n")
            
            # 提交回答
            tech_result = interviewer.submit_technical_answer(tech_answer)
            
            # 显示评分和反馈
            if 'score' in tech_result:
                print(f"📊 评分: {tech_result['score']}/100")
                print(f"💬 反馈: {tech_result['feedback']}\n")
            
            # 获取下一个问题
            if tech_result.get('stage') == 'concluded':
                print("📝 所有技术问题已回答\n")
                break
            
            current_tech_question = tech_result.get('next_question') or tech_result.get('question')
            if not current_tech_question:
                break
    
    # ============ 步骤 5: 面试总结 ============
    print("\n" + "=" * 70)
    print("[步骤 5] 面试总结")
    print("=" * 70)
    
    conclude_data = interviewer.conclude_interview()
    
    print("\n" + "=" * 70)
    print("📊 面试结果")
    print("=" * 70)
    print(f"最终评分: {conclude_data['final_score']}/100")
    if 'average_score' in conclude_data:
        print(f"平均分: {conclude_data['average_score']:.1f}/100")
    print(f"\n💬 最终反馈:\n{conclude_data['final_feedback']}")
    print("=" * 70)
    
    # 统计信息
    print("\n📈 面试统计:")
    print(f"  项目问题数: {project_question_count}")
    print(f"  技术问题数: {tech_question_num}")
    print(f"  总问题数: {project_question_count + tech_question_num}")
    print("=" * 70)


def test_import_questions():
    """测试导入面试题"""
    print("\n" + "=" * 50)
    print("导入面试题测试")
    print("=" * 50)
    print("\n注意：需要先准备一个面试题PDF或文本文件")
    print("然后通过以下方式导入：")
    print(f"  curl -X POST '{BASE_URL}/interview/questions/import' \\")
    print("    -F 'file=@your_questions.pdf'")
    print("\n或者在浏览器访问 /docs 页面，使用界面导入")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1 and sys.argv[1] == "import":
        test_import_questions()
    else:
        test_interview_flow()
