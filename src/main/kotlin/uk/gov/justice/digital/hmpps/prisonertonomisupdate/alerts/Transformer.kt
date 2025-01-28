package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertRequest

fun Alert.toNomisCreateRequest(): CreateAlertRequest = CreateAlertRequest(
  alertCode = this.alertCode.code,
  date = this.activeFrom,
  isActive = this.isActive,
  createUsername = this.createdBy,
  expiryDate = this.activeTo,
  comment = this.description,
  authorisedBy = this.authorisedBy,
)

fun Alert.toNomisUpdateRequest(): UpdateAlertRequest = UpdateAlertRequest(
  date = this.activeFrom,
  isActive = this.isActive,
  updateUsername = this.lastModifiedBy ?: this.createdBy,
  expiryDate = this.activeTo,
  comment = this.description,
  authorisedBy = this.authorisedBy,
)
