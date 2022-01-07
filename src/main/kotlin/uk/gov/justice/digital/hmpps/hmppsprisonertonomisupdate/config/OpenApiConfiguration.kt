package uk.gov.justice.digital.hmpps.hmppsprisonertonomisupdate.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-to-nomis-update.hmpps.service.justice.gov.uk").description("Prod"),
        Server().url("https://prisoner-to-nomis-update-preprod.hmpps.service.justice.gov.uk").description("PreProd"),
        Server().url("https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("http://localhost:8080").description("Local"),
      )
    )
    .info(
      Info().title("Prisoner to NOMIS update API")
        .version(version)
        .description("Handles HMPPS domain events for NOMIS updates")
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk"))
    )
}
