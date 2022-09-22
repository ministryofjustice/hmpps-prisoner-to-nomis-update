package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post visit data to nomis api`() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit(offenderNo = "AB123D"))

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.offenderNo", equalTo("AB123D")))
      )
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

  @Nested
  inner class CancelVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCancel(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post cancel request to nomis api`() {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
      )
    }

    @Test
    internal fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 404)

      assertThatThrownBy {
        nomisApiService.cancelVisit(cancelVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 503)

      assertThatThrownBy {
        nomisApiService.cancelVisit(cancelVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class UpdateVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitUpdate(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post cancel request to nomis api`() {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
      )
    }

    @Test
    internal fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 404)

      assertThatThrownBy {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 503)

      assertThatThrownBy {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should call nomis api with OAuth2 token`() {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to nomis api`() {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("MDI")))
      )
    }

    @Test
    internal fun `when offender is not found an exception is thrown`() {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 404)

      assertThatThrownBy {
        nomisApiService.createIncentive(456, newIncentive())
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 503)

      assertThatThrownBy {
        nomisApiService.createIncentive(456, newIncentive())
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}

fun newVisit(offenderNo: String = "AB123D"): CreateVisitDto = CreateVisitDto(
  offenderNo = offenderNo,
  prisonId = "MDI",
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  visitType = "SCON",
  issueDate = LocalDate.now(),
  visitComment = "VSIP ref: 123",
  visitOrderComment = "VO VSIP ref: 123",
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun cancelVisit(): CancelVisitDto = CancelVisitDto(offenderNo = "AB123D", nomisVisitId = "12", outcome = "VISCANC")
fun updateVisit(): UpdateVisitDto = UpdateVisitDto(
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun newIncentive() = CreateIncentiveDto(
  iepDateTime = LocalDateTime.now(),
  prisonId = "MDI",
  iepLevel = "High"
)
