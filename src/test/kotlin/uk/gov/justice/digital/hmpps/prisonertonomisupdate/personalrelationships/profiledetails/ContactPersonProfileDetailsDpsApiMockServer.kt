package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class ContactPersonProfileDetailsDpsApiMockServer {
  fun stubGetDomesticStatus(prisonerNumber: String, response: SyncPrisonerDomesticStatusResponse = domesticStatus()) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetDomesticStatus(prisonerNumber: String, errorStatus: HttpStatus) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .willReturn(
          aResponse()
            .withStatus(errorStatus.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
        ),
    )
  }

  fun stubGetDomesticStatusAfterRetry(prisonerNumber: String, response: SyncPrisonerDomesticStatusResponse = domesticStatus()) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .inScenario("Domestic status works on retry")
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ).willSetStateTo("Failed first call"),
    )

    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .inScenario("Domestic status works on retry")
        .whenScenarioStateIs("Failed first call")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetNumberOfChildren(
    prisonerNumber: String,
    response: SyncPrisonerNumberOfChildrenResponse = numberOfChildren(),
  ) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetNumberOfChildren(prisonerNumber: String, errorStatus: HttpStatus) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .willReturn(
          aResponse()
            .withStatus(errorStatus.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
        ),
    )
  }

  fun stubGetNumberOfChildrenAfterRetry(
    prisonerNumber: String,
    response: SyncPrisonerNumberOfChildrenResponse = numberOfChildren(),
  ) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .inScenario("Number of children works on retry")
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ).willSetStateTo("Failed first call"),
    )
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .inScenario("Number of children works on retry")
        .whenScenarioStateIs("Failed first call")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(count, pattern)
}

fun domesticStatus(dpsId: Long = 54321, domesticStatusCode: String? = "M") = SyncPrisonerDomesticStatusResponse(
  id = dpsId,
  active = true,
  domesticStatusCode = domesticStatusCode,
  createdTime = LocalDateTime.now(),
  createdBy = "A_USER",
)

fun numberOfChildren(dpsId: Long = 54321, numberOfChildren: String? = "3") = SyncPrisonerNumberOfChildrenResponse(
  id = dpsId,
  active = true,
  numberOfChildren = numberOfChildren,
  createdTime = LocalDateTime.now(),
  createdBy = "A_USER",
)
