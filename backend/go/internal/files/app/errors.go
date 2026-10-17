package app

import apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"

var (
	ErrMissing   = apperrors.New(apperrors.NotFound, "files.not_found", "file not found").WithPublic("This file is not available.")
	ErrReceipt   = apperrors.New(apperrors.NotFound, "files.receipt_not_found", "file receipt not found").WithPublic("No committed receipt was found.")
	ErrForbidden = apperrors.New(apperrors.Forbidden, "files.account_forbidden", "file account access denied").WithPublic("You cannot access files in this account.")
	ErrConflict  = apperrors.New(apperrors.Conflict, "files.version_conflict", "file version changed").WithPublic("Refresh the file before trying again.")
	ErrKey       = apperrors.New(apperrors.Conflict, "files.command_conflict", "idempotency key reused with different input").WithPublic("This command key belongs to a different operation.")
	ErrInput     = apperrors.New(apperrors.Invalid, "files.invalid_input", "invalid files application input").WithPublic("Check the file request fields.")
	ErrContent   = apperrors.New(apperrors.Invalid, "files.content_mismatch", "file bytes do not match declared size or digest").WithPublic("The content does not match the declared size or SHA-256 digest.")
	ErrObject    = apperrors.New(apperrors.Unavailable, "files.object_missing", "file object not present").WithPublic("Upload the file content before completing the upload.")
	ErrStorage   = apperrors.New(apperrors.Unavailable, "files.storage_unavailable", "file object store unavailable").WithPublic("File storage is temporarily unavailable.")
)
