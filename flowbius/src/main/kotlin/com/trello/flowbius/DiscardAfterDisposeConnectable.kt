package com.trello.flowbius

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.disposables.CompositeDisposable
import com.spotify.mobius.disposables.Disposable
import com.spotify.mobius.functions.Consumer

/**
 * A [Connectable] that ensures that [Connection]s created by the wrapped [Connectable] don't emit or receive any
 * values after being disposed.
 *
 * This only acts as a safeguard, you still need to make sure that the Connectable disposes of resources correctly.
 */
internal class DiscardAfterDisposeConnectable<I, O>(private val actual: Connectable<I, O>) : Connectable<I, O> {

  override fun connect(output: Consumer<O>): Connection<I> {
    val safeOutput = output.discardAfterDispose()
    val input = actual.connect(safeOutput)
    val safeInput = input.discardAfterDispose()

    val disposable = CompositeDisposable.from(safeInput, safeOutput)

    return object : Connection<I> {
      override fun accept(effect: I) = safeInput.accept(effect)
      override fun dispose() = disposable.dispose()
    }
  }

  /**
   * Wraps a [Connection] or a [Consumer] and blocks them from receiving any further
   * values after the wrapper has been disposed.
   */
  private class DiscardAfterDisposeWrapper<I>(
    private val consumer: Consumer<I>,
    private val disposable: Disposable?
  ) : Consumer<I>, Disposable {

    @Volatile
    private var disposed = false

    override fun accept(effect: I) {
      if (!disposed) {
        consumer.accept(effect)
      }
    }

    override fun dispose() {
      disposed = true
      disposable?.dispose()
    }
  }

  private fun <I> Connection<I>.discardAfterDispose() = DiscardAfterDisposeWrapper(this, this)

  private fun <I> Consumer<I>.discardAfterDispose() = DiscardAfterDisposeWrapper(this, null)

}