package com.trello.flowbius

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

internal class MergedFlowTransformer<F, E>(
  private val transformers: Iterable<FlowTransformer<F, E>>
) : FlowTransformer<F, E> {

  override fun invoke(source: Flow<F>): Flow<E> {
    return merge(*transformers.map { source.run(it) }.toTypedArray())
  }
}