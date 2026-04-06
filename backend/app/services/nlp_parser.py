import json
import os
from datetime import date

try:
    import anthropic
except ImportError:
    anthropic = None  # type: ignore

from app.schemas.nlp import ParsedTask


def _build_prompt(text: str, user_projects: list[str], today: date) -> str:
    projects_str = ", ".join(user_projects) if user_projects else "none"
    return f"""You are a task parser. Given a natural language input, extract structured task information.

Today's date is {today.isoformat()}.

The user's existing projects are: {projects_str}

Parse the following input and return a JSON object with these fields:
- "title": (string, required) The task title, cleaned up
- "project_suggestion": (string or null) If the input mentions or implies one of the user's existing projects, include the project name
- "due_date": (string in YYYY-MM-DD format, or null) Any mentioned due date, resolved relative to today
- "priority": (integer 1-4 or null) 1=Urgent, 2=High, 3=Medium, 4=Low. Infer from urgency words if present
- "parent_task_suggestion": (string or null) If the input suggests this is a subtask of something, include the parent task description
- "confidence": (float 0.0-1.0) How confident you are in the overall parse

Input: "{text}"

Respond with ONLY valid JSON, no other text."""


def parse_task_input(
    text: str, user_projects: list[str], today: date
) -> ParsedTask:
    if not text or not text.strip():
        raise ValueError("Input text cannot be empty")

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise RuntimeError(
            "ANTHROPIC_API_KEY environment variable is not set"
        )

    if anthropic is None:
        raise RuntimeError("anthropic package is not installed")

    client = anthropic.Anthropic(api_key=api_key)
    prompt = _build_prompt(text.strip(), user_projects, today)

    last_error: Exception | None = None
    for attempt in range(2):
        try:
            message = client.messages.create(
                model="claude-haiku-4-5-20251001",
                max_tokens=512,
                messages=[{"role": "user", "content": prompt}],
            )
            content = message.content[0].text
            parsed = json.loads(content)
            return ParsedTask(**parsed)
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            last_error = e
            if attempt == 0:
                continue
            raise ValueError(
                f"Failed to parse NLP response after retry: {e}"
            ) from e
        except Exception as e:
            if "APIError" in type(e).__name__:
                raise RuntimeError(f"Anthropic API error: {e}") from e
            raise

    raise ValueError(f"Failed to parse NLP response: {last_error}")
