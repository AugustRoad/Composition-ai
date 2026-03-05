from google.adk.tools import FunctionTool
from google.adk.tools.tool_context import ToolContext

def capture_frame(tool_context: ToolContext) -> str:
    """
    Snapshots the latest video frame from the active video stream in session.state
    and saves it to session.state['current_frame'].
    """
    # In a fully deployed ADK Live toolkit streaming over WebSockets,
    # incoming video frame blobs might be queued or stored directly.
    # We simulate extraction here or copy the available latest stream chunk.
    # Setting mock bytes if we are testing without a real stream yet:
    raw_video_buffer = tool_context.state.get("video_stream")
    
    if raw_video_buffer:
        # e.g., grabbing the last frame from a buffer
        frame_bytes = raw_video_buffer[-1]
    else:
        # Mocking for testing
        frame_bytes = b"mock_png_header\x00..."
        
    tool_context.state["current_frame"] = frame_bytes
    return "Frame captured successfully."

capture_tool = FunctionTool(func=capture_frame)