package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

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
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PropertyDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val propertyDpsApi = PropertyDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    propertyDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    propertyDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    propertyDpsApi.stop()
  }
}

class PropertyDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8107
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
    this.withBody(PropertyDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }

  fun stubGetProperty(response: PropertyContainerDto = dpsProperty(), status: Int = 200) {
    stubFor(
      get(urlMatching("/sync/property-containers/.+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(status),
        ),
    )
  }
}

fun dpsProperty(id: UUID = UUID.randomUUID(), locationId: UUID = UUID.randomUUID()) = PropertyContainerDto(
  id = id,
  prisonerNumber = "A1234KT",
  prisonId = "MDI",
  containerType = PropertyContainerDto.ContainerType.STANDARD,
  currentStatus = PropertyContainerDto.CurrentStatus.COMBINED,
  createDateTime = LocalDateTime.now(),
  createdByUserId = "ME",
  currentSealNumber = "SEAL1234",
  currentLocation = locationId,
  currentLocationType = PropertyContainerDto.CurrentLocationType.INTERNAL,
  proposedDisposalDate = LocalDate.parse("2026-03-04"),
  removalOutcome = PropertyContainerDto.RemovalOutcome.TRANSFERRED,
  removalDate = LocalDate.parse("2026-01-02"),
)
