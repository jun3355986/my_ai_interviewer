# question-bank Specification

## Purpose
问题库管理，基于 Chroma 向量数据库实现 RAG 检索，从预设面试题库中检索与候选人背景相关的技术问题。

## Requirements

### Requirement: Question Bank Import
The system SHALL support importing interview questions into the Chroma vector database.

#### Scenario: Import Questions from PDF
- **Given** a PDF file containing categorized interview questions
- **When** the import process is triggered
- **Then** the system SHALL parse the PDF, split into individual questions
- **And** generate embeddings via DashScope `text-embedding-v4`
- **And** store in the Chroma vector database with metadata (category, difficulty).

### Requirement: RAG Question Retrieval
The system SHALL retrieve relevant interview questions based on candidate context using vector similarity search.

#### Scenario: Retrieve Technical Questions
- **Given** a candidate's resume content and current interview stage `technical`
- **When** the system needs to select the next question
- **Then** the system SHALL perform vector similarity search in Chroma
- **And** return the top-K most relevant questions filtered by category.

### Requirement: Question Categories
The system SHALL support multiple question categories for comprehensive interview coverage.

#### Scenario: Category-based Filtering
- **Given** available question categories (Java, Python, Database, System Design, Spring, etc.)
- **When** retrieving questions for a specific domain
- **Then** the system SHALL filter by the relevant category metadata.
