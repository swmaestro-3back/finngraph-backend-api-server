package com.finngraph.user.port

import com.finngraph.user.model.Nickname
import com.finngraph.user.model.UserView

interface UserPort {

    fun create(nickname: Nickname): Long
    fun findById(id: Long): UserView?
    fun updateNickname(id: Long, nickname: Nickname): Boolean
    fun delete(id: Long): Boolean
}
