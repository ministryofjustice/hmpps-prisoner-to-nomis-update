package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class CorePersonReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val cprCorePersonApiService: CorePersonCprApiService,
  private val nomisCorePersonApiService: CorePersonNomisApiService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val TELEMETRY_CORE_PERSON_PREFIX = "coreperson-reports-reconciliation"
  }

  private fun List<MismatchCorePerson>.asPrisonerMap(): Map<String, String> = this.associate { it.prisonNumber to "cprPerson=${it.cprCorePerson}, nomisPerson=${it.nomisCorePerson}" }

  suspend fun checkCorePersonMatch(prisonerId: PrisonerIds): MismatchCorePerson? = runCatching {
    val nomisCorePerson = doApiCallWithRetries { nomisCorePersonApiService.getPrisoner(prisonerId.offenderNo) }?.toPerson() ?: PrisonerPerson()
    val cprCorePerson = doApiCallWithRetries { cprCorePersonApiService.getCorePerson(prisonerId.offenderNo) }?.toPerson() ?: PrisonerPerson()

    return if (nomisCorePerson != cprCorePerson) {
      MismatchCorePerson(prisonNumber = prisonerId.offenderNo, cprCorePerson = cprCorePerson, nomisCorePerson = nomisCorePerson).also { mismatch ->
        log.info("CorePerson mismatch found {}", mismatch)
        val telemetry = telemetryOf(
          "prisonNumber" to mismatch.prisonNumber,
          "nomisCorePerson" to (mismatch.nomisCorePerson.nationality ?: "null"),
          "cprCorePerson" to (mismatch.cprCorePerson.nationality ?: "null"),
        )
        // booking will be 0 if reconciliation is run for a single prisoner, in which case ignore
        prisonerId.bookingId.takeIf { it != 0L }?.let { telemetry["bookingId"] = it }
        telemetryClient.trackEvent("$TELEMETRY_CORE_PERSON_PREFIX-mismatch", telemetry)
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match core person for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_CORE_PERSON_PREFIX-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  suspend fun checkCorePersonMatch(offenderNo: String): MismatchCorePerson? = checkCorePersonMatch(PrisonerIds(0, offenderNo))
}

fun CanonicalRecord.toPerson() = PrisonerPerson(nationality = nationalities.firstOrNull()?.code)
fun CorePerson.toPerson() = PrisonerPerson(nationality = this.nationalities?.firstOrNull()?.nationality?.code)

data class MismatchCorePerson(
  val prisonNumber: String,
  val cprCorePerson: PrisonerPerson,
  val nomisCorePerson: PrisonerPerson,
)

data class PrisonerPerson(
  val nationality: String? = null,
)
