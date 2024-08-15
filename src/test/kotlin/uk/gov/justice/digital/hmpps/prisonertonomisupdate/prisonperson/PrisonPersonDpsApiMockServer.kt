package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

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
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.HealthDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PrisonPersonDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.ValueWithMetadataInteger
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

class PrisonPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonPersonDpsApi = PrisonPersonDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
    prisonPersonDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonPersonDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonPersonDpsApi.stop()
  }
}

class PrisonPersonDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8097
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
    this.withBody(PrisonPersonDpsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }

  fun stubGetPrisonPerson(
    prisonerNumber: String = "A1234AA",
    height: Int = 180,
    weight: Int = 80,
  ) {
    stubFor(
      get(urlMatching("/prisoners/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(prisonPerson(prisonerNumber, height, weight))
            .withStatus(200),
        ),
    )
  }

  fun stubGetPrisonPerson(errorStatus: HttpStatus) {
    stubFor(
      get(urlMatching("/prisoners/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(ErrorResponse(status = errorStatus, userMessage = "Some error"))
            .withStatus(errorStatus.value()),
        ),
    )
  }
}

fun prisonPerson(
  prisonerNumber: String = "A1234AA",
  height: Int = 180,
  weight: Int = 80,
): PrisonPersonDto = PrisonPersonDto(
  prisonerNumber = prisonerNumber,
  physicalAttributes = PhysicalAttributesDto(
    height = ValueWithMetadataInteger(value = height, lastModifiedAt = LocalDateTime.now().toString(), lastModifiedBy = "someone"),
    weight = ValueWithMetadataInteger(value = weight, lastModifiedAt = LocalDateTime.now().toString(), lastModifiedBy = "someone"),
  ),
  health = HealthDto(),
)
