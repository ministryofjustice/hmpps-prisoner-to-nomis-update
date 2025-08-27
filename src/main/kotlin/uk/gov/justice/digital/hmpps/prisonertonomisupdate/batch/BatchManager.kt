package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.time.LocalDate

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @Value($$"${batch.type}") private val batchType: String,
  private val beanDestroyer: BeanDestroyer,
  private val activitiesReconService: ActivitiesReconService,
) {

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBlocking {
    beanDestroyer.destroyBeans(DomainEventListener::class.java)

    when (batchType) {
      "ALLOCATION_RECON" -> activitiesReconService.allocationReconciliationReport()
      "ATTENDANCE_RECON" -> activitiesReconService.attendanceReconciliationReport(LocalDate.now().minusDays(1))
      "SUSPENDED_ALLOCATION_RECON" -> activitiesReconService.suspendedAllocationReconciliationReport()
      else -> log.error("Batch type $batchType not supported")
    }
  }.also { event.closeApplication() }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Component
class BeanDestroyer(applicationContext: ConfigurableApplicationContext) {
  private val beanFactory = applicationContext.autowireCapableBeanFactory

  fun destroyBeans(vararg types: Class<*>) = types.forEach { type ->
    beanFactory.getBeanProvider(type).forEach { beanFactory.destroyBean(it) }
  }
}
