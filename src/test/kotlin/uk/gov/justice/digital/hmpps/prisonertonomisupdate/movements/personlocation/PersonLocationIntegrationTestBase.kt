package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase

class PersonLocationIntegrationTestBase : IntegrationTestBase() {
  @MockitoSpyBean
  lateinit var reconciliationConfig: PersonLocationReconciliationConfig
}
