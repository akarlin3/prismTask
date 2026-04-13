from fastapi import Depends, HTTPException, status

from app.middleware.auth import get_current_user
from app.models import User


async def require_admin(
    current_user: User = Depends(get_current_user),
) -> User:
    """Dependency that checks the current user has the is_admin flag.

    Returns the User if they are an admin; raises 403 otherwise.
    """
    if not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required",
        )
    return current_user
