package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentAttendeeSearchResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AppointmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class AppointmentsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val mappingService: AppointmentMappingService,
  private val dpsApiService: AppointmentsApiService,
  @Value("\${reports.appointments.reconciliation.page-size}") private val pageSize: Int,
  @Value("\${reports.appointments.reconciliation.lookahead-days}") private val lookaheadDays: Long,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    // Ids that are in DPS only
    private val excludeDpsIds = (
      listOf<Long>(
        7901394, 7901700, 7901764, 7902088, 7902095, 7902325, 7902483, 7902525, 7902592, 7902608, 7902772, 7903118, 7903323, 7903462, 7903660, 7903908, 7904195, 7904209, 7904257, 7904528, 7904557, 7904561, 7904564, 7904576, 7905016, 7905092, 7905176, 7905185, 7905866, 7905930, 7910332, 7910359, 7910431, 7910440, 9118422, 9120100, 9121264, 9122895, 9122946, 9123734,
      ) + listOf<Long>(
        // CWI CHAP 31 Dec - 28 Jan
        7911068, 7906220, 7906234, 7910446, 7904390, 7900132, 7898266, 7904571, 7906221, 7905304, 7901850, 7904382, 7904207, 7901773, 7901539, 7899852, 7899834, 7905110, 7903421, 7903536, 7905922, 7900505, 7905334, 7903829, 7900011, 7904669, 7900888, 7903559, 7901855, 7903672, 7901236, 7903202, 7900053, 7900678, 7903774, 7902509, 7904287, 7902090, 7898045, 7905325, 7905085, 7899717, 7905508, 7898267, 7900374, 7903991, 7903461, 7901868, 7905023, 7901317, 7903285, 7901504, 7903913, 7904212, 7902900, 7905551, 7904808, 7906146, 7900838, 7905130, 7900208, 7903902, 7904490, 7900580, 7903490, 7901389, 7901150, 7904604, 7899683,
      )
      ).toSet()

    // Ids that are in Nomis only
    private val excludeNomisIds = setOf<Long>(676842281, 681900464) // occur on 10th and 4th dec respectively
  }

  suspend fun generateReconciliationReportBatch() {
    telemetryClient.trackEvent("appointments-reports-reconciliation-requested")
    log.info("Appointments reconciliation report requested")

    runCatching { generateReconciliationReport() }
      .onSuccess { fullResults ->
        log.info("Appointments reconciliation report completed with ${fullResults.size} mismatches")
        val results = fullResults.take(10) // Only log the first 10 to avoid an insights error with too much data
        val map = mapOf("mismatch-count" to fullResults.size.toString()) +
          results.associate { "${it.nomisId},${it.dpsId}" to "nomis=${it.nomisAppointment}, dps=${it.dpsAppointment}" }
        telemetryClient.trackEvent("appointments-reports-reconciliation-success", map)
        log.info("Appointments reconciliation report logged")
      }
      .onFailure {
        telemetryClient.trackEvent("appointments-reports-reconciliation-failed")
        log.error("Appointments reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(): List<MismatchAppointment> {
    val yesterday = LocalDate.now().minusDays(1)
    val horizonDate = LocalDate.now().plusDays(lookaheadDays)
    return dpsApiService.getRolloutPrisons()
      .filter { it.appointmentsRolledOut }
      .flatMap {
        val currentPrisonId = it.prisonCode
        val appointmentsCount = nomisApiService.getAppointmentIds(listOf(currentPrisonId), yesterday, horizonDate, 0, 1).totalElements.toInt()
        log.info("--------- Scanning prison {} with appointmentsCount = {}", currentPrisonId, appointmentsCount)
        generateReconciliationReportForPrison(currentPrisonId, yesterday, horizonDate, appointmentsCount)
      }
//    val currentPrisonId = "ISI"
//    val appointmentsCount = nomisApiService.getAppointmentIds(listOf(currentPrisonId), yesterday, horizonDate, 0, 1).totalElements.toInt()
//    return generateReconciliationReportForPrison(currentPrisonId, yesterday, horizonDate, appointmentsCount)
  }

  suspend fun generateReconciliationReportForPrison(prisonId: String, startDate: LocalDate, endDate: LocalDate, nomisTotal: Int): List<MismatchAppointment> {
    val allDpsIdsInNomisPrison = HashSet<Long>(nomisTotal)
    var nomisExcludedCount = 0
    if (nomisTotal == 0) {
      return checkForMissingDpsRecords(allDpsIdsInNomisPrison, prisonId, startDate, endDate, nomisTotal)
    }
    val results = nomisTotal.asPages(pageSize).flatMap { page ->
      val appointmentMappings = if (page.first > -1) {
        getNomisAppointmentsForPage(prisonId, startDate, endDate, page)
      } else {
        emptyList() // HACK - add items here
      }
        .mapNotNull { appointmentIdResponse ->
          val nomisId = appointmentIdResponse.eventId

          if (excludeNomisIds.contains(nomisId)) {
            log.info("Ignoring excluded nomisId: $nomisId")
            nomisExcludedCount++
            null
          } else {
            runCatching {
              mappingService.getMappingGivenNomisIdOrNull(nomisId)
                ?.also { mappingDto ->
                  allDpsIdsInNomisPrison.add(mappingDto.appointmentInstanceId)
                }
                ?: run {
                  val nomisDetails = nomisApiService.getAppointment(nomisId)

                  checkForNomisDuplicateAndDelete(nomisDetails)

                  log.info("No mapping found for appointment $nomisId, prisoner ${nomisDetails.offenderNo}")
                  telemetryClient.trackEvent(
                    "appointments-reports-reconciliation-mismatch-missing-mapping",
                    mapOf("nomisId" to nomisId.toString(), "prisonId" to prisonId, "offenderNo" to nomisDetails.offenderNo),
                  )
                  null
                }
            }.onFailure {
              telemetryClient.trackEvent(
                "appointments-reports-reconciliation-retrieval-error",
                mapOf("nomis-appointment-id" to nomisId.toString(), "prisonId" to prisonId, "error" to (it.message ?: "")),
              )
              log.error("Unexpected error from api getting nomis appointment $nomisId", it)
            }.getOrNull()
          }
        }

      withContext(Dispatchers.Unconfined) {
        appointmentMappings.map { async { checkMatch(it) } }
      }.awaitAll().filterNotNull()
    }
    return results + checkForMissingDpsRecords(allDpsIdsInNomisPrison, prisonId, startDate, endDate, nomisTotal - nomisExcludedCount)
  }

  internal suspend fun checkForNomisDuplicateAndDelete(nomisDetails: AppointmentResponse) {
    if (nomisDetails.internalLocation == null || nomisDetails.startDateTime == null) {
      return
    }
    val possibleDuplicates: List<AppointmentResponse> = nomisApiService
      .getAppointmentsByFilter(nomisDetails.bookingId, nomisDetails.internalLocation, nomisDetails.startDateTime)

    if (possibleDuplicates.size == 2) {
      val d1 = possibleDuplicates.first()
      val d2 = possibleDuplicates.last()
      if (d1.subtype == d2.subtype) {
        nomisApiService.deleteAppointment(nomisDetails.eventId)
        telemetryClient.trackEvent(
          "appointments-reports-reconciliation-mismatch-deleted",
          mapOf(
            "nomisId" to nomisDetails.eventId.toString(),
            "offenderNo" to nomisDetails.offenderNo,
            "duplicate-of" to (if (d1.eventId == nomisDetails.eventId) d2.eventId else d1.eventId),
          ),
        )
      }
    }
  }

  internal suspend fun getNomisAppointmentsForPage(prisonId: String, startDate: LocalDate, endDate: LocalDate, page: Pair<Int, Int>) = runCatching {
    nomisApiService.getAppointmentIds(listOf(prisonId), startDate, endDate, page.first, page.second).content
  }
    .onFailure {
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-mismatch-page-error",
        mapOf("page" to page.first.toString(), "prisonId" to prisonId, "error" to (it.message ?: "")),
      )
      log.error("Unable to match entire Nomis page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Nomis Page requested: $page, with ${it.size} appointments") }

  internal suspend fun getDpsAppointmentsForPrison(prisonId: String, startDate: LocalDate, endDate: LocalDate): List<AppointmentAttendeeSearchResult> = runCatching {
    dpsApiService.searchAppointments(prisonId, startDate, endDate)
      .filterNot { app -> app.isDeleted }
      .flatMap { app -> app.attendees }
  }
    .onFailure {
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-mismatch-dps-error",
        mapOf("prisonId" to prisonId, "error" to (it.message ?: "")),
      )
      log.error("Unable to match entire DPS prison of prisoners: $prisonId", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("DPS prison requested: $prisonId, with ${it.size} appointment attendees") }

  internal suspend fun checkForMissingDpsRecords(
    allDpsIdsInNomisPrison: Set<Long>,
    prisonId: String,
    startDate: LocalDate,
    endDate: LocalDate,
    nomisTotal: Int,
  ): List<MismatchAppointment> = getDpsAppointmentsForPrison(
    prisonId,
    startDate,
    endDate,
  )
    .takeIf { it.size != nomisTotal || allDpsIdsInNomisPrison.size != nomisTotal }
    ?.also { appointmentAttendeeSearchResult ->
      val dpsIds = appointmentAttendeeSearchResult.map { it.appointmentAttendeeId }
      log.info("DPS stats are nomisTotal for prison = $nomisTotal, attendees = ${dpsIds.size}, allDpsIdsInNomisPrison size = ${allDpsIdsInNomisPrison.size}")
      log.info("${dpsIds.minus(allDpsIdsInNomisPrison)} are in attendees only; ${allDpsIdsInNomisPrison.minus(dpsIds.toSet())} are in allDpsIdsInNomisPrison only}")
    }
    ?.filterNot {
      allDpsIdsInNomisPrison.contains(it.appointmentAttendeeId)
    }
    ?.filterNot {
      excludeDpsIds.contains(it.appointmentAttendeeId)
        .also { b ->
          if (b) {
            log.info("Ignoring excluded dpsId: ${it.appointmentAttendeeId}")
          }
        }
    }
    ?.map { appointmentAttendeeSearchResult ->
      val appointmentAttendeeId = appointmentAttendeeSearchResult.appointmentAttendeeId
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-dps-only",
        mapOf("prisonId" to prisonId, "details" to appointmentAttendeeSearchResult),
      )
      MismatchAppointment(dpsId = appointmentAttendeeId)
    }
    ?: emptyList()

  internal suspend fun checkMatch(mapping: AppointmentMappingDto): MismatchAppointment? = runCatching {
    val (nomisRecord, dpsRecord) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAppointment(mapping.nomisEventId) } to
        async { dpsApiService.getAppointmentInstanceWithRetries(mapping.appointmentInstanceId) }
    }.awaitBoth()
    val startDateTime = nomisRecord.startDateTime
    if (startDateTime != null && startDateTime >= LocalDateTime.now().plusYears(1)) {
      log.info("Ignoring appointment with crazy typo date: $nomisRecord")
      return null
    }
    val verdict = doesNotMatch(nomisRecord, dpsRecord)
    return if (verdict != null) {
      val mismatch =
        MismatchAppointment(
          mapping.nomisEventId,
          dpsRecord.appointmentAttendeeId,
          AppointmentReportDetail(
            offenderNo = nomisRecord.offenderNo,
            bookingId = nomisRecord.bookingId,
            code = nomisRecord.subtype,
            startDateTime = startDateTime,
            endTime = nomisRecord.endDateTime?.toLocalTime(),
            internalLocationId = nomisRecord.internalLocation,
          ),
          AppointmentReportDetail(
            offenderNo = dpsRecord.prisonerNumber,
            bookingId = dpsRecord.bookingId,
            code = dpsRecord.categoryCode,
            startDateTime = dpsRecord.appointmentDate.atTime(parseOrNull(dpsRecord.startTime)),
            endTime = parseOrNull(dpsRecord.endTime),
            internalLocationId = dpsRecord.internalLocationId,
          ),
        )
      log.info("Appointment Mismatch found $verdict in:\n  $mismatch")
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-mismatch",
        mapOf(
          "nomisId" to mismatch.nomisId.toString(),
          "dpsId" to mismatch.dpsId.toString(),
          "prisonId" to nomisRecord.prisonId.toString(),
          "verdict" to verdict,
          "nomis" to (mismatch.nomisAppointment?.toString() ?: "null"),
          "dps" to (mismatch.dpsAppointment?.toString() ?: "null"),
        ),
      )
      mismatch
    } else {
      // log.info("Appointment matches: nomis ${mapping.nomisEventId} = dps ${mapping.appointmentInstanceId}")
      null
    }
  }.onSuccess {
    log.debug("Checking appointment (onSuccess: ${mapping.nomisEventId})")
  }.onFailure {
    log.error("Unable to match appointments for id: ${mapping.nomisEventId},${mapping.appointmentInstanceId}", it)
    telemetryClient.trackEvent(
      "appointments-reports-reconciliation-mismatch-error",
      mapOf(
        "nomisId" to mapping.nomisEventId.toString(),
        "dpsId" to mapping.appointmentInstanceId.toString(),
        "error" to (it.message ?: ""),
      ),
    )
  }.getOrNull()

  internal fun doesNotMatch(
    nomis: AppointmentResponse,
    dps: AppointmentInstance,
  ): String? {
    val nstart = nomis.startDateTime
    if (nomis.offenderNo != dps.prisonerNumber) return "offender mismatch"
    if (nomis.bookingId != dps.bookingId) return "bookingId mismatch"
    if (nomis.subtype != dps.categoryCode) return "subtype/code mismatch"
    if (nstart?.toLocalDate() != dps.appointmentDate) return "date mismatch"
    if (nstart.toLocalTime() != parseOrNull(dps.startTime)) return "start time mismatch: ${nstart.toLocalTime()} vs ${dps.startTime}"
    if (endTimeDoesNotMatch(nomis, dps)) return "end time mismatch: ${nomis.endDateTime?.toLocalTime()} vs ${dps.endTime}"
    if (!dps.inCell && nomis.internalLocation != dps.internalLocationId) return "location mismatch"
    if (dps.prisonCode != nomis.prisonId) return "prison mismatch"
    return null
  }

  /**
   * If Nomis end time is null, 1 hour length is assumed
   */
  private fun endTimeDoesNotMatch(
    nomis: AppointmentResponse,
    dps: AppointmentInstance,
  ): Boolean {
    val endDateTime = nomis.endDateTime
    return if (endDateTime == null) {
      dps.endTime != null && parseOrNull(dps.endTime) != nomis.startDateTime?.plusHours(1)?.toLocalTime()
    } else {
      endDateTime.toLocalTime() != parseOrNull(dps.endTime)
    }
  }
}

private fun parseOrNull(endTime: String?): LocalTime? = endTime?.let { LocalTime.parse(endTime) }

data class AppointmentReportDetail(
  val offenderNo: String,
  val bookingId: Long,
  val code: String,
  val startDateTime: LocalDateTime? = null,
  val endTime: LocalTime? = null,
  val internalLocationId: Long? = null,
)

data class MismatchAppointment(
  val nomisId: Long? = null,
  val dpsId: Long? = null,
  val nomisAppointment: AppointmentReportDetail? = null,
  val dpsAppointment: AppointmentReportDetail? = null,
)
