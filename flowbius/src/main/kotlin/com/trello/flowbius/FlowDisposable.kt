package com.trello.flowbius

import com.spotify.mobius.disposables.Disposable
import kotlinx.coroutines.Job

internal class FlowDisposable(private val job: Job) : Disposable {
  override fun dispose() {
    job.cancel()
  }
}
