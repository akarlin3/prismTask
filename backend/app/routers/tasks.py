from datetime import date, datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.middleware.auth import get_current_user
from app.models import Project, Task, TaskStatus, User
from app.schemas.nlp import ParseRequest, ParseResponse
from app.schemas.task import SubtaskCreate, TaskCreate, TaskResponse, TaskUpdate

router = APIRouter(tags=["tasks"])

MAX_SUBTASK_DEPTH = 1


def _task_to_response(task: Task, subtasks: list | None = None) -> TaskResponse:
    child_responses = []
    if subtasks is not None:
        child_responses = [_task_to_response(s, []) for s in subtasks]
    return TaskResponse(
        id=task.id,
        project_id=task.project_id,
        user_id=task.user_id,
        parent_id=task.parent_id,
        title=task.title,
        description=task.description,
        status=task.status.value if hasattr(task.status, "value") else task.status,
        priority=task.priority,
        due_date=task.due_date,
        completed_at=task.completed_at,
        sort_order=task.sort_order,
        depth=task.depth,
        created_at=task.created_at,
        updated_at=task.updated_at,
        subtasks=child_responses,
    )


async def _verify_project_ownership(project_id: int, user: User, db: AsyncSession) -> Project:
    result = await db.execute(
        select(Project).where(Project.id == project_id, Project.user_id == user.id)
    )
    project = result.scalar_one_or_none()
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")
    return project


async def _get_task_for_user(task_id: int, user: User, db: AsyncSession) -> Task:
    result = await db.execute(
        select(Task)
        .options(selectinload(Task.subtasks))
        .where(Task.id == task_id, Task.user_id == user.id)
    )
    task = result.scalar_one_or_none()
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return task


@router.get("/projects/{project_id}/tasks", response_model=list[TaskResponse])
async def list_tasks(
    project_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await _verify_project_ownership(project_id, current_user, db)
    result = await db.execute(
        select(Task)
        .options(selectinload(Task.subtasks))
        .where(Task.project_id == project_id, Task.parent_id.is_(None))
        .order_by(Task.sort_order, Task.created_at)
    )
    tasks = result.scalars().all()
    return [_task_to_response(t, t.subtasks) for t in tasks]


@router.post(
    "/projects/{project_id}/tasks",
    response_model=TaskResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_task(
    project_id: int,
    data: TaskCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await _verify_project_ownership(project_id, current_user, db)
    task_data = data.model_dump(exclude_unset=True)
    if "status" in task_data:
        task_data["status"] = TaskStatus(task_data["status"])
    task = Task(project_id=project_id, user_id=current_user.id, depth=0, **task_data)
    db.add(task)
    await db.flush()
    await db.refresh(task)
    return _task_to_response(task, [])


@router.get("/tasks/{task_id}", response_model=TaskResponse)
async def get_task(
    task_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_task_for_user(task_id, current_user, db)
    return _task_to_response(task, task.subtasks)


@router.patch("/tasks/{task_id}", response_model=TaskResponse)
async def update_task(
    task_id: int,
    data: TaskUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_task_for_user(task_id, current_user, db)
    update_data = data.model_dump(exclude_unset=True)

    if "status" in update_data:
        new_status = TaskStatus(update_data["status"])
        update_data["status"] = new_status
        if new_status == TaskStatus.DONE and task.status != TaskStatus.DONE:
            update_data["completed_at"] = datetime.now(timezone.utc)
        elif new_status != TaskStatus.DONE:
            update_data["completed_at"] = None

    for key, value in update_data.items():
        setattr(task, key, value)

    await db.flush()
    await db.refresh(task)
    # Re-fetch to get updated subtasks
    result = await db.execute(
        select(Task)
        .options(selectinload(Task.subtasks))
        .where(Task.id == task.id)
    )
    task = result.scalar_one()
    return _task_to_response(task, task.subtasks)


@router.delete("/tasks/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(
    task_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    task = await _get_task_for_user(task_id, current_user, db)
    await db.delete(task)


@router.post("/tasks/{task_id}/subtasks", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
async def create_subtask(
    task_id: int,
    data: SubtaskCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    parent = await _get_task_for_user(task_id, current_user, db)

    if parent.depth >= MAX_SUBTASK_DEPTH:
        raise HTTPException(
            status_code=400,
            detail=f"Maximum subtask depth of {MAX_SUBTASK_DEPTH} exceeded",
        )

    task_data = data.model_dump(exclude_unset=True)
    if "status" in task_data:
        task_data["status"] = TaskStatus(task_data["status"])

    subtask = Task(
        project_id=parent.project_id,
        user_id=current_user.id,
        parent_id=parent.id,
        depth=parent.depth + 1,
        **task_data,
    )
    db.add(subtask)
    await db.flush()
    await db.refresh(subtask)
    return _task_to_response(subtask, [])


@router.post("/tasks/parse", response_model=ParseResponse)
async def parse_task(data: ParseRequest):
    """
    Parse free-text task input into structured fields.

    This is a utility endpoint and does not require authentication — it has no
    user context and simply runs the NLP parser against the supplied text.
    Because there is no user, project-name suggestions are not available.
    """
    try:
        from app.services.nlp_parser import parse_task_input
        parsed = parse_task_input(data.text, [], date.today())
    except (ValueError, RuntimeError) as e:
        raise HTTPException(status_code=422, detail=str(e))

    return ParseResponse(**parsed.model_dump(), needs_confirmation=True)


@router.patch("/tasks/reorder", status_code=status.HTTP_200_OK)
async def reorder_tasks(
    items: list[dict],
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    for item in items:
        task_id = item.get("id")
        sort_order = item.get("sort_order")
        if task_id is None or sort_order is None:
            raise HTTPException(status_code=400, detail="Each item must have 'id' and 'sort_order'")
        result = await db.execute(
            select(Task).where(Task.id == task_id, Task.user_id == current_user.id)
        )
        task = result.scalar_one_or_none()
        if not task:
            raise HTTPException(status_code=404, detail=f"Task {task_id} not found")
        task.sort_order = sort_order
    await db.flush()
    return {"detail": "Tasks reordered"}
