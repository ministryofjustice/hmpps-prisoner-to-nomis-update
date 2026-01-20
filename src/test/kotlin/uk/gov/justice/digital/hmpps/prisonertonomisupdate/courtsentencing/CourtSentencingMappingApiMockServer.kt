package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

@Component
class CourtSentencingMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingServer.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubCreateCourtCase() {
    stubCreate("/mapping/court-sentencing/court-cases")
  }

  fun stubDeleteCourtCase(id: String) {
    stubDelete("/mapping/court-sentencing/court-cases/dps-court-case-id/$id")
  }

  fun stubDeleteCourtAppearance(id: String) {
    stubDelete("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$id")
  }

  fun stubCreateCourtCaseWithErrorFollowedBySuccess() {
    stubCreateWithErrorFollowedBySuccess(url = "/mapping/court-sentencing/court-cases", "Court case")
  }

  fun stubGetCourtCaseMappingGivenDpsId(id: String, nomisCourtCaseId: Long = 54321) {
    stubGet(
      "/mapping/court-sentencing/court-cases/dps-court-case-id/$id",
      response = CourtCaseMappingDto(
        nomisCourtCaseId = nomisCourtCaseId,
        dpsCourtCaseId = id,
        mappingType = CourtCaseMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubGetCourtCaseMappingGivenDpsId(id: String, status: HttpStatus) {
    stubGetWithError(
      "/mapping/court-sentencing/court-cases/dps-court-case-id/$id",
      status = status.value(),
    )
  }

  fun stubGetCourtCaseMappingGivenNomisId(id: Long, dpsCourtCaseId: String = "54321") {
    stubGet(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/$id",
      response = CourtCaseMappingDto(
        nomisCourtCaseId = id,
        dpsCourtCaseId = dpsCourtCaseId,
        mappingType = CourtCaseMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubGetCourtCaseMappingGivenNomisId(id: Long, status: HttpStatus) {
    stubGetWithError(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/$id",
      status = status.value(),
    )
  }

  fun stubPostCourtCaseMappingsGivenNomisIds(ids: List<Long>, dpsCourtCaseIds: List<String> = listOf("54321")) {
    val response = ids.mapIndexed { index, id ->
      CourtCaseMappingDto(
        nomisCourtCaseId = id,
        dpsCourtCaseId = dpsCourtCaseIds[index],
        mappingType = CourtCaseMappingDto.MappingType.DPS_CREATED,
      )
    }
    mappingServer.stubFor(
      post("/mapping/court-sentencing/court-cases/nomis-case-ids/get-list")
        // just check the first one to differentiate
        .withRequestBodyJsonPath("[0]", equalTo(ids.first().toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              jsonMapper.writeValueAsString(
                response,
              ),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetCaseMappingGivenDpsIdWithError(id: String, status: Int = 500) {
    stubGetWithError("/mapping/court-sentencing/court-cases/dps-court-case-id/$id", status)
  }

  fun stubGetCourtAppearanceMappingGivenDpsIdWithError(id: String, status: Int = 500) {
    stubGetWithError("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$id", status)
  }

  fun stubCourtChargeBatchUpdate() {
    mappingServer.stubFor(
      put("/mapping/court-sentencing/court-charges").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtAppearanceMappingGivenDpsId(id: String, nomisCourtAppearanceId: Long = 54321) {
    stubGet(
      "/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$id",
      response = CourtAppearanceMappingDto(
        nomisCourtAppearanceId = nomisCourtAppearanceId,
        dpsCourtAppearanceId = id,
        mappingType = CourtAppearanceMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubGetCourtChargeMappingGivenDpsIdWithError(id: String, status: Int = 500) {
    stubGetWithError("/mapping/court-sentencing/court-charges/dps-court-charge-id/$id", status)
  }

  fun stubCreateCourtChargeWithErrorFollowedBySuccess() {
    stubCreateWithErrorFollowedBySuccess(url = "/mapping/court-sentencing/court-charges", "Court charge")
  }

  fun stubGetCourtChargeNotFoundFollowedBySuccess(id: String, nomisCourtChargeId: Long = 54321) {
    mappingServer.stubFor(
      get("/mapping/court-sentencing/court-charges/dps-court-charge-id/$id")
        .inScenario("Retry Mapping Court Charge Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(404) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Get Mapping Court Charge Success"),
    )

    mappingServer.stubFor(
      get("/mapping/court-sentencing/court-charges/dps-court-charge-id/$id")
        .inScenario("Retry Mapping Court Charge Scenario")
        .whenScenarioStateIs("Get Mapping Court Charge Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              jsonMapper.writeValueAsString(
                CourtChargeMappingDto(
                  nomisCourtChargeId = nomisCourtChargeId,
                  dpsCourtChargeId = id,
                  mappingType = CourtChargeMappingDto.MappingType.DPS_CREATED,
                ),
              ),
            )
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetCourtChargeMappingGivenDpsId(id: String, nomisCourtChargeId: Long) {
    stubGet(
      "/mapping/court-sentencing/court-charges/dps-court-charge-id/$id",
      response = CourtChargeMappingDto(
        nomisCourtChargeId = nomisCourtChargeId,
        dpsCourtChargeId = id,
        mappingType = CourtChargeMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubCreateCourtCharge() {
    stubCreate("/mapping/court-sentencing/court-charges")
  }

  fun stubGetSentenceMappingGivenDpsId(id: String, nomisSentenceSequence: Long, nomisBookingId: Long) {
    stubGet(
      "/mapping/court-sentencing/sentences/dps-sentence-id/$id",
      response = SentenceMappingDto(
        nomisSentenceSequence = nomisSentenceSequence.toInt(),
        nomisBookingId = nomisBookingId,
        dpsSentenceId = id,
        mappingType = SentenceMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubGetSentenceMappingGivenDpsIdWithError(id: String, status: Int = 500) {
    stubGetWithError("/mapping/court-sentencing/sentences/dps-sentence-id/$id", status)
  }

  fun stubGetMappingsGivenSentenceIds(request: List<String>, mappings: List<SentenceMappingDto>) {
    mappingServer.stubFor(
      post("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list")
        .withRequestBody(equalToJson(MappingExtension.jsonMapper.writeValueAsString(request)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(MappingExtension.jsonMapper.writeValueAsString(mappings))
            .withStatus(200),
        ),
    )
  }

  fun stubCreateSentence() {
    stubCreate("/mapping/court-sentencing/sentences")
  }

  fun stubDeleteSentence(id: String) {
    stubDelete("/mapping/court-sentencing/sentences/dps-sentence-id/$id")
  }

  fun stubCreateSentenceWithErrorFollowedBySuccess() {
    stubCreateWithErrorFollowedBySuccess(url = "/mapping/court-sentencing/sentences", "Sentence")
  }

  fun stubGetSentenceTermMappingGivenDpsId(
    id: String,
    nomisSentenceSequence: Long,
    nomisTermSequence: Long,
    nomisBookingId: Long,
  ) {
    stubGet(
      "/mapping/court-sentencing/sentence-terms/dps-term-id/$id",
      response = SentenceTermMappingDto(
        nomisTermSequence = nomisTermSequence.toInt(),
        nomisSentenceSequence = nomisSentenceSequence.toInt(),
        nomisBookingId = nomisBookingId,
        dpsTermId = id,
        mappingType = SentenceTermMappingDto.MappingType.DPS_CREATED,
      ),
    )
  }

  fun stubGetSentenceTermMappingGivenDpsIdWithError(id: String, status: Int = 500) {
    stubGetWithError("/mapping/court-sentencing/sentence-terms/dps-term-id/$id", status)
  }

  fun stubCreateSentenceTerm() {
    stubCreate("/mapping/court-sentencing/sentence-terms")
  }

  fun stubDeleteSentenceTerm(id: String) {
    stubDelete("/mapping/court-sentencing/sentence-terms/dps-term-id/$id")
  }

  fun stubCreateSentenceTermWithErrorFollowedBySuccess() {
    stubCreateWithErrorFollowedBySuccess(url = "/mapping/court-sentencing/sentence-terms", "Sentence term")
  }

  fun stubCreateAppearanceRecallMapping() {
    stubCreate("/mapping/court-sentencing/court-appearances/recall")
  }

  fun stubGetAppearanceRecallMappings(recallId: String, mappings: List<CourtAppearanceRecallMappingDto> = emptyList()) {
    stubGet(
      "/mapping/court-sentencing/court-appearances/dps-recall-id/$recallId",
      response = mappings,
    )
  }

  fun stubDeleteAppearanceRecallMappings(recallId: String) {
    stubDelete(
      "/mapping/court-sentencing/court-appearances/dps-recall-id/$recallId",
    )
  }

  fun stubUpdateAndCreateMappings() {
    stubPut("/mapping/court-sentencing/court-cases/update-create")
  }

  fun stubUpdateAndCreateMappingsWithErrorFollowedBySuccess() {
    stubPutWithErrorFollowedBySuccess(url = "/mapping/court-sentencing/court-cases/update-create", "Update and create mappings")
  }

  fun stubReplaceMappings() {
    stubPut("/mapping/court-sentencing/court-cases/replace")
  }

  fun stubDeleteMappingsByDpsIds() {
    stubPost("/mapping/court-sentencing/court-cases/delete-by-dps-ids")
  }

  // helper methods

  fun stubCreate(url: String) {
    mappingServer.stubFor(
      post(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPut(url: String) {
    mappingServer.stubFor(
      put(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubPost(url: String) {
    mappingServer.stubFor(
      post(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubDelete(url: String) {
    mappingServer.stubFor(
      delete(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubGet(url: String, response: Any) {
    mappingServer.stubFor(
      get(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              response,
            ),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetWithError(url: String, status: Int = 500) {
    mappingServer.stubFor(
      get(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateWithErrorFollowedBySuccess(url: String, name: String) {
    mappingServer.stubFor(
      post(url)
        .inScenario("Retry $name")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Create $name Success"),
    )

    mappingServer.stubFor(
      post(url)
        .inScenario("Retry $name")
        .whenScenarioStateIs("Create $name Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun stubPutWithErrorFollowedBySuccess(url: String, name: String) {
    mappingServer.stubFor(
      put(url)
        .inScenario("Retry $name")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Create $name Success"),
    )

    mappingServer.stubFor(
      put(url)
        .inScenario("Retry $name")
        .whenScenarioStateIs("Create $name Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
