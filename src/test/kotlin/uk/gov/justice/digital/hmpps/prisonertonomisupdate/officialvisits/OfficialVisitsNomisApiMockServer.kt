package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactRelationship
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelVisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdsPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class OfficialVisitsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun pageVisitIdResponse(content: List<VisitIdResponse>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PagedModelVisitIdResponse = PagedModelVisitIdResponse(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )

    fun officialVisitResponse() = OfficialVisitResponse(
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
      visitId = 1,
      visitSlotId = 20,
      prisonId = "MDI",
      offenderNo = "A1234KT",
      bookingId = 30,
      currentTerm = true,
      startDateTime = LocalDateTime.parse("2020-01-01T10:00"),
      endDateTime = LocalDateTime.parse("2020-01-01T11:00"),
      internalLocationId = 40,
      visitStatus = CodeDescription("NORM", "Normal Completion"),
      visitOutcome = null,
      visitors = listOf(officialVisitor()),
    )

    fun officialVisitor() = OfficialVisitor(
      id = 123,
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
      personId = 100,
      firstName = "AYOMIDE",
      lastName = "OLAWALE",
      leadVisitor = true,
      assistedVisit = true,
      visitorAttendanceOutcome = CodeDescription("ATT", "Attended"),
      relationships = listOf(
        ContactRelationship(
          relationshipType = CodeDescription(code = "POL", description = "Police"),
          contactType = CodeDescription(code = "O", description = "Official"),
        ),
      ),
    )
  }
  fun stubGetOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
    totalElements: Long = content.size.toLong(),
    content: List<VisitIdResponse>,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(pageVisitIdResponse(content, pageSize = pageSize, pageNumber = pageNumber, totalElements = totalElements))),
        ),
    )
  }

  fun stubGetOfficialVisit(
    visitId: Long = 1234,
    response: OfficialVisitResponse = officialVisitResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/$visitId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetOfficialVisitOrNull(
    visitId: Long = 1234,
    response: OfficialVisitResponse? = null,
  ) {
    if (response == null) {
      nomisApi.stubFor(
        get(urlPathEqualTo("/official-visits/$visitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    } else {
      stubGetOfficialVisit(visitId, response)
    }
  }

  fun stubGetOfficialVisitIdsByLastId(
    content: List<VisitIdResponse>,
    visitId: Long = 0,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/ids/all-from-id")).withQueryParam("visitId", equalTo(visitId.toString())).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(VisitIdsPage(content))),
      ),
    )
  }

  fun stubGetOfficialVisitsForPrisoner(offenderNo: String, response: List<OfficialVisitResponse> = emptyList()) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoner/$offenderNo/official-visits")).willReturn(
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
