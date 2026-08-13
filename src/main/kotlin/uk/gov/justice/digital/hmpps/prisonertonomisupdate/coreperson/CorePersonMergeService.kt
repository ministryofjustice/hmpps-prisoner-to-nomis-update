package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList

@Service
class CorePersonMergeService(
  private val telemetryClient: TelemetryClient,
  private val religionService: ReligionService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun mergePerson(event: MergePersonEvent) {
    val toPrisonNumber = event.prisonNumber()
    if (toPrisonNumber == null) {
      telemetryClient.trackEvent(
        "cpr-person-merged-no-prison-number",
        event.personReferenceTo.identifiers.associate { it.type to it.value },
      )
      return
    }
    val telemetryMap = mutableMapOf(
      "prisonNumber" to toPrisonNumber,
    )

    religionService.mergeReligions(toPrisonNumber)

    telemetryClient.trackEvent("cpr-person-merged-success", telemetryMap)
  }

  private fun MergePersonEvent.prisonNumber() = personReferenceTo.identifiers.firstOrNull { it.type == "prisonNumber" }?.value

  data class MergePersonEvent(
    val eventType: String,
    val personReferenceTo: PersonReferenceList,
  )
}
