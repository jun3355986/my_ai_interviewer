import os
import re
from typing import Annotated

from fastapi import APIRouter, File, HTTPException, UploadFile

from services.resume_parser import ResumeParser


router = APIRouter(prefix="/resume", tags=["resume"])
resume_parser = ResumeParser()
SUPPORTED_EXTENSIONS = {".pdf", ".txt", ".md"}


def _detect_file_extension(file: UploadFile, content: bytes) -> str:
    filename = file.filename or ""
    file_extension = os.path.splitext(filename)[1].lower()
    if file_extension in SUPPORTED_EXTENSIONS:
        return file_extension

    content_type = (file.content_type or "").lower()
    if "pdf" in content_type:
        return ".pdf"
    if content_type.startswith("text/"):
        return ".txt"

    if content.startswith(b"%PDF"):
        return ".pdf"

    try:
        _ = content.decode("utf-8")
        return ".txt"
    except UnicodeDecodeError as exc:
        raise ValueError("unsupported_file_type") from exc


def _first_match(patterns: list[str], text: str, flags: int = re.IGNORECASE) -> str | None:
    for pattern in patterns:
        matched = re.search(pattern, text, flags)
        if matched:
            return matched.group(1).strip()
    return None


def _extract_skills(text: str) -> list[str]:
    patterns = [
        r"(?:skills?|skill set|technical skills)\s*[:：]\s*([^\n]+)",
        r"(?:技能|技术栈|擅长技术)\s*[:：]\s*([^\n]+)",
    ]
    skills_line = _first_match(patterns, text)
    if not skills_line:
        return []

    candidates = re.split(r"[,，;；/|]+", skills_line)
    normalized = [item.strip() for item in candidates if item.strip()]
    deduped: list[str] = []
    for item in normalized:
        if item not in deduped:
            deduped.append(item)
    return deduped


def _parse_resume_content(resume_text: str) -> dict[str, object]:
    content: dict[str, object] = {
        "name": _first_match([
            r"(?:name|candidate)\s*[:：]\s*([^\n]+)",
            r"(?:姓名)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "gender": _first_match([
            r"(?:gender|sex)\s*[:：]\s*([^\n]+)",
            r"(?:性别)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "age": None,
        "phone": _first_match([
            r"(?:phone|mobile|tel)\s*[:：]\s*([+\d\-\s()]{6,})",
            r"(?:电话|手机)\s*[:：]\s*([+\d\-\s()]{6,})",
        ], resume_text),
        "email": _first_match([
            r"([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+)",
        ], resume_text),
        "location": _first_match([
            r"(?:location|city|address)\s*[:：]\s*([^\n]+)",
            r"(?:居住地|所在地|城市|地址)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "jobIntent": _first_match([
            r"(?:job intent|target role|objective)\s*[:：]\s*([^\n]+)",
            r"(?:求职意向|应聘岗位|目标岗位)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "expectedSalary": _first_match([
            r"(?:expected salary|salary expectation)\s*[:：]\s*([^\n]+)",
            r"(?:期望薪资|薪资期望)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "workYears": _first_match([
            r"(?:work years|experience)\s*[:：]\s*([^\n]+)",
            r"(?:工作年限|工作经验)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "education": _first_match([
            r"(?:education|degree)\s*[:：]\s*([^\n]+)",
            r"(?:学历|教育背景)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "university": _first_match([
            r"(?:university|college|school)\s*[:：]\s*([^\n]+)",
            r"(?:毕业院校|学校)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "major": _first_match([
            r"(?:major)\s*[:：]\s*([^\n]+)",
            r"(?:专业)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "skills": _extract_skills(resume_text),
        "workExperience": [],
        "projectExperience": [],
        "certificates": [],
        "selfEvaluation": _first_match([
            r"(?:self evaluation|profile summary)\s*[:：]\s*([^\n]+)",
            r"(?:自我评价|个人评价)\s*[:：]\s*([^\n]+)",
        ], resume_text),
        "otherInfo": resume_text[:6000],
    }

    age_text = _first_match([
        r"(?:age)\s*[:：]\s*(\d{1,2})",
        r"(?:年龄)\s*[:：]\s*(\d{1,2})",
    ], resume_text)
    if age_text and age_text.isdigit():
        content["age"] = int(age_text)

    return content


@router.post("/parse")
async def parse_resume(file: Annotated[UploadFile, File(...)]) -> dict[str, object]:
    try:
        content = await file.read()
        file_extension = _detect_file_extension(file, content)
    except ValueError:
        raise HTTPException(
            status_code=415,
            detail="Unsupported file type. Please upload PDF, TXT, or MD resume file.",
        )

    try:
        resume_text = resume_parser.parse_content(content, file_extension)
        parsed_content = _parse_resume_content(resume_text)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=f"Failed to parse resume: {str(exc)}")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Resume parsing internal error: {str(exc)}")

    # 原文随解析结果一并返回，Java 侧持久化到 t_resume.raw_text，
    # 供 durable 面试根分支与模拟面试候选人上下文使用。
    parsed_content["rawText"] = resume_text
    return parsed_content
