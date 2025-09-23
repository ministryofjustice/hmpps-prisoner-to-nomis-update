package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.SchedulesService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments.AppointmentsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ADJUDICATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALERT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.APPOINTMENT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ATTENDANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CASE_NOTES_ACTIVE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CASE_NOTES_FULL_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CONTACT_PERSON_PROFILE_DETAILS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.COURT_CASE_PRISONER_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CSIP_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.DELETE_UNKNOWN_ACTIVITY_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.INCENTIVES_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PERSON_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_RESTRICTION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PURGE_ACTIVITY_DLQ
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.PrisonerRestrictionsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.lang.IllegalArgumentException
import java.time.LocalDate

enum class BatchType {
  ADJUDICATION_RECON,
  ALERT_RECON,
  ALLOCATION_RECON,
  APPOINTMENT_RECON,
  ATTENDANCE_RECON,
  CASE_NOTES_ACTIVE_RECON,
  CASE_NOTES_FULL_RECON,
  CONTACT_PERSON_PROFILE_DETAILS_RECON,
  CSIP_RECON,
  DELETE_UNKNOWN_ACTIVITY_MAPPINGS,
  INCENTIVES_RECON,
  PERSON_CONTACT_RECON,
  PRISONER_CONTACT_RECON,
  COURT_CASE_PRISONER_RECON,
  PRISONER_RESTRICTION_RECON,
  PURGE_ACTIVITY_DLQ,
  SUSPENDED_ALLOCATION_RECON,
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @Value($$"${batch.type}") private val batchType: BatchType,
  @Value($$"${hmpps.sqs.queues.activity.dlq.name}") private val activitiesDlqName: String,
  private val activitiesReconService: ActivitiesReconService,
  private val adjudicationsReconService: AdjudicationsReconciliationService,
  private val alertsReconciliationService: AlertsReconciliationService,
  private val appointmentsReconciliationService: AppointmentsReconciliationService,
  private val caseNotesReconciliationService: CaseNotesReconciliationService,
  private val contactPersonProfileDetailsReconService: ContactPersonProfileDetailsReconciliationService,
  private val contactPersonReconciliationService: ContactPersonReconciliationService,
  private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
  private val csipReconciliationService: CSIPReconciliationService,
  private val hmppsQueueService: HmppsQueueService,
  private val incentivesReconciliationService: IncentivesReconciliationService,
  private val prisonerRestrictionsReconciliationService: PrisonerRestrictionsReconciliationService,
  private val schedulesService: SchedulesService,
) {

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBatchJob(batchType).also { event.closeApplication() }

  @WithSpan
  fun runBatchJob(@SpanAttribute batchType: BatchType) = runBlocking {
    when (batchType) {
      ADJUDICATION_RECON -> adjudicationsReconService.generateAdjudicationsReconciliationReport()
      ALERT_RECON -> alertsReconciliationService.generateAlertsReconciliationReport()
      ALLOCATION_RECON -> activitiesReconService.allocationReconciliationReport()
      APPOINTMENT_RECON -> appointmentsReconciliationService.generateReconciliationReportBatch()
      ATTENDANCE_RECON -> activitiesReconService.attendanceReconciliationReport(LocalDate.now().minusDays(1))
      CASE_NOTES_ACTIVE_RECON -> caseNotesReconciliationService.generateReconciliationReport(activeOnly = true)
      CASE_NOTES_FULL_RECON -> caseNotesReconciliationService.generateReconciliationReport(activeOnly = false)
      CONTACT_PERSON_PROFILE_DETAILS_RECON -> contactPersonProfileDetailsReconService.reconciliationReport()
      COURT_CASE_PRISONER_RECON -> courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()
      CSIP_RECON -> csipReconciliationService.generateCSIPReconciliationReport()
      DELETE_UNKNOWN_ACTIVITY_MAPPINGS -> schedulesService.deleteUnknownMappings()
      INCENTIVES_RECON -> incentivesReconciliationService.generateIncentiveReconciliationReport()
      PERSON_CONTACT_RECON -> contactPersonReconciliationService.generatePersonContactReconciliationReportBatch()
      PRISONER_CONTACT_RECON -> contactPersonReconciliationService.generatePrisonerContactReconciliationReportBatch()
      PRISONER_RESTRICTION_RECON -> prisonerRestrictionsReconciliationService.generatePrisonerRestrictionsReconciliationReportBatch()
      PURGE_ACTIVITY_DLQ -> purgeQueue(activitiesDlqName)
      SUSPENDED_ALLOCATION_RECON -> activitiesReconService.suspendedAllocationReconciliationReport()
    }
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  private suspend fun purgeQueue(queueName: String) = hmppsQueueService.findQueueToPurge(queueName)
    ?.let { request -> hmppsQueueService.purgeQueue(request) }
    ?: throw IllegalArgumentException("$queueName not found")
}
