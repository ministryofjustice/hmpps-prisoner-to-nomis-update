package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesSyncDto
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Component
class PhysicalAttributesDpsApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubHealthPing(status: Int) {
    dpsApi.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(objectMapper.writeValueAsString(body))
    return this
  }

  fun stubGetPhysicalAttributes(
    offenderNo: String,
    height: Int? = 180,
    weight: Int? = 80,
    build: String? = "SMALL",
    face: String? = "ROUND",
    facialHair: String? = "CLEAN_SHAVEN",
    hair: String? = "BLACK",
    leftEyeColour: String? = "BLUE",
    rightEyeColour: String? = "GREEN",
    shoeSize: String? = "9.5",
  ) {
    dpsApi.stubFor(
      get(urlMatching("/sync/prisoners/$offenderNo/physical-attributes"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(physicalAttributes(height, weight, build, face, facialHair, hair, leftEyeColour, rightEyeColour, shoeSize))
            .withStatus(200),
        ),
    )
  }

  fun stubGetPhysicalAttributes(errorStatus: HttpStatus) {
    dpsApi.stubFor(
      get(urlMatching("/sync/prisoners/.*/physical-attributes"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(ErrorResponse(status = errorStatus, userMessage = "Some error"))
            .withStatus(errorStatus.value()),
        ),
    )
  }
  fun stubGetPhysicalAttributesWithRetry(
    offenderNo: String,
    height: Int? = 180,
    weight: Int? = 80,
  ) {
    dpsApi.stubFor(
      get(urlMatching("/sync/prisoners/$offenderNo/physical-attributes"))
        .inScenario("Retry Prison Person")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ).willSetStateTo("Prison Person first call failed"),
    )

    dpsApi.stubFor(
      get(urlMatching("/sync/prisoners/$offenderNo/physical-attributes"))
        .inScenario("Retry Prison Person")
        .whenScenarioStateIs("Prison Person first call failed")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(physicalAttributes(height, weight))
            .withStatus(200),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = dpsApi.verify(count, pattern)
}

fun physicalAttributes(
  height: Int? = 180,
  weight: Int? = 80,
  build: String? = "SMALL",
  face: String? = "ROUND",
  facialHair: String? = "CLEAN_SHAVEN",
  hair: String? = "BLACK",
  leftEyeColour: String? = "BLUE",
  rightEyeColour: String? = "GREEN",
  shoeSize: String? = "9.5",
): PhysicalAttributesSyncDto = PhysicalAttributesSyncDto(
  height = height,
  weight = weight,
  build = build,
  face = face,
  facialHair = facialHair,
  hair = hair,
  leftEyeColour = leftEyeColour,
  rightEyeColour = rightEyeColour,
  shoeSize = shoeSize,
)
