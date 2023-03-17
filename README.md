[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-prisoner-to-nomis-update/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-prisoner-to-nomis-update)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui.html)

# hmpps-prisoner-to-nomis-update

**Handles HMPPS Domain events for prisoner NOMIS updates.**

The purpose of this service is to handle HMPPS Domain events for prisoner NOMIS updates. This listens for domain events related to changes that affect prisoner data in NOMIS. 
It will interrogate the event and will possibly request further information from the publishing service. Using that information with will call `hmpps-nomis-prisoner-api` to apply any updates to NOMIS. 


## Running locally

For running locally against docker instances of the following services:
- hmpps-auth
- hmpps-nomis-prisoner-api
- localstack  
- run this application independently e.g. in IntelliJ

`docker compose up hmpps-auth hmpps-nomis-prisoner-api hmpps-nomis-visit-mapping-api localstack`

Running all services including this service 

`docker compose up`

## Running locally against T3 test services

Though it is possible to run this service against both the test services deployed in the cloud **and** the SQS queue in AWS this is not recommended while the deployed version of this service is running in the Cloud Platform since there is no guarantee this local instance will read an incoming HMPPS domain event since other pods are also reading from the same queue.

However, if you do wish to do that the steps would be:
- Set all environment variables to match those in [values-dev.yaml](/helm_deploy/values-dev.yaml) e.g. `API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth`
- Set additional environment variables for personal **client credentials** you have that have the correct roles required to access the remotes services, the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-to-nomis-update/values.yaml)
- Set additional environment variables for the SQS Queue secrets that can be found in the `hmpps-prisoner-to-nomis-update-dev` namespace, again the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-to-nomis-update/values.yaml)

A better hybrid solution which gives better control messaging would be similar to above but using the `dev` profile and therefore localstack.

The first 2 of the 3 steps is required but instead of step 3

- `docker-compose up localstack` or `docker compose up localstack` (there is also docker-compose-localstack.yaml with just localstack defined )

Then run any of the `bash` scripts at the root of this project to send events to the local topic

## Mock services

There are circumstances where you want to run this service end to end but without the publishing service being available, for example the publishing service
has not been written yet. To emulate the publishing service we may provide a mock, for instance MockVisitsResource which returns canned data. The canned data might have limited ability to be configured per-environment. 
Details of the configuration follows:

### MockVisitsResource

Environment overrides for the MockVisitsResource are:
`MOCK_VISITS_PRISON_ID` - The prison where the visit is scheduled
`MOCK_VISITS_VISITORS`- the person ids of the visitors

e.g.
`MOCK_VISITS_PRISON_ID=WWI`
`MOCK_VISITS_VISITORS=1838,273723`

### Runbook

#### Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.

#### Duplicate handling

There are various scenarios where a duplicate event may be received:
- The call to the mapping service hangs for more than 2 minutes but eventually returns
- Bugs in the publishing service, for instance double submits
- SNS service simple sending a duplicate event

In all these scenarios manual intervention is required to solve duplicate issues. All duplicates will result in a single alert to the main Slack alerts channel.

These are as follows:

- *To NOMIS synchronisation duplicate activity detected in Production*
- *To NOMIS synchronisation duplicate incentive detected in Production*
- *To NOMIS synchronisation duplicate visit detected in Production*
- *To NOMIS synchronisation duplicate sentencing adjustment detected in Production*

The action top be taken as as follows: 

##### Activities

TBD

##### Incentives

Duplicate incentives have no business impact in NOMIS but can cause confusion to users. That confusion is only the case so long as the NOMIS IEP page is still in use. This screen is currently being phased out. The only way to delete an IEP is to ask `#dps-appsupport` and supply them with the prisoner number and sequence. *For now it advised to take no action unless there is a complaint from the prison* since the impact on the business is negligible.

##### Visits

A duplicate visit is serious since for sentenced prisoners they will have one less visit for that week. Therefore the visit should be cancelled. This could be dome by #dps-appsupport or by us using the cancel endpoint. The cancel endpoint is the quickest solution.

Example PUT to cancel a visit:

```
curl --location --request PUT 'https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/prisoners/A9999DP/visits/16999999/cancel' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--header 'Authorization: Bearer <token with role NOMIS_VISITS>' \
--data-raw '{
"outcome": "ADMIN"
}'
```
##### Sentencing adjustments

A duplicate sentencing adjustment is serious since it will result in the prisoner being released early/late. Therefore the sentencing adjustment should be cancelled. This could be dome by #dps-appsupport or by us using the delete endpoint. The delete endpoint is the quickest solution.

Example DELETE to cancel a sentencing adjustment:

For sentence adjustment:

```
curl -X 'DELETE' \
  'https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/sentence-adjustments/99999' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer <token with role NOMIS_SENTENCING>'
```

For key date adjustment:

```
curl -X 'DELETE' \
  'https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/key-date-adjustments/99999' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer <token with role NOMIS_SENTENCING>'
```


### Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
