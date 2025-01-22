package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDate

@Service
class CourtSentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CourtSentencingApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: CourtCaseMappingService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckCaseDps(dpsCaseId: String): MismatchCaseResponse =
    mappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCaseId).let {
      MismatchCaseResponse(mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId))
    }

  suspend fun manualCheckCaseNomis(nomisCaseId: Long): MismatchCaseResponse =
    mappingService.getMappingGivenNomisCourtCaseId(nomisCourtCaseId = nomisCaseId).let {
      MismatchCaseResponse(mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId))
    }

  suspend fun checkCase(dpsCaseId: String, nomisCaseId: Long): MismatchCase? =
    runCatching {
      val nomisResponse = doApiCallWithRetries { nomisApiService.getCourtCaseForMigration(nomisCaseId) }
      val dpsResponse = doApiCallWithRetries { dpsApiService.getCourtCase(dpsCaseId).toNomisCourtCase() }

      val dpsFields = CaseFields(
        startDate = dpsResponse.startDate,
        active = dpsResponse.status == "A",
        id = dpsCaseId,
      )
      val nomisFields = CaseFields(
        startDate = nomisResponse.beginDate,
        active = nomisResponse.caseStatus.code == "A",
        id = nomisResponse.id.toString(),
      )
      if (!dpsFields.equals(nomisFields)) {
        return MismatchCase(
          nomisCase = nomisFields,
          dpsCase = dpsFields,
        )
      } else {
        return null
      }
    }.onFailure {
      log.error("Unable to match case with ids: dps:$dpsCaseId and nomis:$nomisCaseId", it)
    }.getOrNull()
}

data class MismatchCaseResponse(
  val mismatch: MismatchCase?,
)

data class CaseFields(
  val startDate: LocalDate?,
  val active: Boolean,
  val id: String,
  // court is not at case level in DPS so could go out of sync on nomis
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as CaseFields
    return startDate == other.startDate &&
      active == other.active
  }
}

data class MismatchCase(
  val nomisCase: CaseFields,
  val dpsCase: CaseFields,
)
