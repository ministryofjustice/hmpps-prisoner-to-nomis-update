package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

inline fun logger(): Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
