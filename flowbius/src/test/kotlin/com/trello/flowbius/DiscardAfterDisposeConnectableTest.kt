package com.trello.flowbius

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.test.RecordingConsumer
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscardAfterDisposeConnectableTest {

  @Test
  fun forwardsMessagesToWrappedConsumer() {
    val consumer = RecordingConsumer<String>()
    val underlyingConnection = TestConnection(consumer)
    val connectable = DiscardAfterDisposeConnectable(Connectable<Int, String> { underlyingConnection })
    val connection = connectable.connect(consumer)

    connection.accept(14)
    consumer.assertValues("Value is: 14")
  }

  @Test
  fun delegatesDisposeToActualConnection() {
    val consumer = RecordingConsumer<String>()
    val underlyingConnection = TestConnection(consumer)
    val connectable = DiscardAfterDisposeConnectable(Connectable<Int, String> { underlyingConnection })
    val connection = connectable.connect(consumer)

    connection.dispose()
    assertTrue(underlyingConnection.disposed)
  }

  @Test
  fun discardsEventsAfterDisposal() {
    val consumer = RecordingConsumer<String>()
    val underlyingConnection = TestConnection(consumer)
    val connectable = DiscardAfterDisposeConnectable(Connectable<Int, String> { underlyingConnection })
    val connection = connectable.connect(consumer)

    // given a disposed connection
    connection.dispose()

    // when a message arrives
    connection.accept(1)

    // it is discarded
    consumer.assertValues()
  }

  private class TestConnection(private val eventConsumer: Consumer<String>) : Connection<Int> {
    @Volatile
    var disposed = false

    override fun accept(effect: Int) = eventConsumer.accept("Value is: $effect")

    override fun dispose() {
      disposed = true
    }
  }
}