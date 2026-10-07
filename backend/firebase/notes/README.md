# Firebase learning notes

This notebook explains the Local Emulator Suite profile and later native
integration. Follow [the shared workflow](../../../docs/NOTES.md); modules,
language, patterns, concepts, techniques, and substrate keep Bento's meanings.

## Reading order

1. [Profile setup](../README.md) for commands, endpoints, and SDK requirements.
2. [Demo projects and emulator persistence](substrate/local-emulator-suite.md)
   for the toolchain, export/import behavior, and observed verification.

## Current coverage

The initial profile contains Auth, Firestore, Storage, and emulator UI. Data
rules deny client access until a feature defines its ownership. Native SDK
connections, account policies, cloud deployment, functions, and sync semantics
remain unimplemented.

## Next questions

- Where should the native composition switch each SDK to its emulator endpoint?
- Which account checks must a rule enforce for the first feature?
- Which SDK cache behavior belongs in the repository contract and its fixtures?
