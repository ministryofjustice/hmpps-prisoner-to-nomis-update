package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId

class OfficialVisitsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOfficialVisitsServer = OfficialVisitsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsOfficialVisitsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.stop()
  }
}

class OfficialVisitsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8104
    fun pagedModelSyncOfficialVisitIdResponse(content: List<SyncOfficialVisitId>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PagedModelSyncOfficialVisitIdResponse = PagedModelSyncOfficialVisitIdResponse(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )
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

  fun stubGetOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
    totalElements: Long = content.size.toLong(),
    content: List<SyncOfficialVisitId>,
  ) {
    dpsOfficialVisitsServer.stubFor(
      get(urlPathEqualTo("/reconcile/official-visits/identifiers"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              OfficialVisitsDpsApiExtension.objectMapper.writeValueAsString(
                pagedModelSyncOfficialVisitIdResponse(
                  content,
                  pageSize = pageSize,
                  pageNumber = pageNumber,
                  totalElements = totalElements,
                ),
              ),
            ),
        ),
    )
  }
}
