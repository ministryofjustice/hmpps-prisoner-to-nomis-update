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

## Generating APi client models

For some of our external API calls we use `openapi-generator` to generate the models used in the API clients. The Open API specifications used can be found in directory `openapi-specs`.

### Updating the Open API specs

Run the following commands to take a copy of the latest specs (requires `jq` is installed):

```
curl https://nomis-prsner-dev.aks-dev-1.studio-hosting.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/nomis-sync-api-docs.json
curl https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/activities-api-docs.json
```

Then run another command to regenerate the models in the `build/generated/src` directory:

`./gradlew clean compileKotlin`

Now build the project and deal with any compile errors caused by changes to the generated models.

Finally run the tests and fix any issues caused by changes to the models.

## Runbook

### Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.

### Duplicate handling

There are various scenarios where a duplicate event may be received:
- The call to the mapping service hangs for more than 2 minutes but eventually returns
- Bugs in the publishing service, for instance double submits
- SNS service simply sending a duplicate event

In all these scenarios manual intervention is required to solve duplicate issues. All duplicates will result in a single alert to the main Slack alerts channel.

These are as follows:

- *To NOMIS synchronisation duplicate activity detected in Production*
- *To NOMIS synchronisation duplicate incentive detected in Production*
- *To NOMIS synchronisation duplicate visit detected in Production*
- *To NOMIS synchronisation duplicate sentencing adjustment detected in Production*

The action to be taken is as follows: 

#### Activities

##### Course Activities

A duplicate course activity in Nomis will have no detrimental effects:
* the Nomis Course Activities id mentioned in the mapping table `activity_mapping` is "active" 
* updates/allocations/attendances will all be synchronised against this course activity
* the duplicate course activity does no particular harm as it is not used or referenced 

However, you should still remove the duplicate in Nomis so it doesn't cause any confusion:
* click the View button on the Slack alert to find the customEvent in App Insights
* the customDimensions should contain a `duplicateNomisCourseActivityId` - this is the id of the COURSE_ACTIVITIES record in Nomis we need to remove
* there is an endpoint in hmpps-nomis-prisoner-api - `DEL /activities/{courseActivityId}` - call this to remove the duplicate

##### Allocations (OFFENDER_PROGRAM_PROFILES)

A duplicate allocation won't receive a `To NOMIS synchronisation duplicate activity detected` alert but will end up with a DLQ message. See [duplicate allocations](#duplicate-allocations-and-attendances) for more details.

##### Attendances (OFFENDER_COURSE_ATTENDANCES)

A duplicate attendance won't receive a `To NOMIS synchronisation duplicate activity detected` alert but will end up with a DLQ message. See [duplicate attendances](#duplicate-allocations-and-attendances) for more details.

#### Incentives

Duplicate incentives have no business impact in NOMIS but can cause confusion to users. That confusion is only the case so long as the NOMIS IEP page is still in use. This screen is currently being phased out. The only way to delete an IEP is to ask `#dps-appsupport` and supply them with the prisoner number and sequence. *For now it advised to take no action unless there is a complaint from the prison* since the impact on the business is negligible.

#### Visits

A duplicate visit is serious since for sentenced prisoners they will have one less visit for that week. Therefore the visit should be cancelled. This could be done by #dps-appsupport or by us using the cancel endpoint. The cancel endpoint is the quickest solution.

* Click on the View button of the alert and check the `customDimensions` of the App Insights query results to get the `offenderNo` and `duplicateNomisId`
* Check the offender's visits in production Nomis to prove there is a duplicate, e.g. `https://digital.prison.service.justice.gov.uk/prisoner/<offenderNo>/visits-details`
* Grab an auth token with role `NOMIS_VISITS` and call the following API to cancel the duplicate visit:
```
curl --location --request PUT 'https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/prisoners/<offenderNo>/visits/<duplicateNomisId>/cancel' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--header 'Authorization: Bearer <token with role NOMIS_VISITS>' \
--data-raw '{
"outcome": "ADMIN"
}'
* Check in Nomis again and the duplicate visit should have been cancelled

```
#### Sentencing adjustments

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
### Incentive Reconciliation Report Alert

