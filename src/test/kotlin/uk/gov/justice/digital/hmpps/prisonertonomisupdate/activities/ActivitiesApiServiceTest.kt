@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.ACTIVE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(ActivitiesApiService::class, ActivitiesConfiguration::class)
internal class ActivitiesApiServiceTest {

  @Autowired
  private lateinit var activitiesApiService: ActivitiesApiService

  @Nested
  inner class GetSchedule {
    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubGetSchedule(
        1234,
        """
      {
        "id": 1234,
        "instances": [
          {
            "id": 123456,
            "date": "2022-12-30",
            "startTime": "9:00",
            "endTime": "10:00",
            "cancelled": false,
            "cancelledTime": "2022-12-30T14:03:06.365Z",
            "cancelledBy": "Adam Smith",
            "timeSlot": "AM",
            "attendances": [
              {
                "id": 123456,
                "scheduleInstanceId": 123456,
                "prisonerNumber": "A1234AA",
                "attendanceReason": {
                  "id": 123456,
                  "code": "ABS",
                  "description": "Unacceptable absence",
                  "attended": true,
                  "capturePay": true,
                  "captureMoreDetail": true,
                  "captureCaseNote": true,
                  "captureIncentiveLevelWarning": false,
                  "captureOtherText": false,
                  "displayInAbsence": false,
                  "displaySequence": 1,
                  "notes": "Maps to ACCAB in NOMIS"
                },
                "comment": "Prisoner was too unwell to attend the activity.",
                "recordedTime": "2022-12-30T14:03:06.365Z",
                "recordedBy": "10/09/2023",
                "status": "SCHEDULED",
                "payAmount": 100,
                "bonusAmount": 50,
                "pieces": 0,
                "attendanceHistory": [],
                "editable": false,
                "payable": false
              }
            ],
            "advanceAttendances": []
          }
        ],
        "allocations": [
          {
            "id": 123456,
            "prisonerNumber": "A1234AA",
            "bookingId": 10001,
            "activitySummary": "string",
            "activityId": 123456,
            "scheduleId": 2345,
            "scheduleDescription": "string",
            "isUnemployment": false,
            "prisonPayBand": {
               "id": 987,
               "displaySequence": 1,
               "alias": "Low",
               "description": "Pay band 1",
               "nomisPayBand": 1,
               "prisonCode": "PVI"
            },
            "startDate": "2022-12-30",
            "endDate": "2022-12-30",
            "allocatedTime": "2022-12-30T14:03:06",
            "allocatedBy": "Mr Blogs",
            "deallocatedTime": "2022-12-30T14:03:06",
            "deallocatedBy": "Mrs Blogs",
            "deallocatedReason": {
              "code": "RELEASED",
              "description": "Released from prison"
            },
            "status": "ACTIVE",
            "exclusions": []
          }
        ],
        "description": "Monday AM Houseblock 3",
        "suspensions": [
          {
            "suspendedFrom": "2022-12-30",
            "suspendedUntil": "2022-12-30"
          }
        ],
        "usePrisonRegimeTime": true,
        "usePrisonRegimeTime": true,
        "internalLocation": {
          "id": 98877667,
          "code": "EDU-ROOM-1",
          "description": "Education - R1",
          "dpsLocationId": "17f5a650-f82b-444d-aed3-aef1719cfa8f"
        },
        "capacity": 10,
        "activity": {
          "id": 123456,
          "prisonCode": "PVI",
          "attendanceRequired": false,
          "inCell": false,
          "pieceWork": false,
          "outsideWork": false,
          "payPerSession": "F",
          "summary": "Maths level 1",
          "description": "A basic maths course suitable for introduction to the subject",
          "onWing": false,
          "offWing": true,
          "paid": false,
          "category": {
            "id": 1,
            "code": "LEISURE_SOCIAL",
            "name": "Leisure and social",
            "description": "Such as association, library time and social clubs, like music or art"
          },
          "riskLevel": "High",
          "minimumIncentiveLevel": "Basic",
          "minimumIncentiveNomisCode": "BAS",
          "minimumEducationLevel": [
            {
              "id": 123456,
              "educationLevelCode": "Basic",
              "educationLevelDescription": "Basic",
              "studyAreaCode": "ENGLA",
              "studyAreaDescription":  "English language"
            }
          ],
          "createdTime": "2023-06-01T09:17:30.425Z",
          "activityState": "LIVE",
          "capacity": 10,
          "allocated": 5
        },
        "scheduleWeeks": 1,
        "slots": [
          {
            "id": 123456,
            "startTime": "9:00",
            "endTime": "11:30",
            "weekNumber": 2,
            "daysOfWeek": ["Mon","Tue","Wed"],
            "mondayFlag": true,
            "tuesdayFlag": true,
            "wednesdayFlag": true,
            "thursdayFlag": false,
            "fridayFlag": false,
            "saturdayFlag": false,
            "sundayFlag": false,
            "timeSlot": "AM"
          }
        ],
        "startDate" : "2023-01-20",
        "endDate" : "2023-12-24",
        "runsOnBankHoliday": true
      }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getActivitySchedule(1234)

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/schedules/1234?earliestSessionDate=${LocalDate.now()}"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val schedule = activitiesApiService.getActivitySchedule(1234)

      assertThat(schedule.id).isEqualTo(1234)
      assertThat(schedule.startDate).isEqualTo("2023-01-20")
      assertThat(schedule.endDate).isEqualTo("2023-12-24")
      assertThat(schedule.description).isEqualTo("Monday AM Houseblock 3")
      assertThat(schedule.internalLocation?.id).isEqualTo(98877667)
      assertThat(schedule.internalLocation?.code).isEqualTo("EDU-ROOM-1")
      assertThat(schedule.internalLocation?.dpsLocationId).isEqualTo(UUID.fromString("17f5a650-f82b-444d-aed3-aef1719cfa8f"))
      assertThat(schedule.capacity).isEqualTo(10)
      assertThat(schedule.activity.payPerSession).isEqualTo(ActivityLite.PayPerSession.F)
      with(schedule.instances.first()) {
        assertThat(date).isEqualTo("2022-12-30")
        assertThat(startTime).isEqualTo("9:00")
        assertThat(endTime).isEqualTo("10:00")
      }
      with(schedule.slots.first()) {
        assertThat(startTime).isEqualTo("9:00")
        assertThat(endTime).isEqualTo("11:30")
        assertThat(mondayFlag).isTrue()
        assertThat(tuesdayFlag).isTrue()
        assertThat(wednesdayFlag).isTrue()
        assertThat(thursdayFlag).isFalse()
        assertThat(fridayFlag).isFalse()
        assertThat(saturdayFlag).isFalse()
        assertThat(sundayFlag).isFalse()
      }
      assertThat(schedule.runsOnBankHoliday).isTrue()
      assertThat(schedule.activity.category.code).isEqualTo("LEISURE_SOCIAL")
    }

    @Test
    fun `when schedule is not found an exception is thrown`() = runTest {
      activitiesApi.stubGetScheduleWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getActivitySchedule(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubGetScheduleWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getActivitySchedule(1234)
      }
    }
  }

  @Nested
  inner class GetActivity {
    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubGetActivity(
        1234,
        """
    {
      "id": 1234,
      "prisonCode": "PVI",
      "attendanceRequired": false,
      "inCell": false,
      "pieceWork": false,
      "outsideWork": false,
      "payPerSession": "F",
      "summary": "Maths level 1",
      "description": "A basic maths course suitable for introduction to the subject",
      "onWing": false,
      "offWing": true,
      "paid": false,
      "category": {
        "id": 1,
        "code": "LEISURE_SOCIAL",
        "name": "Leisure and social",
        "description": "Such as association, library time and social clubs, like music or art"
      },
      "eligibilityRules": [],
      "schedules": [
        {
          "id": 123456,
          "instances": [
            {
              "id": 123456,
              "date": "2022-12-30",
              "startTime": "9:00",
              "endTime": "10:00",
              "cancelled": false,
              "cancelledTime": "2022-12-30T16:09:11.127Z",
              "cancelledBy": "Adam Smith",
              "timeSlot": "AM",
              "attendances": [
                {
                  "id": 123456,
                  "scheduleInstanceId": 123456,
                  "prisonerNumber": "A1234AA",
                  "attendanceReason": {
                    "id": 123456,
                    "code": "SICK",
                    "description": "Sick",
                    "attended": true,
                    "capturePay": true,
                    "captureMoreDetail": true,
                    "captureCaseNote": true,
                    "captureIncentiveLevelWarning": false,
                    "captureOtherText": false,
                    "displayInAbsence": false,
                    "displaySequence": 1,
                    "notes": "Maps to ACCAB in NOMIS"
                  },
                  "comment": "Prisoner was too unwell to attend the activity.",
                  "recordedTime": "2022-12-30T16:09:11.127Z",
                  "recordedBy": "10/09/2023",
                  "status": "SCHEDULED",
                  "payAmount": 100,
                  "bonusAmount": 50,
                  "pieces": 0,
                  "attendanceHistory": [],
                  "editable": false,
                  "payable": false
                }
              ],
              "advanceAttendances": []
            }
          ],
          "allocations": [
            {
              "id": 123456,
              "prisonerNumber": "A1234AA",
              "bookingId": 10001,
              "activitySummary": "string",
              "activityId": 123456,
              "scheduleId": 2345,
              "scheduleDescription": "string",
              "isUnemployment": false,
              "prisonPayBand": {
                 "id": 987,
                 "displaySequence": 1,
                 "alias": "Low",
                 "description": "Pay band 1",
                 "nomisPayBand": 1,
                 "prisonCode": "PVI"
              },
              "startDate": "2022-12-30",
              "endDate": "2022-12-30",
              "allocatedTime": "2022-12-30T16:09:11.127Z",
              "allocatedBy": "Mr Blogs",
              "deallocatedTime": "2022-12-30T16:09:11.127Z",
              "deallocatedBy": "Mrs Blogs",
              "deallocatedReason": {
                "code": "RELEASED",
                "description": "Released from prison"
              },
              "status": "ACTIVE",
              "exclusions": []
            }
          ],
          "description": "Monday AM Houseblock 3",
          "suspensions": [
            {
              "suspendedFrom": "2022-12-30",
              "suspendedUntil": "2022-12-30"
            }
          ],
          "usePrisonRegimeTime": true,
          "internalLocation": {
            "id": 98877667,
            "code": "EDU-ROOM-1",
            "description": "Education - R1",
            "dpsLocationId": "17f5a650-f82b-444d-aed3-aef1719cfa8f"
          },
          "capacity": 10,
          "activity": {
            "id": 123456,
            "prisonCode": "PVI",
            "attendanceRequired": false,
            "inCell": false,
            "pieceWork": false,
            "outsideWork": false,
            "payPerSession": "F",
            "summary": "Maths level 1",
            "description": "A basic maths course suitable for introduction to the subject",
            "onWing": false,
            "offWing": true,
            "paid": false,
            "category": {
              "id": 1,
              "code": "LEISURE_SOCIAL",
              "name": "Leisure and social",
              "description": "Such as association, library time and social clubs, like music or art"
            },
            "riskLevel": "High",
            "minimumIncentiveLevel": "Basic",
            "minimumIncentiveNomisCode": "BAS",
            "minimumEducationLevel": [
              {
                "id": 123456,
                "educationLevelCode": "Basic",
                "educationLevelDescription": "Basic",
                "studyAreaCode": "ENGLA",
                "studyAreaDescription":  "English language"
              }
            ],
            "createdTime": "2023-06-01T09:17:30.425Z",
            "activityState": "LIVE",
            "capacity": 10,
            "allocated": 5
          },
          "scheduleWeeks": 1,
          "slots": [
            {
              "id": 123456,
              "startTime": "9:00",
              "endTime": "11:30",
              "weekNumber": 4,
              "daysOfWeek": ["Mon","Tue","Wed"],
              "mondayFlag": true,
              "tuesdayFlag": true,
              "wednesdayFlag": true,
              "thursdayFlag": false,
              "fridayFlag": false,
              "saturdayFlag": false,
              "sundayFlag": false,
              "timeSlot": "AM"
            }
          ],
          "startDate" : "2023-01-20",
          "runsOnBankHoliday": true
        }
      ],
      "waitingList": [
        {
          "id": 123456,
          "prisonerNumber": "A1234AA",
          "priority": 1,
          "createdTime": "2022-12-30T16:09:11.127Z",
          "createdBy": "Adam Smith"
        }
      ],
      "pay": [
        {
          "id": 3456,
          "incentiveLevel": "Basic",
          "incentiveNomisCode": "BAS",
          "prisonPayBand": {
             "id": 987,
             "displaySequence": 1,
             "alias": "Low",
             "description": "Pay band 1",
             "nomisPayBand": 1,
             "prisonCode": "PVI"
          },
          "rate": 150,
          "pieceRate": 150,
          "pieceRateItems": 10
        }
      ],
      "payChange": [],
      "startDate": "2022-12-30",
      "endDate": "2022-12-31",
      "riskLevel": "High",
      "minimumIncentiveLevel": "Basic",
      "minimumIncentiveNomisCode": "BAS",
      "createdTime": "2022-12-30T16:09:11.127Z",
      "createdBy": "Adam Smith",
      "minimumEducationLevel": [
        {
          "id": 123456,
          "educationLevelCode": "Basic",
          "educationLevelDescription": "Basic",
          "studyAreaCode": "ENGLA",
          "studyAreaDescription":  "English language"
        }
      ]
    }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getActivity(1234)

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/activities/1234/filtered?earliestSessionDate=${LocalDate.now()}"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val activity = activitiesApiService.getActivity(1234)

      assertThat(activity.id).isEqualTo(1234)
      assertThat(activity.prisonCode).isEqualTo("PVI")
      assertThat(activity.description).isEqualTo("A basic maths course suitable for introduction to the subject")
      assertThat(activity.category.code).isEqualTo("LEISURE_SOCIAL")
      assertThat(activity.startDate).isEqualTo("2022-12-30")
      assertThat(activity.endDate).isEqualTo("2022-12-31")
      assertThat(activity.summary).isEqualTo("Maths level 1")
      val pay = activity.pay[0]
      assertThat(pay.incentiveNomisCode).isEqualTo("BAS")
      assertThat(pay.prisonPayBand.nomisPayBand).isEqualTo(1)
      assertThat(pay.rate).isEqualTo(150)
      assertThat(pay.pieceRate).isEqualTo(150)
      assertThat(pay.pieceRateItems).isEqualTo(10)
    }

    @Test
    fun `when schedule is not found an exception is thrown`() = runTest {
      activitiesApi.stubGetActivityWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getActivity(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubGetActivityWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getActivity(1234)
      }
    }
  }

