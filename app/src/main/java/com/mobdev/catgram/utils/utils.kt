package com.mobdev.catgram.utils

import com.mobdev.catgram.network.BreedInfo


fun List<BreedInfo>.getNameAndDescription(): Pair<String, String>? {
    if (isEmpty()) {
        return null
    }

    return first().let { it.name to it.description }
}