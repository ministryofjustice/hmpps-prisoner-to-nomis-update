[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-to-nomis-update)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-to-nomis-update "Link to report")
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-to-nomis-update)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

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

- `docker compose up localstack` or `docker compose up localstack` (there is also docker-compose-localstack.yaml with just localstack defined )

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

## Generating API client models

For some of our external API calls we use `openapi-generator` to generate the models used in the API clients. The Open
API specifications used can be found in directory `openapi-specs`.

In the build.gradle.kts there is a `ModelConfiguration` for each of the models. This contains the URL to be used to
obtain the JSON Open API specification and also the package name to be used for the generated models. For each model
configuration two tasks are created:

1. `write<ModelName>Json` - this task will download the Open API specification and save it to `openapi-specs`.
2. `build<ModelName>ApiModel` - this task will generate the models from the specification

So, for example, running

```shell
./gradlew writeNonAssociationsJson compileKotlin compileTestKotlin
```

Will download the non associations Open API specification from dev, generate the model and then compile the code.
The json specification can then be committed to the repository.

Running:
```shell
./gradlew tasks
```
Will show all the build API and write JSON tasks available.

### Generating the infrastructure client

The generated APIs rely on a generated org.openapitools.client.infrastructure.ApiClient. Since we have lots of generated
APIs we only want one version of the client, so we have copied the client to `src/main/kotlin`. Also, by default, the
client is generated with `protected` functions, but we need these to be public in order to call the methods.  Running:
```shell
generate-infrastructure-client.bash
```
will generate the client and copy it into `src/main/kotlin`, removing `protected` from the functions.

This will be necessary if the openapi generator is updated and the API clients that are generated no longer compile.

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

There may still be some discrepancies between NOMIS and DPS, e.g. a new schedule or allocation could have been created in DPS between the NOMIS and DPS refreshes. These can probably be fixed by calling the manual sync endpoints, but it's messy - see advice on the [reconciliation report](activities-reconciliation-report-alerts-allocations-and-attendances) if you really want to try this. It's probably better to advise people to create new test data in DPS, which will be possible now that the bad mappings have been deleted.

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

## Incentives

Duplicate incentives have no business impact in NOMIS but can cause confusion to users. That confusion is only the case so long as the NOMIS IEP page is still in use. This screen is currently being phased out. The only way to delete an IEP is to ask `#dps-appsupport` and supply them with the prisoner number and sequence. *For now it advised to take no action unless there is a complaint from the prison* since the impact on the business is negligible.

## Adjudications

### Punishment Repair

A rare scenario happens where the NOMIS punishment is deleted as a result of a DPS synchronisation. This is for old historic Adjudications that have multiple charges. In DPS these are represented with multiple adjudications whereas NOMIS represents these as a single Adjudication with multiple charges. In NOMIS the hearing can therefore be shared between multiple DPS adjudications. If that hearing is deleted in DPS it was also delete the NOMIS hearing which might also cascade to the outcomes and punishments or multiple charges.

In this situation the following has been deleted in NOMIS:
* The hearing
* The outcome result
* The punishments

The mapping for the punishments and the hearings will remain that complicates the repair.

To repair, the mappings need deleting and the hearing, outcome and punishments all need resynchronisation.

1. Find the hearing and punishments in DPS that need synchronising
2. Delete the hearing mapping DELETE https://nomis-sync-prisoner-mapping.hmpps.service.justice.gov.uk/mapping/hearings/dps/{dps-hearing-id}
3. Delete each of the punishments mappings DELETE https://nomis-sync-prisoner-mapping.hmpps.service.justice.gov.uk/mapping/punishments/{dps-punishment-id}
4. Repair hearing POST https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/prisons/{prison-id}/prisoners/{offender-no}/adjudication/dps-charge-number/{dps-charge-id}/hearing/dps-hearing-id/{dps-hearingid}
5. Repair hearing outcome POST https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/prisons/{prison-id}/prisoners/{offender-no}/adjudication/dps-charge-number/{dps-charge-id}/hearing/dps-hearing-id/{dps-hearingid}/outcome
6. Repair hearing punishments POST https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk/prisons/{prison-id}/prisoners/{offender-no}/adjudication/dps-charge-number/{dps-charge-id}/punishments/repair

TODO - if this becomes common add new endpoint to do all of the above programmatically

