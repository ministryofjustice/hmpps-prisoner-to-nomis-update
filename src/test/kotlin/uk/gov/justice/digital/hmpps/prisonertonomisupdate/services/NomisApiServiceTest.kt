package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringAPIServiceTest
@Import(NomisApiService::class)
internal class NomisApiServiceTest {

  @Autowired
  private lateinit var nomisApiService: NomisApiService

  @Nested
  inner class CreateVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() {
      nomisApiService.createVisit(newVisit())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post visit data to nomis api`() {
      nomisApiService.createVisit(newVisit(offenderNo = "AB123D"))

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.offenderNo", equalTo("AB123D")))
      )
    }

    @Test
    internal fun `expect the visit id to be returned for created visit`() {
      NomisApiExtension.nomisApi.stubVisitCreate(
        """
                  {
                    "visitId": 12345
                  }
     """
      )

      val result = nomisApiService.createVisit(newVisit())

      assertThat(result.visitId).isEqualTo("12345")
    }

    @Test
    internal fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 404)

      assertThatThrownBy {
        nomisApiService.createVisit(newVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 503)

      assertThatThrownBy {
        nomisApiService.createVisit(newVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}

fun newVisit(offenderNo: String = "AB123D"): CreateVisitDto = CreateVisitDto(
  offenderNo = offenderNo,
  prisonId = "MDI",
  startTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  visitType = "SCON",
  visitRoomId = "1",
  issueDate = LocalDate.now()
)
