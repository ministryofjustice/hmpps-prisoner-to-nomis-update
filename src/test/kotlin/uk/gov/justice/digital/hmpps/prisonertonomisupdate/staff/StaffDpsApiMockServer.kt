package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.StaffDpsApiExtension.Companion.dpsStaffServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserRole
import java.time.LocalDateTime
import java.util.UUID

class StaffDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsStaffServer = StaffDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsStaffServer.start()
    jsonMapper = SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsStaffServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsStaffServer.stop()
  }
}

class StaffDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    const val WIREMOCK_PORT = 8106
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

  fun stubGetStaff(
    nomisStaffId: Long = 1234,
    response: PrisonUserReconciliationResponse? = dpsStaffDetails(nomisStaffId),
  ) {
    if (response == null) {
      dpsStaffServer.stubFor(
        get(urlPathEqualTo("/reconciliation/user/$nomisStaffId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.NOT_FOUND.value())
              .withBody(
                StaffDpsApiExtension.jsonMapper.writeValueAsString(ErrorResponse(status = 404)),
              ),
          ),
      )
    } else {
      dpsStaffServer.stubFor(
        get(urlPathEqualTo("/reconciliation/user/$nomisStaffId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.OK.value())
              .withBody(
                StaffDpsApiExtension.jsonMapper.writeValueAsString(response),
              ),
          ),
      )
    }
  }

  /*
  TODO - check if we need this
  fun stubGetStaffIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
    totalElements: Long = content.size.toLong(),
    content: List<DpsStaffId>,
  ) {
    dpsStaffServer.stubFor(
      get(urlPathEqualTo("/prison-users/staffIds"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              OfficialVisitsDpsApiExtension.jsonMapper.writeValueAsString(
                pagedModelDpsStaffIds(
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
   */
}

fun dpsStaffDetails(nomisStaffId: Long = 1234) = PrisonUserReconciliationResponse(
  userId = UUID.randomUUID(),
  staffId = nomisStaffId.toString(),
  emails = listOf(
    PrisonUserEmail(
      emailId = 3456,
      email = "john.smith@justice.gov.uk",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM",
      modifiedTimestamp = LocalDateTime.parse("2021-09-12T10:42:43"),
      modifiedBy = "FRED_BROWN",
      isPrimary = "true",
      primary = true,
    ),
  ),
  firstName = "JOHN",
  lastName = "SMITH",
  status = PrisonUserReconciliationResponse.Status.ACTIVE,
  accounts = listOf(
    PrisonUserAccount(
      username = "JOHNSMITH_ADM",
      accountType = PrisonUserAccount.AccountType.ADMIN,
      accountStatus = PrisonUserAccount.AccountStatus.OPEN,
      activeCaseloadId = "MDI",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM2",
      modifiedTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      modifiedBy = "FRED_BROWN2",
      lastLoggedIn = LocalDateTime.parse("2026-03-17T12:30:00"),
      roles = listOf(
        PrisonUserRole(
          roleCode = "DPS_CODE_1",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM3",
        ),
        PrisonUserRole(
          roleCode = "DPS_CODE_2",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM3",
        ),
      ),
      caseloads = listOf(
        PrisonUserCaseload(
          caseloadId = "MDI",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM4",
        ),
        PrisonUserCaseload(
          caseloadId = "LEI",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM4",
        ),
        PrisonUserCaseload(
          caseloadId = "NWEB",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM4",
        ),
      ),
    ),
  ),
  createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
  createdBy = "JIM_BEAM2",
  modifiedTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
  modifiedBy = "FRED_BROWN2",
)
