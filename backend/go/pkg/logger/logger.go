// Package logger configures the standard structured logging boundary.
package logger

import (
	"io"
	"log/slog"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

func New(output io.Writer, format, level string) (*slog.Logger, error) {
	if output == nil {
		return nil, apperrors.New(apperrors.Invalid, "logger.invalid", "log writer is required")
	}
	var severity slog.Level
	switch level {
	case "debug":
		severity = slog.LevelDebug
	case "info":
		severity = slog.LevelInfo
	case "warn":
		severity = slog.LevelWarn
	case "error":
		severity = slog.LevelError
	default:
		return nil, apperrors.New(apperrors.Invalid, "logger.invalid", "unsupported log level")
	}
	options := &slog.HandlerOptions{Level: severity}
	switch format {
	case "console":
		return slog.New(slog.NewTextHandler(output, options)), nil
	case "json":
		return slog.New(slog.NewJSONHandler(output, options)), nil
	default:
		return nil, apperrors.New(apperrors.Invalid, "logger.invalid", "unsupported log format")
	}
}
