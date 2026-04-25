from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.router import router as interview_router
from api.resume_router import router as resume_router


app = FastAPI(
    title="AI Interviewer",
    description="基于 LangChain 和 DeepSeek 的智能面试助手",
    version="0.1.0",
)

# 添加 CORS 支持（用于前端调用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(interview_router)
app.include_router(resume_router)


@app.get("/")
def root():
    """根路径"""
    return {
        "message": "AI Interviewer API",
        "version": "0.1.0",
        "docs": "/docs",
    }


@app.get("/health")
def health():
    """健康检查"""
    return {"status": "ok"}


def main():
    """启动应用（生产模式）"""
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)


def main_debug():
    """启动应用（调试模式）"""
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        reload_dirs=["./"],
        log_level="debug",
        access_log=True,
    )


if __name__ == "__main__":
    main()
