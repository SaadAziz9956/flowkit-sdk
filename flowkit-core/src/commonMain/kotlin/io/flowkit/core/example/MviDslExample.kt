package io.flowkit.core.example

import io.flowkit.core.MviContainer
import io.flowkit.core.MviIntent
import io.flowkit.core.MviSideEffect
import io.flowkit.core.MviState
import io.flowkit.core.ReducerResult
import io.flowkit.core.just
import io.flowkit.core.noChange
import io.flowkit.core.sameStateWith
import io.flowkit.core.simpleState
import io.flowkit.core.stateManager
import io.flowkit.core.stateWith
import kotlinx.coroutines.CoroutineScope

/**
 * Examples showing how to use the enhanced MVI DSL
 */
object MviDslExamples {

    // Sample state, intents, and side effects
    sealed class UserState : MviState {
        object Loading : UserState()
        data class Success(val user: User) : UserState()
        data class Error(val message: String) : UserState()
    }

    sealed class UserIntent : MviIntent {
        data class LoadUser(val userId: String) : UserIntent()
        object RefreshUser : UserIntent()
        data class UpdateName(val newName: String) : UserIntent()
        object Logout : UserIntent()
    }

    sealed class UserSideEffect : MviSideEffect {
        data class LoadUserFromApi(val userId: String) : UserSideEffect()
        data class ShowToast(val message: String) : UserSideEffect()
        object NavigateToLogin : UserSideEffect()
        data class SaveUserToCache(val user: User) : UserSideEffect()
    }

    data class User(val id: String, val name: String, val email: String)

    /**
     * 1. Full MVI setup with natural language
     */
    fun createUserViewModel(scope: CoroutineScope): MviContainer<UserState, UserIntent, UserSideEffect> {
        return scope.stateManager {
            startsWith(UserState.Loading)
            handleIntents {
                on<UserIntent.LoadUser> { state, intent ->
                    stateWith(
                        newState = UserState.Loading,
                        sideEffect = UserSideEffect.LoadUserFromApi(intent.userId)
                    )
                }

                on<UserIntent.RefreshUser> { state, intent ->
                    when (state) {
                        is UserState.Success -> stateWith(
                            newState = UserState.Loading,
                            sideEffect = UserSideEffect.LoadUserFromApi(state.user.id)
                        )
                        else -> noChange(state)
                    }
                }

                on<UserIntent.UpdateName> { state, intent ->
                    when (state) {
                        is UserState.Success -> {
                            val updatedUser = state.user.copy(name = intent.newName)
                            stateWith(
                                newState = UserState.Success(updatedUser),
                                sideEffects = listOf(
                                    UserSideEffect.SaveUserToCache(updatedUser),
                                    UserSideEffect.ShowToast("Name updated!")
                                )
                            )
                        }
                        else -> noChange(state)
                    }
                }

                on<UserIntent.Logout> { state, intent ->
                    stateWith(
                        newState = UserState.Loading,
                        sideEffect = UserSideEffect.NavigateToLogin
                    )
                }
            }
            withLogging()
        }
    }

    /**
     * 2. Simple MVI setup without side effects
     */
    fun createSimpleCounterViewModel(scope: CoroutineScope): MviContainer<CounterState, CounterIntent, Nothing> {
        return scope.simpleState(CounterState(0)) {
            on<CounterIntent.Increment> { state, _ ->
                state.copy(count = state.count + 1)
            }

            on<CounterIntent.Decrement> { state, _ ->
                state.copy(count = state.count - 1)
            }

            on<CounterIntent.Reset> { _, _ ->
                CounterState(0)
            }

            withLogging()
        }
    }

    /**
     * 3. Alternative syntax examples
     */
    fun createAlternativeUserViewModel(scope: CoroutineScope): MviContainer<UserState, UserIntent, UserSideEffect> {
        return scope.stateManager {
            // Alternative natural language
            beginsWith(UserState.Loading)

            // Alternative intent handling syntax
            whenIntentReceived {
                when_<UserIntent.LoadUser> { state, intent ->
                    just(UserState.Loading)  // Just state change
                }

                handle<UserIntent.RefreshUser> { state, intent ->
                    sameStateWith(state, UserSideEffect.ShowToast("Refreshing..."))
                }

                process<UserIntent.UpdateName> { state, intent ->
                    // Complex logic with pattern matching
                    when (state) {
                        is UserState.Success -> {
                            val user = state.user.copy(name = intent.newName)
                            ReducerResult(
                                newState = UserState.Success(user),
                                sideEffects = listOf(UserSideEffect.SaveUserToCache(user))
                            )
                        }
                        else -> noChange(state)
                    }
                }

                // Ignore certain intents
                ignore<UserIntent.Logout>()
            }

            logged() // Alternative to withLogging()
        }
    }

