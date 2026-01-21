package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AllPrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.util.UUID

@Component
class CaseNotesMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByDpsId(
    dpsCaseNoteId: String = UUID.randomUUID().toString(),
    mappings: List<CaseNoteMappingDto> = listOf(
      CaseNoteMappingDto(
        offenderNo = "A1234AA",
        nomisBookingId = 123456,
        nomisCaseNoteId = 1,
        dpsCaseNoteId = dpsCaseNoteId,
        mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
      ),
    ),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/casenotes/dps-casenote-id/$dpsCaseNoteId/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubGetByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/casenotes/dps-casenote-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByPrisoner(offenderNo: String, mappings: AllPrisonerCaseNoteMappingsDto = AllPrisonerCaseNoteMappingsDto(listOf())) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/casenotes/$offenderNo/all")).willReturn(
        okJson(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubGetByPrisoner(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/casenotes/.*/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingServer.stubFor(
      post("/mapping/casenotes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/casenotes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFollowedBySuccess(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/casenotes")
        .inScenario("Retry Mapping CaseNotes Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping CaseNotes Success"),
    )

    mappingServer.stubFor(
      post("/mapping/casenotes")
        .inScenario("Retry Mapping CaseNotes Scenario")
        .whenScenarioStateIs("Cause Mapping CaseNotes Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      delete(urlMatching("/mapping/casenotes/dps-casenote-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteByDpsId(
    dpsCaseNoteId: String = UUID.randomUUID().toString(),
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/casenotes/dps-casenote-id/$dpsCaseNoteId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
