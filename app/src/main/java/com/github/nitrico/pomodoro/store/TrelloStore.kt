package com.github.nitrico.pomodoro.store

import android.content.Context
import android.preference.PreferenceManager
import com.github.nitrico.flux.action.Action
import com.github.nitrico.flux.store.Store
import com.github.nitrico.pomodoro.action.trello.*
import com.github.nitrico.pomodoro.data.*

/**
 * Singleton object used to keep Trello account status
 */
object TrelloStore : Store() {

    private const val KEY_TOKEN = "KEY_TOKEN"
    private const val KEY_SECRET = "KEY_SECRET"
    private const val KEY_USER_ID = "KEY_USER_ID"
    private const val KEY_USER_EMAIL = "KEY_USER_EMAIL"
    private const val KEY_USER_FULL_NAME = "KEY_USER_FULL_NAME"
    private const val KEY_USER_AVATAR_HASH = "KEY_USER_AVATAR_HASH"
    private const val KEY_BOARD_ID = "KEY_BOARD_ID"
    private const val KEY_LIST_ID_TODO = "KEY_LIST_ID_TODO"
    private const val KEY_LIST_ID_DOING = "KEY_LIST_ID_DOING"
    private const val KEY_LIST_ID_DONE = "KEY_LIST_ID_DONE"

    private lateinit var context: Context
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private var secret: String? = null
    private var token: String? = null; private set

    var logged = false; private set
    var user: TrelloMember? = null; private set
    var board: TrelloBoard? = null; private set
    var boardId: String? = null; private set
    var todoListId: String? = null; private set
    var doingListId: String? = null; private set
    var doneListId: String? = null; private set
    var boards: List<TrelloBoard> = emptyList(); private set
    var lists: List<TrelloList> = emptyList(); private set
    var todoCards: List<TrelloCard> = emptyList(); private set
    var doingCards: List<TrelloCard> = emptyList(); private set
    var doneCards: List<TrelloCard> = emptyList(); private set
    var listIds: List<String>? = null; private set
    val boardNames: List<String>
        get() {
            val names = mutableListOf<String>()
            boards.forEach { names.add(it.name) }
            return names
        }

    /**
     * Initialize the object with a Context
     * This method must be called before any other of this object
     */
    @Synchronized fun init(context: Context) {
        this.context = context
        loadSessionFromPreferences()
        if (user != null && token != null && secret != null) {
            Trello.consumer.setTokenWithSecret(token, secret)
            GetUser()
        }
    }

    override fun onAction(action: Action) = when (action) {
        is LogIn.Success -> logIn(action)
        is LogOut -> logOut(action)
        is GetUser -> setUser(action)
        is GetCards -> setCards(action)
        is SelectBoard -> setupBoard(action.board, action)
        is ReorderLists -> setupLists(action.todoId, action.doingId, action.doneId, action)
        is AddTodo, is AddComment, is EditCard, is MoveCard, is DeleteCard -> postChange(action)
        else -> { }
    }

    @Synchronized private fun loadSessionFromPreferences() {
        token = preferences.getString(KEY_TOKEN, null)
        secret = preferences.getString(KEY_SECRET, null)
        boardId = preferences.getString(KEY_BOARD_ID, null)
        todoListId = preferences.getString(KEY_LIST_ID_TODO, null)
        doingListId = preferences.getString(KEY_LIST_ID_DOING, null)
        doneListId = preferences.getString(KEY_LIST_ID_DONE, null)

        // load User
        val userId = preferences.getString(KEY_USER_ID, null)
        val userEmail = preferences.getString(KEY_USER_EMAIL, null)
        val userFullName = preferences.getString(KEY_USER_FULL_NAME, null)
        val userAvatarHash = preferences.getString(KEY_USER_AVATAR_HASH, null)

        if (userId != null && userEmail != null && userFullName != null && userAvatarHash != null) {
            user = TrelloMember(userId, userEmail, userFullName, userAvatarHash)
        }
    }

