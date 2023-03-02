package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

abstract class MappingService<DPSID, MAPPING> {
  abstract suspend fun getMappingFromDPSId(dpsId: DPSID): MAPPING?
}
