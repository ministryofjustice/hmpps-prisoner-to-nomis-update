package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService

class BatchControllerTest {

  @Nested
  inner class AllocationReconciliationReport {
    private val activitiesReconService = mock<ActivitiesReconService>()
    private val batchManager = BatchController("ALLOCATION_RECON", activitiesReconService)
    private val event = mock<ContextRefreshedEvent>()
    private val context = mock<ConfigurableApplicationContext>()

    @BeforeEach
    fun setUp() {
      whenever(event.applicationContext).thenReturn(context)
    }

    @Test
    fun `should call the reconciliation service`() = runTest {
      batchManager.onApplicationEvent(event)

      verify(activitiesReconService).allocationReconciliationReport()
      verify(context).close()
    }
  }
}
