# PrismTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688.svg)](https://fastapi.tiangolo.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://postgresql.org)
[![Android CI](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml)
[![Backend CI](https://github.com/akarlin3/prismTask/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/akarlin3/prismTask/actions/workflows/backend-ci.yml)
[![Web CI](https://github.com/akarlin3/prismTask/actions/workflows/web-ci.yml/badge.svg?branch=main)](https://github.com/akarlin3/prismTask/actions/workflows/web-ci.yml)
[![Release](https://github.com/akarlin3/prismTask/actions/workflows/release.yml/badge.svg)](https://github.com/akarlin3/prismTask/actions/workflows/release.yml)

A cross-platform task manager with a Python API backend featuring AI-powered natural language processing, voice input, full accessibility support, deep customization, productivity analytics, and first-class integrations with Gmail, Slack, and Google Calendar. Available as a native Android app (Kotlin/Jetpack Compose) and a web app (React/TypeScript/Vite), both powered by a shared FastAPI/PostgreSQL backend.

## Download

<!-- TODO: Replace with actual Play Store link once published -->
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.averycorp.prismtask)

## Free vs Pro

PrismTask ships with a two-tier pricing model.

| Feature | Free | Pro |
|---------|------|-----|
| Task management, projects, tags, subtasks | Yes | Yes |
| Habit tracking with streaks | Yes | Yes |
| Templates, widgets, voice, accessibility | Yes | Yes |
| Work-Life Balance Engine | Yes | Yes |
| Cloud sync across devices | -- | Yes |
| AI Eisenhower & Pomodoro | -- | Yes |
| AI briefing, weekly planner, time blocking | -- | Yes |
| AI coach, task breakdown, daily planning | -- | Yes |
| Syllabus import | -- | Yes |

Pro is available as an annual subscription ($4.99/mo billed $59.88/year with a
7-day free trial) or month-to-month at $7.99/mo.

AI features run on Claude Haiku; the weekly planner and monthly review use
Claude Sonnet for higher-quality output. Debug builds expose a tier override
in Settings for local development.

## Platforms

### Android

Native app built with Kotlin and Jetpack Compose. See the [Download](#download) section above for Play Store links.

### Web

React + TypeScript + Vite web client with TailwindCSS. Connects to the same FastAPI backend as the Android app. See [`web/README.md`](web/README.md) for setup instructions.

### Backend

FastAPI server with PostgreSQL, JWT authentication, and Claude-powered NLP parsing. Deployed on Railway. See [`ARCHITECTURE.md`](ARCHITECTURE.md) for API docs and data model.
