# resume-parsing Specification

## Purpose
简历解析服务，使用 PyPDF 和 LangChain Document Loaders 解析 PDF 简历，提取结构化信息用于面试提问。

## Requirements

### Requirement: PDF Resume Parsing
The system SHALL parse uploaded PDF resumes and extract text content.

#### Scenario: Parse PDF File
- **Given** a valid PDF resume file (max 10MB)
- **When** the parsing endpoint is called
- **Then** the system SHALL extract text content from the PDF
- **And** return the parsed content as structured text.

### Requirement: Resume Content Extraction
The system SHALL extract key information from resume text for interview question generation.

#### Scenario: Extract Key Information
- **Given** parsed resume text content
- **When** the content is processed
- **Then** the system SHALL identify project experience, technical skills, and education background
- **And** make this information available for personalized interview question generation.