    @Synchronized private fun logIn(action: LogIn.Success) {
        token = Trello.consumer.token
        secret = Trello.consumer.tokenSecret
        preferences.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_SECRET, secret)
                .commit()
        postChange(action)
        GetUser()
    }

    @Synchronized private fun logOut(action: LogOut) {
        logged = false
        token = null
        secret = null
        user = null
        todoListId = null
        doingListId = null
        doneListId = null
        listIds = null
        todoCards = emptyList()
        doingCards = emptyList()
        doneCards = emptyList()
        preferences.edit()
                .putString(KEY_TOKEN, null)
                .putString(KEY_SECRET, null)
                .putString(KEY_USER_ID, null)
                .putString(KEY_USER_EMAIL, null)
                .putString(KEY_USER_FULL_NAME, null)
                .putString(KEY_USER_AVATAR_HASH, null)
                .putString(KEY_BOARD_ID, null)
                .putString(KEY_LIST_ID_TODO, null)
                .putString(KEY_LIST_ID_DOING, null)
                .putString(KEY_LIST_ID_DONE, null)
                .commit()
        postChange(action)
    }

    @Synchronized private fun setUser(action: GetUser) {
        user = action.user
        boards = action.boards
        preferences.edit()
                .putString(KEY_USER_ID, user!!.id)
                .putString(KEY_USER_EMAIL, user!!.email)
                .putString(KEY_USER_FULL_NAME, user!!.fullName)
                .putString(KEY_USER_AVATAR_HASH, user!!.avatarHash)
                .commit()
        // use latest used board or first one if never used one
        if (boardId != null) {
            val position = boards.findIndexById(boardId!!)
            if (position != -1) setupBoard(boards[position], action)
            else setupBoard(boards[0], action)
        }
        else setupBoard(boards[0], action)
        postChange(action)
    }

    @Synchronized private fun setupBoard(board: TrelloBoard, action: Action) {
        this.board = board
        boardId = board.id
        lists = board.lists
        preferences.edit().putString(KEY_BOARD_ID, boardId).commit()
        // use latest used lists (checking they belong to the current board) or first ones
        if (lists.findIndexById(todoListId) != -1
                && lists.findIndexById(doingListId) != -1
                && lists.findIndexById(doneListId) != -1) {
            setupLists(todoListId!!, doingListId!!, doneListId!!, action)
        }
        else setupLists(lists[0].id, lists[1].id, lists[2].id, action)
    }

    @Synchronized private fun setupLists(todoId: String, doingId: String, doneId: String, action: Action) {
        todoListId = todoId
        doingListId = doingId
        doneListId = doneId
        listIds = listOf(todoListId!!, doingListId!!, doneListId!!)

        val todoPos = lists.findIndexById(todoId)
        val doingPos = lists.findIndexById(doingId)
        val donePos = lists.findIndexById(doneId)

        if (todoPos != -1 && doingPos != -1 && donePos != -1) {
            val temp = mutableListOf(lists[todoPos], lists[doingPos], lists[donePos])
            lists.forEach { if (!temp.contains(it)) temp.add(it) }
            lists = temp.toList()
            preferences.edit()
                    .putString(KEY_LIST_ID_TODO, todoListId)
                    .putString(KEY_LIST_ID_DOING, doingListId)
                    .putString(KEY_LIST_ID_DONE, doneListId)
                    .commit()
        }

        logged = true
        postChange(action)
        //GetCards()
    }

    @Synchronized private fun setCards(action: GetCards) {
        todoCards = action.todoCards
        doingCards = action.doingCards
        doneCards = action.doneCards
        postChange(action)
    }

    private fun List<TrelloItem>.findIndexById(id: String?): Int {
        if (id == null) return -1
        for (i in 0..size-1) { if (get(i).id.equals(id)) return i }
        return -1
    }

}
