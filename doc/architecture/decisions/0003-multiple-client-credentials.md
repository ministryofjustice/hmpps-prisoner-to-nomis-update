# 3. Multiple Client Credentials

[Next >>](9999-end.md)


Date: 2022-01-12

## Status

Accepted

## Context

Because this service will need to interact with multiple clients, in particular the publisher of domain events, 
and we expect the number of clients to grow as more services become the primary source of data, there are two reasonable choices on managing the client credentials  required to access these services:

- Use a single client credential for all services
- Use multiple client credentials, one for each service

Using a single set of credentials for access all services is the simplest configuration both within this service and with HMPPS Auth which creates the credentials.

The disadvantage of the singe credential set would be it would need to have the privileges to access all services, every role and scope for every possible API that this service needs to call.  


## Decision

**Use multiple client credentials, one for each service**

This pattern has been used in other HMPPS services and though the configuration is more complicated within HMPPS Auth, within this service the configuration is relative simple with changes to
- Helm values yaml
- Simple change in the application.yaml
- That credential set is then referenced in the Web Client Configuration which is required for each client anyway.

## Consequences

- HMPPS Auth might want to consider better ways to manage credentials as a group given all credentials are associated with the same owning service
- There is a possibility to revoke a single set of credentials as a hard emergency feature switch to stop synchronisation of data of a specific service


[Next >>](9999-end.md)
