# Separate fixed ingress addresses from automatic allocation

Claim: a static service IP remains safe only when automatic allocation cannot
give that address to an earlier-started container.

Origin/evidence: 2026-10-18 Docker 29.8.2/Caddy 2.10.2 local execution. Starting
database/API before the edge produced “Address already in use” for the edge's .2.
[Compose's IPAM documentation](https://docs.docker.com/reference/compose-file/networks/#ipam)
defines subnet separately from the container allocation range.

What and why: declaring a static IP does not reserve it before that service's
endpoint exists. Constrain dynamic container addresses to a range that excludes
the edge, while keeping both inside the chosen subnet. The matching trusted CIDR
must continue to name the actual edge; silently trusting the whole subnet broadens
who can assert an effective client address.

Example (Conceptual): a /24 has automatic addresses in its upper /25 and the
edge fixed at .2 below that range. Database/tool start order no longer competes
for the edge address. A disposable driver selects unused subnets and derives both
the upper allocation range and the matching edge trust policy.

Gotchas: update subnet, allocation range, edge address and trust together. A
valid configuration and a Caddyfile syntax check did not exercise endpoint
assignment. The corrected profile passed actual edge/native WebSocket execution;
DNS/public TLS issuance remains separate.
Used in: [portable profile](../modules/infra/production/README.md).
Next: how would an external load balancer alter the trusted socket boundary?
