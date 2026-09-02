package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.CsraDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraCurrentRating
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CsraDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val csraApi = CsraDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    csraApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    csraApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    csraApi.stop()
  }
}

class CsraDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8108
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
    this.withBody(jsonMapper.writeValueAsString(body))
    return this
  }

  fun stubGetCsraHistory(prisonerNumber: String, response: CsraReviewHistory) {
    stubFor(
      get(urlPathEqualTo("/csra-review/prisoner/$prisonerNumber/history"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCurrentCsra(prisonerNumber: String, response: CsraCurrentRating) {
    stubFor(
      get(urlPathEqualTo("/csra-review/prisoner/$prisonerNumber/current-rating"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCurrentCsraError(prisonerNumber: String, status: Int) {
    stubFor(
      get(urlPathEqualTo("/csra-review/prisoner/$prisonerNumber/current-rating"))
        .willReturn(status(status)),
    )
  }

  fun stubGetCsraReview(id: UUID, response: CsraReviewDetail = dpsCsra(id)) {
    stubFor(
      get(urlPathEqualTo("/csra-review/$id"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }
}

fun dpsCsra(
  id: UUID = UUID.randomUUID(),
  prisonerNumber: String = CSRA_OFFENDER_NO,
  finalResult: CsraReviewDetail.FinalResult? = CsraReviewDetail.FinalResult.STANDARD,
) = CsraReviewDetail(
  id = id,
  prisonerNumber = prisonerNumber,
  assessmentDate = LocalDate.parse("2026-01-02"),
  type = CsraReviewDetail.Type.RATING,
  createdAt = LocalDateTime.parse("2026-01-02T10:00:00"),
  createdBy = "ME",
  prisonId = "MDI",
  finalResult = finalResult,
  finalResultDate = LocalDate.parse("2026-01-03"),
)
