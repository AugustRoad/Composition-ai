from google.adk.tools import FunctionTool
from google.adk.tools.tool_context import ToolContext
from google.genai import types

def get_frame_for_analysis(tool_context: ToolContext) -> "types.Part":
    """Reads the current video frame available in the session state."""
    frame_bytes = tool_context.state.get("current_frame")
    if not frame_bytes:
        raise ValueError("No video frame captured yet. The orchestrator must capture a frame first.")
    
    return types.Part.from_bytes(frame_bytes, "image/png")
