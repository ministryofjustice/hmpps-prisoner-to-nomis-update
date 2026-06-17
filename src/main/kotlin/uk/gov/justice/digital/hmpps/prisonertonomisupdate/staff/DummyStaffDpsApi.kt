package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUser
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUserRole
import java.time.LocalDateTime

@RestController
@PreAuthorize("hasRole('ROLE_PRISON_USER_STAFF__SYNC__RW')")
class DummyStaffDpsApi {

  @GetMapping("/prison-users/staff/{staffId}")
  fun getDpsStaff(
    @Schema(description = "staff Id") @PathVariable staffId: Long,
  ) = dpsStaffDetails(staffId.toString())

  @GetMapping("/prison-users/staff/ids")
  fun getDpsStaffIds(page: Int = 0, size: Int = 1): PagedModelDpsStaffId = pagedModelDpsStaffIds(pageNumber = page, pageSize = size)
}

// TODO - this is just a copy of the migrateRequest class
fun dpsStaffDetails(staffId: String = "4321") = DpsStaffDetails(
  user = dpsStaffUser(staffId),
  accounts = listOf(
    MigratedUserAccount(
      username = "JOHNSMITH_ADM",
      accountType = MigratedUserAccount.AccountType.ADMIN,
      accountStatus = MigratedUserAccount.AccountStatus.OPEN,
      activeCaseloadId = "MDI",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM2",
      modifiedTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      modifiedBy = "FRED_BROWN2",
      lastLoggedIn = LocalDateTime.parse("2026-03-17T12:30:00"),
    ),
  ),
  roles = listOf(
    MigratedUserRole(
      username = "JOHNSMITH_ADM",
      roleCode = "DPS_CODE_1",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM3",
    ),
    MigratedUserRole(
      username = "JOHNSMITH_ADM",
      roleCode = "DPS_CODE_2",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM3",
    ),
  ),
  accessibleCaseloads = listOf(
    MigratedUserAccessibleCaseload(
      username = "JOHNSMITH_ADM",
      caseloadId = "MDI",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM4",
    ),
    MigratedUserAccessibleCaseload(
      username = "JOHNSMITH_ADM",
      caseloadId = "LEI",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM4",
    ),
    MigratedUserAccessibleCaseload(
      username = "JOHNSMITH_ADM",
      caseloadId = "NWEB",
      createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
      createdBy = "JIM_BEAM4",
    ),
  ),
)

fun dpsStaffUser(staffId: String = "4321") = DpsUser(
  id = staffId,
  email = "john.smith@justice.gov.uk",
  firstName = "JOHN",
  lastName = "SMITH",
  status = MigratedUser.Status.ACTIVE,
)

private val dpsStaffIds = listOf(DpsStaffId("4321"), DpsStaffId("4321"), DpsStaffId("4321"))
fun pagedModelDpsStaffIds(content: List<DpsStaffId> = dpsStaffIds, pageSize: Int = 20, pageNumber: Int = 1, totalElements: Long = content.size.toLong()) = PagedModelDpsStaffId(
  content = content,
  page = PageMetadata(
    propertySize = pageSize.toLong(),
    number = pageNumber.toLong(),
    totalElements = totalElements,
    totalPages = Math.ceilDiv(totalElements, pageSize),
  ),
)

// Temporary return type until API ready
data class DpsStaffDetails(
  val user: DpsUser,
  val accounts: List<MigratedUserAccount>,
  val roles: List<MigratedUserRole>? = null,
  val accessibleCaseloads: List<MigratedUserAccessibleCaseload>? = null,
)
data class DpsUser(
  val id: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val status: MigratedUser.Status,
)

data class PagedModelDpsStaffId(
  val content: List<DpsStaffId>? = null,
  val page: PageMetadata? = null,
)

data class DpsStaffId(
  val staffId: String,
)
