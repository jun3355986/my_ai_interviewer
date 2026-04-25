"""
简历解析服务：支持PDF和纯文本格式
"""
import os
from pathlib import Path
from typing import Optional

from pypdf import PdfReader


class ResumeParser:
    """简历解析器，支持PDF和文本格式"""
    
    @staticmethod
    def parse_file(file_path: str) -> str:
        """
        解析简历文件（PDF或文本）
        
        Args:
            file_path: 文件路径
            
        Returns:
            解析后的文本内容
        """
        path = Path(file_path)
        suffix = path.suffix.lower()
        
        if suffix == '.pdf':
            return ResumeParser._parse_pdf(file_path)
        elif suffix in ['.txt', '.md']:
            return ResumeParser._parse_text(file_path)
        else:
            raise ValueError(f"不支持的文件格式: {suffix}")
    
    @staticmethod
    def parse_content(content: bytes, file_extension: str) -> str:
        """
        解析简历内容（从上传的字节流）
        
        Args:
            content: 文件内容字节流
            file_extension: 文件扩展名（如 '.pdf', '.txt'）
            
        Returns:
            解析后的文本内容
        """
        if file_extension.lower() == '.pdf':
            import io
            pdf_file = io.BytesIO(content)
            reader = PdfReader(pdf_file)
            text = ""
            for page in reader.pages:
                text += page.extract_text() + "\n"
            return text.strip()
        elif file_extension.lower() in ['.txt', '.md']:
            return content.decode('utf-8')
        else:
            raise ValueError(f"不支持的文件格式: {file_extension}")
    
    @staticmethod
    def _parse_pdf(file_path: str) -> str:
        """解析PDF文件"""
        reader = PdfReader(file_path)
        text = ""
        for page in reader.pages:
            text += page.extract_text() + "\n"
        return text.strip()
    
    @staticmethod
    def _parse_text(file_path: str) -> str:
        """解析文本文件"""
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read().strip()

