package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import java.time.LocalDate
import java.util.UUID

class VisitBalanceDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val visitBalanceDpsApi = VisitBalanceDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    visitBalanceDpsApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    visitBalanceDpsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    visitBalanceDpsApi.stop()
  }
}

class VisitBalanceDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8101
  }

  fun stubGetVisitBalance(response: PrisonerBalanceDto = visitBalanceDto()) {
    stubFor(
      get("/visits/allocation/prisoner/${response.prisonerId}/balance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalance(offenderNo: String = "A1234KT", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get("/visits/allocation/prisoner/$offenderNo/balance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(error),
      ),
    )
  }

  fun stubGetVisitBalanceAdjustment(vbAdjId: String = "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef", response: VisitBalanceAdjustmentDto = visitBalanceAdjustmentDto(vbAdjId)) {
    stubFor(
      get("/visits/allocation/adjustment/$vbAdjId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalanceAdjustment(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathMatching("/visits/allocation/adjustment/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(error),
      ),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(VisitBalanceDpsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }
}

fun visitBalanceDto() = PrisonerBalanceDto(
  prisonerId = "A1234KT",
  voBalance = 24,
  pvoBalance = 3,
)

fun visitBalanceAdjustmentDto(vbAdjId: String) = VisitBalanceAdjustmentDto(
  id = UUID.fromString(vbAdjId),
  prisonerId = "A1234KT",
  oldVoBalance = 12,
  changeToVoBalance = 2,
  oldPvoBalance = 7,
  changeToPvoBalance = -1,
  adjustmentDate = LocalDate.parse("2021-01-18"),
  adjustmentReasonCode = VisitBalanceAdjustmentReasonCode.GOV,
  comment = "A comment",
  expiryBalance = 6,
  expiryDate = LocalDate.parse("2021-02-19"),
  endorsedStaffId = 123,
  authorisedStaffId = 345,
)
