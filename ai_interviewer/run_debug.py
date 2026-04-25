"""
调试模式启动脚本
支持：
1. 详细日志输出
2. 代码热重载
3. 自动重新加载依赖
4. 开发友好的错误页面
"""
import uvicorn
import logging
import sys

# 配置日志
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

# 设置特定模块的日志级别
logging.getLogger("uvicorn").setLevel(logging.INFO)
logging.getLogger("uvicorn.error").setLevel(logging.INFO)
logging.getLogger("uvicorn.access").setLevel(logging.INFO)
logging.getLogger("fastapi").setLevel(logging.DEBUG)

# 启用 SQLAlchemy 的 SQL 查询日志（可选，用于调试数据库）
# logging.getLogger("sqlalchemy.engine").setLevel(logging.INFO)


if __name__ == "__main__":
    print("=" * 50)
    print("启动 AI 面试助手 - 调试模式")
    print("=" * 50)
    print("访问地址: http://127.0.0.1:8000")
    print("API 文档: http://127.0.0.1:8000/docs")
    print("=" * 50)
    print()
    
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,  # 启用热重载
        reload_dirs=["./"],  # 监控整个项目目录的变化
        reload_includes=["*.py"],  # 只监控 Python 文件
        log_level="debug",  # 详细日志
        access_log=True,  # 访问日志
        use_colors=True,  # 彩色日志输出
        loop="auto",  # 自动选择事件循环
    )

