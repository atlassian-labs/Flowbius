package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.test.RecordingConsumer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.time.ExperimentalTime

@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
class FlowConnectablesKtTest {

  @Test
  fun asConnectable() {
    val consumer = RecordingConsumer<Int>()

    val transformer = { source: Flow<String> -> source.flatMapConcat { flowOf(it.length, it.length * 2) } }
    val connectable = transformer.asConnectable()
    val connection = connectable.connect(consumer)

    // Verify we receive transformed values
    connection.accept("one")
    connection.accept("three")
    consumer.assertValues(3, 6, 5, 10)
    consumer.clearValues()

    // Verify that after disposing, we get no more values
    connection.dispose()
    assertEquals(0, consumer.valueCount())
  }

  @Test
  fun awaitSubscriptions() = runTest(UnconfinedTestDispatcher()) {
    val consumer = RecordingConsumer<Int>()

    val transformer = { source: Flow<String> -> source.flatMapConcat { flowOf(it.length, it.length * 2) } }
    val connectable = transformer.asConnectable()

    // Run in quick sequence to ensure we're waiting on the subscription to occur before emitting results
    val connection = async {
      val currentConnection = connectable.connect(consumer)
      currentConnection.accept("one")
      currentConnection.accept("three")
      currentConnection.accept("ten")
      currentConnection.accept("five")
      currentConnection
    }
    advanceUntilIdle()

    consumer.assertValues(3, 6, 5, 10, 3, 6, 4, 8)
    consumer.clearValues()

    // Verify that after disposing, we get no more values
    connection.await().dispose()
    assertEquals(0, consumer.valueCount())
  }

  @Test
  fun asConnectable_handlesConcurrentConnections() {
    val numTrials = 15
    val pool = Executors.newCachedThreadPool()
    val barrier = CyclicBarrier(numTrials + 1)

    val consumer = RecordingConsumer<Int>()
    val transformer = { source: Flow<Int> -> source.map { it * it } }
    val connectable = transformer.asConnectable()
    val connection = connectable.connect(consumer)

    (1..numTrials).forEach {
      pool.execute {
        barrier.await()
        connection.accept(it)
        barrier.await()
      }
    }

    barrier.await() // wait for all threads to be ready
    barrier.await() // wait for all threads to finish

    consumer.assertValuesInAnyOrder(*(1..numTrials).map { it * it }.toTypedArray())
  }

  @Test
  fun asConnectableCreatesIndependentConnections() {
    val consumer1 = RecordingConsumer<Int>()
    val consumer2 = RecordingConsumer<Int>()

    val transformer = { source: Flow<String> -> source.flatMapConcat { flowOf(it.length, it.length * 2) } }
    val connectable = transformer.asConnectable()
    val connection1 = connectable.connect(consumer1)
    val connection2 = connectable.connect(consumer2)

    connection1.accept("one")
    connection1.dispose()

    connection2.accept("three")
    connection2.dispose()

    // Assert that the two connections do not pass values/state to each other by accident
    consumer1.assertValues(3, 6)
    consumer2.assertValues(5, 10)
  }

  @Test
  fun mapShouldPropagateCompletion() = runTest {
    val connectable = Connectable<String, Int> { output ->
      object : Connection<String> {
        override fun accept(value: String) = output.accept(value.length)
        override fun dispose() {}
      }
    }

    flowOf("hi", "bye")
      .flatMapMerge(connectable)
      .test {
        assertEquals(2, awaitItem())
        assertEquals(3, awaitItem())
        awaitComplete()
      }
  }

  @Test
  fun mapShouldPropagateErrorsFromConnectable() = runTest {
    val crashingCollectable = Connectable<String, Int> {
      object : Connection<String> {
        override fun accept(value: String) = error("crashing!")
        override fun dispose() = error("Should not be here in this test")
      }
    }

    flowOf("crash")
      .flatMapMerge(crashingCollectable)
      .test { assertEquals("crashing!", awaitError().message) }
  }

  @Test
  fun mapShouldPropagateErrorsFromUpstream() = runTest {
    val connectable = Connectable<String, Int> { output ->
      object : Connection<String> {
        override fun accept(value: String) = output.accept(value.length)
        override fun dispose() {}
      }
    }

    flow<String> { error("expected") }
      .flatMapMerge(connectable)
      .test { assertEquals("expected", awaitError().message) }
  }
}