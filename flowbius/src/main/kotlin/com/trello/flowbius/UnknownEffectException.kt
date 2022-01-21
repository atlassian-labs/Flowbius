package com.trello.flowbius

/**
 * Indicates that a [FlowMobiusEffectRouter] has received an effect that it hasn't received
 * configuration for. This is a programmer error.
 */
class UnknownEffectException internal constructor(internal val effect: Any) : RuntimeException(effect.toString())