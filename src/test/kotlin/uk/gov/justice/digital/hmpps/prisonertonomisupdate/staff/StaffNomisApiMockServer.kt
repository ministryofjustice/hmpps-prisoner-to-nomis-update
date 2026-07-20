package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseloadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelStaffIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RoleResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffIdsPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class StaffNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun pagedModelStaffIdResponse(content: List<StaffIdResponse>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PagedModelStaffIdResponse = PagedModelStaffIdResponse(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )
  }

  fun stubGetStaffIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
    totalElements: Long = content.size.toLong(),
    content: List<StaffIdResponse>,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/staff/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              jsonMapper.writeValueAsString(
                pagedModelStaffIdResponse(
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

  fun stubGetStaffById(
    staffId: Long = 1234,
    response: StaffDetails = staffDetails(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/staff/id/$staffId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetStaffIdsFromId(
    content: List<StaffIdResponse>,
    staffId: Long = 0,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/staff/ids/all-from-id"))
        .withQueryParam("staffId", equalTo(staffId.toString())).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(StaffIdsPage(content))),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun staffDetails(staffId: Long = 1234) = StaffDetails(
  id = staffId,
  firstName = "JOHN",
  lastName = "SMITH",
  status = "ACTIVE",
  emailAddresses = listOf(
    StaffEmail(emailAddressId = 3456, email = "john.smith@justice.gov.uk", audit = audit()),
  ),
  audit = audit(),
  accounts = listOf(
    StaffAccount(
      username = "JOHNSMITH_ADM",
      sourceCode = "USER",
      status = "OPEN",
      typeCode = "ADMIN",
      activeCaseloadId = "MDI",
      lastLoggedIn = LocalDateTime.parse("2026-03-17T12:30:00"),
      caseloads = listOf(
        CaseloadResponse(
          caseloadId = "LEI",
          roles = emptyList(),
          audit = audit(),
        ),
        CaseloadResponse(caseloadId = "MDI", roles = emptyList(), audit = audit()),
        CaseloadResponse(
          caseloadId = "NWEB",
          roles = listOf(
            RoleResponse(code = "DPS_CODE_1", name = "Dps Role 1", audit = audit()),
            RoleResponse(code = "DPS_CODE_2", name = "Dps Role 2", audit = audit()),
          ),
          audit = audit(),
        ),
      ),
      audit = audit(),
    ),
  ),
)
fun audit() = NomisAudit(
  createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
  createUsername = "KOFEADDY",
  createDisplayName = "KOFE ADDY",
  modifyDatetime = LocalDateTime.parse("2017-08-01T10:55:00"),
  modifyUserId = "KOFE_MOD",
)
