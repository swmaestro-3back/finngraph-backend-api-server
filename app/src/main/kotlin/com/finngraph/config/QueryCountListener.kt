package com.finngraph.config

import org.jooq.ExecuteContext
import org.jooq.ExecuteListener

object RequestQueryCounter {

    private val holder = ThreadLocal<IntArray>()

    fun begin() = holder.set(IntArray(1))

    fun increment() {
        holder.get()?.let { it[0]++ }
    }

    fun current(): Int = holder.get()?.get(0) ?: 0

    fun end(): Int = current().also { holder.remove() }
}

class QueryCountListener : ExecuteListener {
    override fun executeStart(ctx: ExecuteContext) = RequestQueryCounter.increment()
}
