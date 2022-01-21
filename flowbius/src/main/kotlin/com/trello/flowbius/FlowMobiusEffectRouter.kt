package com.trello.flowbius

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.*

internal class FlowMobiusEffectRouter<F : Any, E : Any>(
  handledEffectClasses: Set<Class<*>>,
  effectPerformers: Collection<FlowTransformer<F, E>>
) : FlowTransformer<F, E> {

  private val effectClasses: Set<Class<*>> = Collections.unmodifiableSet(handledEffectClasses)

  private val mergedTransformer = MergedFlowTransformer(
    Collections.unmodifiableList(effectPerformers.toList() + ::unhandledEffectHandler)
  )

  private fun unhandledEffectHandler(source: Flow<F>): Flow<E> {
    return source
      .filter { effect -> effectClasses.none { effectClass -> effectClass.isAssignableFrom(effect::class.java) } }
      .map { throw UnknownEffectException(it) }
  }

  override fun invoke(source: Flow<F>): Flow<E> = source.run(mergedTransformer)

}