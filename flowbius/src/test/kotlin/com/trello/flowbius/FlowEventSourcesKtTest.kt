package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.EventSource
import com.spotify.mobius.disposables.Disposable
import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.test.RecordingConsumer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
class FlowEventSourcesKtTest {

  @Test
  fun eventsAreForwardedInOrder() {
    val source = flowOf(1, 2, 3).asEventSource()
    val consumer = RecordingConsumer<Int>()

    source.subscribe(consumer)

    consumer.assertValues(1, 2, 3)
  }

  @Test
  fun disposePreventsFurtherEvents() {
    val sharedFlow = MutableSharedFlow<Int>()
    val source = sharedFlow.asEventSource()
    val consumer = RecordingConsumer<Int>()

    val d = source.subscribe(consumer)

    runBlocking {
      sharedFlow.emit(1)
      sharedFlow.emit(2)
      d.dispose()
      sharedFlow.emit(3)
    }

    consumer.assertValues(1, 2)
  }

  @Test
  fun errorsAreForwardedToErrorHandler() {
    var caughtException: Throwable? = null
    val exceptionHandler = CoroutineExceptionHandler { _, e -> caughtException = e }
    val sharedFlow = flow<Int> { throw RuntimeException("crash!") }
    val source = sharedFlow.asEventSource(exceptionHandler)
    val consumer = RecordingConsumer<Int>()

    source.subscribe(consumer)

    assertEquals("crash!", caughtException?.message)
  }

  @Test
  fun eventsToFlow() = runBlocking {
    val eventSource = TestEventSource<Int>()

    eventSource.asFlow().test {
      expectNoEvents()

      eventSource.accept(1)
      assertEquals(1, awaitItem())

      eventSource.accept(2)
      assertEquals(2, awaitItem())

      eventSource.accept(3)
      assertEquals(3, awaitItem())
    }
  }

  @Test
  fun cancelPreventsFurtherFlowItems() = runBlocking {
    val eventSource = TestEventSource<Int>()

    eventSource.asFlow().test {
      expectNoEvents()

      eventSource.accept(1)
      assertEquals(1, awaitItem())

      cancel()

      eventSource.accept(2)
      expectNoEvents()
    }
  }

  private class TestEventSource<E> : EventSource<E> {

    private val consumers = mutableListOf<Consumer<E>>()
    private var disposed = false

    override fun subscribe(eventConsumer: Consumer<E>): Disposable {
      consumers += eventConsumer
      return Disposable { disposed = true }
    }

    fun accept(value: E) {
      if (!disposed) {
        consumers.forEach { it.accept(value) }
      }
    }
  }

}