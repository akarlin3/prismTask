from datetime import date, datetime, timezone


def compute_urgency(
    due_date: date | None,
    priority: int,
    created_at: datetime | None,
    completed_at: datetime | None = None,
) -> float:
    """Compute urgency score (0.0 to 1.0) matching the Android UrgencyScorer logic.

    Factors:
    - due_date proximity (40% weight)
    - priority level (30% weight)
    - task age (20% weight)
    - overdue penalty (10% weight)
    """
    if completed_at:
        return 0.0

    score = 0.0
    today = date.today()

    # Due date factor (0-1, 40% weight)
    if due_date:
        days_until = (due_date - today).days
        if days_until < 0:
            due_factor = 1.0  # overdue
        elif days_until == 0:
            due_factor = 0.95
        elif days_until <= 1:
            due_factor = 0.8
        elif days_until <= 3:
            due_factor = 0.6
        elif days_until <= 7:
            due_factor = 0.4
        elif days_until <= 14:
            due_factor = 0.2
        else:
            due_factor = 0.1
        score += due_factor * 0.4

        # Overdue penalty (10% weight)
        if days_until < 0:
            overdue_factor = min(abs(days_until) / 14.0, 1.0)
            score += overdue_factor * 0.1
    else:
        score += 0.05 * 0.4  # No due date: low urgency

    # Priority factor (30% weight): matches Android UrgencyScorer
    # (0=None, 1=Low, 2=Medium, 3=High, 4=Urgent)
    priority_map = {0: 0.0, 1: 0.2, 2: 0.5, 3: 0.8, 4: 1.0}
    priority_factor = priority_map.get(priority, 0.5)
    score += priority_factor * 0.3

    # Age factor (20% weight)
    if created_at:
        if created_at.tzinfo is None:
            age_days = (datetime.now() - created_at).days
        else:
            age_days = (datetime.now(timezone.utc) - created_at).days
        age_factor = min(age_days / 30.0, 1.0)
        score += age_factor * 0.2

    return round(min(score, 1.0), 3)
