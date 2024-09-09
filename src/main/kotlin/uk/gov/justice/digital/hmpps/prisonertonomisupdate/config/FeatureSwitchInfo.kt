package uk.gov.justice.digital.hmpps.prisonertonomisupdate.config

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@EnableConfigurationProperties
@Configuration
class EventFeatureSwitchConfig {
  @ConfigurationProperties(prefix = "feature.event")
  @Bean
  fun eventFeatureSwitchProperties(): Map<String, Boolean?> = mutableMapOf()
}

@Component
class FeatureSwitchInfo(
  private val eventFeatureSwitchProperties: Map<String, Boolean?>,
) : InfoContributor {

  override fun contribute(builder: Info.Builder) {
    builder.withDetail("event-feature-switches", eventFeatureSwitchProperties.toSortedMap())
  }
}
