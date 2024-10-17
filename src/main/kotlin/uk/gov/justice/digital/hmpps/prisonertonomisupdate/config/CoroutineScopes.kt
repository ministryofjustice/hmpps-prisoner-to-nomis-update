package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineScopes {
  // since everything is non-blocking, we don't need to worry about blocking threads so use whatever thread
  // the last coroutine was on, continue on that one rather than switching threads
  @Bean
  fun nonBlockingReportScope() = CoroutineScope(Dispatchers.Unconfined + Context.current().asContextElement())
}
