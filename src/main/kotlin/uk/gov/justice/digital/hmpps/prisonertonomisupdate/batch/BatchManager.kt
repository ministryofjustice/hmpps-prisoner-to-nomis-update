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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CORE_PERSON_ACTIVE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CORE_PERSON_FULL_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.COURT_CASE_ACTIVE_PRISONER_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.COURT_CASE_PRISONER_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CSIP_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.DELETE_UNKNOWN_ACTIVITY_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.INCENTIVES_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.INCIDENTS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.LOCATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.NON_ASSOCIATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.OFFICIAL_VISIT_ACTIVE_SCH_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.OFFICIAL_VISIT_ALL_MISSING_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.OFFICIAL_VISIT_ALL_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ORGANISATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PERSON_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_RESTRICTION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_TRANSACTIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISON_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISON_TRANSACTIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PURGE_ACTIVITY_DLQ
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SENTENCING_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.TAP_ALL_PRISONERS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.VISIT_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.VISIT_SLOTS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonBalanceReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonTransactionReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonerBalanceReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonerTransactionReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.LocationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.TemporaryAbsencesAllPrisonersReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.NonAssociationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsActiveScheduledReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsAllMissingFromNOMISReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsAllReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.VisitSlotsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.PrisonerRestrictionsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.SentencingReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceReconciliationService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
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
  CORE_PERSON_ACTIVE_RECON,
  CORE_PERSON_FULL_RECON,
  COURT_CASE_PRISONER_RECON,
  COURT_CASE_ACTIVE_PRISONER_RECON,
  CSIP_RECON,
  DELETE_UNKNOWN_ACTIVITY_MAPPINGS,
  INCENTIVES_RECON,
  INCIDENTS_RECON,
  LOCATIONS_RECON,
  NON_ASSOCIATIONS_RECON,
  OFFICIAL_VISIT_ALL_RECON,
  OFFICIAL_VISIT_ALL_MISSING_RECON,
  OFFICIAL_VISIT_ACTIVE_SCH_RECON,
  VISIT_SLOTS_RECON,
  ORGANISATIONS_RECON,
  PERSON_CONTACT_RECON,
  PRISON_BALANCE_RECON,
  PRISON_TRANSACTIONS_RECON,
  PRISONER_BALANCE_RECON,
  PRISONER_CONTACT_RECON,
  PRISONER_RESTRICTION_RECON,
  PRISONER_TRANSACTIONS_RECON,
  PURGE_ACTIVITY_DLQ,
  SENTENCING_RECON,
  SUSPENDED_ALLOCATION_RECON,
  TAP_ALL_PRISONERS_RECON,
  VISIT_BALANCE_RECON,
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
  private val corePersonReconciliationService: CorePersonReconciliationService,
  private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
  private val csipReconciliationService: CSIPReconciliationService,
  private val hmppsQueueService: HmppsQueueService,
  private val incentivesReconciliationService: IncentivesReconciliationService,
  private val incidentsReconciliationService: IncidentsReconciliationService,
  private val locationsReconciliationService: LocationsReconciliationService,
  private val nonAssociationsReconciliationService: NonAssociationsReconciliationService,
  private val officialVisitsAllReconciliationService: OfficialVisitsAllReconciliationService,
  private val officialVisitsAllMissingFromNOMISReconciliationService: OfficialVisitsAllMissingFromNOMISReconciliationService,
  private val officialVisitsActiveScheduledReconciliationService: OfficialVisitsActiveScheduledReconciliationService,
  private val organisationReconciliationService: OrganisationsReconciliationService,
  private val prisonBalanceReconciliationService: PrisonBalanceReconciliationService,
  private val prisonTransactionsReconciliationService: PrisonTransactionReconciliationService,
  private val prisonerBalanceReconciliationService: PrisonerBalanceReconciliationService,
  private val prisonerRestrictionsReconciliationService: PrisonerRestrictionsReconciliationService,
  private val prisonerTransactionsReconciliationService: PrisonerTransactionReconciliationService,
  private val schedulesService: SchedulesService,
  private val sentencingReconciliationService: SentencingReconciliationService,
  private val temporaryAbsencesAllPrisonersReconciliationService: TemporaryAbsencesAllPrisonersReconciliationService,
  private val visitBalancesReconciliationService: VisitBalanceReconciliationService,
  private val visitSlotsReconciliationService: VisitSlotsReconciliationService,
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
      CORE_PERSON_ACTIVE_RECON -> corePersonReconciliationService.generateReconciliationReport(activeOnly = true)
      CORE_PERSON_FULL_RECON -> corePersonReconciliationService.generateReconciliationReport(activeOnly = false)
      COURT_CASE_PRISONER_RECON -> courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()
      COURT_CASE_ACTIVE_PRISONER_RECON -> courtSentencingReconciliationService.generateCourtCaseActivePrisonerReconciliationReportBatch()
      CSIP_RECON -> csipReconciliationService.generateCSIPReconciliationReport()
      DELETE_UNKNOWN_ACTIVITY_MAPPINGS -> schedulesService.deleteUnknownMappings()
      INCENTIVES_RECON -> incentivesReconciliationService.generateIncentiveReconciliationReport()
      INCIDENTS_RECON -> incidentsReconciliationService.incidentsReconciliation()
      LOCATIONS_RECON -> locationsReconciliationService.generateReconciliationReport()
      NON_ASSOCIATIONS_RECON -> nonAssociationsReconciliationService.generateReconciliationReport()
      OFFICIAL_VISIT_ALL_RECON -> officialVisitsAllReconciliationService.generateAllVisitsReconciliationReportBatch()
      OFFICIAL_VISIT_ALL_MISSING_RECON -> officialVisitsAllMissingFromNOMISReconciliationService.generateAllVisitsReconciliationReportBatch()
      OFFICIAL_VISIT_ACTIVE_SCH_RECON -> officialVisitsActiveScheduledReconciliationService.generateActiveScheduledVisitsReconciliationReportBatch()
      ORGANISATIONS_RECON -> organisationReconciliationService.generateOrganisationsReconciliationReport()
      PERSON_CONTACT_RECON -> contactPersonReconciliationService.generatePersonContactReconciliationReportBatch()
      PRISON_BALANCE_RECON -> prisonBalanceReconciliationService.generateReconciliationReport()
      PRISON_TRANSACTIONS_RECON -> prisonTransactionsReconciliationService.generateReconciliationReport()
      PRISONER_BALANCE_RECON -> prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()
      PRISONER_CONTACT_RECON -> contactPersonReconciliationService.generatePrisonerContactReconciliationReportBatch()
      PRISONER_RESTRICTION_RECON -> prisonerRestrictionsReconciliationService.generatePrisonerRestrictionsReconciliationReportBatch()
      PRISONER_TRANSACTIONS_RECON -> prisonerTransactionsReconciliationService.generateReconciliationReportBatch()
      PURGE_ACTIVITY_DLQ -> purgeQueue(activitiesDlqName)
      SENTENCING_RECON -> sentencingReconciliationService.generateSentencingReconciliationReport()
      SUSPENDED_ALLOCATION_RECON -> activitiesReconService.suspendedAllocationReconciliationReport()
      TAP_ALL_PRISONERS_RECON -> temporaryAbsencesAllPrisonersReconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      VISIT_BALANCE_RECON -> visitBalancesReconciliationService.generateReconciliationReport()
      VISIT_SLOTS_RECON -> visitSlotsReconciliationService.generateVisitSlotsReconciliationReportBatch()
    }
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  private suspend fun purgeQueue(queueName: String) = hmppsQueueService.findQueueToPurge(queueName)
    ?.let { request -> hmppsQueueService.purgeQueue(request) }
    ?: throw IllegalArgumentException("$queueName not found")
}
