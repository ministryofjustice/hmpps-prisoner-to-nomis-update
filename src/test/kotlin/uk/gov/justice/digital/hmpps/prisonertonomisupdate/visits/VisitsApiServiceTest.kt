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
import java.time.LocalDateTime

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
            "reference": "1234",
            "prisonId": "MDI",
            "prisonerId": "A1234FG",
            "visitType": "SOCIAL",
            "startTimestamp": "2019-12-02T09:00:00",
            "endTimestamp": "2019-12-02T10:00:00",
            "visitStatus": "BOOKED",
            "visitRoom": "Legal Visit Room 6",
            "visitRestriction": "OPEN",
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

      assertThat(visit.reference).isEqualTo("1234")
      assertThat(visit.startTimestamp).isEqualTo(LocalDateTime.parse("2019-12-02T09:00:00"))
      assertThat(visit.endTimestamp).isEqualTo(LocalDateTime.parse("2019-12-02T10:00:00"))
      assertThat(visit.prisonId).isEqualTo("MDI")
      assertThat(visit.visitStatus).isEqualTo("BOOKED")
      assertThat(visit.visitRoom).isEqualTo("Legal Visit Room 6")
      assertThat(visit.visitRestriction).isEqualTo("OPEN")
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
}
