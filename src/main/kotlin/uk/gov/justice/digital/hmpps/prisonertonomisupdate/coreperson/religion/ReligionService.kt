package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.*

@Service
class ReligionService(private val telemetryClient: TelemetryClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun religionCreated(event: ReligionEvent) {
    val prisonNumber =
      event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprReligionId = event.additionalInformation.cprReligionId
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprReligionId" to cprReligionId,
    )
    // TODO create religion in nomis
    telemetryClient.trackEvent("religion-created-success", telemetryMap)
  }

  data class ReligionEvent(
    val eventType: String,
    val additionalInformation: CprReligionCreatedInfo,
    val personReference: PersonReferenceList,
  )
  data class CprReligionCreatedInfo(val cprReligionId: UUID)
}
