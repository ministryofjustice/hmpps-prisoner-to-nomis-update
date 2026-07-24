package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import java.time.LocalDateTime

const val DPS_CASELOAD = "NWEB"

// TODO there is no source value (how row was updated - user/seq) - check if DPS want this
data class StaffSummary(
  val firstName: String,
  val lastName: String,
  val status: String,
  val accounts: List<StaffAccountSummary>,
  val emails: List<String>,
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
  emails = emailAddresses.map { it.email },
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

// DPS conversions
fun PrisonUserReconciliationResponse.toStaff() = StaffSummary(
  firstName = firstName,
  lastName = lastName,
  status = status.mapDps(),
  accounts = accounts.map { it.toStaffAccountSummary() }.sortedBy { it.username },
  emails = emails.map { it.email },
)
fun PrisonUserAccount.toStaffAccountSummary() = StaffAccountSummary(
  username = username,
  typeCode = accountType.value,
  status = accountStatus.mapDps(),
  caseloads = caseloads.map { it.caseloadId }.sorted(),
  dpsRoles = roles.map { it.roleCode }.sorted(),
  lastLoggedIn = lastLoggedIn,
  activeCaseloadId = activeCaseloadId,
)
fun PrisonUserReconciliationResponse.Status.mapDps(): String = when (this) {
  PrisonUserReconciliationResponse.Status.ACTIVE -> "ACTIVE"
  PrisonUserReconciliationResponse.Status.INACTIVE -> "INACT"
}
fun PrisonUserAccount.AccountStatus.mapDps(): String = when (this) {
  PrisonUserAccount.AccountStatus.OPEN -> return "OPEN"
  PrisonUserAccount.AccountStatus.EXPIRED -> "EXPIRED"
  PrisonUserAccount.AccountStatus.EXPIRED_GRACE -> "EXPIRED(GRACE)"
  PrisonUserAccount.AccountStatus.EXPIRED_LOCKED -> "EXPIRED & LOCKED"
  PrisonUserAccount.AccountStatus.EXPIRED_LOCKED_TIMED -> "EXPIRED & LOCKED(TIMED)"
  PrisonUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED -> "EXPIRED(GRACE) & LOCKED"
  PrisonUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED_TIMED -> "EXPIRED(GRACE) & LOCKED(TIMED)"
  PrisonUserAccount.AccountStatus.LOCKED -> "LOCKED"
  PrisonUserAccount.AccountStatus.LOCKED_TIMED -> return "LOCKED(TIMED)"
}