  @Nested
  inner class GetAllocation {
    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubGetAllocation(
        1234,
        """
          {
            "id": 1234,
            "activityId": 1434,
            "prisonerNumber": "A1234AA",
            "bookingId": 10001,
            "activitySummary": "Some activity summary",
            "scheduleId": 2345,
            "scheduleDescription": "Some schedule description",
            "isUnemployment": true,
            "prisonPayBand": {
              "id": 3456,
              "displaySequence": 1,
              "alias": "Low",
              "description": "Pay band 1",
              "nomisPayBand": 1,
              "prisonCode": "MDI"
            },
            "startDate": "2022-09-10",
            "endDate": "2023-09-10",
            "allocatedTime": "2023-03-17T10:35:19.136Z",
            "allocatedBy": "Mr Blogs",
            "deallocatedTime": "2023-03-17T10:35:19.136Z",
            "deallocatedBy": "Mrs Blogs",
            "deallocatedReason": {
              "code": "RELEASED",
              "description": "Released from prison"
            },
            "suspendedTime": "2023-03-17T10:35:19.136Z",
            "suspendedBy": "Mrs Blogs",
            "suspendedReason": "TRANSFERRED",
            "status": "ACTIVE",
            "exclusions": []
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getAllocation(1234)

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/allocations/id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val allocation = activitiesApiService.getAllocation(1234)

      assertThat(allocation.id).isEqualTo(1234)
      assertThat(allocation.activityId).isEqualTo(1434)
      assertThat(allocation.prisonerNumber).isEqualTo("A1234AA")
      assertThat(allocation.bookingId).isEqualTo(10001)
      assertThat(allocation.activitySummary).isEqualTo("Some activity summary")
      assertThat(allocation.scheduleId).isEqualTo(2345)
      assertThat(allocation.scheduleDescription).isEqualTo("Some schedule description")
      assertThat(allocation.isUnemployment).isEqualTo(true)
      assertThat(allocation.prisonPayBand?.id).isEqualTo(3456)
      assertThat(allocation.prisonPayBand?.nomisPayBand).isEqualTo(1)
      assertThat(allocation.prisonPayBand?.prisonCode).isEqualTo("MDI")
      assertThat(allocation.startDate).isEqualTo(LocalDate.of(2022, 9, 10))
      assertThat(allocation.endDate).isEqualTo(LocalDate.of(2023, 9, 10))
      assertThat(allocation.allocatedTime).isEqualTo(LocalDateTime.of(2023, 3, 17, 10, 35, 19, 136000000))
      assertThat(allocation.allocatedBy).isEqualTo("Mr Blogs")
      assertThat(allocation.deallocatedTime).isEqualTo(LocalDateTime.of(2023, 3, 17, 10, 35, 19, 136000000))
      assertThat(allocation.deallocatedBy).isEqualTo("Mrs Blogs")
      assertThat(allocation.deallocatedReason?.code).isEqualTo("RELEASED")
      assertThat(allocation.suspendedTime).isEqualTo(LocalDateTime.of(2023, 3, 17, 10, 35, 19, 136000000))
      assertThat(allocation.suspendedBy).isEqualTo("Mrs Blogs")
      assertThat(allocation.suspendedReason).isEqualTo("TRANSFERRED")
      assertThat(allocation.status).isEqualTo(ACTIVE)
    }

    @Test
    fun `when allocation is not found an exception is thrown`() = runTest {
      activitiesApi.stubGetAllocationWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getAllocation(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubGetAllocationWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAllocation(1234)
      }
    }
  }

  @Nested
  inner class GetAttendanceSync {
    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubGetAttendanceSync(
        1234,
        """
          {
            "attendanceId": 1234,
            "scheduledInstanceId": 2345,
            "activityScheduleId": 3456,
            "sessionDate": "2023-03-23",
            "sessionStartTime": "10:00",
            "sessionEndTime": "11:00",
            "prisonerNumber": "A1234AA",
            "bookingId": 4567,
            "attendanceReasonCode": "SICK",
            "comment": "Prisoner was too unwell to attend the activity.",
            "status": "WAITING",
            "payAmount": 100,
            "bonusAmount": 50,
            "issuePayment": true
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getAttendanceSync(1234)

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/synchronisation/attendance/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val attendance = activitiesApiService.getAttendanceSync(1234)

      with(attendance) {
        assertThat(attendanceId).isEqualTo(1234)
        assertThat(scheduledInstanceId).isEqualTo(2345)
        assertThat(activityScheduleId).isEqualTo(3456)
        assertThat(sessionDate).isEqualTo("2023-03-23")
        assertThat(sessionStartTime).isEqualTo("10:00")
        assertThat(sessionEndTime).isEqualTo("11:00")
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(bookingId).isEqualTo(4567)
        assertThat(attendanceReasonCode).isEqualTo("SICK")
        assertThat(comment).isEqualTo("Prisoner was too unwell to attend the activity.")
        assertThat(status).isEqualTo("WAITING")
        assertThat(payAmount).isEqualTo(100)
        assertThat(bonusAmount).isEqualTo(50)
        assertThat(issuePayment).isEqualTo(true)
      }
    }

    @Test
    fun `when attendance is not found an exception is thrown`() = runTest {
      activitiesApi.stubGetAttendanceSyncWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getAttendanceSync(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubGetAttendanceSyncWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAttendanceSync(1234)
      }
    }
  }

  @Nested
  inner class GetScheduledInstance {
    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubGetScheduledInstance(
        1234,
        """
{
  "id": 1234,
  "date": "2023-05-02",
  "startTime": "09:00",
  "endTime": "12:00",
  "cancelled": true,
  "cancelledTime": "2023-04-21T13:13:28.192Z",
  "cancelledBy": "Adam Smith",
  "cancelledReason": "Staff unavailable",
  "previousScheduledInstanceId": 58,
  "previousScheduledInstanceDate": "2023-05-01",
  "nextScheduledInstanceId": 60,
  "nextScheduledInstanceDate": "2023-05-02",
  "attendances": [],
  "timeSlot": "AM",
  "activitySchedule": {
    "id": 4,
    "description": "Pen testing again",
    "usePrisonRegimeTime": true,
    "internalLocation": {
      "id": 197684,
      "code": "ASSO",
      "description": "ASSOCIATION",
      "dpsLocationId": "17f5a650-f82b-444d-aed3-aef1719cfa8f"
    },
    "capacity": 10,
    "activity": {
      "id": 4,
      "prisonCode": "MDI",
      "attendanceRequired": true,
      "inCell": false,
      "pieceWork": false,
      "outsideWork": false,
      "payPerSession": "H",
      "summary": "Pen testing again",
      "description": "Pen testing again",
      "onWing": false,
      "offWing": true,
      "paid": false,
      "category": {
        "id": 3,
        "code": "SAA_PRISON_JOBS",
        "name": "Prison jobs",
        "description": "Such as kitchen, cleaning, gardens or other maintenance and services to keep the prison running"
      },
      "riskLevel": "high",
      "minimumIncentiveNomisCode": "STD",
      "minimumIncentiveLevel": "Standard",
      "minimumEducationLevel": [],
      "createdTime": "2023-06-01T09:17:30.425Z",
      "activityState": "LIVE",
      "capacity": 10,
      "allocated": 5
    },
    "scheduleWeeks": 1,
    "slots": [
      {
        "id": 5,
        "startTime": "09:00",
        "endTime": "12:00",
        "weekNumber": 3,
        "daysOfWeek": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "mondayFlag": true,
        "tuesdayFlag": true,
        "wednesdayFlag": true,
        "thursdayFlag": true,
        "fridayFlag": true,
        "saturdayFlag": false,
        "sundayFlag": false,
        "timeSlot": "AM"
      },
      {
        "id": 6,
        "startTime": "13:00",
        "endTime": "16:30",
        "weekNumber": 4,
        "daysOfWeek": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "mondayFlag": true,
        "tuesdayFlag": true,
        "wednesdayFlag": true,
        "thursdayFlag": true,
        "fridayFlag": true,
        "saturdayFlag": false,
        "sundayFlag": false,
        "timeSlot": "PM"
      }
    ],
    "startDate": "2023-04-20",
    "endDate": null
  },
  "advanceAttendances": []
}
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getScheduledInstance(1234)

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/scheduled-instances/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val attendance = activitiesApiService.getScheduledInstance(1234)

      with(attendance) {
        assertThat(id).isEqualTo(1234)
        assertThat(cancelled).isEqualTo(true)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubGetScheduledInstanceWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getScheduledInstance(1234)
      }
    }
  }

  @Nested
  inner class AllocationReconciliation {
    @BeforeEach
    fun `stub allocation reconciliation`() {
      activitiesApi.stubAllocationReconciliation(
        "BXI",
        """
          {
            "prisonCode": "BXI",
            "bookings": [
              { 
                "bookingId": 1234,
                "count": 2
              },
              {
                "bookingId": 1235,
                "count": 1
              }
            ]
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call API with auth token`() = runTest {
      activitiesApiService.getAllocationReconciliation("BXI")

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/synchronisation/reconciliation/allocations/BXI"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse return data`() = runTest {
      val response = activitiesApiService.getAllocationReconciliation("BXI")

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubAllocationReconciliationWithError("BXI", status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAllocationReconciliation("BXI")
      }
    }
  }

  @Nested
  inner class SuspendedAllocationReconciliation {
    @BeforeEach
    fun `stub allocation reconciliation`() {
      activitiesApi.stubSuspendedAllocationReconciliation(
        "BXI",
        """
          {
            "prisonCode": "BXI",
            "bookings": [
              { 
                "bookingId": 1234,
                "count": 2
              },
              {
                "bookingId": 1235,
                "count": 1
              }
            ]
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call API with auth token`() = runTest {
      activitiesApiService.getSuspendedAllocationReconciliation("BXI")

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/synchronisation/reconciliation/suspended-allocations/BXI"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse return data`() = runTest {
      val response = activitiesApiService.getSuspendedAllocationReconciliation("BXI")

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubSuspendedAllocationReconciliationWithError("BXI", status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getSuspendedAllocationReconciliation("BXI")
      }
    }
  }

  @Nested
  inner class AttendanceReconciliation {
    @BeforeEach
    fun `stub attendance reconciliation`() {
      activitiesApi.stubAttendanceReconciliation(
        "BXI",
        LocalDate.now(),
        """
          {
            "prisonCode": "BXI",
            "date": "${LocalDate.now()}",
            "bookings": [
              { 
                "bookingId": 1234,
                "count": 2
              },
              {
                "bookingId": 1235,
                "count": 1
              }
            ]
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call API with auth token`() = runTest {
      activitiesApiService.getAttendanceReconciliation("BXI", LocalDate.now())

      activitiesApi.verify(
        getRequestedFor(urlEqualTo("/synchronisation/reconciliation/attendances/BXI?date=${LocalDate.now()}"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse return data`() = runTest {
      val response = activitiesApiService.getAttendanceReconciliation("BXI", LocalDate.now())

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(date).isEqualTo("${LocalDate.now()}")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      activitiesApi.stubAttendanceReconciliationWithError("BXI", LocalDate.now(), status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAttendanceReconciliation("BXI", LocalDate.now())
      }
    }
  }
}
