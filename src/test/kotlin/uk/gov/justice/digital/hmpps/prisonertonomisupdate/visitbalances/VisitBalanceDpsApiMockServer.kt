package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto.ChangeLogSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto.ChangeLogType
import java.time.LocalDateTime

class VisitBalanceDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val visitBalanceDpsApi = VisitBalanceDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    visitBalanceDpsApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
          .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubGetVisitBalanceAdjustment(
    prisonNumber: String,
    vbAdjId: String = "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef",
    response: VisitAllocationPrisonerAdjustmentResponseDto = visitBalanceAdjustmentDto(prisonNumber),
  ) {
    stubFor(
      get("/visits/allocation/prisoner/$prisonNumber/adjustments/$vbAdjId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalanceAdjustment(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathMatching("/visits/allocation/prisoner/.*/adjustments/.*")).willReturn(
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
    this.withBody(VisitBalanceDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }
}

fun visitBalanceDto() = PrisonerBalanceDto(
  prisonerId = "A1234KT",
  voBalance = 24,
  pvoBalance = 3,
)

fun visitBalanceAdjustmentDto(
  prisonNumber: String,
  changeLogSource: ChangeLogSource = ChangeLogSource.SYSTEM,
  userId: String = "SYSTEM",
) = VisitAllocationPrisonerAdjustmentResponseDto(
  prisonerId = prisonNumber,
  voBalance = 12,
  changeToVoBalance = 2,
  pvoBalance = 7,
  changeToPvoBalance = -1,
  changeLogType = ChangeLogType.SYNC,
  userId = userId,
  changeLogSource = changeLogSource,
  changeTimestamp = LocalDateTime.parse("2021-01-18T01:02:03"),
  comment = "A comment",
)
