// Package env separates process environment access from configuration validation.
package env

import "os"

type Source func(string) (string, bool)

func Process(key string) (string, bool) { return os.LookupEnv(key) }
func (source Source) String(key, fallback string) string {
	if value, found := source(key); found {
		return value
	}
	return fallback
}
