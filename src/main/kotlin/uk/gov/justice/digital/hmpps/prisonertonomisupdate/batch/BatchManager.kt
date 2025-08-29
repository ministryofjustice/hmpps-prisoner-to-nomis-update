package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ATTENDANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import java.time.LocalDate

enum class BatchType {
  ALLOCATION_RECON,
  ATTENDANCE_RECON,
  SUSPENDED_ALLOCATION_RECON,
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @Value($$"${batch.type}") private val batchType: BatchType,
  private val activitiesReconService: ActivitiesReconService,
) {

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBatchJob(batchType).also { event.closeApplication() }

  @WithSpan
  fun runBatchJob(@SpanAttribute batchType: BatchType) = runBlocking {
    when (batchType) {
      ALLOCATION_RECON -> activitiesReconService.allocationReconciliationReport()
      ATTENDANCE_RECON -> activitiesReconService.attendanceReconciliationReport(LocalDate.now().minusDays(1))
      SUSPENDED_ALLOCATION_RECON -> activitiesReconService.suspendedAllocationReconciliationReport()
    }
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}
