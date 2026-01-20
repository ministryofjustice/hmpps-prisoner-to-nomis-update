package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class CaseNotesNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetCaseNote(caseNoteId: Long, response: CaseNoteResponse = caseNoteResponse(caseNoteId)) {
    nomisApi.stubFor(
      get(urlEqualTo("/casenotes/$caseNoteId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPostCaseNote(
    offenderNo: String = "A1234AK",
    caseNoteId: Long = 1001,
    caseNote: CreateCaseNoteResponse = CreateCaseNoteResponse(
      id = caseNoteId,
      bookingId = 1,
    ),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(caseNote)),
      ),
    )
  }

  fun stubPutCaseNote(
    caseNoteId: Long,
    caseNote: CaseNoteResponse = caseNoteResponse(caseNoteId),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/casenotes/$caseNoteId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(caseNote)),
      ),
    )
  }

  fun stubPutCaseNoteError(caseNoteId: Long) {
    nomisApi.stubFor(
      put(urlEqualTo("/casenotes/$caseNoteId")).willReturn(status(500)),
    )
  }

  fun stubDeleteCaseNote(
    caseNoteId: Long,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/casenotes/$caseNoteId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubGetCaseNotesForPrisoner(
    offenderNo: String,
    response: PrisonerCaseNotesResponse = PrisonerCaseNotesResponse(caseNotes = emptyList()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun caseNoteResponse(caseNoteId: Long) = CaseNoteResponse(
  caseNoteId = caseNoteId,
  bookingId = 12345678,
  prisonId = "MDI",
  caseNoteType = CodeDescription("X", "Security"),
  caseNoteSubType = CodeDescription("X", "Security"),
  authorUsername = "me",
  authorStaffId = 123456L,
  authorFirstName = "First",
  authorLastName = "Last",
  amendments = emptyList(),
  createdDatetime = LocalDateTime.parse("2021-02-03T04:05:06"),
  createdUsername = "John",
  noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
  occurrenceDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
  caseNoteText = "the actual casenote",
  sourceSystem = CaseNoteResponse.SourceSystem.DPS,
  auditModuleName = "audit",
)