A weekly job checks whether for any active prisoner the NOMIS IEP level matches the DPS Incentive level. When there is a mismatch an alert is sent to the `#sycon-alerts` channel. 

The alert is as follows: `NOMIS IEP level does not match DPS Incentive level in Production`. This will be sent of each individual prisoner that has a mismatch when the following custom event is written `incentives-reports-reconciliation-mismatch`, this contains the levels, bookingId and prisoner number.

In Application Insights there is also an additional custom event `incentives-reports-reconciliation-report` which contains the summary of mismatches (if any) for the week.

#### Action to be taken

The action to be taken depends on the system that is in incorrect state and the cause of the mismatch.

First check the state of NOMIS and ths state of DPS.

To check NOMIS you can run this endpoint:
```bash
curl --location 'https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/incentives/booking-id/{bookingId}}/current' \
--header 'Authorization: Bearer <token with ROLE_NOMIS_INCENTIVES>'
```
This will return what NOMIS believes is the current IEP, for instance the snippet 
```json
{
  "iepDateTime": "2023-02-24T13:14:23",
  "iepLevel": {
    "code": "ENH",
    "description": "Enhanced"
  }
}
```
clearly means NOMIS thinks the prisoner is on Enhanced level.

To check DPS you can run this endpoint:

```bash
curl --location 'https://incentives-api.hmpps.service.justice.gov.uk/iep/reviews/booking/{bookingId}}?with-details=true' \
--header 'Authorization: Bearer <token with valid token>'
```
This will return what DPS believes is the current IEP, for instance the snippet
```json
{
  "iepCode": "STD",
  "iepLevel": "Standard",
  "iepDate": "2022-06-30",
  "iepTime": "2022-06-30T15:12:36"
}
 ```
clearly means DPS thinks the prisoner is on Standard level.

#### Finding the source of truth

The current level should either be set to the last manually created review or the Incentive DPS service needs to create a system generate review based on the last movement. Given that 
any new mismatches should never have an IEP level derived from a system generated one from NOMIS, the current IEP in NOMIS should have an `auditModule` that is either `JDBC Thin Client` or `OIDOIEPS` else it means NOMIS has the wrong level.

It is likely to have the wrong level for one of these two reasons:
- The DPS Incentive service was not aware of a prisoner movement, e.g. they never received a `prisoner-offender-search.prisoner.received` event. This will be the case if the last DPS Incentive Review has not been created when the prisoner moved to the current prison. This date could be found in the database or from the DPS case notes.
- NOMIS has created a system generated IEP *after* DPS has created its system generated IEP review. This would be the case if there are multiple IEP records all around the same time and the auditModule is the transfer or admission NOMIS screen. This indicates the prisoner has somehow been transferred in twice (which is possible if two users transfer the same prisoner in at roughly the same time)


#### Fixing the mismatch
- For the missing `prisoner-offender-search.prisoner.received` event, this can be triggered manually using an endpoint in `prisoner-offender-search`. The endpoint requires client credentials with the role `EVENTS_ADMIN`. Example request is 
```bash

  curl --location --request PUT 'https://prisoner-offender-search.prison.service.justice.gov.uk/events/prisoner/received/A9999AR' \
--header 'Authorization: Bearer <token with ROLE_EVENTS_ADMIN>' \
--header 'Content-Type: application/json' \
--data '{
  "reason": "TRANSFERRED",
  "prisonId": "MDI",
  "occurredAt": "2023-02-24T13:14:23"
}'
  ```
The side effect of this event is a DPS IEP Review is created which is then written back to NOMIS

