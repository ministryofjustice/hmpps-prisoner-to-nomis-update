package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import java.time.LocalDateTime

const val DPS_CASELOAD = "NWEB"

data class StaffSummary(
  val staffId: Long,
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
  staffId = id,
  firstName = firstName,
  lastName = lastName,
  status = if (status == "ACTIVE") "ACTIVE" else "INACT",
  accounts = accounts.map { it.toStaffAccountSummary() }.sortedBy { it.username },
  emails = emailAddresses.map { it.email },
)
fun StaffAccount.toStaffAccountSummary() = StaffAccountSummary(
  username = username,
  typeCode = typeCode,
  status = status,
  caseloads = caseloads.map { it.caseloadId },
  dpsRoles = caseloads.firstOrNull { it.caseloadId == DPS_CASELOAD }?.roles?.map { it.code }.orEmpty(),
  lastLoggedIn = lastLoggedIn,
  activeCaseloadId = activeCaseloadId,
)

// DPS conversions
fun PrisonUserReconciliationResponse.toStaff() = StaffSummary(
  staffId = staffId,
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
  caseloads = caseloads.map { it.caseloadId },
  dpsRoles = roles.map { it.roleCode },
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

fun appendStaffDifferences(nomis: StaffSummary, dps: StaffSummary, differences: MutableMap<String, String>) {
  appendDifference(nomis.firstName, dps.firstName, differences, "firstName")
  appendDifference(nomis.lastName, dps.lastName, differences, "lastName")
  appendDifference(nomis.status, dps.status, differences, "status")
  appendDifference(nomis.emails.toSet(), dps.emails.toSet(), differences, "emails")
  appendAccountsDifferences(nomis.accounts, dps.accounts, differences)
}

private fun <T> appendDifference(nomisField: T, dpsField: T, differences: MutableMap<String, String>, fieldName: String) {
  if (nomisField != dpsField) differences[fieldName] = "nomis=$nomisField, dps=$dpsField"
}

private fun appendAccountsDifferences(nomisAccounts: List<StaffAccountSummary>, dpsAccounts: List<StaffAccountSummary>, differences: MutableMap<String, String>) {
  if (nomisAccounts.size != dpsAccounts.size) {
    differences["accounts.size"] = "nomis=${nomisAccounts.size}, dps=${dpsAccounts.size}"
    return
  }

  nomisAccounts.mapIndexedNotNull { i, nomis ->
    val dps = dpsAccounts[i]
    when {
      nomis.username != dps.username -> differences["accounts[$i].username"] = "nomis=${nomis.username}, dps=${dps.username}"
      nomis.typeCode != dps.typeCode -> differences["accounts[$i].typeCode"] = "nomis=${nomis.typeCode}, dps=${dps.typeCode}"
      nomis.status != dps.status -> differences["accounts[$i].status"] = "nomis=${nomis.status}, dps=${dps.status}"
      nomis.caseloads.toSet() != dps.caseloads.toSet() -> differences["accounts[$i].caseloads"] = "nomis=${nomis.caseloads.sorted()}, dps=${dps.caseloads.sorted()}"
      nomis.dpsRoles.toSet() != dps.dpsRoles.toSet() -> differences["accounts[$i].dpsRoles"] = "nomis=${nomis.dpsRoles.sorted()}, dps=${dps.dpsRoles.sorted()}"
      nomis.lastLoggedIn != dps.lastLoggedIn -> differences["accounts[$i].lastLoggedIn"] = "nomis=${nomis.lastLoggedIn}, dps=${dps.lastLoggedIn}"
      nomis.activeCaseloadId != dps.activeCaseloadId -> differences["accounts[$i].activeCaseloadId"] = "nomis=${nomis.activeCaseloadId}, dps=${dps.activeCaseloadId}"
      else -> null
    }
  }
}
