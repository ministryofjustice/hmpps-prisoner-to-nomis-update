package uk.gov.justice.digital.hmpps.prisonertonomisupdate.data

import java.util.function.Supplier

class NotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<NotFoundException> {
  override fun get(): NotFoundException = NotFoundException(message)
}
