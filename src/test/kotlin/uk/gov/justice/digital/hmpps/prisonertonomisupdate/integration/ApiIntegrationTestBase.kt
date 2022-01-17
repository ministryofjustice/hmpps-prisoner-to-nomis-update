package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension

@ExtendWith(NomisApiExtension::class, HmppsAuthApiExtension::class, VisitsApiExtension::class)
@ActiveProfiles("test")
@SpringBootTest(classes = [WebClientConfiguration::class, WebClientAutoConfiguration::class, OAuth2ClientAutoConfiguration::class, SecurityAutoConfiguration::class])
abstract class ApiIntegrationTestBase
