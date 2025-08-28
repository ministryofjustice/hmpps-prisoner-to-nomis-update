package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.ActivitiesReconService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.ATTENDANCE_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.SUSPENDED_ALLOCATION_RECON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
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
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBlocking {
    when (batchType) {
      ALLOCATION_RECON -> activitiesReconService.allocationReconciliationReport()
      ATTENDANCE_RECON -> activitiesReconService.attendanceReconciliationReport(LocalDate.now().minusDays(1))
      SUSPENDED_ALLOCATION_RECON -> activitiesReconService.suspendedAllocationReconciliationReport()
    }
  }.also { event.closeApplication() }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}

/**
 * This configuration is used in batch mode to remove all event listeners, otherwise they will read messages from queues
 * which should be left to the main application.
 */
@Configuration
@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
class BatchConfiguration {

  private inline fun <reified T> DefaultListableBeanFactory.isBeanAssignableFrom(beanName: String): Boolean {
    // Look for a @Component bean
    getBeanDefinition(beanName).beanClassName
      ?.let { runCatching { Class.forName(it, false, beanClassLoader) }.getOrNull() }
      ?.let { return T::class.java.isAssignableFrom(it) }

    // Check for a factory-method @Bean
    return runCatching { getType(beanName, false) }.getOrNull()
      ?.let { T::class.java.isAssignableFrom(it) } == true
  }

  @Bean
  fun pruneDomainEventListenerBeans(): BeanFactoryPostProcessor = BeanFactoryPostProcessor { beanFactory ->
    (beanFactory as DefaultListableBeanFactory).beanDefinitionNames
      .filter { name -> beanFactory.isBeanAssignableFrom<DomainEventListener>(name) }
      .forEach { beanFactory.removeBeanDefinition(it) }
  }
}
