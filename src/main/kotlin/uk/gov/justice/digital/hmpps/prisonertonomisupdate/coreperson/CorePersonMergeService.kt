package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.*

@Service
class CorePersonMergeService(
  private val telemetryClient: TelemetryClient,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val mapping: ReligionMappingApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun mergePerson(event: MergePersonEvent) {
    val prisonNumber =
      event.personReferenceTo.identifiers.first { it.type == "prisonNumber" }.value
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
    )
    // TODO pull back the religion history from cpr and write it back to nomis
    telemetryClient.trackEvent("person-merged-success", telemetryMap)
  }

  data class MergePersonEvent(
    val eventType: String,
    val personReferenceTo: PersonReferenceList,
  )
}
