package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest

fun Alert.toNomisCreateRequest(): CreateAlertRequest =
  CreateAlertRequest(
    alertCode = this.alertCode.code,
    // DPS will change this to not be Nullable
    date = this.activeFrom!!,
    isActive = this.isActive,
    createUsername = this.createdBy,
    expiryDate = this.activeTo,
    comment = this.description,
    authorisedBy = this.authorisedBy,
  )