    /**
     * 4. State-only and effect-only handlers
     */
    fun createSpecializedViewModel(scope: CoroutineScope): MviContainer<UserState, UserIntent, UserSideEffect> {
        return scope.stateManager {
            startsWith(UserState.Loading)
            handleIntents {
                // Only change state, no side effects
                onJustState<UserIntent.LoadUser> { state, intent ->
                    UserState.Loading
                }

                // Only emit side effects, keep current state
                onJustEffect<UserIntent.RefreshUser> { state, intent ->
                    listOf(UserSideEffect.ShowToast("Refreshing..."))
                }

                // Single side effect
                onSingleEffect<UserIntent.UpdateName> { state, intent ->
                    UserSideEffect.ShowToast("Name will be updated...")
                }
            }
            withLogging()
        }
    }

    /**
     * 5. Real-world complex example - Shopping Cart
     */
    fun createShoppingCartViewModel(scope: CoroutineScope): MviContainer<CartState, CartIntent, CartSideEffect> {
        return scope.stateManager {
            startsWith(CartState.Empty)

            handleIntents {
                on<CartIntent.AddItem> { state, intent ->
                    when (state) {
                        is CartState.Empty -> stateWith(
                            newState = CartState.WithItems(listOf(intent.item)),
                            sideEffect = CartSideEffect.ShowItemAdded(intent.item.name)
                        )

                        is CartState.WithItems -> {
                            val updatedItems = state.items + intent.item
                            stateWith(
                                newState = CartState.WithItems(updatedItems),
                                sideEffects = listOf(
                                    CartSideEffect.ShowItemAdded(intent.item.name),
                                    CartSideEffect.UpdateCartBadge(updatedItems.size),
                                    CartSideEffect.SaveCartToStorage(updatedItems)
                                )
                            )
                        }

                        is CartState.Loading -> noChange(state)
                    }
                }

                on<CartIntent.RemoveItem> { state, intent ->
                    when (state) {
                        is CartState.WithItems -> {
                            val updatedItems = state.items.filter { it.id != intent.itemId }
                            when {
                                updatedItems.isEmpty() -> stateWith(
                                    newState = CartState.Empty,
                                    sideEffect = CartSideEffect.ShowToast("Cart is now empty")
                                )
                                else -> stateWith(
                                    newState = CartState.WithItems(updatedItems),
                                    sideEffects = listOf(
                                        CartSideEffect.ShowToast("Item removed"),
                                        CartSideEffect.UpdateCartBadge(updatedItems.size)
                                    )
                                )
                            }
                        }
                        else -> noChange(state)
                    }
                }

                on<CartIntent.ClearCart> { state, intent ->
                    stateWith(
                        newState = CartState.Empty,
                        sideEffect = CartSideEffect.ShowToast("Cart cleared")
                    )
                }

                on<CartIntent.Checkout> { state, intent ->
                    when (state) {
                        is CartState.WithItems -> stateWith(
                            newState = CartState.Loading,
                            sideEffect = CartSideEffect.ProcessCheckout(state.items)
                        )
                        else -> sameStateWith(
                            currentState = state,
                            sideEffect = CartSideEffect.ShowToast("Cart is empty")
                        )
                    }
                }
            }

            withLogging()
        }
    }
}

// Sample data classes for examples
data class CounterState(val count: Int) : MviState

sealed class CounterIntent : MviIntent {
    object Increment : CounterIntent()
    object Decrement : CounterIntent()
    object Reset : CounterIntent()
}

// Shopping cart example
sealed class CartState : MviState {
    object Empty : CartState()
    object Loading : CartState()
    data class WithItems(val items: List<CartItem>) : CartState()
}

sealed class CartIntent : MviIntent {
    data class AddItem(val item: CartItem) : CartIntent()
    data class RemoveItem(val itemId: String) : CartIntent()
    object ClearCart : CartIntent()
    object Checkout : CartIntent()
}

sealed class CartSideEffect : MviSideEffect {
    data class ShowItemAdded(val itemName: String) : CartSideEffect()
    data class ShowToast(val message: String) : CartSideEffect()
    data class UpdateCartBadge(val count: Int) : CartSideEffect()
    data class SaveCartToStorage(val items: List<CartItem>) : CartSideEffect()
    data class ProcessCheckout(val items: List<CartItem>) : CartSideEffect()
}

data class CartItem(val id: String, val name: String, val price: Double)