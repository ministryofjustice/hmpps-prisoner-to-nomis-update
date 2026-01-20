package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import java.time.LocalDateTime
import java.util.UUID

class CaseNotesDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val caseNotesDpsApi = CaseNotesDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    caseNotesDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    caseNotesDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    caseNotesDpsApi.stop()
  }
}

class CaseNotesDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8096
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
    this.withBody(CaseNotesDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }

  fun stubGetCaseNote(caseNote: CaseNote = dpsCaseNote(), status: Int = 200) {
    stubFor(
      get(urlMatching("/case-notes/.+/.+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(caseNote)
            .withStatus(status),
        ),
    )
  }

  fun stubGetCaseNotesForPrisoner(offenderNo: String, response: String) {
    stubFor(
      get(urlPathEqualTo("/sync/case-notes/$offenderNo"))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetCaseNotesForPrisoner(offenderNo: String, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathEqualTo("/case-notes/$offenderNo"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }
}

fun dpsCaseNote(): CaseNote = CaseNote(
  caseNoteId = UUID.randomUUID().toString(),
  legacyId = 1234,
  offenderIdentifier = "A1234AA",
  type = "X",
  typeDescription = "Security",
  subType = "Y",
  subTypeDescription = "subtype desc",
  source = "DPS",
  creationDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
  occurrenceDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
  authorName = "me",
  authorUserId = "123456",
  authorUsername = "ME.COM",
  text = "contents of case note",
  eventId = 1234567,
  sensitive = false,
  amendments = emptyList(),
  systemGenerated = false,
)
