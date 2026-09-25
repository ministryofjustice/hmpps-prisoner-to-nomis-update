package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList

@Service
class CorePersonMergeService(
  private val telemetryClient: TelemetryClient,
  private val religionService: ReligionService,
) {
  suspend fun mergePerson(event: MergePersonEvent) {
    val toPrisonNumber = event.prisonNumber()
    if (toPrisonNumber == null) {
      telemetryClient.trackEvent(
        "coreperson-person-merged-no-prison-number",
        event.personReference.identifiers.associate { it.type to it.value },
      )
      return
    }
    val telemetryMap = mutableMapOf(
      "prisonNumber" to toPrisonNumber,
    )

    religionService.mergeReligions(toPrisonNumber)

    telemetryClient.trackEvent("coreperson-person-merged-success", telemetryMap)
  }

  private fun MergePersonEvent.prisonNumber() = personReference.identifiers.firstOrNull { it.type == "toPrisonNumber" }?.value

  data class MergePersonEvent(
    val eventType: String,
    val personReference: PersonReferenceList,
  )
}
