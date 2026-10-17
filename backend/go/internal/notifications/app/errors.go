package app

import apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"

var (
	ErrInput     = apperrors.New(apperrors.Invalid, "notifications.invalid_input", "invalid notification input").WithPublic("Check the notification request fields.")
	ErrForbidden = apperrors.New(apperrors.Forbidden, "notifications.account_forbidden", "notification account access denied").WithPublic("You cannot access notifications in this account.")
	ErrMissing   = apperrors.New(apperrors.NotFound, "notifications.not_found", "notification not found").WithPublic("This notification is not available.")
	ErrReplay    = apperrors.New(apperrors.Conflict, "notifications.event_conflict", "source event identity reused with different input")
	ErrCapacity  = apperrors.New(apperrors.Unavailable, "notifications.capacity", "notification store capacity reached")
)
