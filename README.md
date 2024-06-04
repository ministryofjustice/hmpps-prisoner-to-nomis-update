[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-to-nomis-update)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-to-nomis-update "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-to-nomis-update)
[![Docker Repository on Quay](https://img.shields.io/badge/quay.io-repository-2496ED.svg?logo=docker)](https://quay.io/repository/hmpps/hmpps-prisoner-to-nomis-update)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html)

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
- Set all environment variables to match those in [values-dev.yaml](/helm_deploy/values-dev.yaml) e.g. `API_BASE_URL_HMPPS_AUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth`
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
curl https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/nomis-sync-api-docs.json
curl https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/activities-api-docs.json
curl https://manage-adjudications-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/adjudications-api-docs.json
curl https://non-associations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/non-associations-api-docs.json
curl https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/nomis-mapping-service-api-docs.json
curl https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/sentencing-adjustments-api-docs.json
curl https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/court-sentencing-api-docs.json
```

Then run compile gradle task that will regenerate the models in the `build/generated/src` directory:

`./gradlew clean compileKotlin`

Now build the project and deal with any compile errors caused by changes to the generated models.

Finally run the tests and fix any issues caused by changes to the models.

### Adding new Open API specs

Add the instructions for the curl command above but obviously with a different file name

In the build.gradle add a new task similar to the `buildActivityApiModel` task
In the build.gradle add dependencies in the appropriate tasks e.g. in `withType<KotlinCompile>` for the new task


## Runbook

### Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.

### Preprod Refresh Problems

#### Activities

As NOMIS, DPS Activities and our synchronisation mappings are all refreshed from preprod at slightly different times, after a preprod refresh the activities synchronisation can start failing with strange errors. The most common is conflicts for IDs that exist in the mapping table but not in NOMIS / DPS.

After the preprod refresh in order to make preprod usable for new test data (i.e. created in DPS) we need to do the following:

On the NOMIS preprod database find:
* `select max(crs_acty_id) from oms_owner.course_activities;`
* `select max(crs_sch_id) from oms_owner.course_schedules;`

On the DPS Activities preprod database find:
* `select max(activity_schedule_id) from activity_schedules;`
* `select max(scheduled_instance_id) from scheduled_instances;`

Then we delete any conflicting mappings from the mappings preprod database:
* `delete from activity_mapping where nomis_course_activity_id > <max(crs_acty_id)>;`
* `delete from activity_mapping where activity_schedule_id > <max(activity_schedule_id>;`
* `delete from activity_schedule_mapping where nomis_course_schedule_id > <max(crs_sch_id)>;`
* `delete from activity_schedule_mapping where scheduled_instance_id > <max(scheduled_instance_id)>;`

There may still be some discrepancies between NOMIS and DPS, e.g. a new schedule or allocation could have been created in DPS between the NOMIS and DPS refreshes. These can probably be fixed by calling the manual sync endpoints, but it's messy - see advise on the [reconciliation report](activities-reconciliation-report-alerts-allocations-and-attendances) if you really want to try this. It's probably better to advise people to create new test data in DPS, which will be possible now that the bad mappings have been deleted.

TODO: we normally need to purge the DLQ after a preprod refresh because of bad data - next time do the above first and see if that's enough to avoid the need to purge the DLQ.

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

##### Scheduled Instances (OFFENDER_COURSE_SCHEDULES)

A duplicate scheduled instance won't receive a `To NOMIS synchronisation duplicate activity detected` alert but will end up with a DLQ message. See [scheduled instance update errors](#scheduled-instance-update-errors) for more details.

##### Activity schedules (various tables including pay rates, schedule rules and schedules)

A duplicate activity schedule update won't receive a `To NOMIS synchronisation duplicate activity detected` alert but will end up with a DLQ message. See [duplicate activity schedule update errors](#duplicate-activity-schedule-update-errors) for more details.

#### Incentives

Duplicate incentives have no business impact in NOMIS but can cause confusion to users. That confusion is only the case so long as the NOMIS IEP page is still in use. This screen is currently being phased out. The only way to delete an IEP is to ask `#dps-appsupport` and supply them with the prisoner number and sequence. *For now it advised to take no action unless there is a complaint from the prison* since the impact on the business is negligible.

#### Visits

A duplicate visit is serious since for sentenced prisoners they will have one less visit for that week. Therefore the visit should be cancelled. This could be done by #dps-appsupport or by us using the cancel endpoint. The cancel endpoint is the quickest solution.

* Click on the View button of the alert and check the `customDimensions` of the App Insights query results to get the `offenderNo` and `duplicateNomisId`
* Check the offender's visits in production Nomis to prove there is a duplicate, e.g. `https://digital.prison.service.justice.gov.uk/prisoner/<offenderNo>/visits-details`
* Grab an auth token with role `NOMIS_VISITS` and call the following API to cancel the duplicate visit:
```
curl --location --request PUT 'https://nomis-prisoner-api.prison.service.justice.gov.uk/prisoners/<offenderNo>/visits/<duplicateNomisId>/cancel' \
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
  'https://nomis-prisoner-api.prison.service.justice.gov.uk/sentence-adjustments/99999' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer <token with role NOMIS_SENTENCING>'
```

For key date adjustment:

```
curl -X 'DELETE' \
  'https://nomis-prisoner-api.prison.service.justice.gov.uk/key-date-adjustments/99999' \
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
curl --location 'https://nomis-prisoner-api.prison.service.justice.gov.uk/incentives/booking-id/{bookingId}}/current' \
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
curl --location 'https://incentives-api.hmpps.service.justice.gov.uk/incentive-reviews/booking/{bookingId}}?with-details=true' \
--header 'Authorization: Bearer <token with role ROLE_INCENTIVE_REVIEWS>'
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

  curl --location --request PUT 'https://prisoner-search.prison.service.justice.gov.uk/events/prisoner/received/A9999AR' \
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

Where the mismatch is due to out of order domain events this can be fixed by creating a new IEP record in NOMIS based on the correct incentive record
This can happen if 2 incentives are created in quick succession in DPS but the first IEP takes a few minutes to process due to network issues.

```bash

  curl --location --request PUT 'https://https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/incentives/prisoner/booking-id/{bookingId}/repair' \
--header 'Authorization: Bearer <token with ROLE_NOMIS_INCENTIVES>' \
--header 'Content-Type: application/json' 
  ```

### Activities Reconciliation Report Alerts (allocations and attendances)

Daily jobs check each of the prisons which are feature switched on for the Activities service (in the SERVICE_AGENCY_SWITCHES table in NOMIS) and compares the number of allocations and attendances to pay in NOMIS with the number in DPS. When there is a mismatch an alert is sent to the `#sycon-alerts` channel.

Clicking on the `view` button included in the alert will run a query showing all the failures for that day.

#### Allocations reconciliation failures

For allocations a failure indicates that some bookings have a different number of active allocations in NOMIS and DPS. This is a problem that may cause problems with unlock lists in the prison and will likely cause future synchronisation events from DPS to fail.

To try and work out what's different start with the following queries:

NOMIS:
```sql
select * from OMS_OWNER.OFFENDER_PROGRAM_PROFILES 
where OFFENDER_BOOK_ID=<insert offender book id here> 
and OFFENDER_PROGRAM_STATUS='ALLOC';
```

DPS:
```sql
select * from allocations 
where booking_id=<insert offender book id here>
and active=true;
```

To get an overview of what's been happening with the synchronisation events for this offender run the following query in App Insights:
```ksql
customEvents
| where name startswith 'activity'
| where customDimensions.bookingId == '<insert booking id here>'
```

To get a broader overview of what's been happening with the prisoner run the following query in App Insights:
```ksql
customEvents
| where (customDimensions.bookingId == '<insert booking id>' or customDimensions.offenderNo == '<insert offenderNo>' or customDimensions.nomsNumber == '<insert offenderNo>' or customDimensions.prisonerNumber == '<insert offenderNo>')
```

Often the fix involves re-synchronising the DPS allocations which can be done with an [endpoint in the synchronisation service](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html#/activities-resource/synchroniseUpsertAllocation).

##### Known issues

If the prisoner has been released or transferred to a different prison then there's not much to be done. This information can be found in the `customDimensions` of the report failures found in App Insights. 

#### Attendances reconciliation failures

For attendances a failure indicates that some bookings have a different number of attendances to pay in NOMIS and DPS. By "attendances to pay" we mean the pay flag is true - not related to the results of the NOMIS payroll for which the rules are many and complex. However, this is a problem that might cause NOMIS to miss payments to the prisoner.

To try and work out what's different start with the following queries:

NOMIS:
```sql
select * from OMS_OWNER.OFFENDER_COURSE_ATTENDANCES 
where OFFENDER_BOOK_ID=<insert offender book id here> 
and event_date=to_date('<insert report date here>', 'YYYY-MM-DD') 
and PAY_FLAG='Y';
```

DPS:
```sql
select att.* from attendance att
join scheduled_instance si on att.scheduled_instance_id = si.scheduled_instance_id
join allocation al on al.activity_schedule_id = si.activity_schedule_id and att.prisoner_number = al.prisoner_number
where si.session_date = '<insert report date here>'
and att.prisoner_number = '<insert offenderNo here>';
```

To get an overview of what's been happening with the synchronisation events for this offender run the following query in App Insights:
```ksql
customEvents
| where name startswith "activity"
| where customDimensions.bookingId == '<insert booking id here>'
```

To get a broader overview of what's been happening with the prisoner run the following query in App Insights:
```ksql
customEvents
| where (customDimensions.bookingId == '<insert booking id>' or customDimensions.offenderNo == '<insert offenderNo>' or customDimensions.nomsNumber == '<insert offenderNo>' or customDimensions.prisonerNumber == '<insert offenderNo>')
```

Often the fix involves re-synchronising the DPS attendances which can be done with an [endpoint in the synchronisation service](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html#/activities-resource/synchroniseUpsertAttendance).

##### Re-running old attendance reconciliation errors

If the attendance reconciliation fails in error we publish a `customEvent` with name `activity-attendance-reconciliation-report-error` and do not know the results of the reconciliation.

In order to re-run the attendance reconciliation for a particular day we need to manually perform the same task as the cronjob.

Find a running pod and port-forward to it:

e.g.
```bash
kubectl -n hmpps-prisoner-to-nomis-update-prod port-forward hmpps-prisoner-to-nomis-update-5ddc8c6d56-6ph6s 8080:8080
```

Then in another terminal run the curl command entering the date to be reconciled:

e.g.
```bash
curl -XPOST 'http://localhost:8080/attendances/reports/reconciliation?date=2023-11-03'
```

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
* Look for failed requests (results with success = `False`):
```ksql
requests
| where operationId == '<insert operationId here>'
```
* Look for exceptions:
```ksql
exceptions
| where operationId == '<insert operationId here>'
```

After investigating the error you should now know if it's recoverable or not.

* If the error is recoverable (e.g. calls to another service received a 504) then you can leave the message on the DLQ because a retry should work once the other service recovers.
* If the error is unrecoverable (e.g. calls to another service received a 400) then you probably have enough information to diagnose the issue and constant failing retries/alerts will be annoying. Consider [purging the DLQ](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html#/hmpps-reactive-queue-resource/purgeQueue).

#### Activities API breaking changes

After investigating an error you find the last request was a successful call to the Activities API and that an exception was thrown with a message something like `JSON decoding error: Cannot deserialize value of type uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model` then it would appear that a breaking change has been made to the Activities API.

To fix this see [Updating the Open API specs](#updating-the-open-api-specs).

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
* if you find a duplicate allocation call the [delete allocation endpoint](https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs#/activities-resource/deleteAllocation) using column `OFF_PRGREF_ID`
* if you find a duplicate attendance call the [delete attendance endpoint](https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs#/activities-resource/deleteAttendance) using column `EVENT_ID`

As this error should recover once the duplicate is deleted you don't need to purge the DLQ.

#### Scheduled instance update errors

If we receive an alert because of a DLQ message for event type `activities.scheduled-instance.amended` then it is likely one of the following scenarios:
* we received duplicate messages for the event, one pod succeeded to update but the other pod's Hibernate session fails to update a dirty entity
* the scheduled instance was already deleted on a previous activity schedule update but the messages were processed out of order

In either case the update has correctly failed and Nomis reflects reality. The DLQ message will be retried and this should be a no-op - the entity is already upto date.

NOTE: You should still investigate the error in case of other scenarios not foreseen above. If any are found then please document them here.

#### Activity schedule update errors

An activity update (event type `activities.activity-schedule.amended`) could involve one or more of the following:
* the activity details have changed
* pay rates have been updated and/or deleted and/or created
* schedule rules have been deleted and/or created
* scheduled instances have been delete and/or created

Normally the error will represent a validation failure which should appear in the traces. In theory this should not happen but if it does then either the validation in the Activities service or the validation in this service is wrong. Work out which is wrong and fix - when fixed the message will eventually be retried and succeed.

##### Duplicate activity schedule update errors

If your investigation points to a problem caused by duplicate activity updates being processed at the same time then the likely error is that one of the pods failed because Hibernate failed to update a dirty entity. The failed message will be retried automatically and the next update will be a no-op - the entity is already upto date.

NOTE: You should still investigate the error in case of other scenarios not foreseen above. If any are found then please document them here.

## Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