- For duplicate NOMIS system generated IEP the only solution is for NOMIS Support Team to delete the rouge IEP records, that is the one or more records that was generated after the DPS one.

### Activities Synchronisation Error Handling

When a message ends up on the Activities Dead Letter Queue (DLQ) we receive an alert. To work out what went wrong:

* First call the [get-dlq-messages endpoint](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html#/hmpps-reactive-queue-resource/getDlqMessages) to get the failed message. Note that the queue name can be found on the `/health` endpoint.
* There should be an event type in the message and an id in the `additionalInformation` details, both of which tell you what the message was trying to achieve

Find recent failures in App Insights Logs with the following query and look for the additionalInformation id in customDimensions (probably `dpsActivityScheduleId`/`nomisCourseActivityId` but there are other dps/nomis ids for different event types):
```ksql
customEvents
| where name startswith "activity"
| where name endswith "failed"
| where timestamp > ago(30m)
| join traces on $left.operation_Id == $right.operation_Id
| where message !startswith "Received message"
| where message !startswith "Created access"
| where message !startswith "Setting visibility"
| project timestamp, message, operation_Id, customDimensions
| order by timestamp desc 
```

Sometimes you may also need further information to diagnose the problem. You can do this based upon the `operation_Id` returned from the above query.
* Look for failed requests:
```ksql
requests
| where operationId == '<insert operationId here>'
| where success == False
```
* Look for exceptions:
```ksql
exceptions
| where operationId == '<insert operationId here>'
```

After investigating the error you should now know if it's recoverable or not.

* If the error is recoverable (e.g. calls to another service received a 504) then you can leave the message on the DLQ because a retry should work once the other service recovers.
* If the error is unrecoverable (e.g. calls to another service received a 400) then you probably have enough information to diagnose the issue and constant failing retries/alerts will be annoying. Consider [purging the DLQ](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html#/hmpps-reactive-queue-resource/purgeQueue).

#### Duplicate allocations and attendances

If we receive duplicate create allocation messages on different pods at the same time then we may end up with duplicate `OFFENDER_PROGRAM_PROFILES` records in Nomis. For attendances we may end up with duplicate `OFFENDER_COURSE_ATTENDANCES` records in Nomis.

As Hibernate isn't expecting these duplicates it will fail the next time we try to update an allocation or attendance.

This error is identifiable by looking at the exceptions in App Insights checkin for:
* a Hibernate exception with text containing `query did not return a unique result` 
* further down the call stack a mention of the `AllocationService` for allocations or `AttendanceService` for attendances.

To recover from this error we need to delete the duplicate allocation or attendance record:
* the custom event from App Insights should have customDimension of `nomisCourseActivityId`
* in the Nomis database run the following query to see all allocations for that course - 2 records for the same `OFFENDER_BOOK_ID`, `CRS_ACTY_ID` with `OFFENDER_PROGRAM_STATUS`='ALLOC' are duplicates:
```sql
select * from OMS_OWNER.OFFENDER_PROGRAM_PROFILES where CRS_ACTY_ID=<insert nomisCourseActivityId here>;
```
* and the following query to see all attendances for the course - 2 records for the same `OFFENDER_BOOK_ID`, `EVENT_DATE`, `START_TIME` and `END_TIME` are duplicates:
```sql
select * from OMS_OWNER.OFFENDER_COURSE_ATTENDANCES where CRS_ACTY_ID=<insert nomisCourseActivityId here>;
```
* if you find a duplicate allocation call the [delete allocation endpoint](https://nomis-prsner-dev.aks-dev-1.studio-hosting.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs#/activities-resource/deleteAllocation) using column `OFF_PRGREF_ID`
* if you find a duplicate attendance call the [delete attendance endpoint](https://nomis-prsner-dev.aks-dev-1.studio-hosting.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs#/activities-resource/deleteAttendance) using column `EVENT_ID`

As this error should recover once the duplicate is deleted you don't need to purge the DLQ.

## Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
