package com.trello.flowbius

import com.spotify.mobius.EventSource
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Converts the given [Flow] to an [EventSource].
 *
 * @param context an optional context that controls the execution context of calls to [Consumer] methods.
 * @param E the event type
 * @return an [EventSource] based on the provided [Flow]
 */
@ExperimentalCoroutinesApi
fun <E> Flow<E>.asEventSource(context: CoroutineContext = EmptyCoroutineContext): EventSource<E> =
  EventSource<E> { eventConsumer ->
    val job = GlobalScope.launch(Dispatchers.Unconfined + context, start = CoroutineStart.ATOMIC) {
      collect(eventConsumer::accept)
    }

    return@EventSource FlowDisposable(job)
  }

/**
 * Converts the given [EventSource] to a [Flow].
 *
 * @param E the event type
 * @return a [Flow] based on the provided [EventSource]
 */
@ExperimentalCoroutinesApi
fun <E> EventSource<E>.asFlow(): Flow<E> = callbackFlow {
  val eventConsumer = Consumer<E> { event ->
    try {
      trySendBlocking(event).onFailure { throw it!! }
    } catch (e: InterruptedException) {
      // EventSource interrupts the source
    }
  }

  val disposable = subscribe(eventConsumer)
  awaitClose { disposable.dispose() }
}
