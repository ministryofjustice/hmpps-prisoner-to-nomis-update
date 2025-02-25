package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Component
class ContactPersonProfileDetailsDpsApiMockServer {
  fun domesticStatus() = SyncPrisonerDomesticStatusResponse(123)
  fun numberOfChildren() = SyncPrisonerNumberOfChildrenResponse(123)

  fun stubGetDomesticStatus(prisonerNumber: String, response: SyncPrisonerDomesticStatusResponse = domesticStatus()) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetDomesticStatus(prisonerNumber: String, errorStatus: HttpStatus) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/domestic-status")
        .willReturn(
          aResponse()
            .withStatus(errorStatus.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
        ),
    )
  }

  fun stubGetNumberOfChildren(prisonerNumber: String, response: SyncPrisonerNumberOfChildrenResponse = numberOfChildren()) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetNumberOfChildren(prisonerNumber: String, errorStatus: HttpStatus) {
    dpsContactPersonServer.stubFor(
      get("/sync/$prisonerNumber/number-of-children")
        .willReturn(
          aResponse()
            .withStatus(errorStatus.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
        ),
    )
  }
}
