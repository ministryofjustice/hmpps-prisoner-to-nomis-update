@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ActivitiesApiService::class, ActivitiesConfiguration::class)
internal class ActivitiesApiServiceTest {

  @Autowired
  private lateinit var activitiesApiService: ActivitiesApiService

  @Nested
  inner class GetSchedule {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubGetSchedule(
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
            "attendances": [
              {
                "id": 123456,
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
                "posted": true,
                "recordedTime": "2022-12-30T14:03:06.365Z",
                "recordedBy": "10/09/2023",
                "status": "SCHEDULED",
                "payAmount": 100,
                "bonusAmount": 50,
                "pieces": 0
              }
            ]
          }
        ],
        "allocations": [
          {
            "id": 123456,
            "prisonerNumber": "A1234AA",
            "activitySummary": "string",
            "scheduleDescription": "string",
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
            "deallocatedReason": "Not attending regularly"
          }
        ],
        "description": "Monday AM Houseblock 3",
        "suspensions": [
          {
            "suspendedFrom": "2022-12-30",
            "suspendedUntil": "2022-12-30"
          }
        ],
        "internalLocation": {
          "id": 98877667,
          "code": "EDU-ROOM-1",
          "description": "Education - R1"
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
              "educationLevelDescription": "Basic"
            }
          ]
        },
        "slots": [
          {
            "id": 123456,
            "startTime": "9:00",
            "endTime": "11:30",
            "daysOfWeek": ["Mon","Tue","Wed"],
            "mondayFlag": true,
            "tuesdayFlag": true,
            "wednesdayFlag": true,
            "thursdayFlag": false,
            "fridayFlag": false,
            "saturdayFlag": false,
            "sundayFlag": false
          }
        ],
        "startDate" : "2023-01-20",
        "runsOnBankHoliday": true
      }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getActivitySchedule(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/schedules/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val schedule = activitiesApiService.getActivitySchedule(1234)

      assertThat(schedule.id).isEqualTo(1234)
      assertThat(schedule.description).isEqualTo("Monday AM Houseblock 3")
      assertThat(schedule.internalLocation?.id).isEqualTo(98877667)
      assertThat(schedule.internalLocation?.code).isEqualTo("EDU-ROOM-1")
      assertThat(schedule.capacity).isEqualTo(10)
      val activity = schedule.activity
      assertThat(activity.id).isEqualTo(123456)
      assertThat(activity.prisonCode).isEqualTo("PVI")
      assertThat(activity.description).isEqualTo("A basic maths course suitable for introduction to the subject")
      assertThat(activity.category.code).isEqualTo("LEISURE_SOCIAL")
      assertThat(activity.minimumIncentiveNomisCode).isEqualTo("BAS")
      with(schedule.instances.first()) {
        assertThat(date).isEqualTo("2022-12-30")
        assertThat(startTime).isEqualTo("9:00")
        assertThat(endTime).isEqualTo("10:00")
      }
      // TODO: pay, start/end date
    }

    @Test
    fun `when schedule is not found an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetScheduleWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getActivitySchedule(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetScheduleWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getActivitySchedule(1234)
      }
    }
  }

  @Nested
  inner class GetActivity {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubGetActivity(
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
              "attendances": [
                {
                  "id": 123456,
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
                  "posted": true,
                  "recordedTime": "2022-12-30T16:09:11.127Z",
                  "recordedBy": "10/09/2023",
                  "status": "SCHEDULED",
                  "payAmount": 100,
                  "bonusAmount": 50,
                  "pieces": 0
                }
              ]
            }
          ],
          "allocations": [
            {
              "id": 123456,
              "prisonerNumber": "A1234AA",
              "activitySummary": "string",
              "scheduleDescription": "string",
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
              "deallocatedReason": "Not attending regularly"
            }
          ],
          "description": "Monday AM Houseblock 3",
          "suspensions": [
            {
              "suspendedFrom": "2022-12-30",
              "suspendedUntil": "2022-12-30"
            }
          ],
          "internalLocation": {
            "id": 98877667,
            "code": "EDU-ROOM-1",
            "description": "Education - R1"
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
                "educationLevelDescription": "Basic"
              }
            ]
          },
          "slots": [
            {
              "id": 123456,
              "startTime": "9:00",
              "endTime": "11:30",
              "daysOfWeek": ["Mon","Tue","Wed"],
              "mondayFlag": true,
              "tuesdayFlag": true,
              "wednesdayFlag": true,
              "thursdayFlag": false,
              "fridayFlag": false,
              "saturdayFlag": false,
              "sundayFlag": false
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
          "educationLevelDescription": "Basic"
        }
      ]
    }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getActivity(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/activities/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val activity = activitiesApiService.getActivity(1234)

      assertThat(activity.id).isEqualTo(1234)
      assertThat(activity.prisonCode).isEqualTo("PVI")
      assertThat(activity.description).isEqualTo("A basic maths course suitable for introduction to the subject")
      assertThat(activity.category.code).isEqualTo("LEISURE_SOCIAL")
      assertThat(activity.minimumIncentiveNomisCode).isEqualTo("BAS")
      assertThat(activity.startDate).isEqualTo("2022-12-30")
      assertThat(activity.endDate).isEqualTo("2022-12-31")
      val pay = activity.pay[0]
      assertThat(pay.id).isEqualTo(3456)
      assertThat(pay.incentiveNomisCode).isEqualTo("BAS")
      assertThat(pay.prisonPayBand.nomisPayBand).isEqualTo(1)
      assertThat(pay.rate).isEqualTo(150)
      assertThat(pay.pieceRate).isEqualTo(150)
      assertThat(pay.pieceRateItems).isEqualTo(10)
    }

    @Test
    fun `when schedule is not found an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetActivityWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getActivity(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetActivityWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getActivity(1234)
      }
    }
  }

  @Nested
  inner class GetAllocation {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(
        1234,
        """
          {
            "id": 1234,
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
            "deallocatedReason": "Not attending regularly"
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getAllocation(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/allocations/id/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val allocation = activitiesApiService.getAllocation(1234)

      assertThat(allocation.id).isEqualTo(1234)
      assertThat(allocation.prisonerNumber).isEqualTo("A1234AA")
      assertThat(allocation.bookingId).isEqualTo(10001)
      assertThat(allocation.activitySummary).isEqualTo("Some activity summary")
      assertThat(allocation.scheduleId).isEqualTo(2345)
      assertThat(allocation.scheduleDescription).isEqualTo("Some schedule description")
      assertThat(allocation.isUnemployment).isEqualTo(true)
      assertThat(allocation.prisonPayBand.id).isEqualTo(3456)
      assertThat(allocation.prisonPayBand.nomisPayBand).isEqualTo(1)
      assertThat(allocation.prisonPayBand.prisonCode).isEqualTo("MDI")
      assertThat(allocation.startDate).isEqualTo(LocalDate.of(2022, 9, 10))
      assertThat(allocation.endDate).isEqualTo(LocalDate.of(2023, 9, 10))
      assertThat(allocation.allocatedTime).isEqualTo(LocalDateTime.of(2023, 3, 17, 10, 35, 19, 136000000))
      assertThat(allocation.allocatedBy).isEqualTo("Mr Blogs")
      assertThat(allocation.deallocatedTime).isEqualTo(LocalDateTime.of(2023, 3, 17, 10, 35, 19, 136000000))
      assertThat(allocation.deallocatedBy).isEqualTo("Mrs Blogs")
      assertThat(allocation.deallocatedReason).isEqualTo("Not attending regularly") // TODO SDIT-421 Do we need to receive a code that we can map?
    }

    @Test
    fun `when allocation is not found an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetAllocationWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getAllocation(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetAllocationWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAllocation(1234)
      }
    }
  }

  @Nested
  inner class GetAttendance {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendance(
        1234,
        """
          {
            "id": 1234,
            "prisonerNumber": "A1234AA",
            "attendanceReason": {
              "id": 1,
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
            "recordedTime": "2023-03-28T14:26:08.975Z",
            "recordedBy": "A.JONES",
            "status": "WAITING",
            "payAmount": 100,
            "bonusAmount": 50,
            "pieces": 0,
            "issuePayment": true,
            "incentiveLevelWarningIssued": true,
            "otherAbsenceReason": "Prisoner has a valid reason to miss the activity."
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      activitiesApiService.getAttendance(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/attendances/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `get parse core data`() = runTest {
      val attendance = activitiesApiService.getAttendance(1234)

      assertThat(attendance.id).isEqualTo(1234)
      assertThat(attendance.prisonerNumber).isEqualTo("A1234AA")
      assertThat(attendance.attendanceReason?.code).isEqualTo("SICK")
      assertThat(attendance.comment).isEqualTo("Prisoner was too unwell to attend the activity.")
      assertThat(attendance.recordedTime).isEqualTo("2023-03-28T14:26:08.975")
      assertThat(attendance.recordedBy).isEqualTo("A.JONES")
      assertThat(attendance.status).isEqualTo("WAITING")
      assertThat(attendance.payAmount).isEqualTo(100)
      assertThat(attendance.bonusAmount).isEqualTo(50)
      assertThat(attendance.pieces).isEqualTo(0)
      assertThat(attendance.issuePayment).isEqualTo(true)
      assertThat(attendance.incentiveLevelWarningIssued).isEqualTo(true)
      assertThat(attendance.otherAbsenceReason).isEqualTo("Prisoner has a valid reason to miss the activity.")
    }

    @Test
    fun `when attendance is not found an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceWithError(1234, status = 404)

      assertThrows<NotFound> {
        activitiesApiService.getAttendance(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        activitiesApiService.getAttendance(1234)
      }
    }
  }
}
