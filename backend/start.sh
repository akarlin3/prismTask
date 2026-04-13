#!/bin/bash
if [ -f /app/alembic.ini ]; then
  cd /app
elif [ -f /app/backend/alembic.ini ]; then
  cd /app/backend
fi
alembic upgrade head
exec gunicorn app.main:app -c gunicorn.conf.py
