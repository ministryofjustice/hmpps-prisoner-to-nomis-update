package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

abstract class DPSService<DPSID, DPSENTITY> {
  abstract suspend fun getEntityFromDPS(id: DPSID): DPSENTITY
}
