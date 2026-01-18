package com.mobdev.catgram.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val mainImmediate: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val mainImmediate = Dispatchers.Main.immediate
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
}

class TestDispatcherProvider(testDispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val mainImmediate = testDispatcher
    override val io = testDispatcher
    override val default = testDispatcher
}