package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.Effects.effects
import com.spotify.mobius.First.first
import com.spotify.mobius.Mobius
import com.spotify.mobius.Next.next
import com.spotify.mobius.runners.ImmediateWorkRunner
import com.spotify.mobius.test.RecordingConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
class FlowMobiusLoopTest {

  @Test
  fun startModelAndEffects() = runBlocking {
    val connection = RecordingConnection<Boolean>()
    val builder = Mobius.loop<String, Int, Boolean>(
      { model, event -> next(model + event.toString()) },
      { connection }
    ).effectRunner { ImmediateWorkRunner() }

    val loop = FlowMobius.loopFrom(builder, "StartModel", effects(true, false))

    val events = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    events
      .run(loop)
      .test {
        assertEquals("StartModel", awaitItem())

        events.tryEmit(1)
        assertEquals("StartModel1", awaitItem())
      }

    assertEquals(2, connection.valueCount())
    connection.assertValuesInAnyOrder(true, false)
  }

  @Test
  fun shouldSupportStartingALoopWithAnInit() = runBlocking {
    val connection = RecordingConnection<Boolean>()
    val builder = Mobius.loop<String, Int, Boolean>(
      { model, event -> next(model + event.toString()) },
      { connection }
    ).init { model -> first("$model-init") }

    val loop = FlowMobius.loopFrom(builder, "hi")

    val events = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    events
      .run(loop)
      .test {
        assertEquals("hi-init", awaitItem())

        events.tryEmit(10)
        assertEquals("hi-init10", awaitItem())
      }
  }

  @Test
  fun shouldThrowIfStartingALoopWithInitAndStartEffects() = runBlocking {
    val connection = RecordingConnection<Boolean>()
    val builder = Mobius.loop<String, Int, Boolean>(
      { model, event -> next(model + event.toString()) },
      { connection }
    ).init { model -> first("$model-init") }

    val loop = FlowMobius.loopFrom(builder, "hi", effects(true))

    val events = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    events
      .run(loop)
      .test {
        val error = awaitError()
        assertTrue(error is IllegalArgumentException)
        assertEquals("cannot pass in start effects when a loop has init defined", error.message)
      }
  }

  @Test
  fun shouldPropagateIncomingErrorsAsUnrecoverable() = runBlocking {
    val connection = RecordingConnection<Boolean>()
    val builder = Mobius.loop<String, Int, Boolean>(
      { model, event -> next(model + event.toString()) },
      { connection }
    ).effectRunner { ImmediateWorkRunner() }

    val loop = FlowMobius.loopFrom(builder, "")

    val events = flow<Int> { throw RuntimeException("expected") }

    events
      .run(loop)
      .test {
        // Initial model
        assertEquals("", awaitItem())

        // Error
        val error = awaitError()
        assertTrue(error is UnrecoverableIncomingException)
      }

    assertEquals(0, connection.valueCount())
  }

}