package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPRequest

fun CsipRecord.toNomisUpsertRequest(nomisId: Long? = null) =
  UpsertCSIPRequest(
    id = nomisId,
    offenderNo = prisonNumber,
    incidentDate = referral.incidentDate,
    incidentTime = referral.incidentTime,
    typeCode = referral.incidentType.code,
    locationCode = referral.incidentLocation.code,
    areaOfWorkCode = referral.refererArea.code,
    reportedBy = referral.referredBy,
    reportedDate = referral.referralDate,
    proActiveReferral = referral.isProactiveReferral ?: false,
    staffAssaulted = referral.isStaffAssaulted ?: false,
    createUsername = createdBy,
    logNumber = logCode,
    staffAssaultedName = referral.assaultedStaffName,
  )
