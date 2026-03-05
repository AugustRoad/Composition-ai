import os
from .orchestrator.agent import orchestrator_agent
from . import config  # noqa: F401 — triggers load_dotenv() on import

# Expected entry point for standard ADK deploy command:
# `adk deploy cloud_run --project=$PROJECT --region=$REGION ./photography_agents`
# The runtime expects a variable named `root_agent` here.
root_agent = orchestrator_agent

if __name__ == "__main__":
    # Provides an easy way to run or verify locally if you use an internal runner
    print(f"Loaded Root Agent: {root_agent.name}")
    print(f"Sub-agents accessible via tools: {[t.name for t in root_agent.tools]}")