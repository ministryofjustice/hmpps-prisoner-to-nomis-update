package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ATTENDANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON

@ExtendWith(OutputCaptureExtension::class)
class BatchManagerTest {

  private val activitiesReconService = mock<ActivitiesReconService>()
  private val event = mock<ContextRefreshedEvent>()
  private val context = mock<ConfigurableApplicationContext>()

  @BeforeEach
  fun setUp() {
    whenever(event.applicationContext).thenReturn(context)
  }

  @Test
  fun `should call the allocation reconciliation service`() = runTest {
    val batchManager = BatchManager(ALLOCATION_RECON, activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).allocationReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the attendance reconciliation service`() = runTest {
    val batchManager = BatchManager(ATTENDANCE_RECON, activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).attendanceReconciliationReport(any())
    verify(context).close()
  }

  @Test
  fun `should call the suspended allocation reconciliation service`() = runTest {
    val batchManager = BatchManager(SUSPENDED_ALLOCATION_RECON, activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).suspendedAllocationReconciliationReport()
    verify(context).close()
  }
}
