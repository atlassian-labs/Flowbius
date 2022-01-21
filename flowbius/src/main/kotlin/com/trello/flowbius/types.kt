package com.trello.flowbius

import kotlinx.coroutines.flow.Flow

typealias FlowTransformer<I, O> = (Flow<I>) -> Flow<O>
