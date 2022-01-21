package com.trello.flowbius

/**
 * Used to indicate that an [FlowMobiusLoop] transformer has received a [Throwable], which is illegal.
 * This exception means Mobius is in an undefined state and should be considered a fatal programmer error. Do not try
 * to handle this exception in your code, ensure it never gets thrown.
 */
class UnrecoverableIncomingException internal constructor(throwable: Throwable) : RuntimeException(
  "PROGRAMMER ERROR: Mobius cannot recover from this exception; ensure your event sources don't throw exceptions",
  throwable
)
