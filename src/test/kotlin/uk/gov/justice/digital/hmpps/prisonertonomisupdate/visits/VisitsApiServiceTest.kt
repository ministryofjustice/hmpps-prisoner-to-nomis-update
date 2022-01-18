package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.VisitDto.Visitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import java.time.LocalDate
import java.time.LocalTime

@SpringAPIServiceTest
@Import(VisitsApiService::class, VisitsConfiguration::class)
internal class VisitsApiServiceTest {

  @Autowired
  private lateinit var visitsApiService: VisitsApiService

  @Nested
  inner class GetVisit {
    @BeforeEach
    internal fun setUp() {
      VisitsApiExtension.visitsApi.stubVisitGet(
        "1234",
        """
          {
            "visitId": "1234",
            "prisonId": "MDI",
            "prisonerId": "A1234FG",
            "visitType": "STANDARD_SOCIAL",
            "visitRoom": "Room 1",
            "visitDate": "2019-12-02",
            "startTime": "09:00:00",
            "endTime": "10:00:00",
            "currentStatus": "BOOKED",
            "visitors": [
              {
                "nomisPersonId": 543524
              },
              {
                "nomisPersonId": 344444
              },
              {
                "nomisPersonId": 655656
              }
            ]
          }
        """.trimIndent()
      )
    }

    @Test
    fun `should call visit api with OAuth2 token`() {
      visitsApiService.getVisit("1234")

      VisitsApiExtension.visitsApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/visits/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `get parse core visit data`() {
      val visit = visitsApiService.getVisit("1234")

      assertThat(visit.visitId).isEqualTo("1234")
      assertThat(visit.visitDate).isEqualTo(LocalDate.parse("2019-12-02"))
      assertThat(visit.startTime).isEqualTo(LocalTime.parse("09:00:00"))
      assertThat(visit.endTime).isEqualTo(LocalTime.parse("10:00:00"))
      assertThat(visit.prisonId).isEqualTo("MDI")
      assertThat(visit.visitors).containsExactly(Visitor(543524), Visitor(344444), Visitor(655656))
    }

    @Test
    internal fun `when visit is not found an exception is thrown`() {
      VisitsApiExtension.visitsApi.stubVisitGetWithError("1234", status = 404)

      Assertions.assertThatThrownBy {
        visitsApiService.getVisit("1234")
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      VisitsApiExtension.visitsApi.stubVisitGetWithError("1234", status = 503)

      Assertions.assertThatThrownBy {
        visitsApiService.getVisit("1234")
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class AddVisitMapping {
    @BeforeEach
    internal fun setUp() {
      VisitsApiExtension.visitsApi.stubVisitMappingPost(
        "1234"
      )
    }

    @Test
    fun `should call visit api with OAuth2 token`() {
      visitsApiService.addVisitMapping("1234", "5432")

      VisitsApiExtension.visitsApi.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/visits/1234/nomis-mapping"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `when visit is not found an exception is thrown`() {
      VisitsApiExtension.visitsApi.stubVisitMappingPostWithError(visitId = "1234", status = 404)

      Assertions.assertThatThrownBy {
        visitsApiService.addVisitMapping("1234", "5432")
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      VisitsApiExtension.visitsApi.stubVisitMappingPostWithError(visitId = "1234", status = 503)

      Assertions.assertThatThrownBy {
        visitsApiService.addVisitMapping("1234", "5432")
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}
