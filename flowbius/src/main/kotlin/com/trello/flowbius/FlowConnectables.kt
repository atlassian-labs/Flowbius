package com.trello.flowbius

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
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
    val sharedFlow = MutableSharedFlow<I>(extraBufferCapacity = Int.MAX_VALUE)

    val job = GlobalScope.launch(Dispatchers.Unconfined + context, start = CoroutineStart.ATOMIC) {
      sharedFlow
        .run { mapper(this) }
        .collect { output.accept(it) }
    }

    return object : Connection<I> {
      override fun accept(value: I) {
        sharedFlow.tryEmit(value)
      }

      override fun dispose() = job.cancel()
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