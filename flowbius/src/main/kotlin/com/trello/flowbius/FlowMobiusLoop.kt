package com.trello.flowbius

import com.spotify.mobius.MobiusLoop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

@ExperimentalCoroutinesApi
internal class FlowMobiusLoop<E, M, F>(
  private val loopFactory: MobiusLoop.Factory<M, E, F>,
  private val startModel: M,
  private val startEffects: Set<F>?
) : FlowTransformer<E, M> {
  override fun invoke(events: Flow<E>): Flow<M> = callbackFlow {
    val loop = if (startEffects.isNullOrEmpty()) loopFactory.startFrom(startModel)
    else loopFactory.startFrom(startModel, startEffects)

    loop.observe { newModel ->
      try {
        trySendBlocking(newModel).onFailure { throw it!! }
      } catch (e: InterruptedException) {
        // EventSource interrupts the source
      }
    }

    events
      .onCompletion { channel.close() }
      .catch { throwable -> throw UnrecoverableIncomingException(throwable) }
      .collect { event -> loop.dispatchEvent(event) }

    awaitClose {
      loop.dispose()
    }
  }
}