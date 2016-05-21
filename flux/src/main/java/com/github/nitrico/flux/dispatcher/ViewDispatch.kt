package com.github.nitrico.flux.dispatcher

import com.github.nitrico.flux.action.ErrorAction
import com.github.nitrico.flux.store.Store
import com.github.nitrico.flux.store.StoreChange

interface ViewDispatch {

    fun register() = Dispatcher.registerView(this)
    fun unregister() = Dispatcher.unregisterView(this)

    fun getStores(): List<Store>
    fun getPauseableStores(): List<Store> = emptyList()

    fun onStoreChanged(change: StoreChange)
    fun onError(error: ErrorAction)

}
