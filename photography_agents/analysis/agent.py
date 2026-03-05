from google.adk.agents import LlmAgent
from .tools import get_frame_for_analysis
from google.adk.tools import FunctionTool

analysis_tool = FunctionTool(func=get_frame_for_analysis)

analysis_agent = LlmAgent(
    name="AnalysisAgent",
    model="gemini-2.0-flash",
    instruction="""
    You are an expert photography analyzer.
    1. Call the `get_frame_for_analysis` tool to access the user's latest video frame.
    2. Examine the frame closely.
    3. Evaluate and return structured JSON feedback covering:
       - composition: rule of thirds, leading lines, framing
       - lighting: direction, quality, exposure
       - camera_settings: suggested ISO/aperture/shutter based on the scene
       - angle: shooting position recommendations
    """,
    tools=[analysis_tool],
    output_key="analysis_feedback"
)