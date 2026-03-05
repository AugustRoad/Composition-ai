from google.adk.agents import LlmAgent
from google.adk.tools import agent_tool
from ..search.agent import search_agent
from ..analysis.agent import analysis_agent
from .tools import capture_tool

search_agent_tool = agent_tool.AgentTool(agent=search_agent)
analysis_agent_tool = agent_tool.AgentTool(agent=analysis_agent)

orchestrator_agent = LlmAgent(
    name="OrchestratorAgent",
    model="gemini-2.0-flash",
    instruction="""
    You are a photography multi-agent orchestrator.
    You interact with a user who is sending a live video stream.
    When the user requests analysis or searches (via text):
    1. Call the `capture_frame` tool to extract the latest frame from the video stream.
    2. Delegate the task to the appropriate sub-agent:
       - Use SearchAgent if they want to find similar photos or identify the location.
       - Use AnalysisAgent if they want feedback on composition, lighting, or settings.
       - You can use both if the user asks for both.
    3. Return a helpful synthesis of the results back to the user.
    """,
    # Setting modalities for video in / text out (representing the streaming live config).
    # Actual syntax varies by streaming config, but configuring via tools and instructions works for logic.
    tools=[capture_tool, search_agent_tool, analysis_agent_tool],
    # Usually ADK Live API is configured via the streaming runtime / agent runner, 
    # but the logic remains the same for the agent definition.
)