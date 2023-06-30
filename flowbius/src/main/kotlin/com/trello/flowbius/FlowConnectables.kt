package com.trello.flowbius

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val sharedFlow = MutableSharedFlow<I>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    val connectableJob = Job()
    val connectableScope = CoroutineScope(connectableJob + Dispatchers.Unconfined + context)

    connectableScope.launch(start = CoroutineStart.ATOMIC) {
      sharedFlow
        .run {
          mapper(this)
        }
        .collect { output.accept(it) }
    }

    return object : Connection<I> {
      private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is NoSuchElementException) {
          // flow was cancelled before receiving a subscriber, no need to handle
        } else {
          throw throwable
        }
      }

      override fun accept(value: I) {
        connectableScope.launch(exceptionHandler) {
          // Wait for a subscription before emitting!
          sharedFlow.subscriptionCount.first { it > 0 }
          sharedFlow.emit(value)
        }
      }

      override fun dispose() {
        connectableJob.cancel()
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