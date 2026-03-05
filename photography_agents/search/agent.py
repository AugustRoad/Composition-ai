from google.adk.agents import LlmAgent
from .tools import get_frame_for_location
from google.adk.tools import FunctionTool, google_search

location_tool = FunctionTool(func=get_frame_for_location)

search_agent = LlmAgent(
    name="SearchAgent",
    model="gemini-2.0-flash",
    instruction="""
    You are a location-based photo search assistant.
    1. Call `get_frame_for_location` to see the current video frame.
    2. Identify landmarks or scene context to guess the location.
    3. Then, use Google Search (if available in your capabilities) to find URLs and descriptions 
       of photos taken at or near the same place. Pioritize recent and relevant photos from instagram.
    4. Compile these findings into a list of results and output them.
    """,
    tools=[location_tool, google_search],
    output_key="search_results"
)