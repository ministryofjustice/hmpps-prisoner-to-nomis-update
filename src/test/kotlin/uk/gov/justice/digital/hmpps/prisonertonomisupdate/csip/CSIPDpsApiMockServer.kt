package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Referral
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CSIPDpsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val csipDpsApi = CSIPDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
    csipDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    csipDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    csipDpsApi.stop()
  }
}

class CSIPDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8098
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
    this.withBody(CSIPDpsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }

  fun stubGetCsipReport(dpsCsipReport: CsipRecord = dpsCsipRecord()) {
    stubFor(
      get(urlMatching("/csip-records/\\S+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(dpsCsipReport)
            .withStatus(200),
        ),
    )
  }
}

fun dpsCsipRecord() =
  CsipRecord(
    recordUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00"),
    prisonNumber = "A12234",
    createdAt = LocalDateTime.now(),
    createdBy = "JSMITH",
    createdByDisplayName = "JOHN SMITH",
    status = CsipRecord.Status.CSIP_OPEN,
    referral = Referral(
      incidentDate = LocalDate.parse("2024-08-09"),
      incidentType = ReferenceData(code = "INT"),
      incidentLocation = ReferenceData(code = "LIB"),
      referredBy = "JIM_ADM",
      referralDate = LocalDate.parse("2024-10-01"),
      refererArea = ReferenceData(code = "EDU"),
      isSaferCustodyTeamInformed = Referral.IsSaferCustodyTeamInformed.NO,
      contributoryFactors = listOf(),
    ),
  )
