package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.Effects.effects
import com.spotify.mobius.First.first
import com.spotify.mobius.Mobius
import com.spotify.mobius.Next.next
import com.spotify.mobius.runners.ImmediateWorkRunner
import com.spotify.mobius.test.RecordingConnection
import com.spotify.mobius.test.RecordingConsumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
class FlowMobiusLoopTest {

  @Test
  fun loop() = runBlocking {
    val recordingConsumer = RecordingConsumer<Boolean>()

    val loop = FlowMobius.loop<String, Int, Boolean>(
      update = { model, event -> next(model + event.toString(), setOf(true)) },
      effectHandler = subtypeEffectHandler { addConsumer(recordingConsumer) }
    )
      .eventRunner(::ImmediateWorkRunner)
      .effectRunner(::ImmediateWorkRunner)
      .startFrom("Hello")

    assertEquals("Hello", loop.mostRecentModel)
    recordingConsumer.assertValues()

    loop.dispatchEvent(5)
    assertEquals("Hello5", loop.mostRecentModel)
    recordingConsumer.assertValues(true)
  }

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

  /**
   * [Dispatchers.Unconfined] has unfortunate nested behavior for us, which is that its order of execution is undefined.
   * This can cause problems where start effects aren't ever handled because the start effect fires *before* the loop
   * is fully configured. This test verifies that we won't miss start effects in this situation.
   */
  @Test
  fun startEffectWorksWithUnconfinedDispatchers() = runBlocking {
    data class LoadEffect(val id: String)
    data class LoadEvent(val id: String)

    val job = launch(Dispatchers.Unconfined) {
      val loop = FlowMobius.loop<String, LoadEvent, LoadEffect>(
        update = { _, event -> next("Loaded data: ${event.id}") },
        context = Dispatchers.Unconfined,
        effectHandler = subtypeEffectHandler { addTransformer<LoadEffect> { s -> s.map { LoadEvent(it.id) } } }
      )
        .eventRunner(::ImmediateWorkRunner)
        .effectRunner(::ImmediateWorkRunner)
        .startFrom("No data loaded", setOf(LoadEffect("abc")))

      // I hate to add this delay, but we have to give the loop a suspend point with which to initially process data.
      //
      // This implies that the start effect will not *immediately* fire off on loop startup, which is unfortunate
      // but unavoidable given what happens with scheduling nested Dispatchers.Unconfined launches.
      delay(10)

      assertEquals("Loaded data: abc", loop.mostRecentModel)
    }

    job.join()
  }
}