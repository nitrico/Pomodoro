package com.github.nitrico.pomodoro.ui

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.nitrico.flux.action.ErrorAction
import com.github.nitrico.flux.store.StoreChange
import com.github.nitrico.pomodoro.R
import com.github.nitrico.pomodoro.action.timer.Finish
import com.github.nitrico.pomodoro.action.timer.Stop
import com.github.nitrico.pomodoro.action.trello.*
import com.github.nitrico.pomodoro.store.TimerStore
import com.github.nitrico.pomodoro.store.TrelloStore
import com.github.nitrico.pomodoro.tool.navigationBarHeight
import kotlinx.android.synthetic.main.fragment_list.*
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.support.v4.withArguments

class ListFragment : FluxFragment(), SwipeRefreshLayout.OnRefreshListener {

    companion object {
        const val KEY_LIST_TYPE = "KEY_LIST_TYPE"
        fun newInstance(listType: Int) = ListFragment().withArguments(KEY_LIST_TYPE to listType)
    }

    private val listType: Int by lazy { arguments.getInt(KEY_LIST_TYPE, -1) }

    override fun getStores() = listOf(TrelloStore, TimerStore)

    override fun onCreateView(li: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View {
        retainInstance = true
        return li.inflate(R.layout.fragment_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // poniendo estas condiciones arriba como lazy no se pierde el scroll al rotar, así sí
        // pero haciendo eso entonces no cambia el número de columnas al rotar :/
        // probar a poner los items o el adapter o los dos como propiedades
        val padding = activity.resources.getDimension(R.dimen.recycler_padding).toInt()
        val landscape = activity.resources.getBoolean(R.bool.landscape)
        val tablet = activity.resources.getBoolean(R.bool.tablet)

        list.setPadding(padding, padding, padding, padding + activity.navigationBarHeight)

        // initialize SwipeRefreshLayout
        with(layout) {
            setOnRefreshListener(this@ListFragment)
            setColorSchemeResources(android.R.color.white)
            setProgressBackgroundColorSchemeResource(R.color.accent)
        }

        // columns
        val cols = getColumnsNumber(tablet, landscape)
        if (cols == 1) list.layoutManager = LinearLayoutManager(activity)
        else list.layoutManager = StaggeredGridLayoutManager(cols, StaggeredGridLayoutManager.VERTICAL)

        // set items
        if (savedInstanceState == null) GetCards() else setItems()
    }

    override fun onError(error: ErrorAction) {
        toast("${error.action} # ${error.throwable.message}")
        layout.isRefreshing = false
    }

    override fun onStoreChanged(change: StoreChange) {
        when (change.store) {
            TrelloStore -> when (change.action) {
                is SelectBoard,
                is ReorderLists,
                is EditCard,
                is DeleteCard,
                is AddComment,
                is LogIn.Success -> getCards()
                is LogOut -> setItems()
                is AddTodo -> if (listType == 0) getCards()
                is GetCards -> setItems()
            }
            TimerStore -> when (change.action) {
                is Stop,
                is Finish -> getCards()
            }
        }
    }

    override fun onRefresh() {
        if (TrelloStore.logged && TrelloStore.listIds?.get(listType) != null) getCards()
        else layout.isRefreshing = false
    }

    private fun getCards() {
        if (view != null && TrelloStore.logged && TrelloStore.listIds?.get(listType) != null) {
            layout.isRefreshing = true
            GetCards()
        }
    }

    private fun setItems() {
        layout.isRefreshing = false
        val items = when (listType) {
            0 -> TrelloStore.todoCards
            1 -> TrelloStore.doingCards
            2 -> TrelloStore.doneCards
            else -> emptyList()
        }
        if (list.adapter == null) list.adapter = CardsAdapter(items, listType == 0)
        else (list.adapter as CardsAdapter).setItems(items)
    }

    private fun getColumnsNumber(tablet: Boolean, landscape: Boolean): Int {
        return if (tablet) { if (landscape) 2 else 3 }
        else { if (landscape) 2 else 1 }
    }

}
