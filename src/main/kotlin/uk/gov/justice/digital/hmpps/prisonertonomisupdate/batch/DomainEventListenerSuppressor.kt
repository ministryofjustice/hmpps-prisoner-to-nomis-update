package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener

/**
 * This bean is used in batch mode to remove all event listeners, otherwise they will read messages from queues
 * which should be left to the main application.
 */
@Component
@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
class DomainEventListenerSuppressor : BeanFactoryPostProcessor {

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    (beanFactory as DefaultListableBeanFactory).beanDefinitionNames
      .filter { name -> beanFactory.isBeanAssignableFrom<DomainEventListener>(name) }
      .forEach { beanFactory.removeBeanDefinition(it) }
  }

  private inline fun <reified T> DefaultListableBeanFactory.isBeanAssignableFrom(beanName: String): Boolean {
    // Look for a @Component bean
    getBeanDefinition(beanName).beanClassName
      ?.let { runCatching { Class.forName(it, false, beanClassLoader) }.getOrNull() }
      ?.let { return T::class.java.isAssignableFrom(it) }

    // Check for a factory-method @Bean
    return runCatching { getType(beanName, false) }.getOrNull()
      ?.let { T::class.java.isAssignableFrom(it) } == true
  }
}
