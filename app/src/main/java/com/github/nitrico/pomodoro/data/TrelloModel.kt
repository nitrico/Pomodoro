package com.github.nitrico.pomodoro.data

data class TrelloMember(
        val id: String,
        val email: String,
        val username: String,
        val fullName: String)

data class TrelloBoard(
        val id: String,
        val name: String,
        val lists: List<TrelloList>?)

data class TrelloList(
        val id: String,
        val name: String,
        val cards: List<TrelloCard>?)

data class TrelloCard(
        val id: String,
        val name: String)
