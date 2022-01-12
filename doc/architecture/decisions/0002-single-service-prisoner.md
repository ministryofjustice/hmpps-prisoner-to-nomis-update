# 2. Single synchronisation update service used for all prisoner domain events

[Next >>](9999-end.md)


Date: 2022-01-12

## Status

Accepted

## Context


HMPPS Domain events can be raised for business events across HMPPS, these can affect a variety subjects, for instance; staff, documents, defendants, prisoners, etc.
In the prison space these events may be related to prisoners or other subjects such as reference data.
A smaller subset of these events require us to synchronise data from various HMPPS systems to NOMIS.

Since we require synchronisation services that can update NOMIS across a large domain there are 3 stand out choices on how many synchronisation services we have:
- One service for all prison related events
- One service for all publishing services (e.g. one for prison visits, another for case notes, and yet another of registers)
- One service for each major domain subject within NOMIS (e.g. one for prisoners, one for registers)

Furthermore, these services will be owned and maintained by the Syscon team since they are focused on keeping NOMIS is sync with external systems. 

## Decision

We will have one service for each major domain subject related to NOMIS

Currently, this means we will have two services:
- `hmpps-prisoner-to-nomis-update`  (this service)
- `hmpps-registers-to-nomis-update` (already existing)

New services will be created if it needs to process events that do not belong to the above domains.

## Consequences

- This reduces the number of services required to be maintained, and given these services will be retired when NOMIS is retired this should reduce the maintenance overhead on a single team (Syscon) maintaining dozens of services.
- This will increase the complexity of this service since it will need to interact with multiple external services and the `prisoner` domain is obviously very large in NOMIS, so we expect many new services creating data.
- Mitigation of the above needs careful structuring of the service (e.g. using packages/namespaces)
- The existing register service does not need to be merged into this service



[Next >>](9999-end.md)
