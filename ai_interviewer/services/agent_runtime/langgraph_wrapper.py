from __future__ import annotations

from contextlib import contextmanager, nullcontext
import logging
from typing import Any, Iterator, TypedDict

from services.agent_runtime.config import AgentRuntimeConfig, load_agent_runtime_config


logger = logging.getLogger(__name__)


class AgentRunState(TypedDict, total=False):
    agent_run_id: str
    entrypoint: str
    status: str
    metadata: dict[str, Any]


def _checkpoint_node(state: AgentRunState) -> AgentRunState:
    return {**state, "status": "checkpointed"}


def checkpoint_agent_run(
    *,
    agent_run_id: str,
    entrypoint: str,
    metadata: dict[str, Any] | None = None,
    config: AgentRuntimeConfig | None = None,
) -> bool:
    resolved_config = config or load_agent_runtime_config()
    if not resolved_config.langgraph_agent_run_enabled:
        return False

    try:
        from langgraph.checkpoint.sqlite import SqliteSaver
        from langgraph.graph import END, START, StateGraph
    except Exception:
        logger.exception("LangGraph wrapper enabled but langgraph sqlite imports failed")
        return False

    checkpoint_path = resolved_config.langgraph_checkpoint_db_path
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with SqliteSaver.from_conn_string(str(checkpoint_path)) as checkpointer:
            graph = StateGraph(AgentRunState)
            graph.add_node("single_turn_agent_run", _checkpoint_node)
            graph.add_edge(START, "single_turn_agent_run")
            graph.add_edge("single_turn_agent_run", END)
            app = graph.compile(checkpointer=checkpointer)
            app.invoke(
                {
                    "agent_run_id": agent_run_id,
                    "entrypoint": entrypoint,
                    "metadata": metadata or {},
                },
                config={"configurable": {"thread_id": agent_run_id}},
            )
        return True
    except Exception:
        logger.exception("LangGraph checkpoint write failed")
        return False


@contextmanager
def langgraph_agent_run(
    *,
    agent_run_id: str,
    entrypoint: str,
    metadata: dict[str, Any] | None = None,
    config: AgentRuntimeConfig | None = None,
) -> Iterator[None]:
    resolved_config = config or load_agent_runtime_config()
    if not resolved_config.langgraph_agent_run_enabled:
        with nullcontext():
            yield
        return

    checkpoint_agent_run(
        agent_run_id=agent_run_id,
        entrypoint=entrypoint,
        metadata={**(metadata or {}), "phase": "start"},
        config=resolved_config,
    )
    try:
        yield
    finally:
        checkpoint_agent_run(
            agent_run_id=agent_run_id,
            entrypoint=entrypoint,
            metadata={**(metadata or {}), "phase": "finish"},
            config=resolved_config,
        )
