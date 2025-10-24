package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.INCIDENTS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.LOCATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.NON_ASSOCIATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ORGANISATIONS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PERSON_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISONER_RESTRICTION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PRISON_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PURGE_ACTIVITY_DLQ
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SENTENCING_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.VISIT_BALANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonBalanceReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.PrisonerBalanceReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.LocationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.NonAssociationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.PrisonerRestrictionsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.SentencingReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceReconciliationService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@ExtendWith(OutputCaptureExtension::class)
class BatchManagerTest {

  private val activitiesReconService = mock<ActivitiesReconService>()
  private val adjudicationsReconService = mock<AdjudicationsReconciliationService>()
  private val alertsReconService = mock<AlertsReconciliationService>()
  private val appointmentsReconService = mock<AppointmentsReconciliationService>()
  private val caseNotesReconciliationService = mock<CaseNotesReconciliationService>()
  private val contactPersonProfileDetailsReconService = mock<ContactPersonProfileDetailsReconciliationService>()
  private val contactPersonReconService = mock<ContactPersonReconciliationService>()
  private val courtSentencingReconciliationService = mock<CourtSentencingReconciliationService>()
  private val csipReconciliationService = mock<CSIPReconciliationService>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val incentivesReconciliationService = mock<IncentivesReconciliationService>()
  private val incidentsReconciliationService = mock<IncidentsReconciliationService>()
  private val locationsReconciliationService = mock<LocationsReconciliationService>()
  private val nonAssociationsReconciliationService = mock<NonAssociationsReconciliationService>()
  private val organisationsReconciliationService = mock<OrganisationsReconciliationService>()
  private val prisonerRestrictionsReconciliationService = mock<PrisonerRestrictionsReconciliationService>()
  private val schedulesService = mock<SchedulesService>()
  private val sentencingReconciliationService = mock<SentencingReconciliationService>()
  private val visitBalanceReconciliationService = mock<VisitBalanceReconciliationService>()
  private val prisonBalanceReconciliationService = mock<PrisonBalanceReconciliationService>()
  private val prisonerBalanceReconciliationService = mock<PrisonerBalanceReconciliationService>()
  private val activityDlqName = "activity-dlq-name"
  private val event = mock<ContextRefreshedEvent>()
  private val context = mock<ConfigurableApplicationContext>()

  @BeforeEach
  fun setUp() {
    whenever(event.applicationContext).thenReturn(context)
  }

  @Test
  fun `should call the adjudications reconciliation service`() = runTest {
    val batchManager = batchManager(ADJUDICATION_RECON)

    batchManager.onApplicationEvent(event)

    verify(adjudicationsReconService).generateAdjudicationsReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the alerts reconciliation service`() = runTest {
    val batchManager = batchManager(ALERT_RECON)

    batchManager.onApplicationEvent(event)

    verify(alertsReconService).generateAlertsReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the allocation reconciliation service`() = runTest {
    val batchManager = batchManager(ALLOCATION_RECON)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).allocationReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the appointments reconciliation service`() = runTest {
    val batchManager = batchManager(APPOINTMENT_RECON)

    batchManager.onApplicationEvent(event)

    verify(appointmentsReconService).generateReconciliationReportBatch()
    verify(context).close()
  }

  @Test
  fun `should call the attendance reconciliation service`() = runTest {
    val batchManager = batchManager(ATTENDANCE_RECON)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).attendanceReconciliationReport(any())
    verify(context).close()
  }

  @Test
  fun `should call the active only case notes reconciliation service`() = runTest {
    val batchManager = batchManager(CASE_NOTES_ACTIVE_RECON)

    batchManager.onApplicationEvent(event)

    verify(caseNotesReconciliationService).generateReconciliationReport(true)
    verify(context).close()
  }

  @Test
  fun `should call the full case notes reconciliation service`() = runTest {
    val batchManager = batchManager(CASE_NOTES_FULL_RECON)

    batchManager.onApplicationEvent(event)

    verify(caseNotesReconciliationService).generateReconciliationReport(false)
    verify(context).close()
  }

  @Test
  fun `should call the contact person profile details reconciliation service`() = runTest {
    val batchManager = batchManager(CONTACT_PERSON_PROFILE_DETAILS_RECON)

    batchManager.onApplicationEvent(event)

    verify(contactPersonProfileDetailsReconService).reconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the prisoner court cases reconciliation service`() = runTest {
    val batchManager = batchManager(COURT_CASE_PRISONER_RECON)

    batchManager.onApplicationEvent(event)

    verify(courtSentencingReconciliationService).generateCourtCasePrisonerReconciliationReportBatch()
    verify(context).close()
  }

  @Test
  fun `should call the csip reconciliation service`() = runTest {
    val batchManager = batchManager(CSIP_RECON)

    batchManager.onApplicationEvent(event)

    verify(csipReconciliationService).generateCSIPReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the delete unknown activity mappings service`() = runTest {
    val batchManager = batchManager(DELETE_UNKNOWN_ACTIVITY_MAPPINGS)

    batchManager.onApplicationEvent(event)

    verify(schedulesService).deleteUnknownMappings()
    verify(context).close()
  }

  @Test
  fun `should call the incentives reconciliation service`() = runTest {
    val batchManager = batchManager(INCENTIVES_RECON)

    batchManager.onApplicationEvent(event)

    verify(incentivesReconciliationService).generateIncentiveReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the incidents reconciliation service`() = runTest {
    val batchManager = batchManager(INCIDENTS_RECON)

    batchManager.onApplicationEvent(event)

    verify(incidentsReconciliationService).incidentsReconciliation()
    verify(context).close()
  }

  @Test
  fun `should call the locations reconciliation service`() = runTest {
    val batchManager = batchManager(LOCATIONS_RECON)

    batchManager.onApplicationEvent(event)

    verify(locationsReconciliationService).generateReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the non associations reconciliation service`() = runTest {
    val batchManager = batchManager(NON_ASSOCIATIONS_RECON)

    batchManager.onApplicationEvent(event)

    verify(nonAssociationsReconciliationService).generateReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the organisations reconciliation service`() = runTest {
    val batchManager = batchManager(ORGANISATIONS_RECON)

    batchManager.onApplicationEvent(event)

    verify(organisationsReconciliationService).generateOrganisationsReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the sentencing reconciliation service`() = runTest {
    val batchManager = batchManager(SENTENCING_RECON)

    batchManager.onApplicationEvent(event)

    verify(sentencingReconciliationService).generateSentencingReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the person contact reconciliation service`() = runTest {
    val batchManager = batchManager(PERSON_CONTACT_RECON)

    batchManager.onApplicationEvent(event)

    verify(contactPersonReconService).generatePersonContactReconciliationReportBatch()
    verify(context).close()
  }

  @Test
  fun `should call the prisoner contact reconciliation service`() = runTest {
    val batchManager = batchManager(PRISONER_CONTACT_RECON)

    batchManager.onApplicationEvent(event)

    verify(contactPersonReconService).generatePrisonerContactReconciliationReportBatch()
    verify(context).close()
  }

  @Test
  fun `should call the prisoner restrictions reconciliation service`() = runTest {
    val batchManager = batchManager(PRISONER_RESTRICTION_RECON)

    batchManager.onApplicationEvent(event)

    verify(prisonerRestrictionsReconciliationService).generatePrisonerRestrictionsReconciliationReportBatch()
    verify(context).close()
  }

  @Test
  fun `should call the purge dlq service`() = runTest {
    val batchManager = batchManager(PURGE_ACTIVITY_DLQ)
    whenever(hmppsQueueService.findQueueToPurge(anyString())).thenReturn(mock())
    whenever(hmppsQueueService.purgeQueue(any())).thenReturn(mock())

    batchManager.onApplicationEvent(event)

    verify(hmppsQueueService).findQueueToPurge(activityDlqName)
    verify(hmppsQueueService).purgeQueue(any())
    verify(context).close()
  }

  @Test
  fun `should call the suspended allocation reconciliation service`() = runTest {
    val batchManager = batchManager(SUSPENDED_ALLOCATION_RECON)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).suspendedAllocationReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the visit balance reconciliation service`() = runTest {
    val batchManager = batchManager(VISIT_BALANCE_RECON)

    batchManager.onApplicationEvent(event)

    verify(visitBalanceReconciliationService).generateReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the prison balance reconciliation service`() = runTest {
    val batchManager = batchManager(PRISON_BALANCE_RECON)

    batchManager.onApplicationEvent(event)

    verify(prisonBalanceReconciliationService).generateReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the prisoner balance reconciliation service`() = runTest {
    val batchManager = batchManager(PRISONER_BALANCE_RECON)

    batchManager.onApplicationEvent(event)

    verify(prisonerBalanceReconciliationService).generatePrisonerBalanceReconciliationReportBatch()
    verify(context).close()
  }

  private fun batchManager(batchType: BatchType) = BatchManager(
    batchType,
    activityDlqName,
    activitiesReconService,
    adjudicationsReconService,
    alertsReconService,
    appointmentsReconService,
    caseNotesReconciliationService,
    contactPersonProfileDetailsReconService,
    contactPersonReconService,
    courtSentencingReconciliationService,
    csipReconciliationService,
    hmppsQueueService,
    incentivesReconciliationService,
    incidentsReconciliationService,
    locationsReconciliationService,
    nonAssociationsReconciliationService,
    organisationsReconciliationService,
    prisonBalanceReconciliationService,
    prisonerBalanceReconciliationService,
    prisonerRestrictionsReconciliationService,
    schedulesService,
    sentencingReconciliationService,
    visitBalanceReconciliationService,
  )
}
