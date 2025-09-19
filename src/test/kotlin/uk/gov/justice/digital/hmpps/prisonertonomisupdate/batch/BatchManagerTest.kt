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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.DELETE_UNKNOWN_ACTIVITY_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PERSON_CONTACT_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PURGE_ACTIVITY_DLQ
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService
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
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val schedulesService = mock<SchedulesService>()
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
  fun `should call the delete unknown activity mappings service`() = runTest {
    val batchManager = batchManager(DELETE_UNKNOWN_ACTIVITY_MAPPINGS)

    batchManager.onApplicationEvent(event)

    verify(schedulesService).deleteUnknownMappings()
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
    hmppsQueueService,
    schedulesService,
  )
}
