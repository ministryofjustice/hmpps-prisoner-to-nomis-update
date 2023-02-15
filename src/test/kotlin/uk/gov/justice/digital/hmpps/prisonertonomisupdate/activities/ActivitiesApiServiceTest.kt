package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension

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
                  "description": "Unacceptable absence"
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
            "allocatedTime": "2022-12-30T14:03:06.365Z",
            "allocatedBy": "Mr Blogs",
            "deallocatedTime": "2022-12-30T14:03:06.365Z",
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
          "minimumIncentiveNomisCode": "BAS"
        },
        "slots": [
          {
            "id": 123456,
            "startTime": "9:00",
            "endTime": "11:30",
            "daysOfWeek": ["Mon","Tue","Wed"]
          }
        ],
        "startDate" : "2023-01-20"
      }
        """.trimIndent()
      )
    }

    @Test
    fun `should call api with OAuth2 token`() {
      activitiesApiService.getActivitySchedule(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/schedules/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `get parse core data`() {
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
      // TODO: pay, start/end date
    }

    @Test
    fun `when schedule is not found an exception is thrown`() {
      ActivitiesApiExtension.activitiesApi.stubGetScheduleWithError(1234, status = 404)

      Assertions.assertThatThrownBy {
        activitiesApiService.getActivitySchedule(1234)
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      ActivitiesApiExtension.activitiesApi.stubGetScheduleWithError(1234, status = 503)

      Assertions.assertThatThrownBy {
        activitiesApiService.getActivitySchedule(1234)
      }.isInstanceOf(ServiceUnavailable::class.java)
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
                    "code": "ABS",
                    "description": "Unacceptable absence"
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
            "minimumIncentiveNomisCode": "BAS"
          },
          "slots": [
            {
              "id": 123456,
              "startTime": "9:00",
              "endTime": "11:30",
              "daysOfWeek": ["Mon","Tue","Wed"]
            }
          ],
          "startDate" : "2023-01-20"
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
      "createdBy": "Adam Smith"
    }
        """.trimIndent()
      )
    }

    @Test
    fun `should call api with OAuth2 token`() {
      activitiesApiService.getActivity(1234)

      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/activities/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `get parse core data`() {
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
    fun `when schedule is not found an exception is thrown`() {
      ActivitiesApiExtension.activitiesApi.stubGetActivityWithError(1234, status = 404)

      Assertions.assertThatThrownBy {
        activitiesApiService.getActivity(1234)
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      ActivitiesApiExtension.activitiesApi.stubGetActivityWithError(1234, status = 503)

      Assertions.assertThatThrownBy {
        activitiesApiService.getActivity(1234)
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}
