package com.pixel.pokecollect

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform