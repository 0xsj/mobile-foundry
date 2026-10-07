# Module walkthrough template

Create this note under `notes/modules/<source-path>/` in its owning platform.
Mirror the directory being explained, link actual files, and write for someone
reading the implementation between sessions.

## Claim

State what the implemented slice does and the boundary it owns.

## Origin

Name the implemented slice, observation date, source version or relevant tool
versions, and evidence used. Link the contract or source API documentation
that owns the promised behavior.

## Reading order

List the source files in the order that makes the flow easiest to follow. Link
each file and briefly explain its role. Add tests and fixtures where they help.

## Walkthrough

Follow one concrete input through composition, state changes, success, and a
meaningful failure. Explain relevant syntax at its first use and link reusable
language, substrate, concept, and pattern notes. Label selected code Excerpt.

## Verification and limits

Record commands actually run, their observed outcomes, and the behavior they
cover. Identify the remaining limits that affect the slice, such as untested
device behavior or a simulated dependency.

## Gotchas

Explain decisions, edge cases, and alternatives that affect callers or future
changes. Distinguish the generated scaffold from deliberate foundation policy.

## Questions for the next session

Give a few questions answerable from this reading or a bounded experiment.
Keep intended future work explicitly separate from implemented behavior.

## Related

Link neighboring module notes and transferable findings already in the notebook.
