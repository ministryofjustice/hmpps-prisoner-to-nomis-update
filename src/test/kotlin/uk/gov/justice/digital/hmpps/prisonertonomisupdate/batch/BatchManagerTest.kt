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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ATTENDANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CONTACT_PERSON_PROFILE_DETAILS_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.DELETE_UNKNOWN_ACTIVITY_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.PURGE_ACTIVITY_DLQ
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsReconciliationService
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@ExtendWith(OutputCaptureExtension::class)
class BatchManagerTest {

  private val activitiesReconService = mock<ActivitiesReconService>()
  private val contactPersonProfileDetailsReconService = mock<ContactPersonProfileDetailsReconciliationService>()
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
  fun `should call the allocation reconciliation service`() = runTest {
    val batchManager = batchManager(ALLOCATION_RECON)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).allocationReconciliationReport()
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
    contactPersonProfileDetailsReconService,
    hmppsQueueService,
    schedulesService,
  )
}
