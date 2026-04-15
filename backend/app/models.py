import enum
import secrets

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


class IntegrationSource(str, enum.Enum):
    GMAIL = "gmail"
    SLACK = "slack"
    CALENDAR = "calendar"
    WEBHOOK = "webhook"


class SuggestionStatus(str, enum.Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    REJECTED = "rejected"
    IGNORED = "ignored"


# --- Models ---


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    hashed_password = Column(String(255), nullable=False)
    name = Column(String(255), nullable=False)
    display_name = Column(String(255), nullable=True)
    avatar_url = Column(String(500), nullable=True)
    firebase_uid = Column(String(255), unique=True, nullable=True)
    tier = Column(String(20), nullable=False, server_default="FREE")
    is_admin = Column(Boolean, nullable=False, default=False, server_default="0")
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    @property
    def effective_tier(self) -> str:
        """Return PRO for admins, otherwise the stored tier."""
        if self.is_admin:
            return "PRO"
        return self.tier or "FREE"

    goals = relationship("Goal", back_populates="user", cascade="all, delete-orphan")
    tags = relationship("Tag", back_populates="user", cascade="all, delete-orphan")
    habits = relationship("Habit", back_populates="user", cascade="all, delete-orphan")
    templates = relationship("TaskTemplate", back_populates="user", cascade="all, delete-orphan")
    project_memberships = relationship("ProjectMember", back_populates="user")


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
    members = relationship("ProjectMember", back_populates="project", cascade="all, delete-orphan")


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
    eisenhower_quadrant = Column(String(2), nullable=True)  # Q1, Q2, Q3, Q4
    eisenhower_updated_at = Column(DateTime, nullable=True)
    estimated_duration = Column(Integer, nullable=True)  # minutes
    actual_duration = Column(Integer, nullable=True)  # minutes
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
    comments = relationship("TaskComment", back_populates="task", cascade="all, delete-orphan")


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


class ProjectMember(Base):
    __tablename__ = "project_members"
    __table_args__ = (
        UniqueConstraint("project_id", "user_id", name="uq_project_user"),
    )

    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    role = Column(String(20), nullable=False, default="editor")  # "owner", "editor", "viewer"
    joined_at = Column(DateTime, server_default=func.now())

    project = relationship("Project", back_populates="members")
    user = relationship("User", back_populates="project_memberships")


class ProjectInvite(Base):
    __tablename__ = "project_invites"

    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id", ondelete="CASCADE"), nullable=False, index=True)
    inviter_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    invitee_email = Column(String(255), nullable=False)
    role = Column(String(20), nullable=False, default="editor")
    token = Column(String(64), unique=True, nullable=False, index=True)
    status = Column(String(20), nullable=False, default="pending")  # pending, accepted, declined, expired
    created_at = Column(DateTime, server_default=func.now())
    expires_at = Column(DateTime, nullable=False)

    project = relationship("Project")
    inviter = relationship("User", foreign_keys=[inviter_id])

    @staticmethod
    def generate_token() -> str:
        return secrets.token_urlsafe(48)


class ActivityLog(Base):
    __tablename__ = "activity_logs"

    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(Integer, ForeignKey("projects.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    action = Column(String(50), nullable=False)
    entity_type = Column(String(20), nullable=True)  # "task", "member", "comment"
    entity_id = Column(Integer, nullable=True)
    entity_title = Column(String(255), nullable=True)
    metadata_json = Column(Text, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), index=True)

    project = relationship("Project")
    user = relationship("User")


class TaskComment(Base):
    __tablename__ = "task_comments"

    id = Column(Integer, primary_key=True, index=True)
    task_id = Column(Integer, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    content = Column(Text, nullable=False)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    task = relationship("Task", back_populates="comments")
    user = relationship("User")


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


class SuggestedTask(Base):
    __tablename__ = "suggested_tasks"
    __table_args__ = (
        UniqueConstraint("user_id", "source", "source_id", name="uq_user_source_source_id"),
    )

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    source = Column(
        Enum(IntegrationSource, values_callable=lambda x: [e.value for e in x]),
        nullable=False,
    )
    source_id = Column(String(255), nullable=False)
    source_title = Column(String(500), nullable=False)
    source_url = Column(Text, nullable=True)
    suggested_title = Column(String(500), nullable=False)
    suggested_description = Column(Text, nullable=True)
    suggested_due_date = Column(Date, nullable=True)
    suggested_priority = Column(Integer, nullable=True)
    suggested_project = Column(String(255), nullable=True)
    suggested_tags_json = Column(Text, nullable=True)
    confidence = Column(Float, nullable=False, default=0.0)
    status = Column(
        Enum(SuggestionStatus, values_callable=lambda x: [e.value for e in x]),
        default=SuggestionStatus.PENDING,
        nullable=False,
    )
    extracted_at = Column(DateTime, server_default=func.now())
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User")


class IntegrationConfig(Base):
    __tablename__ = "integration_configs"
    __table_args__ = (
        UniqueConstraint("user_id", "source", name="uq_user_integration_source"),
    )

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    source = Column(String(20), nullable=False)
    is_enabled = Column(Boolean, default=False)
    config_json = Column(Text, nullable=True)
    last_scan_at = Column(DateTime, nullable=True)
    scan_frequency_minutes = Column(Integer, default=120)
    webhook_token = Column(String(64), nullable=True, unique=True)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User")


class BugReportStatus(str, enum.Enum):
    SUBMITTED = "SUBMITTED"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    FIXED = "FIXED"
    WONT_FIX = "WONT_FIX"


class BugReportModel(Base):
    __tablename__ = "bug_reports"

    id = Column(Integer, primary_key=True)
    report_id = Column(String(64), unique=True, nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    category = Column(String(50), nullable=False)
    description = Column(Text, nullable=False)
    severity = Column(String(20), nullable=False, default="MINOR")
    steps = Column(Text, nullable=True)
    screenshot_uris = Column(Text, nullable=True)
    device_model = Column(String(255), nullable=True)
    device_manufacturer = Column(String(255), nullable=True)
    android_version = Column(Integer, nullable=True)
    app_version = Column(String(50), nullable=True)
    app_version_code = Column(Integer, nullable=True)
    build_type = Column(String(20), nullable=True)
    user_tier = Column(String(20), nullable=True)
    current_screen = Column(String(255), nullable=True)
    task_count = Column(Integer, nullable=True)
    habit_count = Column(Integer, nullable=True)
    available_ram_mb = Column(Integer, nullable=True)
    free_storage_mb = Column(Integer, nullable=True)
    network_type = Column(String(20), nullable=True)
    battery_percent = Column(Integer, nullable=True)
    is_charging = Column(Boolean, nullable=True)
    status = Column(String(20), nullable=False, default="SUBMITTED")
    admin_notes = Column(Text, nullable=True)
    diagnostic_log = Column(Text, nullable=True)
    submitted_via = Column(String(20), nullable=True, default="backend")
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User", foreign_keys=[user_id])


class NdPreferencesModel(Base):
    __tablename__ = "nd_preferences"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, unique=True, index=True)

    # Top-level mode toggles
    adhd_mode_enabled = Column(Boolean, nullable=False, default=False)
    calm_mode_enabled = Column(Boolean, nullable=False, default=False)

    # Calm Mode sub-settings
    reduce_animations = Column(Boolean, nullable=False, default=False)
    muted_color_palette = Column(Boolean, nullable=False, default=False)
    quiet_mode = Column(Boolean, nullable=False, default=False)
    reduce_haptics = Column(Boolean, nullable=False, default=False)
    soft_contrast = Column(Boolean, nullable=False, default=False)

    # ADHD Mode sub-settings
    task_decomposition_enabled = Column(Boolean, nullable=False, default=False)
    focus_guard_enabled = Column(Boolean, nullable=False, default=False)
    body_doubling_enabled = Column(Boolean, nullable=False, default=False)
    check_in_interval_minutes = Column(Integer, nullable=False, default=25)
    completion_animations = Column(Boolean, nullable=False, default=False)
    streak_celebrations = Column(Boolean, nullable=False, default=False)
    show_progress_bars = Column(Boolean, nullable=False, default=False)
    forgiveness_streaks = Column(Boolean, nullable=False, default=False)

    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User")
