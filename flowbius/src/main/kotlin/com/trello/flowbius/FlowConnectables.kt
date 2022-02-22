package com.trello.flowbius

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Generates a [Connectable] from a Flow map */
fun <I, O> FlowTransformer<I, O>.asConnectable(context: CoroutineContext = EmptyCoroutineContext): Connectable<I, O> {
  return DiscardAfterDisposeConnectable(FlowConnectable(context, this))
}

private class FlowConnectable<I, O>(
  private val context: CoroutineContext,
  private val mapper: FlowTransformer<I, O>
) : Connectable<I, O> {
  override fun connect(output: Consumer<O>): Connection<I> {
    val channel = Channel<I>()

    val job = GlobalScope.launch(Dispatchers.Unconfined + context, start = CoroutineStart.ATOMIC) {
      channel.receiveAsFlow()
        .run { mapper(this) }
        .collect { output.accept(it) }
    }

    return object : Connection<I> {
      override fun accept(value: I) {
        channel.trySendBlocking(value)
      }

      override fun dispose() {
        channel.close()
        job.cancel()
      }
    }
  }
}

/** Applies the [Connectable]'s logic to go from Flow<I> -> Flow<O> */
@ExperimentalCoroutinesApi
fun <I, O> Flow<I>.flatMapMerge(connectable: Connectable<I, O>): Flow<O> = callbackFlow {
  val wrappedConnectable = Connectable<I, O> { output ->
    CloseChannelConnection(connectable.connect(output), channel)
  }

  val connection = wrappedConnectable.connect { output ->
    try {
      trySendBlocking(output).onFailure { throw it!! }
    } catch (e: InterruptedException) {
      // EventSource interrupts the source
    }
  }

  onCompletion { channel.close() }
    .collect { connection.accept(it) }

  awaitClose { connection.dispose() }
}

/** [Connection] wrapper that calls [SendChannel.close] upon disposal */
private class CloseChannelConnection<I, O>(
  private val delegate: Connection<I>,
  private val channel: SendChannel<O>
) : Connection<I> {
  override fun accept(value: I) = delegate.accept(value)

  override fun dispose() {
    delegate.dispose()
    channel.close()
  }
}