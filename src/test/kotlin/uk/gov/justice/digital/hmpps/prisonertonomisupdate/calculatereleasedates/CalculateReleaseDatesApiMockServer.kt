package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.legacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.model.LegacySentenceCalculationDto
import java.time.LocalDate
import java.time.LocalDateTime

class CalculateReleaseDatesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val calculateReleaseDatesApi = CalculateReleaseDatesApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun legacySentenceCalculationDto(
      nomisCalculationId: Long = 12345,
      prisonerId: String = "A1234BC",
      bookingId: Long = 54321,
    ) = LegacySentenceCalculationDto(
      nomisCalculationId = nomisCalculationId,
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculationDate = LocalDateTime.parse("2026-01-02T10:15:30"),
      calculationReasonCode = "NEW",
      nomisCreatedAt = LocalDateTime.parse("2026-01-02T10:15:30"),
      nomisCreatedBy = "JANE.SMITH",
      commentText = "Calculation comment",
      crdCalculatedDate = LocalDate.parse("2030-03-04"),
      sedCalculatedDate = LocalDate.parse("2031-05-06"),
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    calculateReleaseDatesApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    calculateReleaseDatesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    calculateReleaseDatesApi.stop()
  }
}

class CalculateReleaseDatesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8113
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

  fun stubGetSentenceCalculations(
    prisonerId: String = "A1234BC",
    response: List<LegacySentenceCalculationDto> = listOf(legacySentenceCalculationDto(prisonerId = prisonerId)),
  ) {
    stubFor(
      get("/legacy-sentence-calculation/$prisonerId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response))
          .withStatus(200),
      ),
    )
  }
}
