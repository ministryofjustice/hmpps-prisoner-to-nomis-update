package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUser
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.MigratedUserAccount
import java.time.LocalDateTime

const val DPS_CASELOAD = "NWEB"

// TODO there is no source value (how row was updated - user/seq) - check if DPS want this
data class StaffSummary(
  val firstName: String,
  val lastName: String,
  val status: String,
  val accounts: List<StaffAccountSummary>,
  val email: String?,
)
data class StaffAccountSummary(
  val username: String,
  val typeCode: String,
  val status: String,
  val caseloads: List<String>,
  val dpsRoles: List<String>,
  val lastLoggedIn: LocalDateTime?,
  val activeCaseloadId: String?,
)

// Nomis conversions
fun StaffDetails.toStaff() = StaffSummary(
  firstName = firstName,
  lastName = lastName,
  status = status,
  accounts = accounts.map { it.toStaffAccountSummary() }.sortedBy { it.username },
  email = null,
)
fun StaffAccount.toStaffAccountSummary() = StaffAccountSummary(
  username = username,
  typeCode = typeCode,
  status = status,
  caseloads = caseloads.map { it.caseloadId }.sorted(),
  dpsRoles = caseloads.firstOrNull { it.caseloadId == DPS_CASELOAD }?.roles?.map { it.code }?.sorted().orEmpty(),
  lastLoggedIn = lastLoggedIn,
  activeCaseloadId = activeCaseloadId,
)

// TODO DPS conversions - waiting for DPS endpoint
fun DpsStaffDetails.toStaff() = StaffSummary(
  firstName = user.firstName,
  lastName = user.lastName,
  status = user.status.mapDps(),
  accounts = accounts.map { it.toStaffAccountSummary(this) }.sortedBy { it.username },
  email = null,
)
fun MigratedUserAccount.toStaffAccountSummary(dpsStaffDetails: DpsStaffDetails) = StaffAccountSummary(
  username = username,
  typeCode = accountType.value,
  status = accountStatus.mapDps(),
  caseloads = dpsStaffDetails.accessibleCaseloads
    ?.filter { staff -> staff.username == username }
    ?.map { it.caseloadId }?.sorted() ?: emptyList(),
  dpsRoles = dpsStaffDetails.roles?.map { it.roleCode }?.sorted() ?: emptyList(),
  lastLoggedIn = lastLoggedIn,
  activeCaseloadId = activeCaseloadId,
)
fun MigratedUser.Status.mapDps(): String = when (this) {
  MigratedUser.Status.ACTIVE -> "ACTIVE"
  MigratedUser.Status.INACTIVE -> "INACT"
}
fun MigratedUserAccount.AccountStatus.mapDps(): String = when (this) {
  MigratedUserAccount.AccountStatus.OPEN -> return "OPEN"
  MigratedUserAccount.AccountStatus.EXPIRED -> "EXPIRED"
  MigratedUserAccount.AccountStatus.EXPIRED_GRACE -> "EXPIRED(GRACE)"
  MigratedUserAccount.AccountStatus.EXPIRED_LOCKED -> "EXPIRED & LOCKED"
  MigratedUserAccount.AccountStatus.EXPIRED_LOCKED_TIMED -> "EXPIRED & LOCKED(TIMED)"
  MigratedUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED -> "EXPIRED(GRACE) & LOCKED"
  MigratedUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED_TIMED -> "EXPIRED(GRACE) & LOCKED(TIMED)"
  MigratedUserAccount.AccountStatus.LOCKED -> "LOCKED"
  MigratedUserAccount.AccountStatus.LOCKED_TIMED -> return "LOCKED(TIMED)"
}
