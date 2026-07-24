package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserRole
import java.time.LocalDateTime
import java.util.UUID

@RestController
@PreAuthorize("hasRole('ROLE_PRISON_USER_STAFF__SYNC__RW')")
class DummyStaffDpsApi {

  @GetMapping("/reconciliation/user/{staffId}")
  fun getDpsStaff(
    @Schema(description = "staff Id") @PathVariable staffId: Long,
  ) = dpsStaffDetails(staffId)
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
