package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchController(
  @Value($$"${batch.type}") private val batchType: String,
  private val activitiesReconService: ActivitiesReconService,
) {

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBlocking {
    when (batchType) {
      "ALLOCATION_RECON" -> activitiesReconService.allocationReconciliationReport()
      else -> log.error("Batch type $batchType not supported")
    }
  }.also { event.closeApplication() }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}
