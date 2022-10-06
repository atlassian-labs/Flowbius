package com.trello.flowbius

import app.cash.turbine.test
import com.spotify.mobius.functions.Function
import com.spotify.mobius.test.RecordingConsumer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
class FlowMobiusEffectRouterTest {

  private lateinit var consumer: RecordingConsumer<TestEffect.C>
  private lateinit var action: TestAction
  private lateinit var router: FlowTransformer<TestEffect, TestEvent>

  @Before
  fun setup() {
    consumer = RecordingConsumer()
    action = TestAction()
    router = subtypeEffectHandler {
      addTransformer<TestEffect.A> { source -> source.map { TestEvent.A(it.id) } }
      addTransformer<TestEffect.B> { source -> source.map { TestEvent.B(it.id) } }
      addConsumer<TestEffect.C>(consumer)
      addAction<TestEffect.D>(action::invoke)
      addFunction<TestEffect.E> { e -> TestEvent.E(e.id) }
      addFunction(Function<TestEffect.F, TestEvent> { value -> TestEvent.F(value.id) })
    }
  }

  @Test
  fun shouldRouteEffectToPerformer() = runBlocking {
    flowOf(TestEffect.A(456))
      .run(router)
      .test {
        assertEquals(TestEvent.A(456), awaitItem())
        awaitComplete()
      }
  }

  @Test
  fun shouldRouteEffectToMobiusConsumer() = runBlocking {
    flowOf(TestEffect.C(456))
      .run(router)
      .collect()

    consumer.assertValues(TestEffect.C(456))
  }

  @Test
  fun shouldRouteEffectToConsumer() = runBlocking {
    flowOf(TestEffect.C(456))
      .run(router)
      .collect()

    consumer.assertValues(TestEffect.C(456))
  }

  @Test
  fun shouldRunActionOnConsumer() = runBlocking {
    var lastValue: TestEffect.C? = null

    flowOf(TestEffect.C(456))
      .run(subtypeEffectHandler<TestEffect, TestEvent> {
        addConsumer<TestEffect.C> { lastValue = it }
      })
      .collect()

    assertEquals(TestEffect.C(456), lastValue)
  }

  @Test
  fun shouldInvokeFunctionAndEmitEvent() = runBlocking {
    flowOf(TestEffect.E(123))
      .run(router)
      .test {
        assertEquals(TestEvent.E(123), awaitItem())
        awaitComplete()
      }
  }

  @Test
  fun shouldInvokeMobiusFunctionAndEmitEvent() = runBlocking {
    flowOf(TestEffect.F(123))
      .run(router)
      .test {
        assertEquals(TestEvent.F(123), awaitItem())
        awaitComplete()
      }
  }

  @Test
  fun shouldFailForUnhandledEffect() = runBlocking {
    flowOf(TestEffect.Unhandled)
      .run(router)
      .test {
        val error = awaitError() as UnknownEffectException
        assertEquals(TestEffect.Unhandled, error.effect)
      }
  }

  @Test
  fun shouldReportEffectClassCollisionWhenAddingSuperclass() {
    subtypeEffectHandler<TestEffect, TestEvent> {
      addAction<TestEffect.Child> { }

      assertThrows(IllegalArgumentException::class.java) {
        addAction<TestEffect.Parent> { }
      }
    }
  }

  @Test
  fun shouldReportEffectClassCollisionWhenAddingSubclass() {
    subtypeEffectHandler<TestEffect, TestEvent> {
      addAction<TestEffect.Parent> { }

      assertThrows(IllegalArgumentException::class.java) {
        addAction<TestEffect.Child> { }
      }
    }
  }

  @Test
  fun shouldSupportCustomErrorHandler() = runBlocking {
    val expectedException = RuntimeException("expected!")

    val router = subtypeEffectHandler<TestEffect, TestEvent> {
      addFunction<TestEffect.A> { throw expectedException }

      withFatalErrorHandler { _, throwable ->
        assertEquals(expectedException, throwable)
      }
    }

    flowOf(TestEffect.A(1))
      .run(router)
      .test {
        val error = awaitError()
        assertTrue(error is RuntimeException)
        assertEquals("expected!", error.message)
      }
  }

  private class TestAction {
    var runCount = 0

    fun invoke() {
      runCount++
    }
  }

  sealed class TestEffect {
    data class A(val id: Int) : TestEffect()
    data class B(val id: Int) : TestEffect()
    data class C(val id: Int) : TestEffect()
    data class D(val id: Int) : TestEffect()
    data class E(val id: Int) : TestEffect()
    data class F(val id: Int) : TestEffect()

    object Unhandled : TestEffect()

    open class Parent : TestEffect()
    object Child : Parent()
  }

  sealed class TestEvent {
    data class A(val id: Int) : TestEvent()
    data class B(val id: Int) : TestEvent()
    data class E(val id: Int) : TestEvent()
    data class F(val id: Int) : TestEvent()
  }

}