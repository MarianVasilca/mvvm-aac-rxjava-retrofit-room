package tech.ascendio.mvvmstarter.data.repositories

import android.util.Log
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import tech.ascendio.mvvmstarter.data.api.ApiService
import tech.ascendio.mvvmstarter.data.db.dao.BookDao
import tech.ascendio.mvvmstarter.data.vo.Book
import tech.ascendio.mvvmstarter.utilities.schedulers.MainScheduler
import tech.ascendio.mvvmstarter.utilities.schedulers.Scheduler

/*
 * Copyright (C) 2018 Marian Vasilca@Ascendio TechVision
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class BookRepository constructor(
        private val apiService: ApiService,
        private val bookDao: BookDao,
        private val mainScheduler: MainScheduler,
        private val networkScheduler: Scheduler,
        private val ioScheduler: Scheduler
) {
    fun startListeningForBooks(): Flowable<List<Book>> =
            bookDao.listenForBooks()
                    .distinctUntilChanged()
                    .observeOn(mainScheduler.asRxScheduler())
                    .replay(1)
                    .autoConnect(0)

    fun fetch() {
        createSaveBooksTask().subscribeBy(onSuccess = {
            // Success
        }, onError = { throwable ->
            Log.i(TAG, "Fetching failed", throwable)
        })
    }

    private fun createSaveBooksTask(): Single<Unit> = Single.create { emitter ->
        apiService.getBooks()
                .subscribeOn(networkScheduler.asRxScheduler())
                .observeOn(ioScheduler.asRxScheduler())
                .subscribeBy(onSuccess = {
                    if (it == null) {
                        emitter.onSuccess(Unit)
                        return@subscribeBy
                    }
                    bookDao.insert(it)
                    emitter.onSuccess(Unit)
                }, onError = {
                    emitter.onError(it)
                })
    }

    companion object {
        const val TAG = "BookRepository"

        // For Singleton instantiation
        @Volatile
        private var instance: BookRepository? = null

        fun getInstance(apiService: ApiService, bookDao: BookDao, mainScheduler: MainScheduler,
                        ioScheduler: Scheduler, networkScheduler: Scheduler) =
                instance ?: synchronized(this) {
                    instance ?: BookRepository(apiService, bookDao, mainScheduler, networkScheduler,
                            ioScheduler).also { instance = it }
                }
    }
}