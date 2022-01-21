package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.test.RecordingConsumer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
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
  fun mapShouldPropagateCompletion() = runBlocking {
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
  fun mapShouldPropagateErrorsFromConnectable() = runBlocking {
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
  fun mapShouldPropagateErrorsFromUpstream() = runBlocking {
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