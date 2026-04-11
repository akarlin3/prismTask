"""Direct unit tests for [calculate_next_date] in services.recurrence."""

from __future__ import annotations

from datetime import date

from app.services.recurrence import calculate_next_date


def test_daily_adds_interval_days():
    assert calculate_next_date('{"type":"daily","interval":1}', date(2026, 4, 11)) == date(2026, 4, 12)


def test_daily_with_interval_3():
    assert calculate_next_date('{"type":"daily","interval":3}', date(2026, 4, 11)) == date(2026, 4, 14)


def test_weekly_without_days_adds_one_week():
    assert calculate_next_date('{"type":"weekly","interval":1}', date(2026, 4, 11)) == date(2026, 4, 18)


def test_weekly_with_days_of_week_finds_next_match():
    # 2026-04-11 is a Saturday (weekday=5). Asking for Monday (0) next.
    next_date = calculate_next_date(
        '{"type":"weekly","interval":1,"daysOfWeek":[0]}',
        date(2026, 4, 11),
    )
    assert next_date == date(2026, 4, 13)  # Monday


def test_monthly_clamps_to_last_day_when_target_day_missing():
    # 2026-01-31 → monthly → should land on 2026-02-28 (Feb has 28 days).
    result = calculate_next_date(
        '{"type":"monthly","interval":1,"dayOfMonth":31}',
        date(2026, 1, 31),
    )
    assert result == date(2026, 2, 28)


def test_monthly_adds_interval_months():
    result = calculate_next_date(
        '{"type":"monthly","interval":2}',
        date(2026, 1, 15),
    )
    assert result == date(2026, 3, 15)


def test_yearly_adds_interval_years():
    result = calculate_next_date(
        '{"type":"yearly","interval":1}',
        date(2026, 4, 11),
    )
    assert result == date(2027, 4, 11)


def test_empty_rule_returns_none():
    assert calculate_next_date("", date(2026, 4, 11)) is None


def test_malformed_json_returns_none():
    assert calculate_next_date("not json", date(2026, 4, 11)) is None


def test_unknown_type_returns_none():
    result = calculate_next_date(
        '{"type":"unknown","interval":1}',
        date(2026, 4, 11),
    )
    assert result is None
