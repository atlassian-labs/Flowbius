package com.trello.flowbius

import com.spotify.mobius.Mobius
import com.spotify.mobius.MobiusLoop
import com.spotify.mobius.Update
import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.functions.Function
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Factory methods for wrapping Mobius core classes in Flow transformers. */
object FlowMobius {

  /**
   * Create a [MobiusLoop.Builder] to help you configure a [MobiusLoop] before starting it.
   *
   * @param update the [Update] function of the loop
   * @param effectHandler the Flow-based effect handler of the loop
   * @param context optional [CoroutineContext] to control the execution context of the effect handler
   * @param [M] the model type
   * @param [E] the event type
   * @param [F] the effect type
   * @return a [MobiusLoop.Builder] instance that you can further configure before starting the loop
   */
  fun <M, E, F> loop(
    update: Update<M, E, F>,
    context: CoroutineContext = EmptyCoroutineContext,
    effectHandler: FlowTransformer<F, E>
  ): MobiusLoop.Builder<M, E, F> = Mobius.loop(update, effectHandler.asConnectable(context))

  /**
   * Create a [FlowTransformer] that starts from a given model.
   *
   * Every time the resulting Flow is subscribed to, a new [MobiusLoop] will be started from
   * the given model.
   *
   * @param loopFactory gets invoked for each subscription, to create a new MobiusLoop instance
   * @param startModel the starting point for each new loop
   * @param M the model type
   * @param E the event type
   * @param F the effect type
   * @return a transformer from event to model that you can connect to your UI
   */
  fun <M, E, F> loopFrom(
    loopFactory: MobiusLoop.Factory<M, E, F>,
    startModel: M,
    startEffects: Set<F>? = null
  ): FlowTransformer<E, M> = FlowMobiusLoop(loopFactory, startModel, startEffects)

  /**
   * Builder for a type-routing effect handler.
   *
   * Register handlers for different subtypes of [F] using the [add] methods, and call [build]
   * to create an instance of the effect handler. You can then create a loop with the
   * router as the effect handler using [loop].
   *
   * The handler will look at the type of each incoming effect object and try to find a
   * registered handler for that particular subtype of [F]. If a handler is found, it will be given
   * the effect object, otherwise an exception will be thrown.
   *
   * All the classes that the effect router know about must have a common type [F]. Note that
   * instances of the builder are mutable and not thread-safe.
   */
  class SubtypeEffectHandlerBuilder<F : Any, E : Any> internal constructor() {

    private val effectPerformerMap = mutableMapOf<Class<*>, FlowTransformer<F, E>>()

    private var fatalErrorHandler: (suspend (Any, Throwable) -> Unit)? = null

    inline fun <reified G : F> addAction(crossinline action: suspend () -> Unit) {
      addTransformer(G::class.java) { source ->
        source
          .onEach { action() }
          .mapNotNull { null } // Do not emit anything
      }
    }

    inline fun <reified G : F> addConsumer(consumer: Consumer<G>) {
      addConsumer<G> { consumer.accept(it) }
    }

    inline fun <reified G : F> addConsumer(noinline consumer: suspend (G) -> Unit) {
      addTransformer(G::class.java) { source ->
        source
          .onEach(consumer)
          .mapNotNull { null } // Do not emit anything
      }
    }

    inline fun <reified G : F> addFunction(function: Function<G, E>) {
      addFunction<G> { function.apply(it) }
    }

    inline fun <reified G : F> addFunction(noinline function: suspend (G) -> E) {
      addTransformer(G::class.java) { source ->
        source.map(function)
      }
    }

    inline fun <reified G : F> addTransformer(noinline effectHandler: FlowTransformer<G, E>) {
      addTransformer(G::class.java, effectHandler)
    }

    fun <G : F> addTransformer(
      effectClass: Class<G>,
      effectHandler: FlowTransformer<G, E>
    ): SubtypeEffectHandlerBuilder<F, E> = apply {
      requireNoEffectCollisions(effectClass)

      effectPerformerMap[effectClass] = { effects ->
        effects
          .filterIsInstance(effectClass)
          .run(effectHandler)
          .catch { e ->
            fatalErrorHandler?.invoke(effectHandler, e)
              ?: throw EffectHandlerException.createFromEffectHandler(effectHandler, e)

            // If we did custom handling, re-throw (provided they did not already do it)
            throw e
          }
      }
    }

    private fun <G : F> requireNoEffectCollisions(effectClass: Class<G>) {
      effectPerformerMap.keys.forEach { cls ->
        require(!cls.isAssignableFrom(effectClass) && !effectClass.isAssignableFrom(cls)) {
          "Effect classes may not be assignable to each other, collision found: " +
              "${effectClass.simpleName} <-> ${cls.simpleName}"
        }
      }
    }

    fun withFatalErrorHandler(fatalErrorHandler: (suspend (Any, Throwable) -> Unit)) = apply {
      this.fatalErrorHandler = fatalErrorHandler
    }

    internal fun build(): FlowTransformer<F, E> {
      return FlowMobiusEffectRouter(effectPerformerMap.keys, effectPerformerMap.values)
    }

    /** Version of [filterIsInstance] that works with non-reified [Class] references */
    @Suppress("UNCHECKED_CAST")
    private fun <R> Flow<Any>.filterIsInstance(clazz: Class<R>) = filter { clazz.isInstance(it) } as Flow<R>
  }
}

/**
 * Use this builder to configure [FlowMobius.SubtypeEffectHandlerBuilder].
 */
fun <F : Any, E : Any> subtypeEffectHandler(
  configure: FlowMobius.SubtypeEffectHandlerBuilder<F, E>.() -> Unit
): FlowTransformer<F, E> = FlowMobius.SubtypeEffectHandlerBuilder<F, E>().apply(configure).build()
