# PrismTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688.svg)](https://fastapi.tiangolo.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://postgresql.org)
[![Android CI](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml/badge.svg)](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml)
[![Backend CI](https://github.com/akarlin3/prismTask/actions/workflows/ci.yml/badge.svg)](https://github.com/akarlin3/prismTask/actions/workflows/ci.yml)

A native Android task manager with a Python API backend featuring AI-powered natural language processing, voice input, full accessibility support, deep customization, productivity analytics, and first-class integrations with Gmail, Slack, and Google Calendar. Built with Kotlin/Jetpack Compose for the client and FastAPI/PostgreSQL for the server.

## Download

<!-- TODO: Replace with actual Play Store link once published -->
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.averycorp.prismtask)

## Free vs Pro vs Premium vs Ultra

PrismTask v1.4.0 introduces a four-tier pricing model.

| Feature | Free | Pro ($3.99) | Premium ($7.99) | Ultra ($9.99) |
|---------|------|-------------|-----------------|---------------|
| Task management, projects, tags, subtasks | Yes | Yes | Yes | Yes |
| Habit tracking with streaks & analytics | Yes | Yes | Yes | Yes |
| Templates, widgets, voice, accessibility | Yes | Yes | Yes | Yes |
| Cloud sync across devices | -- | Yes | Yes | Yes |
| AI Eisenhower & Pomodoro | -- | Yes | Yes | Yes |
| Analytics & time tracking | -- | Yes | Yes | Yes |
| AI briefing, planner, time blocking | -- | -- | Yes | Yes |
| Collaboration & integrations | -- | -- | Yes | Yes |
| Google Drive backup/restore | -- | -- | Yes | Yes |
| Claude Sonnet AI for all features | -- | -- | -- | Yes |

Ultra uses Claude Sonnet instead of Haiku for all AI features.
Debug builds expose a tier override in Settings for local development.
