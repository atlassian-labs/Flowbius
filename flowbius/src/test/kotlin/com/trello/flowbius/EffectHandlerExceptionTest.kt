package com.trello.flowbius

import org.junit.Assert.assertTrue
import org.junit.Test

class EffectHandlerExceptionTest {

  class PretendEffectHandler

  @Test
  fun shouldProvideAGoodStackTrace() {
    val cause = RuntimeException("hey")
    val effectHandlerException = EffectHandlerException.createFromEffectHandler(PretendEffectHandler(), cause)
    assertTrue(PretendEffectHandler::class.java.name in effectHandlerException.stackTraceToString())
    assertTrue(effectHandlerException.cause == cause)
  }

}