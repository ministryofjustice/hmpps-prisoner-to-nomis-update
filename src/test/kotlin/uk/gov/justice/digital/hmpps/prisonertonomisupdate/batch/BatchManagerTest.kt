package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService

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
  fun `should log for invalid batch type`(output: CapturedOutput) = runTest {
    val batchManager = BatchManager("INVALID_BATCH_TYPE", activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService, never()).allocationReconciliationReport()
    verify(context).close()
    assertThat(output).contains("INVALID_BATCH_TYPE not supported")
  }

  @Test
  fun `should call the allocation reconciliation service`() = runTest {
    val batchManager = BatchManager("ALLOCATION_RECON", activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).allocationReconciliationReport()
    verify(context).close()
  }

  @Test
  fun `should call the attendance reconciliation service`() = runTest {
    val batchManager = BatchManager("ATTENDANCE_RECON", activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).attendanceReconciliationReport(any())
    verify(context).close()
  }

  @Test
  fun `should call the suspended allocation reconciliation service`() = runTest {
    val batchManager = BatchManager("SUSPENDED_ALLOCATION_RECON", activitiesReconService)

    batchManager.onApplicationEvent(event)

    verify(activitiesReconService).suspendedAllocationReconciliationReport()
    verify(context).close()
  }
}
