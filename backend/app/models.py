import enum

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Column,
    Date,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    Time,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import DeclarativeBase, relationship


class Base(DeclarativeBase):
    pass


# --- Enums ---


class GoalStatus(str, enum.Enum):
    ACTIVE = "active"
    ACHIEVED = "achieved"
    ARCHIVED = "archived"


class ProjectStatus(str, enum.Enum):
    ACTIVE = "active"
    COMPLETED = "completed"
    ON_HOLD = "on_hold"
    ARCHIVED = "archived"


class TaskStatus(str, enum.Enum):
    TODO = "todo"
    IN_PROGRESS = "in_progress"
    DONE = "done"
    CANCELLED = "cancelled"


class TaskPriority(int, enum.Enum):
    URGENT = 1
    HIGH = 2
    MEDIUM = 3
    LOW = 4


class HabitFrequency(str, enum.Enum):
    DAILY = "daily"
    WEEKLY = "weekly"


# --- Models ---


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    hashed_password = Column(String(255), nullable=False)
    name = Column(String(255), nullable=False)
    firebase_uid = Column(String(255), unique=True, nullable=True)
    created_at = Column(DateTime, server_default=func.now())

    goals = relationship("Goal", back_populates="user", cascade="all, delete-orphan")
    tags = relationship("Tag", back_populates="user", cascade="all, delete-orphan")
    habits = relationship("Habit", back_populates="user", cascade="all, delete-orphan")
    templates = relationship("TaskTemplate", back_populates="user", cascade="all, delete-orphan")


class Goal(Base):
    __tablename__ = "goals"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(Enum(GoalStatus, values_callable=lambda x: [e.value for e in x]), default=GoalStatus.ACTIVE, nullable=False)
    target_date = Column(Date, nullable=True)
    color = Column(String(7), nullable=True)
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="goals")
    projects = relationship("Project", back_populates="goal", cascade="all, delete-orphan")


class Project(Base):
    __tablename__ = "projects"

    id = Column(Integer, primary_key=True)
    goal_id = Column(Integer, ForeignKey("goals.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(Enum(ProjectStatus, values_callable=lambda x: [e.value for e in x]), default=ProjectStatus.ACTIVE, nullable=False)
    due_date = Column(Date, nullable=True)
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    goal = relationship("Goal", back_populates="projects")
    user = relationship("User")
    tasks = relationship("Task", back_populates="project", cascade="all, delete-orphan")


class Tag(Base):
    __tablename__ = "tags"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    color = Column(String(7), nullable=True)
    created_at = Column(DateTime, server_default=func.now())

    user = relationship("User", back_populates="tags")
    task_tags = relationship("TaskTag", back_populates="tag", cascade="all, delete-orphan")


class Task(Base):
    __tablename__ = "tasks"
    __table_args__ = (
        CheckConstraint("depth >= 0 AND depth <= 1", name="check_depth_range"),
    )

    id = Column(Integer, primary_key=True)
    project_id = Column(Integer, ForeignKey("projects.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    parent_id = Column(Integer, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=True, index=True)
    title = Column(String(500), nullable=False)
    description = Column(Text, nullable=True)
    notes = Column(Text, nullable=True)
    status = Column(Enum(TaskStatus, values_callable=lambda x: [e.value for e in x]), default=TaskStatus.TODO, nullable=False)
    priority = Column(Integer, default=TaskPriority.MEDIUM)
    due_date = Column(Date, nullable=True)
    due_time = Column(Time, nullable=True)
    planned_date = Column(Date, nullable=True)
    completed_at = Column(DateTime, nullable=True)
    urgency_score = Column(Float, default=0.0)
    recurrence_json = Column(Text, nullable=True)
    sort_order = Column(Integer, default=0)
    depth = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    project = relationship("Project", back_populates="tasks")
    user = relationship("User")
    parent = relationship("Task", remote_side=[id], back_populates="subtasks")
    subtasks = relationship("Task", back_populates="parent", cascade="all, delete-orphan")
    task_tags = relationship("TaskTag", back_populates="task", cascade="all, delete-orphan")
    attachments = relationship("Attachment", back_populates="task", cascade="all, delete-orphan")


class TaskTag(Base):
    __tablename__ = "task_tags"
    __table_args__ = (
        UniqueConstraint("task_id", "tag_id", name="uq_task_tag"),
    )

    id = Column(Integer, primary_key=True)
    task_id = Column(Integer, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True)
    tag_id = Column(Integer, ForeignKey("tags.id", ondelete="CASCADE"), nullable=False, index=True)

    task = relationship("Task", back_populates="task_tags")
    tag = relationship("Tag", back_populates="task_tags")


class Attachment(Base):
    __tablename__ = "attachments"

    id = Column(Integer, primary_key=True)
    task_id = Column(Integer, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    uri = Column(Text, nullable=False)
    type = Column(String(50), nullable=False)
    created_at = Column(DateTime, server_default=func.now())

    task = relationship("Task", back_populates="attachments")


class Habit(Base):
    __tablename__ = "habits"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    icon = Column(String(10), nullable=True)
    color = Column(String(7), nullable=True)
    category = Column(String(100), nullable=True)
    frequency = Column(Enum(HabitFrequency, values_callable=lambda x: [e.value for e in x]), default=HabitFrequency.DAILY, nullable=False)
    target_count = Column(Integer, default=1)
    active_days_json = Column(Text, nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="habits")
    completions = relationship("HabitCompletion", back_populates="habit", cascade="all, delete-orphan")


class HabitCompletion(Base):
    __tablename__ = "habit_completions"
    __table_args__ = (
        UniqueConstraint("habit_id", "date", name="uq_habit_date"),
    )

    id = Column(Integer, primary_key=True)
    habit_id = Column(Integer, ForeignKey("habits.id", ondelete="CASCADE"), nullable=False, index=True)
    date = Column(Date, nullable=False)
    count = Column(Integer, default=1)
    created_at = Column(DateTime, server_default=func.now())

    habit = relationship("Habit", back_populates="completions")


class TaskTemplate(Base):
    __tablename__ = "task_templates"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    icon = Column(String(10), nullable=True)
    category = Column(String(100), nullable=True)

    # Template field values (what gets pre-filled when using the template)
    template_title = Column(String(255), nullable=True)
    template_description = Column(Text, nullable=True)
    template_priority = Column(Integer, nullable=True)
    template_project_id = Column(Integer, ForeignKey("projects.id", ondelete="SET NULL"), nullable=True)
    template_tags_json = Column(Text, nullable=True)
    template_recurrence_json = Column(Text, nullable=True)
    template_duration = Column(Integer, nullable=True)
    template_subtasks_json = Column(Text, nullable=True)

    is_built_in = Column(Boolean, default=False)
    usage_count = Column(Integer, default=0)
    last_used_at = Column(DateTime, nullable=True)

    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="templates")
    project = relationship("Project", foreign_keys=[template_project_id])


class AppRelease(Base):
    __tablename__ = "app_releases"

    id = Column(Integer, primary_key=True)
    version_code = Column(Integer, nullable=False, unique=True)
    version_name = Column(String(50), nullable=False)
    release_notes = Column(Text, nullable=True)
    apk_url = Column(Text, nullable=False)
    apk_size_bytes = Column(Integer, nullable=True)
    sha256 = Column(String(64), nullable=True)
    min_sdk = Column(Integer, default=26)
    created_at = Column(DateTime, server_default=func.now())
    is_mandatory = Column(Boolean, default=False)