## Visits

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
## Sentencing adjustments

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

Often the fix involves re-synchronising the DPS allocations which can be done with an [endpoint in the synchronisation service](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html#/activities-resource/synchroniseUpsertAllocation).

##### Known issues

If the prisoner has been released or transferred to a different prison then there's not much to be done. This information can be found in the `customDimensions` of the report failures found in App Insights. 

#### Attendances reconciliation failures

For attendances a failure indicates that some bookings have a different number of attendances to pay in NOMIS and DPS. By "attendances to pay" we mean the pay flag is true - not related to the results of the NOMIS payroll for which the rules are many and complex. However, this is a problem that might cause NOMIS to miss payments to the prisoner.

To try and work out what's different start with the following DB queries (adjusting date for older reconciliation failures):

NOMIS:
```sql
select ca.DESCRIPTION, ca.crs_acty_id,oca.* from OMS_OWNER.OFFENDER_COURSE_ATTENDANCES oca
  join oms_owner.COURSE_ACTIVITIES ca on oca.CRS_ACTY_ID=ca.CRS_ACTY_ID
  join oms_owner.OFFENDER_BOOKINGS ob on oca.OFFENDER_BOOK_ID=ob.OFFENDER_BOOK_ID
  join oms_owner.offenders o on ob.ROOT_OFFENDER_ID=o.OFFENDER_ID
where OFFENDER_ID_DISPLAY='<prisoner number>' and EVENT_DATE = to_date(current_date-1);
```

DPS:
```sql
select a.description, si.time_slot, att.*  from attendance att
  join scheduled_instance si on att.scheduled_instance_id=si.scheduled_instance_id
  join activity_schedule asch on si.activity_schedule_id=asch.activity_schedule_id
  join activity a on asch.activity_id=a.activity_id
where att.prisoner_number='<prisoner number>' and si.session_date = current_date-1;
```

You should be able to compare the attendances in both systems to work out what's different. Then it's a case of trying to work out why they are different.

To get an overview of what's been happening with the synchronisation events for this offender run the following Log Analytics query in App Insights:
```ksql
AppEvents
| where Name startswith "activity"
| where Properties.bookingId == '<insert booking id here>'
```

To get a broader overview of what's been happening with the prisoner run the following Log Analytics query in App Insights:
```ksql
AppEvents
| where (Properties has '<insert booking id>' or Properties has '<insert offenderNo>')
```

##### Resynchronising attendances from DPS to NOMIS

Often the fix involves grabbing the `attendance_id` from DPS and re-synchronising the DPS attendance which can be done with an [endpoint in the synchronisation service](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html#/Activities%20Update%20Resource/synchroniseUpsertAttendance).

##### Known issues with attendance reconciliation

###### Synchronisation to NOMIS failed

You can spot this issue because either the NOMIS attendances are missing entirely, or the audit fields indicate the NOMIS attendances weren't updated when the attendance was marked in DPS.

The reasons for the synchronisation failure are varied. You can usually work out what happened with the above App Insights queries and some further digging. The most interesting scenario is where DPS failed to generate the `activities.prisoner.attendance-*` domain event, in which case we need to raise with the DPS team. Another interesting failure is where the call to nomis-prisoner-api failed with a 400/500 - though this very rarely happens. If the failure reason is that NOMIS payroll has already run so we rejected the update, it looks like DPS updated the attendance record too late and this might be worth raising with the DPS team. 

Most often the fix is to re-synchronise to NOMIS.

###### Synchronisation to NOMIS failed as already paid in NOMIS (OTDDSBAL)

There is a feature in NOMIS where a prisoner can be paid on the same day as an attendance when they're preparing to be released (instead of waiting for the overnight payroll job). Later in DPS the attendance is either paid or cancelled - but we reject the sync because the attendance is alrady paid in NOMIS. These can be identified because the NOMIS `AUDIT_MODULE_NAME` is `OTDDSBAL`, the NOMIS module that allows early pay.

There's nothing to be done here so this issue can be ignored.

This situation will go away once prisoner finance has been migrated to DPS. (We did look at ignoring these in the reconciliation but it's a bit complicated, and they're easy to spot).

###### Prisoner switched to old booking

We've seen a couple of cases recently where a prisoner was admitted, allocated to an activity and then attended. Later that day the prisoner is switched to an old booking because they've actually returned on a recall.

This means that the prisoner is paid on the wrong booking in NOMIS (i.e. not the active booking) and this confuses the reconciliation.

These can be ignored because the prisoner does actually get paid in NOMIS so there's not much to be done.

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

* First call the [get-dlq-messages endpoint](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html#/hmpps-reactive-queue-resource/getDlqMessages) to get the failed message. Note that the queue name can be found on the `/health` endpoint.
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
* If the error is unrecoverable (e.g. calls to another service received a 400) then you probably have enough information to diagnose the issue and constant failing retries/alerts will be annoying. Consider [purging the DLQ](https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html#/hmpps-reactive-queue-resource/purgeQueue).

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
* scheduled instances have been deleted and/or created

Normally the error will represent a validation failure which should appear in the traces. In theory this should not happen but if it does then either the validation in the Activities service or the validation in this service is wrong. Work out which is wrong and fix - when fixed the message will eventually be retried and succeed.

##### Duplicate activity schedule update errors

If your investigation points to a problem caused by duplicate activity updates being processed at the same time then the likely error is that one of the pods failed because Hibernate failed to update a dirty entity. The failed message will be retried automatically and the next update will be a no-op - the entity is already upto date.

NOTE: You should still investigate the error in case of other scenarios not foreseen above. If any are found then please document them here.

## Court sentencing

### Case cloning on to the latest booking

Case cloning is where one or more court cases are copied to the latest booking due to a change made in DPS. Given DPS Remand & Sentencing is booking agnostic, this ensures major updates to court cases are visible in NOMIS on the latest booking.

The current user triggered events supported that trigger a clone are:
* Adding a court appearance to a court case associated with a previous booking (defined as bookingSequence != 1)
* Adding a recall in DPS with a sentence on a previous booking

More details about the justification for this complication can be found in the [DPS Case Cloning](https://dsdmoj.atlassian.net/wiki/spaces/SOM/pages/5831721530/Agreed+solution+to+the+RAS+booking+agnostic+problem) document.

#### Case clone overview

* Case cloning starts for one or more root cases—for a court appearance add, this will be a single case where as for recalls, this can be many depending on how many sentences are involved in the recall.
* For each of these root cases all related cases are also marked as needing cloning. These related cases are defined as cases linked via the NOMIS combined case linking (both source and target) and cases containing sentences that are consecutive to cases containing the related sentences (as parent or child) on the root cases.
* For each cases cloned the case, case identifiers, offender charges, court appearances (court events), court orders, sentences, sentence terms and any sentence related adjustments (e.g. Remand) are copied to the latest booking
* This differs to how NOMIS handles a prisoner merge in the following is not cloned; case status updates, sentence status updates, booking level sentence adjustments, fines, licences (and conditions)  the history of imprisonment statuses (though the latest imprisonment status is recalculated) and case associated tables.
* Court cases, sentences and adjustments are all made active when they are copied
* The hmpps-prisoner-to-update service issues a call to the mapping service so all the cases cloned mappings are updated to point at the new cloned cases. This ensures DPS cases remain visible but now linked to the latest version of those cases in NOMIS
* Since the cases and sentences have a new bookingId these new cases are synchronised back to DPS via a SQS message to the hmpps-prisoner-from-nomis-migration service
* Also the old source cases that have now orphaned with no associated mappings are synchronised back to DPS via a SQS message to the hmpps-prisoner-from-nomis-migration service which uses a special endpoint that knows to mark these cases as DUPLICATE
* DUPLICATE cases in DPS are not visible in the UI but synchronisation between them and their NOMIS counterparts is still possible. Though there is no reason in NOMIS why they should be updated given they are on a previous booking and no longer are the latest versions.
* Sentence adjustments that are cloned are also synchronised back to DPS Adjustments via a SQS message to the hmpps-prisoner-from-nomis-migration service


#### Common question and answers

* Q: When is a case identified to be cloned?
* A: This is done by hmpps-nomis-prisoner-api when the POST/PUT request is made to either the create appearance or create recall endpoints. Only NOMIS API knows if the operation is about to be made on a previous booking, so it needs to initiate the clone.

* Q: Is create appearance and create recall operations applied to both the original and cloned cases?
* A: No. Just the cloned cases are amended. This makes the cloning complicated since hmpps-prisoner-to-nomis-update will request an update to a specific case Id or sentence Id but the operation is carried out on a different entity. hmpps-nomis-prisoner-api has to therefore not only clone case but mutate the request so the Ids of entities are switched to the cloned equivalents.

* Q: How does the hmpps-prisoner-to-update service know a clone has taken place?
* A: The hmpps-nomis-prisoner-api service will return an additional field in the response for both `CreateCourtAppearanceResponse` and `ConvertToRecallResponse` called `clonedCourtCases` which contains the Ids of the cloned cases.

* Q: What messages are sent to the hmpps-prisoner-from-nomis-migration service?
* A: To update the cloned cases bookingId and create the orphaned cases in DPS it is the message called `courtsentencing.resync.case.booking`. To create the sentence adjustments `courtsentencing.resync.sentence-adjustments`

* Q: Why are these messages sent after the mappings are updated?
* A: There is a potential race condition if the mappings are updated at the same time as the messages are sent. It could potentially try to create mappings for the orphaned cases before their mappings have been switched to cloned cases. This would fail with a unique key exception

* Q: For sentence adjustments, why do we not use a single SQS message for all adjustments which might perform better?
* A: The created adjustments also require their own mapping that itself might fail, so creating adjustments are not idempotent. So a single message for each adjustment is guaranteed to succeed eventually and only ever created one adjustment.

#### Technical failure points

* The call to update the mapping fails for some reason. If the mapping service rejects the update no other part of the operation for cloning will complete. This means the new cases have been created but DPS has no reference to them. So any further updates to DPS (e.g another court appearance) would be synchronised to the previous booking. Therefore any manual fixes to get the mappings in the correct state would need to be done in a timely fashion. The only way to view the new mappings is via the SQS message itself and any updated to the mappings database would have to be done manually to resolved the issue. Any manual updates or deletes made must also allow the message `RETRY_CREATE_MAPPING` to eventually succeed so the rest of the synchronisation of cloning can completed
* A failure to send either the `courtsentencing.resync.case.booking` or `courtsentencing.resync.sentence-adjustments`. Though this is unlikely since the send of these messages is wrapped so they are retried several times they could still fail. When they fail a `send-message-$eventType-failed` AppInsights custom event is tracked. Where `$eventType` is replaced by the message name e.g. `send-message-courtsentencing.resync.case.booking-failed`  which will trigger an ApplicationInsights Slack alert `to-nomis-sync-send-message-failure-prod`. The custom event will contain the actual message being sent so for now a manual SQS send with the exact same message will need to be crafted until such point a "repair" endpoint is avaialble.

#### Business failure points

* The prison could switch back to the previous booking. In this scenario the view of the case in NOMIS no longer matches that in DPS. NOMIS is a stale version of this view and any updates made in NOMIS will silently update the DUPLICATE hidden records. Worse is if a new court appearance is added to DPS this will trigger a new clone onto a booking where the case may already exist. DPS has accepted these as risks worth taking.
#### TODO

* Repair endpoints are required if either of the SQS message fail to send. This is preferable to trying to craft SQS messages manually.
* Consider a better way to repair if the mapping service fails to update the mappings and DPS is still pointing at the old cases. It would require taking a clone response and somehow fixing the mappings and triggering all the other SQS messages.

### Prisoner Alerts

#### Duplicate handling

Occasionally DPS allows the same alert to be created twice (e.g. due to a double submit). This will result in a message on the DLQ. The hmpps-nomis-prisoner-api service will return a 409 Conflict error with the message `Conflict http error: Alert code <code>> is already active on booking <bookingId>`and a custom event of `aler-create-failed` will be tracked.

To show the exception and check it is a duplicate
```ksql
let ops = AppEvents
| where Name == "alert-create-failed"
| where AppRoleName == "hmpps-prisoner-to-nomis-update"
| project OperationId;
AppExceptions
| where OperationId in (ops)
| where AppRoleName == "hmpps-prisoner-to-nomis-update"
```

To show the exception and confirm the bookingId and alert code
```ksql
let ops = AppEvents
| where Name == "alert-create-failed"
| where AppRoleName == "hmpps-prisoner-to-nomis-update"
| project OperationId;
AppTraces
| where OperationId in (ops)
| where AppRoleName == "hmpps-nomis-prisoner-api"
```

When this happens you can check that DPS did fire two `person.alert.created` events with different DPS Ids but same prisoner and same code.

Action to take is:
* Clear the message from the DLQ - it will never succeed
* Inform DPS on `#collab-connect-dps-syscon` to delete the duplicate alert; i.e. the one that was failing to sync with no mapping record.

### Contacts a.k.a Personal Relationships

DPS has different terminology for the 2 key entities to NOMIS:

---
* NOMIS: Person
* DPS: Contact

---
* NOMIS: Contact
* DPS: Prisoner Contact
---
#### Duplicate handling

Since a prisoner contact can not exist twice in either NOMIS or DPS; i.e. a prisoner and a civilian can not be related twice with the same type a network failure can result in an attempted duplicate.

These would result in a `contact-create-failed` custom event 
```ksql
let ops = AppEvents
| where Name == "contact-create-failed"
| where AppRoleName == "hmpps-prisoner-to-nomis-update"
| project OperationId;
AppExceptions
| where OperationId in (ops)
| where AppRoleName == "hmpps-prisoner-to-nomis-update"
```

The scenario this happens is where the POST to `hmpps-nomis-prisoner-api` to create the contact succeeded but `hmpps-prisoner-to-nomis-update` received no response the retry will then fail with a 409 Conflict error.

This can be resolved by creating the missing mapping.
* dpsId is in the properties of the `contact-create-failed` custom event
* nomisId can be found by viewing contacts for the person in NOMIS via:

```
GET https://nomis-prisoner-api{{prefix}}.prison.service.justice.gov.uk/persons/{{contactOrPersonId}}
Content-Type: application/json
Authorization: Bearer {{$auth.token("hmpps-auth")}}
```
then create the mapping

```
POST https://nomis-sync-prisoner-mapping.hmpps.service.justice.gov.uk/mapping/contact-person/contact
Content-Type: application/json
Authorization: Bearer {{$auth.token("hmpps-auth")}}

{
  "dpsId": "<dpsId>",
  "nomisId": <existing nomisId>,
  "mappingType": "DPS_CREATED"
}
```

### Case notes

#### Reconciliation mismatches

There are 2 types of mismatch event - size differences and specific diffs in a case note which contains details of what fields are different.

Watch out for size difference reconciliation errors due to :
- A creation happening at the same time as the recon check;
- Dupes caused by NULL audit_module_name column value;
- Dupes caused by a timeout

The reconciliation process can detect and automatically delete duplicates in some circumstances, though it will still report these.

### Appointments

This is a one way sync to Nomis.

#### Reconciliation mismatches

The appointments reconciliation currently (Nov 2025) just looks 28 days ahead rather than doing all.

Mismatches are usually due to:
- An appointment is in DPS only because the Nomis counterpart was deleted, probably very soon after the migration of that prison and
before the P-Nomis screen was disabled. In this case there will be a mapping table entry.
- An appointment is in Nomis only because it was created in Nomis, again very soon after the migration and
  before the P-Nomis screen was disabled. In this case there will NOT be a mapping table entry.

In both these cases it should be established whether an extra similar appointment has been created in the other system, if so the only action needed
is to temporarily add the offending appointment ids to the exclude list.

- An appointment is in Nomis only, and there is no mapping because a Nomis dupe has occurred when there was a timeout, 502 etc. on the
  client side when POSTing the nomis creation (yet it succeeded on the nomis-api side).

In this case the dupe should be deleted. You can directly call the nomis-api endpoint for this.

Troubleshooting approach should be to look at OFFENDER_IND_SCHEDULES in the Nomis database to find when the appointment was created, then check appinsights at this time.

### Non-associations

This is a one way sync to Nomis.

One scenario that still may not be successfully handled automatically is a merge where both merge noms ids have a NA with a 3rd party.
In this case there is merge code which tries to avoid a unique key constraint error which would otherwise occur
in the mapping service because 2 NAs have both parties the same and also the same sequence (usually 1). If this doesn't work, manual intervention is required to identify which NA is open
and set its sequence to 2. The open one can be identified as being set as such in DPS and has no expiry date in Nomis. If neither has an expiry date in Nomis, a sync from DPS can be forced
by e.g. editing the comment in the DPS UI, or the NA can be closed by calling the /close nomis-api endpoint.

## Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
