// Package errors classifies expected failures without changing Go's error contract.
package errors

import (
	"context"
	stderrors "errors"
	"maps"
)

type Kind string

const (
	Invalid         Kind = "invalid"
	Unauthenticated Kind = "unauthenticated"
	Forbidden       Kind = "forbidden"
	NotFound        Kind = "not_found"
	Conflict        Kind = "conflict"
	RateLimited     Kind = "rate_limited"
	Unavailable     Kind = "unavailable"
	Timeout         Kind = "timeout"
	Canceled        Kind = "canceled"
	Internal        Kind = "internal"
)

// Error keeps diagnostics private. Use Public at an external boundary.
type Error struct {
	kind                     Kind
	code, diagnostic, public string
	fields                   map[string]string
	cause                    error
	condition                *Error
}

func New(kind Kind, code, diagnostic string) *Error {
	return &Error{kind: kind, code: code, diagnostic: diagnostic}
}

func (e *Error) Error() string {
	if e == nil {
		return "<nil>"
	}
	if e.cause != nil {
		return e.diagnostic + ": " + e.cause.Error()
	}
	return e.diagnostic
}

func (e *Error) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.cause
}
func (e *Error) identity() *Error {
	if e.condition != nil {
		return e.condition
	}
	return e
}
func (e *Error) Is(target error) bool {
	other, ok := target.(*Error)
	return ok && e != nil && other != nil && e.identity() == other.identity()
}

func (e *Error) WithPublic(message string) *Error {
	next := *e
	next.public = message
	next.condition = e.identity()
	return &next
}
func (e *Error) WithFields(fields map[string]string) *Error {
	next := *e
	next.fields = maps.Clone(fields)
	next.condition = e.identity()
	return &next
}
func (e *Error) WithCause(cause error) *Error {
	next := *e
	next.cause = cause
	next.condition = e.identity()
	return &next
}

// KindOf recognizes cancellation even when a dependency wraps it.
func KindOf(err error) Kind {
	if err == nil {
		return Internal
	}
	if joined, ok := err.(interface{ Unwrap() []error }); ok {
		kind := Kind("")
		for _, child := range joined.Unwrap() {
			if child == nil {
				continue
			}
			childKind := KindOf(child)
			if childKind == Internal || (kind != "" && kind != childKind) {
				return Internal
			}
			kind = childKind
		}
		if kind == "" {
			return Internal
		}
		return kind
	}
	if classified, ok := err.(*Error); ok && classified != nil {
		switch classified.kind {
		case Invalid, Unauthenticated, Forbidden, NotFound, Conflict, RateLimited, Unavailable, Timeout, Canceled, Internal:
			return classified.kind
		}
		return Internal
	}
	if wrapped, ok := err.(interface{ Unwrap() error }); ok {
		return KindOf(wrapped.Unwrap())
	}
	if stderrors.Is(err, context.Canceled) {
		return Canceled
	}
	if stderrors.Is(err, context.DeadlineExceeded) {
		return Timeout
	}
	return Internal
}

type PublicInfo struct {
	Kind          Kind
	Code, Message string
	Fields        map[string]string
}

// Public never copies diagnostic text, causes, or internal condition codes.
// Safe public copy and fields must be deliberately supplied by the owner.
func Public(err error) PublicInfo {
	view := PublicInfo{Kind: KindOf(err), Message: "The request failed."}
	if view.Kind == Internal {
		view.Message = "An unexpected error occurred."
		return view
	}
	// A joined failure has no single owner's public message or condition code.
	for current := err; current != nil; {
		if joined, ok := current.(interface{ Unwrap() []error }); ok {
			var only error
			for _, child := range joined.Unwrap() {
				if child == nil {
					continue
				}
				if only != nil {
					return view
				}
				only = child
			}
			if only != nil {
				return Public(only)
			}
			return view
		}
		if _, ok := current.(*Error); ok {
			break
		}
		wrapped, ok := current.(interface{ Unwrap() error })
		if !ok {
			break
		}
		current = wrapped.Unwrap()
	}
	var classified *Error
	if stderrors.As(err, &classified) && classified != nil {
		view.Code = classified.code
		if classified.public != "" {
			view.Message = classified.public
		}
		if view.Kind == Invalid {
			view.Fields = maps.Clone(classified.fields)
		}
	}
	return view
}
