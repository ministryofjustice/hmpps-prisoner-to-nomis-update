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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.PagedModelSyncOfficialVisitId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.RelationshipType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import java.time.LocalDate
import java.util.*

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
    fun pagedModelSyncOfficialVisitIdResponse(content: List<SyncOfficialVisitId>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1) = PagedModelSyncOfficialVisitId(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )

    fun syncOfficialVisit() = SyncOfficialVisit(
      officialVisitId = 1,
      visitDate = LocalDate.parse("2020-01-01"),
      startTime = "10:00",
      endTime = "11:00",
      prisonVisitSlotId = 10,
      dpsLocationId = UUID.randomUUID(),
      prisonCode = "MDI",
      prisonerNumber = "A1234KT",
      statusCode = VisitStatusType.COMPLETED,
      visitors = listOf(syncOfficialVisitor()),
      completionCode = VisitCompletionType.NORMAL,
      offenderBookId = 20,
      offenderVisitId = 30,
    )

    fun syncOfficialVisitor() = SyncOfficialVisitor(
      officialVisitorId = 1,
      contactId = 100,
      firstName = "Ayomide",
      lastName = "Olawale",
      relationshipType = RelationshipType.OFFICIAL,
      relationshipCode = "POL",
      attendanceCode = AttendanceType.ATTENDED,
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
  fun stubGetOfficialVisit(
    officialVisitId: Long = 1,
    response: SyncOfficialVisit? = syncOfficialVisit(),
  ) {
    if (response == null) {
      dpsOfficialVisitsServer.stubFor(
        get(urlPathEqualTo("/reconcile/official-visit/id/$officialVisitId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.NOT_FOUND.value())
              .withBody(
                OfficialVisitsDpsApiExtension.objectMapper.writeValueAsString(ErrorResponse(status = 404)),
              ),
          ),
      )
    } else {
      dpsOfficialVisitsServer.stubFor(
        get(urlPathEqualTo("/reconcile/official-visit/id/$officialVisitId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.OK.value())
              .withBody(
                OfficialVisitsDpsApiExtension.objectMapper.writeValueAsString(response),
              ),
          ),
      )
    }
  }

  fun stubGetOfficialVisitsForPrisoner(offenderNo: String, response: List<SyncOfficialVisit> = emptyList()) {
    dpsOfficialVisitsServer.stubFor(
      get(urlPathEqualTo("/reconcile/prisoner/$offenderNo")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(OfficialVisitsDpsApiExtension.objectMapper.writeValueAsString(response)),
      ),
    )
  }
}
