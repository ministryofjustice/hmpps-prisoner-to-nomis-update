[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-prisoner-to-nomis-update/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-prisoner-to-nomis-update)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui.html)

# hmpps-prisoner-to-nomis-update

Handles hmpps Domain events for NOMIS updates

## Running locally

For running locally against docker instances of the following services:
- hmpps-auth
- hmpps-nomis-prisoner-api
- run this application independently e.g. in IntelliJ

`docker compose up hmpps-auth hmpps-nomis-prisoner-api`

or 

`docker-compose up hmpps-auth hmpps-nomis-prisoner-api`

Running all services including this service 

`docker compose up`

or

`docker-compose up`
