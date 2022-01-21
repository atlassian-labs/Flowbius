package com.trello.flowbius

import com.spotify.mobius.ConnectionException

class EffectHandlerException internal constructor(throwable: Throwable) :
  ConnectionException("Error in effect handler", throwable) {
  internal companion object {
    fun createFromEffectHandler(effectHandler: Any, cause: Throwable): EffectHandlerException {
      val e = EffectHandlerException(cause)
      val stackTrace = e.stackTrace

      // add a synthetic StackTraceElement so that the effect handler class name will be reported in
      // the exception. This helps troubleshooting where the issue originated from.
      stackTrace[0] = StackTraceElement(effectHandler::class.java.name, "apply", null, -1)

      e.stackTrace = stackTrace

      return e
    }
  }
}
