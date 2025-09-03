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
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(): List<MismatchAppointment> {
    val yesterday = LocalDate.now().minusDays(1)
    val nextWeek = LocalDate.now().plusDays(7)
    return dpsApiService.getRolloutPrisons()
      .filter { it.appointmentsRolledOut }
      .flatMap {
        val currentPrisonId = it.prisonCode
        val appointmentsCount = nomisApiService.getAppointmentIds(listOf(currentPrisonId), yesterday, nextWeek, 0, 1).totalElements.toInt()
        log.info("--------- Scanning prison {} with appointmentsCount = {}", currentPrisonId, appointmentsCount)
        generateReconciliationReportForPrison(currentPrisonId, yesterday, nextWeek, appointmentsCount)
      }
//    val currentPrisonId = "ISI"
//    val appointmentsCount = nomisApiService.getAppointmentIds(listOf(currentPrisonId), yesterday, nextWeek, 0, 1).totalElements.toInt()
//    return  generateReconciliationReportForPrison(currentPrisonId, yesterday, nextWeek, appointmentsCount)
  }

  // -------------------------------------------- Locations-style :

  suspend fun generateReconciliationReportForPrison(prisonId: String, startDate: LocalDate, endDate: LocalDate, nomisTotal: Int): List<MismatchAppointment> {
    val allDpsIdsInNomisPrison = HashSet<Long>(nomisTotal)
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
          runCatching {
            mappingService.getMappingGivenNomisIdOrNull(nomisId)
              ?.also { mappingDto ->
                allDpsIdsInNomisPrison.add(mappingDto.appointmentInstanceId)
              }
              ?: run {
                val nomisDetails = nomisApiService.getAppointment(nomisId)

                log.info("No mapping found for appointment $nomisId, prisoner ${nomisDetails.offenderNo}")
                telemetryClient.trackEvent(
                  "appointments-reports-reconciliation-mismatch-missing-mapping",
                  mapOf("appointmentId" to nomisId.toString()),
                )
                null
              }
          }.onFailure {
            telemetryClient.trackEvent(
              "appointments-reports-reconciliation-retrieval-error",
              mapOf("nomis-appointment-id" to nomisId.toString()),
            )
            log.error("Unexpected error from api getting nomis appointment $nomisId", it)
          }.getOrNull()
        }

      withContext(Dispatchers.Unconfined) {
        appointmentMappings.map { async { checkMatch(it) } }
      }.awaitAll().filterNotNull()
    }
    return results + checkForMissingDpsRecords(allDpsIdsInNomisPrison, prisonId, startDate, endDate, nomisTotal)
  }

  internal suspend fun getNomisAppointmentsForPage(prisonId: String, startDate: LocalDate, endDate: LocalDate, page: Pair<Int, Int>) = runCatching {
    nomisApiService.getAppointmentIds(listOf(prisonId), startDate, endDate, page.first, page.second).content
  }
    .onFailure {
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-mismatch-page-error",
        mapOf("page" to page.first.toString()),
      )
      log.error("Unable to match entire Nomis page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Nomis Page requested: $page, with ${it.size} appointments") }

  internal suspend fun getDpsAppointmentsForPrison(prisonId: String, startDate: LocalDate, endDate: LocalDate): List<Long> = runCatching {
    dpsApiService.searchAppointments(prisonId, startDate, endDate)
      .flatMap { app ->
        app.attendees.map { attendee ->
          attendee.appointmentAttendeeId
        }
      }
  }
    .onFailure {
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-mismatch-page-error",
        mapOf("prisonId" to prisonId),
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
    ?.also {
      log.info("DPS stats are nomisTotal for prison = $nomisTotal, attendees = ${it.size}, allDpsIdsInNomisPrison size = ${allDpsIdsInNomisPrison.size}")
      log.info("${it.minus(allDpsIdsInNomisPrison)} are in attendees only; ${allDpsIdsInNomisPrison.minus(it.toSet())} are in allDpsIdsInNomisPrison only}")
    }
    ?.filterNot {
      allDpsIdsInNomisPrison.contains((it))
    }
    ?.map { appointmentAttendeeId ->
      val mismatch = MismatchAppointment(dpsId = appointmentAttendeeId)
      log.info("Appointment Mismatch found extra DPS appointment $appointmentAttendeeId")
      telemetryClient.trackEvent(
        "appointments-reports-reconciliation-dps-only",
        mapOf(
          "dpsId" to appointmentAttendeeId,
          "dps" to mismatch.dpsAppointment.toString(),
        ),
      )
      mismatch
    }
    ?: emptyList()

  internal suspend fun checkMatch(mapping: AppointmentMappingDto): MismatchAppointment? = runCatching {
    val (nomisRecord, dpsRecord) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAppointment(mapping.nomisEventId) } to
        async { dpsApiService.getAppointmentInstance(mapping.appointmentInstanceId) }
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
      ),
    )
  }.getOrNull()

  internal fun doesNotMatch(
    nomis: AppointmentResponse,
    dps: AppointmentInstance,
  ): String? {
    val nstart = nomis.startDateTime
    if (nomis.offenderNo != dps.prisonerNumber) return "Offender mismatch"
    if (nomis.bookingId != dps.bookingId) return "bookingId mismatch"
    if (nomis.subtype != dps.categoryCode) return "subtype/code mismatch"
    if (nstart?.toLocalDate() != dps.appointmentDate) return "date mismatch"
    if (nstart.toLocalTime() != parseOrNull(dps.startTime)) return "start time mismatch: ${nstart.toLocalTime()} vs ${dps.startTime}"
    if (endTimeDoesNotMatch(nomis, dps)) return "end time mismatch: ${nomis.endDateTime?.toLocalTime()} vs ${dps.endTime}"
    if (!dps.inCell && nomis.internalLocation != dps.internalLocationId) return "location mismatch"
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

// ---------------------------------------------------------- thread-style :

/*suspend fun generateReconciliationReport()
  : ReconciliationResult<MismatchPrisonerAppointmentsResponse> = generateReconciliationReport(
  threadCount = pageSize,
  checkMatch = ::checkAppointmentsMatch,
  nextPage = ::getIdsForPage,
)

data class MismatchAppointments(
  // val offenderNo: String,
  val dpsCount: Int,
  val nomisCount: Int,
)

suspend fun checkAppointment(dpsAppointmentId: Long, nomisAppointmentId: Long): MismatchAppointment? = runCatching {
  val (nomisResponse, dpsResponse) = withContext(Dispatchers.Unconfined) {
    async { nomisApiService.getAppointment(nomisAppointmentId) } to
      async { dpsApiService.getAppointmentInstance(dpsAppointmentId) }
  }.awaitBoth()
  val nomisFields: AppointmentFields = AppointmentFields()
  val dpsFields: AppointmentFields = AppointmentFields()
  val differenceList = compareObjects(dpsFields, nomisFields)

  if (differenceList.isNotEmpty()) {
    // log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
    return MismatchAppointment(
      nomis = nomisFields,
      dps = dpsFields,
      differences = differenceList,
    )
  } else {
    return null
  }
}.onFailure {
  log.error("Unable to match case with ids: dps:$dpsAppointmentId and nomis:$nomisAppointmentId", it)
}.getOrNull()

suspend fun checkAppointments(
  nomisLists: PrisonerAppointmentLists,
  dpsLists: List<DpsAppointment>,
): List<MismatchAppointmentResponse> =
  nomisLists.offenderAppointments
    .map {
      val dpsId = dpsLists.find { }

      MismatchAppointmentResponse(
        offenderNo = "xxx",
        nomisAppointmentId = it.appointmentId,
        dpsAppointmentId = dpsId,
        mismatch = checkAppointment(dpsAppointmentId = dpsId, nomisAppointmentId = it.appointmentId),
      )
    }


suspend fun manualCheckCaseOffenderNo(offenderNo: String): List<MismatchAppointmentResponse> = checkAppointments(
  nomisLists = nomisApiService.getGLAppointmentsForPrisoner(offenderNo),
  dpsLists = dpsApiService.getPrisonerAppointments(offenderNo),
)

suspend fun checkAppointmentsMatch(prisonerId: PrisonerIds): MismatchPrisonerAppointmentsResponse? = runCatching {
  manualCheckCaseOffenderNo(prisonerId.offenderNo)
    .filter { it.mismatch != null }
    .takeIf { it.isNotEmpty() }?.let {
      MismatchPrisonerAppointmentsResponse(
        offenderNo = prisonerId.offenderNo,
        mismatches = it,
      )
    }?.also {
      it.mismatches.forEach { mismatch ->
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-mismatch",
          mapOf(
            "offenderNo" to it.offenderNo,
            "dpsAppointmentId" to mismatch.dpsAppointmentId.toString(),
            "nomisAppointmentId" to mismatch.nomisAppointmentId.toString(),
            "mismatchCount" to mismatch.mismatch!!.differences.size.toString(),
          ),
          null,
        )
      }
    }
}.onFailure {
  log.error("Unable to match prisoner: ${id.offenderNo}", it)
  telemetryClient.trackEvent(
    "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-error",
    mapOf(
      "offenderNo" to id.offenderNo,
      "reason" to (it.message ?: "unknown"),
    ),
    null,
  )
}.getOrNull()

private suspend fun getIdsForPage(pageNumber: Int): ReconciliationPageResult<AppointmentIdResponse> = runCatching {
  nomisApiService.getAppointmentIds(
    prisonIds = xxx,
    fromDate = xxx,
    toDate = null,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )
}.onFailure {
  telemetryClient.trackEvent(
    "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-mismatch-page-error",
    mapOf(
      "booking" to lastBookingId.toString(),
    ),
  )
  log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
}
  .map {
    ReconciliationSuccessPageResult(
      ids = it.prisonerIds,
      last = it.lastBookingId,
    )
  }
  .getOrElse { ReconciliationErrorPageResult(it) }
  .also { log.info("Page requested from booking: $lastBookingId, with $prisonerPageSize bookings") }
}
data class AppointmentFields(
val active: Boolean,
val id: String,
val glReferences: List<String> = emptyList(),
) {
override fun equals(other: Any?): Boolean {
  if (this === other) return true
  other as CaseFields
  return active == other.active
}
}

data class MismatchAppointment(
val nomis: AppointmentFields,
val dps: AppointmentFields,
val differences: List<Difference> = emptyList(),
)
data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)

data class MismatchPrisonerAppointmentsResponse(
val offenderNo: String,
val mismatches: List<MismatchAppointmentResponse>,
)

data class MismatchAppointmentResponse(
val offenderNo: String = "TODO",
val dpsAppointmentId: UUID,
val nomisAppointmentId: Long,
val mismatch: MismatchAppointment?,
)
 */
