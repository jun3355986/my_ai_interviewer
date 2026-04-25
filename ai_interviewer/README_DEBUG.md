# 调试模式运行指南

## 方式一：使用调试脚本（推荐）

最简单的方式，运行调试脚本：

```bash
uv run python run_debug.py
```

或者：

```bash
python run_debug.py
```

这个脚本会：
- ✅ 启用详细日志输出
- ✅ 启用代码热重载（修改代码自动重启）
- ✅ 启用访问日志
- ✅ 彩色日志输出

## 方式二：使用 uvicorn 命令

```bash
uv run uvicorn main:app --reload --host 0.0.0.0 --port 8000 --log-level debug
```

参数说明：
- `--reload`: 启用热重载，代码修改自动重启
- `--log-level debug`: 显示详细的调试日志
- `--host 0.0.0.0`: 允许外部访问
- `--port 8000`: 指定端口

## 方式三：在 IDE 中调试

### VS Code / Cursor

1. 打开项目
2. 按 `F5` 或点击"运行和调试"
3. 选择 "Python: FastAPI (调试模式)" 配置
4. 可以设置断点，单步调试

### PyCharm

1. 打开项目
2. 创建新的运行配置：
   - Run/Debug Configuration
   - 选择 "Python"
   - Script path: 选择 `main.py` 或 `run_debug.py`
   - Parameters: `--reload --host 0.0.0.0 --port 8000`
3. 点击调试按钮

## 方式四：使用 Python 调试器 (pdb)

在代码中添加断点：

```python
import pdb; pdb.set_trace()
```

或者使用 `breakpoint()` (Python 3.7+)：

```python
breakpoint()  # 进入调试器
```

## 调试技巧

### 1. 查看详细日志

调试模式下，所有日志都会显示在终端，包括：
- API 请求日志
- 错误堆栈信息
- 数据库查询日志（如果启用）

### 2. 查看变量值

在调试器中，可以使用：
```python
# 查看变量
print(variable_name)

# 查看对象属性
dir(object)

# 查看类型
type(variable)
```

### 3. 启用 SQLAlchemy 日志（调试数据库）

在 `run_debug.py` 中取消注释：

```python
logging.getLogger("sqlalchemy.engine").setLevel(logging.INFO)
```

这样可以查看所有 SQL 查询语句。

### 4. 使用 FastAPI 的调试端点

访问 `http://127.0.0.1:8000/docs` 可以：
- 查看所有 API 接口
- 直接测试接口
- 查看请求/响应格式

### 5. 环境变量调试

设置环境变量来控制调试行为：

```bash
# 显示更详细的错误信息
export PYTHONUNBUFFERED=1

# 设置日志级别
export LOG_LEVEL=DEBUG
```

## 常见问题

### Q: 代码修改后没有自动重启？

A: 确保：
1. 使用了 `--reload` 参数
2. 修改的是 `.py` 文件
3. 检查终端是否有错误信息

### Q: 看不到详细的错误信息？

A: 
1. 确保 `log_level` 设置为 `debug`
2. 检查终端输出
3. 在代码中添加 `print()` 或日志语句

### Q: 如何调试异步代码？

A: 
- 使用 IDE 的调试器支持异步调试
- 或使用 `asyncio.run()` 包装代码
- 在异步函数中使用 `breakpoint()`

## 生产环境

⚠️ **注意**：调试模式不应该用于生产环境！

生产环境应该：
- 不使用 `--reload`
- 设置 `log_level` 为 `info` 或 `warning`
- 使用进程管理器（如 supervisor, systemd）
- 配置适当的日志文件

